package com.omega.core.meta

import kotlin.math.ceil
import java.util.ArrayDeque

enum class Load { NORMAL, ECONOMY, LIMITED, STOP }
data class Budget(val load:Load,val roles:Int,val cognitiveIntervalNs:Long,val visionMultiplier:Int,val ocr:Boolean,val depth:Boolean,val commands:Boolean,val learning:Boolean)
data class Health(val heapRatio:Double,val lowMemory:Boolean,val thermal:Int?,val batteryC:Double?,val batteryPct:Int?,val charging:Boolean,val powerSave:Boolean,val cpuPct:Double?) {
    init { require(heapRatio.isFinite()&&heapRatio in 0.0..1.0);require(thermal==null||thermal in 0..6);require(batteryC==null||batteryC.isFinite()&&batteryC in -30.0..100.0);require(batteryPct==null||batteryPct in 0..100);require(cpuPct==null||cpuPct.isFinite()&&cpuPct>=0) }
}
data class Performance(val samples:Int,val p95Ms:Double?,val failureRatio:Double)
class CycleMetrics {
    private val times=ArrayDeque<Pair<Long,Boolean>>()
    @Synchronized fun record(durationNs:Long,success:Boolean){require(durationNs>=0);times.addLast(durationNs to success);if(times.size>32)times.removeFirst()}
    @Synchronized fun snapshot():Performance {
        val n=times.size;if(n==0)return Performance(0,null,0.0)
        val sorted=times.map{it.first}.sorted();return Performance(n,sorted[(ceil(.95*n).toInt()-1)]/1e6,times.count{!it.second}.toDouble()/n)
    }
}
/** Conservative bounded budgets; STOP is latched for the session. */
class MetaGovernor {
    private var load=Load.NORMAL
    private var healthy=0
    @Synchronized fun update(h:Health,p:Performance):Budget {
        val target=when {
            h.heapRatio>=.94 || (h.thermal?:0)>=4 || (h.batteryC?:0.0)>=47 -> Load.STOP
            h.lowMemory || h.heapRatio>=.83 || (h.batteryC?:0.0)>=43 || (p.samples>=4&&(p.failureRatio>=.5||(p.p95Ms?:0.0)>=1400)) -> Load.LIMITED
            h.powerSave || (!h.charging&&(h.batteryPct?:100)<=15) || h.heapRatio>=.70 || (h.thermal?:0)>=2 || (h.cpuPct?:0.0)>=180 || (p.samples>=4&&(p.failureRatio>=.20||(p.p95Ms?:0.0)>=650)) -> Load.ECONOMY
            else -> Load.NORMAL
        }
        if(load==Load.STOP)return budget(load)
        if(target.ordinal>load.ordinal){load=target;healthy=0}
        else if(target.ordinal<load.ordinal){if(++healthy>=3){load=Load.values()[load.ordinal-1];healthy=0}}
        else healthy=0
        return budget(load)
    }
    companion object {
        fun budget(mode:Load)=when(mode){
            Load.NORMAL->Budget(mode,4,1_000_000_000L,1,true,true,true,true)
            Load.ECONOMY->Budget(mode,2,2_000_000_000L,2,true,false,false,false)
            Load.LIMITED->Budget(mode,1,3_000_000_000L,3,false,false,false,false)
            Load.STOP->Budget(mode,0,Long.MAX_VALUE,4,false,false,false,false)
        }
        // Keep the session alive and permit bounded gestures at moderate load.
        // Android level 3 uses the reduced economy budget; battery heat, critical thermal,
        // low memory and low battery still suspend actions.
        fun trainingBudget(base:Budget,h:Health?):Budget {
            if(h==null||base.load.ordinal>=Load.LIMITED.ordinal)return base.copy(commands=false,learning=false,depth=false)
            val safe=h.thermal!=null&&h.thermal<=3&&!h.lowMemory&&h.heapRatio<.83&&(h.batteryC?:47.0)<43&&(h.charging||(h.batteryPct?:0)>15)
            return base.copy(commands=safe,learning=safe&&base.load==Load.NORMAL,depth=false)
        }
        fun policyHealthy(updates:Long,rollbacks:Long,disabled:Boolean):Boolean {
            if(disabled||updates<0||rollbacks<0)return false
            return !(rollbacks>=4 && rollbacks.toDouble()>updates.toDouble())
        }
    }
}
