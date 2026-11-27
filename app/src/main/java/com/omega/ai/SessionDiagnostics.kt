package com.omega.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log

/** Bounded private diagnostic trace. No frames, OCR contents, account data or coordinates. */
internal object SessionDiagnostics {
    private var prefs:android.content.SharedPreferences?=null
    private val entries=java.util.ArrayDeque<String>()
    private val last=mutableMapOf<String,Pair<String,Long>>()
    private var running=false
    @Synchronized fun initialize(context:Context){
        val p=context.getSharedPreferences("omega_diagnostic_v1",Context.MODE_PRIVATE)
        prefs=p
        p.getString("trace","")?.lineSequence()?.filter{it.isNotBlank()}?.take(100)?.forEach{entries.addLast(it)}
        if(p.getBoolean("running",false))event("process","previous_process_interrupted",true)
        running=false;save()
    }
    @Synchronized fun begin(){
        entries.clear();last.clear();running=true;event("session","start v1.0.11",true)
    }
    @Synchronized fun end(reason:String){
        if(running){running=false;event("session","stop "+reason,true)}
    }
    @Synchronized fun event(stage:String,detail:String,force:Boolean=false){
        val now=SystemClock.elapsedRealtime()
        val safe=detail.replace('\n',' ').take(220)
        val prior=last[stage]
        if(!force&&prior!=null&&prior.first==safe&&now-prior.second<8000)return
        if(!force&&prior!=null&&now-prior.second<1000)return
        last[stage]=safe to now
        entries.addLast("${System.currentTimeMillis()} | $stage | $safe")
        while(entries.size>100)entries.removeFirst()
        Log.i("OMEGA_DIAG","$stage | $safe");save()
    }
    @Synchronized fun report()=if(entries.isEmpty())"Sin sesiones registradas" else entries.joinToString("\n")
    private fun save(){prefs?.edit()?.putString("trace",entries.joinToString("\n"))?.putBoolean("running",running)?.apply()}
}
