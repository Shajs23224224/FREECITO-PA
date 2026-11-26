package com.omega.ai.neural

import android.os.SystemClock
import android.content.Context
import com.omega.ai.persistence.CognitiveStore
import com.omega.core.world.*
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
                    val p=board.deliberate(frame,now,x,evidence,(0..15).toList(),4)
                    if(!closed.get())message.set("Blackboard: ${p.proposals.size} roles · ${p.activeModules.size} módulos · ${p.selected.size} evidencias · $places firmas espaciales")
                }
            } catch(e:Exception){if(!closed.get())message.set("Inferencia no disponible")}
            finally {busy.set(false)}
        }
    }
    fun status()=message.get()
    @Synchronized override fun close(){ if(closed.compareAndSet(false,true)){worker.execute{store?.close()};worker.shutdown()} }
}
