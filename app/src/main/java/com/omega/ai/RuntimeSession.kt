package com.omega.ai

import android.content.Context
import android.util.Log
import com.omega.ai.persistence.SessionStore
import com.omega.core.NativeEngine
import com.omega.core.EngineStats
import java.nio.ByteBuffer
import java.util.UUID

/** Process-local session owner. No actions or learning claims in v0.1.0. */
object RuntimeSession {
    private lateinit var store: SessionStore
    private var engine: NativeEngine? = null
    private var id: String? = null
    private var message = "Sistema inactivo"
    private var capture = false
    data class Status(val active: Boolean, val capturing: Boolean, val message: String, val stats: EngineStats?)
    @Synchronized fun initialize(context: Context) {
        store = SessionStore(context.applicationContext)
        runCatching { store.recover() }.onFailure { message = "Error de persistencia"; Log.e("OMEGA", "recover", it) }
    }
    @Synchronized fun start(): Boolean {
        if (engine != null) return false
        val session = UUID.randomUUID().toString()
        var candidate: NativeEngine? = null
        return try {
            candidate = NativeEngine(); check(candidate.start())
            store.begin(session)
            engine = candidate; id = session; capture = false
            message = "Núcleo activo · juego sin integrar"
            true
        } catch (t: Throwable) {
            candidate?.close(); message = "No se pudo iniciar el núcleo"; Log.e("OMEGA", "start", t); false
        }
    }
    @Synchronized fun beginCapture(): Boolean {
        if (engine?.observe() != true) return false
        capture = true; message = "Observación autorizada · Fase 1"; return true
    }
    @Synchronized fun submit(buffer: ByteBuffer, w: Int, h: Int, row: Int, pixel: Int, ts: Long): Boolean =
        if (capture) engine?.submit(buffer,w,h,row,pixel,ts) == true else false
    @Synchronized fun status() = Status(engine != null, capture, message, engine?.stats())
    @Synchronized fun stop(reason: String) {
        val e = engine ?: return
        capture = false
        e.stop()
        val stats = e.stats()
        val session = id
        e.close(); engine = null; id = null
        message = "Sistema detenido"
        if (session != null) runCatching { store.finish(session,reason,stats) }.onFailure {
            message = "Detenido · error al guardar"; Log.e("OMEGA", "finish", it)
        }
    }
}
