package com.omega.ai.neural

import android.os.SystemClock
import com.omega.core.neural.NeuralEngine
import com.omega.vision.VisionSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Experimental perception embedding only. Outputs never dispatch actions. */
class NeuralSession : AutoCloseable {
    private val engine=NeuralEngine()
    private val worker=Executors.newSingleThreadExecutor()
    private val closed=AtomicBoolean(false)
    private val busy=AtomicBoolean(false)
    private val message=AtomicReference("858 módulos inicializados · sin entrenar")
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
        s.tracks?.let { t ->
            domains+=0; x[0]=t.value.size/32f; x[16]=1f
            x[20]=t.value.map{it.detection.score}.average().takeIf{it.isFinite()}?.toFloat() ?: 0f
        }
        s.relativeDepth?.let { d -> domains+=1; x[17]=1f; d.value.take(64).forEachIndexed{i,v->x[64+i]=v}; x[1]=d.value.average().toFloat() }
        s.text?.let { t -> domains+=2; x[18]=1f; x[2]=t.value.size/100f }
        worker.execute {
            try {
                if(!closed.get()){
                    val p=engine.infer(x,domains,2)
                    if(!closed.get())message.set("Inferencia experimental: ${p.activeIds.size} módulos · sin entrenar")
                }
            } catch(e:Exception){if(!closed.get())message.set("Inferencia no disponible")}
            finally {busy.set(false)}
        }
    }
    fun status()=message.get()
    @Synchronized override fun close(){ if(closed.compareAndSet(false,true))worker.shutdownNow() }
}
