package org.diggio.obdiggio.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import org.diggio.obdiggio.core.obd.Transport
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Device BLE individuato dalla scansione. */
data class BleDevice(val name: String, val address: String, val rssi: Int) {
    fun looksLikeObd(): Boolean {
        val n = name.uppercase()
        return listOf("OBD", "ELM", "ICAR", "VGATE", "VIECAR", "VLINK").any { it in n }
    }
    override fun toString() = "$name [$address] $rssi dBm"
}

/**
 * Trasporto ELM327 su Bluetooth LE con le API native di Android, pensato per
 * adattatori come il Vgate iCar Pro BLE 4.0.
 *
 * Individua automaticamente la caratteristica di *notify* (da cui arrivano le
 * risposte) e quella di *write* (su cui inviare i comandi): prima prova gli UUID
 * noti dell'iCar Pro, poi ricade sulle proprietà GATT delle caratteristiche.
 *
 * NB: chi usa questa classe deve aver già ottenuto i permessi runtime BLE
 * (BLUETOOTH_SCAN/CONNECT su Android 12+, ACCESS_FINE_LOCATION prima).
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val connectTimeoutMs: Long = 20_000,
) : Transport() {

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = manager.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    @Volatile private var gattConnected = false
    private var servicesLatch: CountDownLatch? = null

    val devices = mutableListOf<BleDevice>()
    private var selected: android.bluetooth.BluetoothDevice? = null

    override val isConnected: Boolean get() = gattConnected && writeChar != null

    // --- Scansione ---

    fun scan(timeoutMs: Long = 8_000): List<BleDevice> {
        devices.clear()
        val scanner = adapter.bluetoothLeScanner ?: return emptyList()
        val found = LinkedHashMap<String, android.bluetooth.BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                if (found.put(dev.address, dev) == null) {
                    devices.add(BleDevice(dev.name ?: "?", dev.address, result.rssi))
                }
            }
        }
        scanner.startScan(callback)
        Thread.sleep(timeoutMs)
        scanner.stopScan(callback)
        scanResults = found
        return devices.toList()
    }

    private var scanResults: Map<String, android.bluetooth.BluetoothDevice> = emptyMap()

    fun select(device: BleDevice) {
        selected = scanResults[device.address] ?: adapter.getRemoteDevice(device.address)
    }

    // --- Connessione ---

    override fun open() {
        val device = selected ?: error("Nessun device selezionato: chiama scan()/select() prima")
        servicesLatch = CountDownLatch(1)
        gatt = device.connectGatt(context, false, gattCallback)
        val ready = servicesLatch!!.await(connectTimeoutMs, TimeUnit.MILLISECONDS)
        if (!ready || writeChar == null) {
            close()
            error("Connessione BLE non riuscita: caratteristiche non trovate")
        }
        notifyChar?.let { enableNotifications(it) }
        gattConnected = true
    }

    override fun close() {
        gattConnected = false
        try { gatt?.close() } catch (e: Exception) { Log.w(TAG, "close", e) }
        gatt = null
        writeChar = null
        notifyChar = null
    }

    override fun write(data: ByteArray) {
        val g = gatt ?: error("GATT non connesso")
        val ch = writeChar ?: error("Caratteristica di scrittura non disponibile")
        // ELM327: comandi corti; spezziamo comunque a 20 byte per sicurezza.
        data.asList().chunked(20).forEach { chunk ->
            val bytes = chunk.toByteArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        }
    }

    private fun enableNotifications(ch: BluetoothGattCharacteristic) {
        val g = gatt ?: return
        g.setCharacteristicNotification(ch, true)
        ch.getDescriptor(CCCD_UUID)?.let { cccd ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
    }

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                gattConnected = false
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            bindCharacteristics(g)
            servicesLatch?.countDown()
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            ch.value?.let { feed(it) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            feed(value)
        }
    }

    /** Individua notify/write dai servizi scoperti: UUID noti, poi proprietà GATT. */
    private fun bindCharacteristics(g: BluetoothGatt) {
        var notify: BluetoothGattCharacteristic? = null
        var write: BluetoothGattCharacteristic? = null
        for (service in g.services) {
            for (ch in service.characteristics) {
                val uuid = ch.uuid
                val props = ch.properties
                val canNotify = props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                val canWrite = props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                if (notify == null && (uuid in KNOWN_NOTIFY_UUIDS || canNotify)) notify = ch
                if (write == null && (uuid in KNOWN_WRITE_UUIDS || canWrite)) write = ch
            }
        }
        notifyChar = notify
        writeChar = write
    }

    companion object {
        private const val TAG = "BleTransport"
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // iCar Pro BLE 4.0 (Vgate): servizio FFF0, notify FFF1, write FFF2.
        private val KNOWN_NOTIFY_UUIDS = setOf(
            UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
        )
        private val KNOWN_WRITE_UUIDS = setOf(
            UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
        )
    }
}
