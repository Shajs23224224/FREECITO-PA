package com.omega.ai.neural

import android.os.SystemClock
import com.omega.ai.SessionDiagnostics
import android.content.Context
import com.omega.ai.persistence.CognitiveStore
import com.omega.ai.game.GameBinding
import com.omega.core.world.*
import com.omega.core.reasoning.*
import com.omega.core.executive.*
import com.omega.core.learning.ContinuousPolicy
import com.omega.core.meta.*
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import com.omega.core.neural.NeuralEngine
import com.omega.core.agents.AgentBoard
import com.omega.core.agents.Evidence
import com.omega.vision.VisionSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Perception and symbolic coordination; optional owner-authorized command port. Untrained embeddings do not dispatch commands. */
class NeuralSession(context:Context,scope:String?,private val sessionId:String,port:GamePort?=null) : AutoCloseable {
    private val learner=scope?.takeIf{port!=null && port.scope==it}?.let{game->
        val hash=MessageDigest.getInstance("SHA-256").digest(game.toByteArray(Charsets.UTF_8)).joinToString(""){ "%02x".format(it.toInt() and 255) }
        ContinuousPolicy(game,File(context.filesDir,"learning/rank17-gated-v1/$hash.bin"))
    }
    private val executive=port?.takeIf{scope!=null && it.scope==scope}?.let{Executive(it,sessionId,{SystemClock.elapsedRealtimeNanos()},ttl=8_000_000_000L,policy=learner,permissionGate={GameBinding.canCapture()&&!closed.get()})}
    private val store=scope?.let{CognitiveStore(context)}
    private val memory=store?.let{CognitiveMemory(requireNotNull(scope),it)}
    private val world=memory?.let{WorldModel(it,sessionId)}
    private var lastWorldFrame=-1L
    private val engine=NeuralEngine()
    private val board=AgentBoard(engine)
    private val reasoner=Reasoner()
    private val availability=ArrayDeque<Boolean>()
    private var previousPlan:String?=null
    private val worker=Executors.newSingleThreadExecutor()
    private val closed=AtomicBoolean(false)
    private val busy=AtomicBoolean(false)
    private val cleanupFailed=AtomicBoolean(false)
    private val message=AtomicReference("858 módulos · 3.111.624 parámetros · 160 roles")
    private var last=-1L
    private var seen=-1L
    private val metrics=CycleMetrics(clockNs = SystemClock::elapsedRealtimeNanos)
    @Volatile private var budget=MetaGovernor.budget(Load.NORMAL)
    fun performance()=metrics.snapshot()
    fun configureBudget(value:Budget){
        budget=value
        learner?.let{p->val h=p.health();p.setSuspended(!value.learning||!MetaGovernor.policyHealthy(h.updates,h.rollbacks,h.disabled))}
    }
    private var priorTick:Tick?=null
    @Synchronized fun offer(s:VisionSnapshot) {
        if(closed.get()||budget.load==Load.STOP)return
        val now=SystemClock.elapsedRealtimeNanos()
        val frame=listOfNotNull(s.tracks?.frameNs,s.text?.frameNs,s.relativeDepth?.frameNs,s.hud?.frameNs).maxOrNull() ?: return
        if(frame<=seen || (last>=0 && now-last<budget.cognitiveIntervalNs) || !busy.compareAndSet(false,true))return
        try {
        last=now; seen=frame
        val x=FloatArray(2048)
        val domains=mutableListOf<Int>()
        val evidence=mutableListOf<Evidence>()
        s.tracks?.let { t ->
            domains+=0; x[0]=t.value.size/32f; x[16]=1f
            x[20]=t.value.map{it.detection.score}.average().takeIf{it.isFinite()}?.toFloat() ?: 0f
            val seen=t.receivedNs
            evidence+=Evidence("evidence.A001",1f,1f,"detector_frame",seen)
            evidence+=Evidence("evidence.A002",x[0],x[20],"detector",seen)
            evidence+=Evidence("evidence.A004",t.value.map{it.detection.label}.distinct().size/32f,x[20],"detector",seen)
            evidence+=Evidence("evidence.A007",t.value.size/32f,x[20],"visible_tracker",seen)
            evidence+=Evidence("evidence.A009",x[20],.5f,"uncalibrated_detector",seen)
        }
        s.relativeDepth?.let { d -> domains+=1; x[17]=1f; d.value.take(64).forEachIndexed{i,v->x[64+i]=v}; x[1]=d.value.average().toFloat(); evidence+=Evidence("evidence.A011",x[1],.5f,"relative_depth_uncalibrated",d.receivedNs) }
        s.text?.let { t -> domains+=2; x[18]=1f; x[2]=t.value.size/100f; evidence+=Evidence("evidence.A021",x[2],.5f,"ocr_confidence_unavailable",t.receivedNs) }
        s.hud?.let{h->
            x[190]=h.value.icons.size/6f
            x[191]=h.value.icons.maxOfOrNull{it.score}?:0f
            evidence+=Evidence("evidence.A024",x[190],.5f,"geometric_hud_hypotheses",h.receivedNs)
            evidence+=Evidence("evidence.A030",x[191],.5f,"untrained_hud_geometry",h.receivedNs)
        }
        evidence+=Evidence("evidence.A010",domains.size/3f,1f,"modality_availability",now)
        worker.execute {
            val started=SystemClock.elapsedRealtimeNanos();var success=false
            try {
                if(!closed.get()){
                    val t=s.tracks
                    var places=0
                    if(world!=null && memory!=null && t!=null && t.frameNs>lastWorldFrame && now-t.receivedNs in 0..1_500_000_000L){
                        val entities=t.value.take(32).map{v->val d=v.detection;Visible(v.id,d.label.take(80),(d.box.left+d.box.right)/2,(d.box.top+d.box.bottom)/2,d.score)}
                        val depth=s.relativeDepth?.takeIf{it.frameNs==t.frameNs}?.value
                        val wall=System.currentTimeMillis()
                        val w=world.observe(Scene(t.frameNs,now,wall,entities,depth))
                        lastWorldFrame=t.frameNs;places=w.placeCount
                        x[140]=w.entities.size/32f;x[141]=(w.relations.size/1488f).coerceIn(0f,1f);x[142]=w.placeSimilarity;x[143]=w.placeCount/256f;x[144]=1f
                        val recalled=memory.retrieve(MemoryKind.SEMANTIC,entities.map{it.label.lowercase()}.toSet(),wall)
                        x[145]=recalled.size/8f
                        evidence+=Evidence("evidence.A018",x[143],.5f,"visual_place_hypotheses",now)
                        evidence+=Evidence("evidence.A020",x[141],.5f,"screen_relative_relations",now)
                        evidence+=Evidence("evidence.A039",w.placeSimilarity,.5f,"visual_signature_uncalibrated",now)
                        evidence+=Evidence("evidence.A111",1f,1f,"memory_write",now)
                        evidence+=Evidence("evidence.A112",1f,1f,"memory_context_write",now)
                        evidence+=Evidence("evidence.A113",1f,1f,"observation_record_write",now)
                        evidence+=Evidence("evidence.A114",x[145],.7f,"lexical_recall_availability",now)
                        evidence+=Evidence("evidence.A115",x[143],.5f,"spatial_hypothesis_store",now)
                        evidence+=Evidence("evidence.A120",1f,1f,"transaction_success",now)
                    }
                    // Facts refer to availability, not truth of uncalibrated game labels.
                    val facts=mutableListOf<Fact>()
                    fun available(key:String, present:Boolean, source:String){
                        facts+=Fact(key,if(present)Truth.TRUE else Truth.FALSE,1.0,source)
                    }
                    available("visual",s.tracks!=null,"vision_availability")
                    available("text",s.text!=null,"ocr_availability")
                    available("aligned_depth",t!=null && s.relativeDepth?.frameNs==t.frameNs,"frame_alignment")
                    available("memory",memory!=null,"memory_scope")
                    val rules=listOf(Rule(setOf("visual","aligned_depth"),"spatial_review"),Rule(setOf("visual","memory"),"recall_review"))
                    val plans=listOf(
                        Plan("spatial_review",listOf(Step("inspect_relative_geometry",setOf("spatial_review"),.9,.2,.25),Step("check_alignment",setOf("aligned_depth"),.8,.1,.15))),
                        Plan("context_review",listOf(Step("inspect_visible",setOf("visual"),.75,.15,.2),Step("retrieve_context",setOf("recall_review"),.8,.15,.2))),
                        Plan("text_review",listOf(Step("inspect_text_availability",setOf("text"),.65,.15,.2))),
                        Plan("observe_more",listOf(Step("await_fresh_observation",emptySet(),.5,.1,.1)))
                    )
                    val result=reasoner.reason(facts,rules,plans,mapOf("spatial_data_available" to setOf("visual","aligned_depth","spatial_review"),"context_available" to setOf("visual","memory","recall_review")),previousPlan)
                    val selected=result.selected
                    previousPlan=selected?.id
                    availability.addLast(s.tracks!=null);if(availability.size>64)availability.removeFirst()
                    val estimate=reasoner.estimate(availability.toList())
                    x[150]=result.alternatives.count{it.score!=null}/4f
                    x[151]=estimate?.probability?.toFloat()?:0f
                    x[152]=if(result.revised)1f else 0f
                    x[153]=result.facts.values.count{it==Truth.CONFLICT}/result.facts.size.coerceAtLeast(1).toFloat()
                    x[154]=(selected?.score?.toFloat()?:0f).coerceIn(0f,1f)
                    evidence+=Evidence("evidence.A051",x[150],1f,"plan_evaluation",now)
                    evidence+=Evidence("evidence.A063",x[154],.7f,"heuristic_plan_score",now)
                    evidence+=Evidence("evidence.A074",x[151],.5f,"availability_estimate",now)
                    evidence+=Evidence("evidence.A121",result.facts.values.count{it==Truth.TRUE}/result.facts.size.coerceAtLeast(1).toFloat(),1f,"deduction_status",now)
                    evidence+=Evidence("evidence.A123",result.hypotheses.size/2f,1f,"hypothesis_count",now)
                    evidence+=Evidence("evidence.A127",x[153],1f,"contradiction_count",now)
                    evidence+=Evidence("evidence.A149",x[152],1f,"plan_revision",now)
                    if(memory!=null && selected!=null){
                        val wall=System.currentTimeMillis()
                        memory.consolidate(listOf(
                            Recall(MemoryKind.STRATEGIC,"observation_plan:$frame",selected.id+" proposed only; no execution",.5f,wall,"symbolic_reasoner"),
                            Recall(MemoryKind.PROSPECTIVE,"pending_observation",selected.id+" not executed",.5f,wall,"symbolic_reasoner")
                        ))
                    }
                    priorTick?.let{tick->
                        x[160]=tick.offered/32f;x[161]=tick.missing/64f
                        evidence+=Evidence("evidence.A081",x[161],1f,"adapter_inventory_deficits",now)
                        evidence+=Evidence("evidence.A043",x[160],1f,"adapter_available_offers",now)
                        tick.result?.let{r->
                            if(r.outcome!=Outcome.UNKNOWN)evidence+=Evidence("evidence.A048",if(r.outcome==Outcome.SUCCESS)1f else 0f,1f,"adapter_confirmed_receipt",now)
                        }
                    }
                    executive?.combatSnapshot()?.let{(stamp,c)->
                        if(stamp in 0..now && now-stamp<=1_500_000_000L && c.usable()){
                            fun signal(role:String,index:Int,value:Float?){
                                if(value!=null){x[index]=value;evidence+=Evidence("evidence.$role",value,c.confidence,"authorized_virtual_combat_state",stamp)}
                            }
                            signal("A041",180,c.threatVisible?.let{if(it)1f else 0f})
                            signal("A046",181,c.health)
                            signal("A082",182,c.loaded?.let{it.coerceAtMost(100)/100f})
                            signal("A053",183,c.inCover?.let{if(it)1f else 0f})
                            signal("A083",184,c.healingItems?.let{it.coerceAtMost(10)/10f})
                        }
                    }
                    val perf=metrics.snapshot()
                    perf.p95Ms?.let{latency->
                        x[170]=(latency/1500.0).coerceIn(0.0,1.0).toFloat()
                        x[171]=perf.failureRatio.toFloat()
                        evidence+=Evidence("evidence.A145",x[170],1f,"measured_cycle_latency",now)
                        evidence+=Evidence("evidence.A144",x[171],1f,"measured_cycle_failure_ratio",now)
                    }
                    learner?.let{lp->
                        val health=lp.health();val healthy=MetaGovernor.policyHealthy(health.updates,health.rollbacks,health.disabled)
                        x[172]=if(healthy)1f else 0f
                        evidence+=Evidence("evidence.A108",x[172],1f,"policy_health_guard",now)
                    }
                    val activeBudget=budget
                    if(activeBudget.roles==0||closed.get())return@execute
                    val p=board.deliberate(frame,now,x,evidence,(0..15).toList(),activeBudget.roles)
                    // F01/F02 remain diagnostic embeddings. Authorization, fresh state and symbolic context gate the command.
                    val visualTime=if(GameBinding.training!=null)GameBinding.currentControlObservationNs() else t?.receivedNs
                    val visualReady=if(GameBinding.training!=null)s.hud!=null&&s.text!=null else result.facts["visual"]==Truth.TRUE
                    val ready=GameBinding.canCommand(budget.commands,visualTime,SystemClock.elapsedRealtimeNanos())&&selected!=null&&visualReady&&p.execution.all{it.isFinite()}
                    val tick=if(closed.get())null else executive?.tick(ready,p.execution)
                    SessionDiagnostics.event("executive","ready=$ready status=${tick?.status?:"no_adapter"} result=${tick?.result?.outcome?:"none"} visualAgeMs=${visualTime?.let{(SystemClock.elapsedRealtimeNanos()-it)/1_000_000L}} selected=${selected!=null}")
                    priorTick=tick
                    if(memory!=null && tick!=null && !closed.get()){
                        val wall=System.currentTimeMillis()
                        val records=mutableListOf<Recall>()
                        tick.goal?.let{g->records+=Recall(MemoryKind.PROSPECTIVE,"executive_goal",g+" "+tick.status,.5f,wall,"executive")}
                        tick.result?.let{r->
                            records+=Recall(MemoryKind.EPISODIC,"command:"+r.command.id,r.family.name+" "+r.outcome.name+" "+r.reason,if(r.outcome==Outcome.UNKNOWN)0f else 1f,wall,"adapter_receipt")
                            if(r.outcome==Outcome.SUCCESS)records+=Recall(MemoryKind.PROCEDURAL,"confirmed:"+r.family.name,"owner_api_receipt; not learned policy",.7f,wall,"adapter_receipt")
                        }
                        memory.consolidate(records)
                    }
                    if(!closed.get())message.set("Blackboard: ${p.proposals.size} roles · ${p.activeModules.size} módulos · ${p.selected.size} evidencias · $places firmas espaciales · plan: ${selected?.id?:"none"} · ejecutivo: ${tick?.status?:"sin adaptador"} · ${learner?.stats()?:"aprendizaje inactivo"} · carga: ${budget.load}")
                    success=true
                }
            } catch(e:Exception){if(!closed.get()){message.set("Inferencia no disponible");SessionDiagnostics.failure("neural","inference_error",e,sessionId)}}
            finally {metrics.record((SystemClock.elapsedRealtimeNanos()-started).coerceAtLeast(0),success);busy.set(false)}
        }
        } catch(e:Throwable){busy.set(false);throw e}
    }
    fun status()=message.get()
    fun terminated()=worker.isTerminated && !cleanupFailed.get()
    @Synchronized override fun close(){
        if(!closed.compareAndSet(false,true))return
        try {
            worker.execute {
                try { executive?.close() } catch(e:Throwable){ cleanupFailed.set(true);SessionDiagnostics.failure("neural","cleanup_error",e,sessionId) }
                try { store?.close() } catch(e:Throwable){ cleanupFailed.set(true);SessionDiagnostics.failure("neural","cleanup_error",e,sessionId) }
            }
        } catch(e:java.util.concurrent.RejectedExecutionException){cleanupFailed.set(true)}
        finally {worker.shutdown()}
    }
}
