package com.omega.ai.neural

import android.os.SystemClock
import android.content.Context
import com.omega.ai.persistence.CognitiveStore
import com.omega.core.world.*
import com.omega.core.reasoning.*
import java.util.ArrayDeque
import com.omega.core.neural.NeuralEngine
import com.omega.core.agents.AgentBoard
import com.omega.core.agents.Evidence
import com.omega.vision.VisionSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Experimental perception embedding only. Outputs never dispatch actions. */
class NeuralSession(context:Context,scope:String?,sessionId:String) : AutoCloseable {
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
    private val message=AtomicReference("858 módulos · 160 roles · sin entrenar")
    private var last=-1L
    private var seen=-1L
    @Synchronized fun offer(s:VisionSnapshot) {
        if(closed.get())return
        val now=SystemClock.elapsedRealtimeNanos()
        val frame=listOfNotNull(s.tracks?.frameNs,s.text?.frameNs,s.relativeDepth?.frameNs).maxOrNull() ?: return
        if(frame<=seen || (last>=0 && now-last<1_000_000_000L) || !busy.compareAndSet(false,true))return
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
        evidence+=Evidence("evidence.A010",domains.size/3f,1f,"modality_availability",now)
        worker.execute {
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
                    val p=board.deliberate(frame,now,x,evidence,(0..15).toList(),4)
                    if(!closed.get())message.set("Blackboard: ${p.proposals.size} roles · ${p.activeModules.size} módulos · ${p.selected.size} evidencias · $places firmas espaciales · plan: ${selected?.id?:"none"}")
                }
            } catch(e:Exception){if(!closed.get())message.set("Inferencia no disponible")}
            finally {busy.set(false)}
        }
    }
    fun status()=message.get()
    @Synchronized override fun close(){ if(closed.compareAndSet(false,true)){worker.execute{store?.close()};worker.shutdown()} }
}
