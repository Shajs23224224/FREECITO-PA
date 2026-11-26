package com.omega.ai.neural

import android.os.SystemClock
import com.omega.core.neural.NeuralEngine
import com.omega.core.agents.AgentBoard
import com.omega.core.agents.Evidence
import com.omega.vision.VisionSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Experimental perception embedding only. Outputs never dispatch actions. */
class NeuralSession : AutoCloseable {
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
                    val p=board.deliberate(frame,now,x,evidence,(0..15).toList(),4)
                    if(!closed.get())message.set("Blackboard: ${p.proposals.size} roles · ${p.activeModules.size} módulos · ${p.selected.size} observaciones aceptadas")
                }
            } catch(e:Exception){if(!closed.get())message.set("Inferencia no disponible")}
            finally {busy.set(false)}
        }
    }
    fun status()=message.get()
    @Synchronized override fun close(){ if(closed.compareAndSet(false,true))worker.shutdownNow() }
}
