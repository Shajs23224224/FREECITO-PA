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

/** Public Android metrics only. CPU percentage is relative to one logical core. */
class ResourceProbe(context:Context){
    private val app=context.applicationContext
    private val activity=app.getSystemService(ActivityManager::class.java)
    private val power=app.getSystemService(PowerManager::class.java)
    private var lastTime=-1L
    private var lastCpu=-1L
    data class Reading(val health:Health,val availableMiB:Long,val processPssMiB:Long?)
    fun sample():Reading{
        val r=Runtime.getRuntime()
        val heap=((r.totalMemory()-r.freeMemory()).toDouble()/r.maxMemory().coerceAtLeast(1)).coerceIn(0.0,1.0)
        val memory=ActivityManager.MemoryInfo();activity.getMemoryInfo(memory)
        val now=SystemClock.elapsedRealtime();val cpu=Process.getElapsedCpuTime()
        val pct=if(lastTime>=0&&now>lastTime&&cpu>=lastCpu)100.0*(cpu-lastCpu)/(now-lastTime) else null
        lastTime=now;lastCpu=cpu
        val battery=app.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp=battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)?.takeIf{it in -300..1000}?.div(10.0)
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
        val percentage=if(scale>0&&level in 0..scale)(100L*level/scale).toInt() else null
        val charging=(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)?:0)!=0
        val thermal=if(Build.VERSION.SDK_INT>=29)power.currentThermalStatus.takeIf{it in 0..6} else null
        val pss=runCatching{Debug.getPss()/1024}.getOrNull()?.takeIf{it>=0}
        return Reading(Health(heap,memory.lowMemory,thermal,temp,percentage,charging,power.isPowerSaveMode,pct),memory.availMem/1048576,pss)
    }
}
