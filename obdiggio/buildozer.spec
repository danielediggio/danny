[app]

# --- Identita' applicazione -------------------------------------------------
title = obdiggio
package.name = obdiggio
package.domain = org.diggio

source.dir = .
source.include_exts = py,png,jpg,kv,atlas,txt

version = 0.1.0

# --- Dipendenze -------------------------------------------------------------
# able = Bluetooth Low Energy per Android (github.com/b3b/able)
requirements = python3,kivy==2.3.0,able-recipe,pyjnius,android

orientation = portrait
fullscreen = 0

# --- Permessi Android -------------------------------------------------------
# BLUETOOTH_SCAN / BLUETOOTH_CONNECT: Android 12+ (API 31+)
# ACCESS_FINE_LOCATION: richiesto per la scansione BLE su Android < 12
android.permissions = BLUETOOTH,BLUETOOTH_ADMIN,BLUETOOTH_SCAN,BLUETOOTH_CONNECT,ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION

# BLUETOOTH_SCAN dichiarato senza mai derivare la posizione fisica.
android.manifest_placeholders = usesCleartextTraffic=false

android.api = 34
# minapi/ndk_api 24: CPython recente usa preadv/pwritev, disponibili su Android
# solo da API 24 (Android 7.0). Con 23 la build di CPython fallisce.
android.minapi = 24
android.ndk_api = 24
android.archs = arm64-v8a,armeabi-v7a

# Accetta automaticamente le licenze SDK (necessario in CI/headless).
android.accept_sdk_license = True

# Fissa python-for-android a una release stabile con Python 3.11: il master
# tira CPython 3.14, il cui C-API rompe il codice pre-generato di Kivy 2.3.0
# (_PyLong_AsByteArray, preadv/pwritev, ...).
p4a.branch = v2024.01.21

# Mantieni lo schermo acceso durante l'uso in auto.
android.wakelock = 1

# --- Presentazione ----------------------------------------------------------
# (icona/presplash opzionali: aggiungere i file e scommentare)
# icon.filename = %(source.dir)s/data/icon.png
# presplash.filename = %(source.dir)s/data/presplash.png

[buildozer]
log_level = 2
warn_on_root = 1
