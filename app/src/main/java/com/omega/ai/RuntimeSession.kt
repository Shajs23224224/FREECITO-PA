package com.omega.ai

import android.content.Context
import android.util.Log
import com.omega.ai.persistence.SessionStore
import com.omega.core.NativeEngine
import com.omega.core.EngineStats
import com.omega.vision.VisionEngine
import com.omega.vision.VisionSnapshot
import com.omega.ai.neural.NeuralSession
import com.omega.ai.game.GameBinding
import java.nio.ByteBuffer
import java.util.UUID

/** Process-local session owner. Optional authorized executive; no trained policy. */
object RuntimeSession {
    private lateinit var store: SessionStore
    private var engine: NativeEngine? = null
    private var id: String? = null
    private var message = "Sistema inactivo"
    private var capture = false
    private lateinit var app: Context
    private var vision: VisionEngine? = null
    private var neural: NeuralSession? = null
    data class Status(val active: Boolean, val capturing: Boolean, val message: String, val stats: EngineStats?, val perception: VisionSnapshot?, val neuralStatus: String?)
    @Synchronized fun initialize(context: Context) {
        app=context.applicationContext
        store = SessionStore(app)
        runCatching { store.recover() }.onFailure { message = "Error de persistencia"; Log.e("OMEGA", "recover", it) }
    }
    @Synchronized fun start(): Boolean {
        if (engine != null) return false
        val session = UUID.randomUUID().toString()
        var candidate: NativeEngine? = null
        var neuralCandidate: NeuralSession? = null
        return try {
            candidate = NativeEngine(); check(candidate.start())
            neuralCandidate = NeuralSession(app,GameBinding.adapter?.memoryScope,session,GameBinding.adapter?.commandPort)
            store.begin(session)
            neural = neuralCandidate
            engine = candidate; id = session; capture = false
            message = "Núcleo activo · juego sin integrar"
            true
        } catch (t: Throwable) {
            neuralCandidate?.close(); candidate?.close(); message = "No se pudo iniciar el núcleo"; Log.e("OMEGA", "start", t); false
        }
    }
    @Synchronized fun beginCapture(): Boolean {
        if (engine?.observe() != true) return false
        vision=VisionEngine(app)
        capture = true; message = "Observación autorizada · Fase 8"; return true
    }
    @Synchronized fun submit(buffer: ByteBuffer, w: Int, h: Int, row: Int, pixel: Int, ts: Long): Boolean {
        if (!capture || engine?.submit(buffer,w,h,row,pixel,ts) != true) return false
        vision?.offer(buffer,w,h,row,pixel,ts)
        vision?.snapshot()?.let { neural?.offer(it) }
        return true
    }
    @Synchronized fun status() = Status(engine != null, capture, message, engine?.stats(), vision?.snapshot(), neural?.status())
    @Synchronized fun stop(reason: String) {
        val e = engine ?: return
        capture = false
        neural?.close(); neural=null
        vision?.close(); vision=null
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
