package com.tuxungram.android.td

/**
 * Raw JNI bindings to libtdjson's C interface. Payloads are UTF-8
 * encoded JSON as byte arrays (see tuxungram_jni.cpp for why bytes,
 * not strings). Higher-level code should go through TdClient.
 */
object TdNative {
    init {
        // Order matters: the bridge links against libtdjson.
        System.loadLibrary("tdjson")
        System.loadLibrary("tuxungram_jni")
    }

    external fun createClientId(): Int
    external fun send(clientId: Int, request: ByteArray)
    external fun receive(timeoutSeconds: Double): ByteArray?
    external fun execute(request: ByteArray): ByteArray?
}
