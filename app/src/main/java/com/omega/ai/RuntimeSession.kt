package com.omega.ai

import android.content.Context
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
import com.omega.core.lifecycle.SessionLifecycle

/** Process-local session owner. Optional authorized executive; no trained policy. */
object RuntimeSession {
    private lateinit var store: SessionStore
    private var engine: NativeEngine? = null
    private var id: String? = null
    private var message = "Sistema inactivo"
    @Volatile private var capture = false
    private val lifecycle=SessionLifecycle()
    private val closing get()=lifecycle.snapshot().state==SessionLifecycle.State.STOPPING
    @Volatile private var published = Status(false,false,"Sistema inactivo",null,null,null,null)
    private val sessionToken get()=lifecycle.token()
    @Volatile private var publishedResourceLabel="Carga normal"
    private lateinit var app: Context
    private var vision: VisionEngine? = null
    private var neural: NeuralSession? = null
    private var probe:ResourceProbe?=null
    private var governor=MetaGovernor()
    @Volatile private var budget=MetaGovernor.budget(Load.NORMAL)
    private var sampledAt=-1L
    private var submittedAt=-1L
    private var resourceStatus:String?=null
    data class Status(val active: Boolean, val capturing: Boolean, val message: String, val stats: EngineStats?, val perception: VisionSnapshot?, val neuralStatus: String?, val resources:String?)
    @Synchronized fun initialize(context: Context) {
        app=context.applicationContext
        store = SessionStore(app)
        runCatching { store.recover() }.onFailure { message = "Error de persistencia"; SessionDiagnostics.failure("persistence","recover_failed",it) };publish()
    }
    @Synchronized fun start(): Boolean {
        val session = UUID.randomUUID().toString()
        if(engine!=null||!lifecycle.begin(session))return false
        var candidate: NativeEngine? = null
        var neuralCandidate: NeuralSession? = null
        var persisted=false
        return try {
            SessionDiagnostics.begin(session)
            candidate = NativeEngine(); check(candidate.start())
            val connection=GameBinding.connection()
            check(connection!=Connection.INVALID)
            neuralCandidate = NeuralSession(app,GameBinding.adapter?.memoryScope,session,GameBinding.adapter?.commandPort)
            probe=ResourceProbe(app);governor=MetaGovernor();budget=MetaGovernor.budget(Load.NORMAL);sampledAt=-1L;submittedAt=-1L;resourceStatus=null;publishedResourceLabel="Carga normal"
            store.begin(session);persisted=true
            neural = neuralCandidate
            engine = candidate; id = session; capture = false
            check(lifecycle.activate(session))
            message = if(connection==Connection.UNCONFIGURED) "Núcleo activo · sin adaptador de juego; no juega ni aprende" else "Núcleo activo · esperando autorización de captura"
            publish();true
        } catch (t: Throwable) {
            SessionDiagnostics.failure("session","start_error",t,session)
            message = "No se pudo iniciar el núcleo"
            lifecycle.retire(session);engine=null;neural=null;id=null;probe=null;capture=false
            published=Status(false,false,message,null,null,null,null)
            retire(candidate,neuralCandidate,null,session,if(persisted)session else null,"start_error");false
        }
    }
    private fun configureVision(){
        val training=GameBinding.training!=null
        val ready=GameBinding.accessibilityTarget()!=null
        val cooling=budget.load.ordinal>=Load.LIMITED.ordinal
        vision?.configureLoad(budget.visionMultiplier,budget.ocr&&!cooling,budget.depth&&!training,!cooling&&(!training||ready&&budget.load==Load.NORMAL),!cooling)
    }
    fun resourceLabel()=publishedResourceLabel
    fun capturePaused()=budget.load.ordinal>=Load.LIMITED.ordinal
    fun captureDelayMs()=when(budget.load){Load.NORMAL->800L;Load.ECONOMY->1000L;Load.LIMITED->3000L;Load.STOP->5000L}
    @Synchronized fun beginCapture(): Boolean {
        if(closing || engine==null)return false
        if(capture)return false // one capture producer per session
        if (!GameBinding.canCapture() || !resourceHealthy() || engine?.observe() != true) return false
        vision=VisionEngine(app);configureVision()
        capture = true; if(budget.commands)TouchBridge.arm(); message = "Observación experimental · v1.0.18"; publish();return true
    }
    @Synchronized fun submit(buffer: ByteBuffer, w: Int, h: Int, row: Int, pixel: Int, ts: Long, token:String?): Boolean {
        if(!lifecycle.accepts(token)||closing||!capture||!GameBinding.canCapture()||!resourceHealthy()||capturePaused())return false
        val now=SystemClock.elapsedRealtimeNanos()
        if(submittedAt>=0&&now-submittedAt<100_000_000L*budget.visionMultiplier)return false
        if(engine?.submit(buffer,w,h,row,pixel,ts)!=true){SessionDiagnostics.event("native","frame_rejected");return false}
        submittedAt=now
        vision?.offer(buffer,w,h,row,pixel,ts)
        vision?.snapshot()?.let {
            SessionDiagnostics.metric("detector","snapshot","backend=${it.detectorBackend} status=${it.detectorStatus} done=${it.detectorCompleted} ms=${it.detectorMs} count=${it.detectorLastCount}")
            SessionDiagnostics.metric("ocr","snapshot","status=${it.ocrStatus} completed=${it.ocrCompleted} ms=${it.ocrMs} count=${it.ocrLastCount} fresh=${it.text!=null}")
            SessionDiagnostics.metric("hud","snapshot","status=${it.hudStatus} completed=${it.hudCompleted} ms=${it.hudMs} count=${it.hudLastCount} fresh=${it.hud!=null}")
            SessionDiagnostics.metric("depth","snapshot","status=${it.depthStatus} done=${it.depthCompleted} ms=${it.depthMs} count=${it.depthLastCount} fresh=${it.relativeDepth!=null}")
            SessionDiagnostics.metric("vision","observation_health",it.observationHealth.entries.joinToString(";"){ (key,h) -> "$key=${h.state} ageMs=${h.ageMs} processingMs=${h.processingMs}" }+" frameGroups=${it.frameGroups.size}")
            GameBinding.ingest(it)
            val ready=GameBinding.accessibilityTarget()!=null
            if(budget.commands&&ready)TouchBridge.arm() else TouchBridge.disarm()
            if(GameBinding.training==null||budget.commands&&ready)neural?.offer(it)
            else SessionDiagnostics.event("executive","waiting_visual_evidence_or_resource_budget")
            configureVision()
        }
        publish();published.stats?.let{SessionDiagnostics.metric("native","snapshot","accepted=${it.accepted} processed=${it.processed} dropped=${it.dropped}")};return true
    }
    /** Sample at most every two seconds. A critical reading is latched; the service closes projection. */
    @Synchronized fun resourceHealthy():Boolean{
        if(closing||engine==null||budget.load==Load.STOP)return false
        val now=SystemClock.elapsedRealtimeNanos()
        if(sampledAt>=0&&now-sampledAt<2_000_000_000L)return true
        sampledAt=now
        val perf=neural?.performance()?:Performance(0,null,0.0)
        val reading=runCatching{requireNotNull(probe).sample()}.onFailure{
            SessionDiagnostics.failure("resource_probe","sample_error",it)
        }.getOrNull()
        val previous=governor.decision()
        budget=if(reading!=null)governor.update(reading.health,perf) else governor.updateUnavailable(perf)
        if(GameBinding.training!=null)budget=MetaGovernor.trainingBudget(budget,reading?.health)
        val decision=governor.decision()
        publishedResourceLabel=decision.label()
        configureVision()
        if(previous.state!=decision.state || previous.causes!=decision.causes || previous.heldCauses!=decision.heldCauses){
            SessionDiagnostics.event("resource_transition","${previous.state}->${decision.state} target=${decision.target} causes=${decision.causes.joinToString(",")} held=${decision.heldCauses.joinToString(",")}",true)
        }
        SessionDiagnostics.metric("budget","sample","${budget.load} state=${decision.state} target=${decision.target} healthy=${decision.healthyReadings} commands=${budget.commands} ocr=${budget.ocr} samples=${perf.samples} p95Ms=${perf.p95Ms} failures=${perf.failureRatio}")
        SessionDiagnostics.metric("resource_health","sample","heap=${reading?.health?.heapRatio} lowMemory=${reading?.health?.lowMemory} thermal=${reading?.health?.thermal} batteryC=${reading?.health?.batteryC} batteryPct=${reading?.health?.batteryPct} cpu=${reading?.health?.cpuPct}")
        reading?.let { r ->
            SessionDiagnostics.metric("resource_memory","sample","availableMiB=${r.availableMiB} totalMiB=${r.totalMiB} thresholdMiB=${r.thresholdMiB} pssMiB=${r.processPssMiB} pssAgeMs=${r.pssAgeMs} nativeAllocatedMiB=${r.nativeAllocatedMiB} javaUsedMiB=${r.javaUsedMiB} javaMaxMiB=${r.javaMaxMiB}")
            SessionDiagnostics.metric("resource_counters","sample","intervalMs=${r.intervalMs} cpuDeltaMs=${r.cpuDeltaMs} gcCountDelta=${r.gcCountDelta} gcTimeDeltaMs=${r.gcTimeDeltaMs} deviceCurrentUa=${r.deviceCurrentUa} deviceChargeUah=${r.deviceChargeUah}")
        }
        neural?.configureBudget(budget)
        if(!budget.commands)TouchBridge.disarm() else if(capture)TouchBridge.arm()
        resourceStatus=reading?.let{r->
            val h=r.health
            "${decision.label()} [${budget.load}] · RAM disponible ${r.availableMiB}/${r.totalMiB} MiB (umbral ${r.thresholdMiB}) · PSS ${r.processPssMiB?:"?"} MiB (edad ${r.pssAgeMs?:"?"} ms) · nativa ${r.nativeAllocatedMiB?:"?"} MiB · heap ${(h.heapRatio*100).toInt()}% · CPU ${h.cpuPct?.toInt()?:"?"}% (1 núcleo) · térmico ${h.thermal?:"?"} · batería ${h.batteryPct?:"?"}% / ${h.batteryC?:"?"} °C · p95 ${perf.p95Ms?.toInt()?:"?"} ms"
        }?:decision.label()
        publish();return budget.load!=Load.STOP
    }
    private fun publish(){published=Status(engine!=null&&!closing,capture,message,runCatching{engine?.stats()}.getOrNull(),vision?.snapshot(),neural?.status(),resourceStatus)}
    fun status()=published
    fun token()=sessionToken
    fun canStart()=lifecycle.canStart()
    fun isClosing()=closing
    @Synchronized fun stop(reason: String, expectedToken:String?=sessionToken) {
        if(closing||expectedToken!=sessionToken)return
        TouchBridge.disarm()
        TouchBridge.stopObservation()
        val e = engine ?: run{GameBinding.endTraining();return}
        val token=checkNotNull(sessionToken)
        check(lifecycle.retire(token));capture=false
        val n=neural;val v=vision;val session=id
        neural=null;vision=null;engine=null;id=null;probe=null
        GameBinding.endTraining()
        message="Sistema deteniéndose · $reason"
        published=Status(false,false,message,null,null,null,resourceStatus)
        retire(e,n,v,token,session,reason)
    }
    // Cleanup runs off the UI thread. A stuck native worker blocks restart rather than
    // allowing overlapping model instances or closing a backend during inference.
    private fun retire(e:NativeEngine?,n:NeuralSession?,v:VisionEngine?,token:String,session:String?,reason:String){
        Thread({
            var failure=false
            fun attempt(block:()->Unit){try{block()}catch(t:Throwable){failure=true;SessionDiagnostics.failure("lifecycle","cleanup_failed",t,token)}}
            attempt{n?.close()};attempt{v?.close()}
            val deadline=SystemClock.elapsedRealtime()+15_000L
            fun drained()=(n==null||n.terminated())&&(v==null||v.terminated())
            while(!drained()&&SystemClock.elapsedRealtime()<deadline){
                try{Thread.sleep(50)}catch(t:InterruptedException){failure=true;break}
            }
            if(!drained())failure=true
            var stats:EngineStats?=null
            attempt{e?.stop();stats=e?.stats()};attempt{e?.close()}
            val finalStats=stats
            if(session!=null&&finalStats!=null)attempt{store.finish(session,reason,finalStats)}
            synchronized(this){
                message=if(failure)"Sistema detenido · cierre incompleto; reinicia el proceso" else if(budget.load==Load.STOP)governor.decision().label() else "Sistema detenido · $reason"
                SessionDiagnostics.end(if(failure)"cleanup_incomplete:$reason" else reason)
                published=Status(false,false,message,finalStats,null,null,resourceStatus)
                check(lifecycle.complete(token,!failure))
            }
        },"omega-session-cleanup").start()
    }
}
