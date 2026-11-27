package com.omega.ai.meta

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import com.omega.core.meta.Health
import com.omega.core.meta.MemoryHeadroom
import com.omega.core.meta.ResourceDelta

/** Process and device metrics are deliberately separate. CPU is relative to one logical core.
 * PSS is sampled less frequently than health; it is diagnostic, not an allocation limit. */
class ResourceProbe(context:Context){
    private val app=context.applicationContext
    private val activity=app.getSystemService(ActivityManager::class.java)
    private val power=app.getSystemService(PowerManager::class.java)
    private val batteryManager=app.getSystemService(BatteryManager::class.java)
    private var lastTime:Long?=null
    private var lastCpu:Long?=null
    private var lastGcCount:Long?=null
    private var lastGcMs:Long?=null
    private var pssAt:Long?=null
    private var pssMiB:Long?=null
    private fun mib(bytes:Long)=bytes/1048576L
    private fun runtimeStat(key:String)=runCatching{Debug.getRuntimeStat(key)?.toLongOrNull()?.takeIf{it>=0}}.getOrNull()
    data class Reading(val health:Health,val availableMiB:Long,val processPssMiB:Long?,
        val pssAgeMs:Long?,val nativeAllocatedMiB:Long?,val javaUsedMiB:Long,val javaMaxMiB:Long,
        val totalMiB:Long,val thresholdMiB:Long,val intervalMs:Long?,val cpuDeltaMs:Long?,
        val gcCountDelta:Long?,val gcTimeDeltaMs:Long?,val deviceCurrentUa:Int?,val deviceChargeUah:Int?)
    @Synchronized fun sample():Reading{
        val r=Runtime.getRuntime()
        val used=(r.totalMemory()-r.freeMemory()).coerceAtLeast(0);val max=r.maxMemory().coerceAtLeast(1)
        val heap=(used.toDouble()/max).coerceIn(0.0,1.0)
        val memory=ActivityManager.MemoryInfo();activity.getMemoryInfo(memory)
        val headroom=runCatching{MemoryHeadroom(memory.availMem,memory.totalMem,memory.threshold)}.getOrNull()
        val now=SystemClock.elapsedRealtime();val cpu=Process.getElapsedCpuTime()
        val interval=lastTime?.let{now-it}?.takeIf{it>0}
        val pct=interval?.let{ResourceDelta.cpuPercent(lastCpu,cpu,it)}
        val cpuDelta=interval?.let{ResourceDelta.counter(lastCpu,cpu)}
        val gcCount=runtimeStat("art.gc.gc-count");val gcMs=runtimeStat("art.gc.gc-time")
        val countDelta=interval?.let{ResourceDelta.counter(lastGcCount,gcCount)}
        val gcDelta=interval?.let{ResourceDelta.counter(lastGcMs,gcMs)}
        lastTime=now;lastCpu=cpu;lastGcCount=gcCount;lastGcMs=gcMs
        val battery=app.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp=battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)?.takeIf{it in -300..1000}?.div(10.0)
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
        val percentage=if(scale>0&&level in 0..scale)(100L*level/scale).toInt() else null
        val charging=(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)?:0)!=0
        val thermal=if(Build.VERSION.SDK_INT>=29)power.currentThermalStatus.takeIf{it in 0..6} else null
        if(pssAt?.let{now-it>=10_000L}!=false){
            pssMiB=runCatching{Debug.getPss().takeIf{it>0}?.div(1024L)}.getOrNull();pssAt=now
        }
        val native=runCatching{Debug.getNativeHeapAllocatedSize().takeIf{it>=0}?.let{mib(it)}}.getOrNull()
        fun batteryProperty(id:Int)=runCatching{batteryManager?.getIntProperty(id)?.takeUnless{it==Int.MIN_VALUE}}.getOrNull()
        return Reading(Health(heap,memory.lowMemory,thermal,temp,percentage,charging,power.isPowerSaveMode,pct,headroom),
            mib(memory.availMem),pssMiB,pssMiB?.let{pssAt?.let{now-it}},native,mib(used),mib(max),
            mib(memory.totalMem),mib(memory.threshold),interval,cpuDelta,countDelta,gcDelta,
            batteryProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            batteryProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf{it>=0})
    }
}
