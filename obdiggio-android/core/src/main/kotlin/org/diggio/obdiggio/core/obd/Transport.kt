package org.diggio.obdiggio.core.obd

/**
 * Canale byte bidirezionale tra il livello ELM327 e il mezzo fisico.
 *
 * Le implementazioni (BLE su Android, oppure il simulatore) chiamano [feed]
 * quando ricevono byte; il livello ELM327 legge con [readUntil] fino al prompt
 * `>`. Il buffer di ricezione è thread-safe perché su BLE le notifiche arrivano
 * su un thread diverso da quello che invia i comandi.
 */
abstract class Transport {

    private val buffer = ArrayDeque<Byte>()
    private val lock = Any()

    abstract fun open()
    abstract fun close()
    abstract fun write(data: ByteArray)
    abstract val isConnected: Boolean

    /** Le implementazioni chiamano questo metodo quando arrivano byte. */
    protected fun feed(data: ByteArray) {
        synchronized(lock) { data.forEach { buffer.addLast(it) } }
    }

    /** Svuota il buffer di ricezione. */
    fun clear() {
        synchronized(lock) { buffer.clear() }
    }

    /**
     * Legge dal buffer finché non trova [terminator] o scade [timeoutMs].
     * Restituisce i byte accumulati (terminatore incluso se trovato); in timeout
     * restituisce comunque quanto ricevuto.
     */
    fun readUntil(terminator: Byte = '>'.code.toByte(), timeoutMs: Long = 5000): ByteArray {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            synchronized(lock) {
                val idx = buffer.indexOf(terminator)
                if (idx != -1) {
                    val out = ByteArray(idx + 1) { buffer.removeFirst() }
                    return out
                }
            }
            Thread.sleep(10)
        }
        synchronized(lock) {
            val out = buffer.toByteArray()
            buffer.clear()
            return out
        }
    }
}
