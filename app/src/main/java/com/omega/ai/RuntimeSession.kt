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
import com.omega.ai.accessibility.TouchBridge
import com.omega.ai.meta.ResourceProbe
import com.omega.core.meta.*
import com.omega.core.release.Connection
import android.os.SystemClock
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
    private var probe:ResourceProbe?=null
    private var governor=MetaGovernor()
    private var budget=MetaGovernor.budget(Load.NORMAL)
    private var sampledAt=-1L
    private var submittedAt=-1L
    private var resourceStatus:String?=null
    data class Status(val active: Boolean, val capturing: Boolean, val message: String, val stats: EngineStats?, val perception: VisionSnapshot?, val neuralStatus: String?, val resources:String?)
    @Synchronized fun initialize(context: Context) {
        app=context.applicationContext
        store = SessionStore(app)
        runCatching { store.recover() }.onFailure { message = "Error de persistencia"; Log.e("OMEGA", "recover", it) }
    }
    @Synchronized fun start(): Boolean {
        if (engine != null) return false
        SessionDiagnostics.begin()
        val session = UUID.randomUUID().toString()
        var candidate: NativeEngine? = null
        var neuralCandidate: NeuralSession? = null
        return try {
            candidate = NativeEngine(); check(candidate.start())
            val connection=GameBinding.connection()
            check(connection!=Connection.INVALID)
            neuralCandidate = NeuralSession(app,GameBinding.adapter?.memoryScope,session,GameBinding.adapter?.commandPort)
            probe=ResourceProbe(app);governor=MetaGovernor();budget=MetaGovernor.budget(Load.NORMAL);sampledAt=-1L;submittedAt=-1L;resourceStatus=null
            store.begin(session)
            neural = neuralCandidate
            engine = candidate; id = session; capture = false
            message = if(connection==Connection.UNCONFIGURED) "Núcleo activo · sin adaptador de juego; no juega ni aprende" else "Núcleo activo · esperando autorización de captura"
            true
        } catch (t: Throwable) {
            SessionDiagnostics.end("start_error:"+t.javaClass.simpleName);neuralCandidate?.close(); candidate?.close(); message = "No se pudo iniciar el núcleo"; Log.e("OMEGA", "start", t); false
        }
    }
    private fun configureVision(){
        val training=GameBinding.training!=null
        val ready=GameBinding.accessibilityTarget()!=null
        val cooling=budget.load.ordinal>=Load.LIMITED.ordinal
        vision?.configureLoad(budget.visionMultiplier,budget.ocr&&!cooling,budget.depth&&!training,!cooling&&(!training||ready&&budget.load==Load.NORMAL),!cooling)
    }
    @Synchronized fun capturePaused()=budget.load.ordinal>=Load.LIMITED.ordinal
    @Synchronized fun captureDelayMs()=when(budget.load){Load.NORMAL->800L;Load.ECONOMY->1000L;Load.LIMITED->3000L;Load.STOP->5000L}
    @Synchronized fun beginCapture(): Boolean {
        if (!GameBinding.canCapture() || !resourceHealthy() || engine?.observe() != true) return false
        vision=VisionEngine(app);configureVision()
        capture = true; if(budget.commands)TouchBridge.arm(); message = "Observación experimental · v1.0.11"; return true
    }
    @Synchronized fun submit(buffer: ByteBuffer, w: Int, h: Int, row: Int, pixel: Int, ts: Long): Boolean {
        if(!capture||!GameBinding.canCapture()||!resourceHealthy()||capturePaused())return false
        val now=SystemClock.elapsedRealtimeNanos()
        if(submittedAt>=0&&now-submittedAt<100_000_000L*budget.visionMultiplier)return false
        if(engine?.submit(buffer,w,h,row,pixel,ts)!=true){SessionDiagnostics.event("native","frame_rejected");return false}
        submittedAt=now
        vision?.offer(buffer,w,h,row,pixel,ts)
        vision?.snapshot()?.let {
            SessionDiagnostics.event("detector","backend=${it.detectorBackend} status=${it.detectorStatus} done=${it.detectorCompleted} ms=${it.detectorMs} count=${it.detectorLastCount}")
            SessionDiagnostics.event("vision","ocr=${it.ocrStatus} done=${it.ocrCompleted} ms=${it.ocrMs} lines=${it.ocrLastCount} hud=${it.hudStatus} done=${it.hudCompleted} ms=${it.hudMs} candidates=${it.hudLastCount} fresh=${it.hud!=null}")
            GameBinding.ingest(it)
            val ready=GameBinding.accessibilityTarget()!=null
            if(budget.commands&&ready)TouchBridge.arm() else TouchBridge.disarm()
            if(GameBinding.training==null||budget.commands&&ready)neural?.offer(it)
            else SessionDiagnostics.event("executive","waiting_visual_evidence_or_resource_budget")
            configureVision()
        }
        return true
    }
    /** Sample at most every two seconds. A critical reading is latched; the service closes projection. */
    @Synchronized fun resourceHealthy():Boolean{
        if(engine==null||budget.load==Load.STOP)return false
        val now=SystemClock.elapsedRealtimeNanos()
        if(sampledAt>=0&&now-sampledAt<2_000_000_000L)return true
        sampledAt=now
        val perf=neural?.performance()?:Performance(0,null,0.0)
        val reading=runCatching{requireNotNull(probe).sample()}.getOrNull()
        budget=if(reading!=null)governor.update(reading.health,perf) else governor.update(Health(.85,true,null,null,null,false,false,null),perf)
        if(GameBinding.training!=null)budget=MetaGovernor.trainingBudget(budget,reading?.health)
        configureVision()
        SessionDiagnostics.event("budget","${budget.load} commands=${budget.commands} ocr=${budget.ocr} heap=${reading?.health?.heapRatio} lowMemory=${reading?.health?.lowMemory} thermal=${reading?.health?.thermal}")
        neural?.configureBudget(budget)
        if(!budget.commands)TouchBridge.disarm() else if(capture)TouchBridge.arm()
        resourceStatus=reading?.let{r->
            val h=r.health
            "Carga ${budget.load} · RAM libre ${r.availableMiB} MiB · PSS ${r.processPssMiB?:"?"} MiB · heap ${(h.heapRatio*100).toInt()}% · CPU ${h.cpuPct?.toInt()?:"?"}% (1 núcleo) · térmico ${h.thermal?:"?"} · batería ${h.batteryPct?:"?"}% / ${h.batteryC?:"?"} °C · p95 ${perf.p95Ms?.toInt()?:"?"} ms"
        }?:"Telemetría no disponible · carga limitada"
        return budget.load!=Load.STOP
    }
    @Synchronized fun status() = Status(engine != null, capture, message, engine?.stats(), vision?.snapshot(), neural?.status(),resourceStatus)
    @Synchronized fun stop(reason: String) {
        TouchBridge.disarm()
        TouchBridge.stopObservation()
        val e = engine ?: run{GameBinding.endTraining();return}
        SessionDiagnostics.end(reason)
        capture = false
        neural?.close(); neural=null
        vision?.close(); vision=null
        e.stop()
        val stats = e.stats()
        val session = id
        e.close(); engine = null; id = null; GameBinding.endTraining()
        probe=null
        message = if(budget.load==Load.STOP)"Sistema detenido por presión térmica/memoria" else "Sistema detenido · $reason"
        if (session != null) runCatching { store.finish(session,reason,stats) }.onFailure {
            message = "Detenido · error al guardar"; Log.e("OMEGA", "finish", it)
        }
    }
}
