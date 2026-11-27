package com.omega.ai

import android.content.Context
import android.os.SystemClock
import com.omega.core.diagnostics.DiagnosticJournal
import com.omega.core.diagnostics.DiagnosticJournal.Severity
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Private bounded telemetry. Call sites must supply technical metadata, never raw input. */
internal object SessionDiagnostics {
    private var prefs:android.content.SharedPreferences?=null
    private val journal=DiagnosticJournal(monotonic=SystemClock::elapsedRealtime)
    private val writer=Executors.newSingleThreadScheduledExecutor()
    private var scheduled=false
    @Volatile private var persistenceFailed=false
    @Synchronized fun initialize(context:Context){
        if(prefs!=null)return
        val p=context.getSharedPreferences("omega_diagnostic_v2",Context.MODE_PRIVATE)
        prefs=p
        val raw=p.getString("journal",null)
        if(raw!=null){
            runCatching{journal.restore(DiagnosticJournal.decode(raw))}.onFailure{
                journal.append("diagnostics","restore_failed",Severity.ERROR,DiagnosticJournal.exceptionDetail(it),true)
            }
        }else{
            // Legacy free-text traces are not re-imported into the structured export.
            val legacy=context.getSharedPreferences("omega_diagnostic_v1",Context.MODE_PRIVATE)
            if(legacy.getBoolean("running",false))journal.append("process","previous_process_interrupted",Severity.WARNING,"legacy session",true)
        }
        scheduleSave()
    }
    @Synchronized fun begin(session:String){journal.begin(session,"1.0.18");scheduleSave()}
    @Synchronized fun end(reason:String){journal.end(reason);scheduleSave()}
    @Synchronized fun event(stage:String,detail:String,force:Boolean=false,expectedSession:String?=null){
        val event=detail.substringBefore(' ').substringBefore(':').substringBefore('=')
        val severity=when {
            stage=="resource_transition"&&detail.contains("->STOP")->Severity.CRITICAL
            detail.contains("error",true)||detail.contains("failed",true)->Severity.ERROR
            stage=="resource_transition"||detail.contains("timeout")||detail.contains("rejected")||detail.startsWith("resource_pause")->Severity.WARNING
            else->Severity.INFO
        }
        if(journal.append(stage,event,severity,detail,force,expectedSession))scheduleSave()
    }
    @Synchronized fun failure(stage:String,event:String,error:Throwable,expectedSession:String?=null){
        if(journal.append(stage,event,Severity.ERROR,DiagnosticJournal.exceptionDetail(error),true,expectedSession))scheduleSave()
    }
    @Synchronized fun metric(stage:String,event:String,detail:String){
        if(journal.append(stage,event,Severity.DEBUG,detail))scheduleSave()
    }
    fun report(maxEntries:Int=Int.MAX_VALUE):String=journal.snapshot().entries.takeLast(maxEntries).joinToString("\n"){it.json()}
    fun hasPersistenceError()=persistenceFailed
    // Single writer, coalescing disk writes. A process kill can lose recent unflushed events.
    private fun scheduleSave(){
        if(scheduled||prefs==null)return
        scheduled=true
        writer.schedule({
            val pair=synchronized(this){scheduled=false; prefs to journal.snapshot()}
            persistenceFailed=runCatching{pair.first?.edit()?.putString("journal",DiagnosticJournal.encode(pair.second))?.commit()!=true}.getOrDefault(true)
        },2,TimeUnit.SECONDS)
    }
}
