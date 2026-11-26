package com.omega.core

import java.nio.ByteBuffer

data class EngineStats(val state: Long, val accepted: Long, val processed: Long, val dropped: Long,
                       val rejected: Long, val lastTimestamp: Long, val checksum: Long)

/** Owns a native handle; all calls serialized to prevent use-after-free. */
class NativeEngine : AutoCloseable {
    companion object { init { System.loadLibrary("omega_jni") } }
    private var handle = create().also { check(it != 0L) { "No se pudo crear el núcleo" } }
    private external fun create(): Long
    private external fun destroy(h: Long)
    private external fun startNative(h: Long): Boolean
    private external fun observeNative(h: Long): Boolean
    private external fun stopNative(h: Long)
    private external fun submitNative(h: Long, b: ByteBuffer, w: Int, height: Int, row: Int, pixel: Int, ts: Long): Boolean
    private external fun statsNative(h: Long): LongArray
    @Synchronized fun start() = handle != 0L && startNative(handle)
    @Synchronized fun observe() = handle != 0L && observeNative(handle)
    @Synchronized fun submit(b: ByteBuffer, w: Int, h: Int, row: Int, pixel: Int, ts: Long): Boolean =
        handle != 0L && b.isDirect && submitNative(handle, b.slice(), w, h, row, pixel, ts)
    @Synchronized fun stats(): EngineStats {
        check(handle != 0L)
        val s = statsNative(handle)
        return EngineStats(s[0],s[1],s[2],s[3],s[4],s[5],s[6])
    }
    @Synchronized fun stop() { if (handle != 0L) stopNative(handle) }
    @Synchronized override fun close() { if (handle != 0L) { destroy(handle); handle=0 } }
}
