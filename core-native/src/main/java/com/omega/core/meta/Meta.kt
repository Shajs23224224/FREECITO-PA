package com.omega.core.meta

import kotlin.math.ceil
import java.util.ArrayDeque

enum class Load { NORMAL, ECONOMY, LIMITED, STOP }
data class Budget(val load:Load,val roles:Int,val cognitiveIntervalNs:Long,val visionMultiplier:Int,val ocr:Boolean,val depth:Boolean,val commands:Boolean,val learning:Boolean)
data class Health(val heapRatio:Double,val lowMemory:Boolean,val thermal:Int?,val batteryC:Double?,val batteryPct:Int?,val charging:Boolean,val powerSave:Boolean,val cpuPct:Double?,val memory:MemoryHeadroom?=null) {
    init { require(heapRatio.isFinite()&&heapRatio in 0.0..1.0);require(thermal==null||thermal in 0..6);require(batteryC==null||batteryC.isFinite()&&batteryC in -30.0..100.0);require(batteryPct==null||batteryPct in 0..100);require(cpuPct==null||cpuPct.isFinite()&&cpuPct>=0) }
}
/** System-wide memory, not a per-process allocation limit. Unknown values stay unknown. */
data class MemoryHeadroom(val availableBytes:Long,val totalBytes:Long,val thresholdBytes:Long) {
    init { require(totalBytes>0 && availableBytes in 0..totalBytes && thresholdBytes in 1..totalBytes) }
    val atThreshold get()=availableBytes<=thresholdBytes
}
/** Monotonic process counters: a reset or non-positive elapsed time invalidates the interval. */
object ResourceDelta {
    fun counter(previous:Long?,current:Long?):Long? = if(previous!=null&&current!=null&&previous>=0&&current>=previous) current-previous else null
    fun cpuPercent(previousMs:Long?,currentMs:Long?,elapsedMs:Long):Double? =
        if(elapsedMs>0) counter(previousMs,currentMs)?.let{100.0*it/elapsedMs} else null
}
data class Performance(val samples:Int,val p95Ms:Double?,val failureRatio:Double)
/** Sliding window by completion time, using a monotonic clock. */
class CycleMetrics(
    private val clockNs: () -> Long = System::nanoTime,
    private val maxAgeNs: Long = 30_000_000_000L
) {
    init { require(maxAgeNs > 0) }
    private data class Sample(val completedNs: Long, val durationNs: Long, val success: Boolean)
    private val times = ArrayDeque<Sample>()
    private fun expire(now: Long) {
        while (times.isNotEmpty() && now - times.first.completedNs >= maxAgeNs) times.removeFirst()
    }
    @Synchronized fun record(durationNs: Long, success: Boolean) {
        require(durationNs >= 0)
        val now = clockNs()
        expire(now)
        times.addLast(Sample(now, durationNs, success))
        if (times.size > 32) times.removeFirst()
    }
    @Synchronized fun snapshot(): Performance {
        expire(clockNs())
        val n = times.size
        if (n == 0) return Performance(0, null, 0.0)
        val sorted = times.map { it.durationNs }.sorted()
        return Performance(n, sorted[ceil(.95*n).toInt()-1]/1e6, times.count { !it.success }.toDouble()/n)
    }
}

enum class ResourceCause(val label: String) {
    HEAP_CRITICAL("Memoria Java crítica"), THERMAL_CRITICAL("Temperatura crítica"), BATTERY_CRITICAL("Batería demasiado caliente"),
    LOW_MEMORY("Memoria del sistema baja"), RAM_THRESHOLD("RAM disponible bajo umbral Android"), HEAP_HIGH("Memoria Java alta"), BATTERY_HEAT("Temperatura de batería alta"),
    COGNITIVE_FAILURES("Fallos cognitivos"), COGNITIVE_LATENCY("Latencia cognitiva"), TELEMETRY_UNAVAILABLE("Telemetría no disponible"),
    POWER_SAVE("Ahorro de energía"), BATTERY_LOW("Batería baja"), HEAP_ELEVATED("Memoria Java elevada"),
    THERMAL_ELEVATED("Carga térmica elevada"), CPU_HIGH("Carga de CPU alta")
}
enum class ResourceState { NORMAL, ECONOMY, LIMITED, RECOVERY, STOP }
data class ResourceDecision(val state: ResourceState, val load: Load, val target: Load,
    val causes: List<ResourceCause>, val heldCauses: List<ResourceCause>, val healthyReadings: Int) {
    fun label(): String {
        val detail = (if (state == ResourceState.STOP || state == ResourceState.RECOVERY || (state == ResourceState.LIMITED && target != Load.LIMITED)) heldCauses else causes.ifEmpty { heldCauses })
            .joinToString(" / ") { it.label }
        val prefix = when(state) {
            ResourceState.NORMAL -> "Carga normal"
            ResourceState.ECONOMY -> "Carga reducida"
            ResourceState.LIMITED -> "Captura suspendida"
            ResourceState.RECOVERY -> "Recuperación supervisada"
            ResourceState.STOP -> "Sistema detenido"
        }
        return if(detail.isEmpty()) prefix else "$prefix · $detail"
    }
}

/** Recovery never relaxes critical protections. STOP is latched for the session.
 * RECOVERY retains the LIMITED budget; fresh resource readings, not missing cognitive
 * samples alone, are required before returning to ECONOMY. */
class MetaGovernor {
    private var state = ResourceState.NORMAL
    private var healthy = 0
    private var heldCauses = emptyList<ResourceCause>()
    // If battery heat caused a pause, absence of a later temperature is not cooling.
    private var needsBatteryTemperature = false
    private var needsMemoryHeadroom = false
    private var last = ResourceDecision(state, Load.NORMAL, Load.NORMAL, emptyList(), emptyList(), 0)
    @Synchronized fun decision(): ResourceDecision = last
    @Synchronized fun update(h: Health, p: Performance): Budget = evaluate(h, p)
    @Synchronized fun updateUnavailable(p: Performance): Budget = evaluate(null, p)

    private fun evaluate(h: Health?, p: Performance): Budget {
        val critical = mutableListOf<ResourceCause>()
        val limited = mutableListOf<ResourceCause>()
        val economy = mutableListOf<ResourceCause>()
        if (h == null) limited += ResourceCause.TELEMETRY_UNAVAILABLE else {
            if((needsBatteryTemperature && h.batteryC == null) || (needsMemoryHeadroom && h.memory == null)) limited += ResourceCause.TELEMETRY_UNAVAILABLE
            if(h.heapRatio>=.94) critical += ResourceCause.HEAP_CRITICAL
            if((h.thermal?:0)>=4) critical += ResourceCause.THERMAL_CRITICAL
            if((h.batteryC?:0.0)>=47) critical += ResourceCause.BATTERY_CRITICAL
            if(h.lowMemory) limited += ResourceCause.LOW_MEMORY
            if(h.memory?.atThreshold==true) limited += ResourceCause.RAM_THRESHOLD
            if(h.heapRatio>=.83) limited += ResourceCause.HEAP_HIGH
            if((h.batteryC?:0.0)>=43) limited += ResourceCause.BATTERY_HEAT
            if(h.powerSave) economy += ResourceCause.POWER_SAVE
            if(!h.charging&&(h.batteryPct?:100)<=15) economy += ResourceCause.BATTERY_LOW
            if(h.heapRatio>=.70) economy += ResourceCause.HEAP_ELEVATED
            if((h.thermal?:0)>=2) economy += ResourceCause.THERMAL_ELEVATED
            if((h.cpuPct?:0.0)>=180) economy += ResourceCause.CPU_HIGH
        }
        if(p.samples>=4) {
            if(p.failureRatio>=.5) limited += ResourceCause.COGNITIVE_FAILURES
            else if(p.failureRatio>=.20) economy += ResourceCause.COGNITIVE_FAILURES
            if((p.p95Ms?:0.0)>=1400) limited += ResourceCause.COGNITIVE_LATENCY
            else if((p.p95Ms?:0.0)>=650) economy += ResourceCause.COGNITIVE_LATENCY
        }
        val target = when { critical.isNotEmpty()->Load.STOP; limited.isNotEmpty()->Load.LIMITED; economy.isNotEmpty()->Load.ECONOMY; else->Load.NORMAL }
        val causes = when(target) { Load.STOP->critical; Load.LIMITED->limited; Load.ECONOMY->economy; Load.NORMAL->emptyList() }.toList()
        if(state != ResourceState.STOP) {
            when {
                target == Load.STOP -> { state=ResourceState.STOP; healthy=0; heldCauses=causes }
                target == Load.LIMITED -> {
                    if(ResourceCause.BATTERY_HEAT in causes) needsBatteryTemperature=true
                    if(ResourceCause.RAM_THRESHOLD in causes) needsMemoryHeadroom=true
                    state=ResourceState.LIMITED; healthy=0; heldCauses=causes
                }
                state == ResourceState.LIMITED -> {
                    if(++healthy>=3) { state=ResourceState.RECOVERY; healthy=0 }
                }
                state == ResourceState.RECOVERY -> {
                    if(++healthy>=3) { state=ResourceState.ECONOMY; healthy=0; needsBatteryTemperature=false; needsMemoryHeadroom=false; heldCauses=if(causes.isEmpty()) heldCauses else causes }
                }
                target == Load.ECONOMY -> { state=ResourceState.ECONOMY; healthy=0; heldCauses=causes }
                state == ResourceState.ECONOMY -> {
                    if(++healthy>=3) { state=ResourceState.NORMAL; healthy=0; heldCauses=emptyList() }
                }
                else -> { healthy=0; heldCauses=emptyList() }
            }
        }
        val load = when(state) { ResourceState.NORMAL->Load.NORMAL; ResourceState.ECONOMY->Load.ECONOMY; ResourceState.LIMITED, ResourceState.RECOVERY->Load.LIMITED; ResourceState.STOP->Load.STOP }
        last=ResourceDecision(state,load,target,causes,heldCauses,healthy)
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
