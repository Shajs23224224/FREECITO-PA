package com.omega.core.diagnostics

import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID

/** Bounded, process-local journal. Only callers' approved technical metadata is accepted. */
class DiagnosticJournal(
    private val wall: () -> Long = System::currentTimeMillis,
    private val monotonic: () -> Long = { System.nanoTime()/1_000_000L },
    private val maxSessions: Int = 5,
    private val maxEntries: Int = 120
) {
    init { require(maxSessions in 1..10 && maxEntries in 2..500) }
    enum class Severity { TRACE, DEBUG, INFO, WARNING, ERROR, CRITICAL }
    data class Entry(val id:String, val session:String, val time:Long, val elapsed:Long,
                     val severity:Severity, val component:String, val event:String, val detail:String) {
        fun json():String = "{\"schema\":2,\"event_id\":${quote(id)},\"session_id\":${quote(session)},\"timestamp_ms\":$time,\"elapsed_ms\":$elapsed,\"severity\":${quote(severity.name)},\"component\":${quote(component)},\"event\":${quote(event)},\"detail\":${quote(detail)}}"
    }
    data class Snapshot(val active:String?, val entries:List<Entry>) {
        fun jsonLines()=entries.joinToString("\n"){it.json()}
    }
    private val process="process-"+UUID.randomUUID().toString()
    private val buckets=linkedMapOf<String,ArrayDeque<Entry>>()
    private var active:String?=null
    private val last=mutableMapOf<String,Pair<String,Long>>()
    @Synchronized fun begin(id:String, version:String) {
        require(active==null && validId(id) && id !in buckets)
        active=id;last.clear(); append("session","start",Severity.INFO,"version=$version",true)
    }
    @Synchronized fun end(reason:String) {
        if(active!=null){append("session","stop",Severity.INFO,reason,true);active=null;last.clear()}
    }
    @Synchronized fun append(component:String,event:String,severity:Severity,detail:String="",force:Boolean=false,expectedSession:String?=null):Boolean {
        if(expectedSession!=null && expectedSession!=active)return false
        val sid=active?:process
        val c=identifier(component);val e=identifier(event);val d=clean(detail)
        val now=monotonic();val key="$c/$e";val previous=last[key]
        val urgent=severity.ordinal>=Severity.ERROR.ordinal
        if(!force&&!urgent&&previous!=null&&now>=previous.second&&now-previous.second<(if(previous.first==d)8000 else 1000))return false
        if(last.size>=256 && key !in last)last.clear()
        last[key]=d to now
        insert(Entry(UUID.randomUUID().toString(),sid,wall(),now,severity,c,e,d))
        return true
    }
    private fun insert(entry:Entry){
        val bucket=buckets.getOrPut(entry.session){ArrayDeque()}
        if(bucket.size>=maxEntries)bucket.removeFirst()
        bucket.addLast(entry)
        while(buckets.size>maxSessions){val key=buckets.keys.first{it!=active};buckets.remove(key)}
    }
    @Synchronized fun snapshot()=Snapshot(active,buckets.values.flatMap{it.toList()})
    /** Restore only bounded, valid records from a previously encoded snapshot. */
    @Synchronized fun restore(snapshot:Snapshot){
        buckets.clear();last.clear();active=snapshot.active?.takeIf{validId(it)}
        snapshot.entries.takeLast(maxSessions*maxEntries).forEach{if(validId(it.session))insert(it)}
        if(active!=null){append("process","previous_process_interrupted",Severity.WARNING,"previous process ended without a recorded stop",true);active=null}
    }
    companion object {
        private fun validId(s:String)=s.length in 1..80 && s.all{it.isLetterOrDigit()||it=='-'}
        private fun identifier(s:String)=s.take(48).map{if(it.isLetterOrDigit()||it=='_'||it=='-')it else '_'}.joinToString("")
        private fun clean(s:String)=s.take(2048).map{if(it.isISOControl())' ' else it}.joinToString("")
        private fun quote(s:String)=buildString{append('"');s.forEach{when(it){'"'->append("\\\"");'\\'->append("\\\\");else->if(it.code<32)append("\\u%04x".format(it.code))else append(it)}};append('"')}
        /** No exception messages, paths, external frame names or local variable contents. */
        fun exceptionDetail(error:Throwable):String {
            val seen=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable,Boolean>())
            val parts=mutableListOf<String>();var cursor:Throwable?=error
            while(cursor!=null&&parts.size<5&&seen.add(cursor)){
                val t=cursor
                val frames=t.stackTrace.asSequence().filter{it.className.startsWith("com.omega.")}.take(6)
                    .joinToString(","){"${it.className}.${it.methodName}:${it.lineNumber}"}
                parts.add(t.javaClass.name+if(frames.isEmpty())"" else " [$frames]")
                cursor=t.cause
            }
            return clean(parts.joinToString(" <- "))
        }
        /** Versioned bounded local persistence format; JSONL is the separate export format. */
        fun encode(s:Snapshot):String=buildString{
            append("OMEGA-DIAG-2\t").append(s.active?:"").append('\n')
            s.entries.forEach{e->append(listOf(e.id,e.session,e.time,e.elapsed,e.severity.name,e.component,e.event,Base64.getEncoder().encodeToString(e.detail.toByteArray(Charsets.UTF_8))).joinToString("\t")).append('\n')}
        }
        fun decode(raw:String):Snapshot {
            require(raw.length<=4_000_000)
            val lines=raw.lineSequence().iterator();require(lines.hasNext())
            val header=lines.next().split('\t');require(header.size==2&&header[0]=="OMEGA-DIAG-2")
            val active=header[1].ifEmpty{null};require(active==null||validId(active))
            val out=ArrayDeque<Entry>()
            while(lines.hasNext()){
                val line=lines.next();if(line.isEmpty())continue
                val entry=runCatching{
                    val p=line.split('\t');require(p.size==8&&validId(p[0])&&validId(p[1])&&p[7].length<=12000)
                    Entry(p[0],p[1],p[2].toLong(),p[3].toLong(),Severity.valueOf(p[4]),identifier(p[5]),identifier(p[6]),clean(String(Base64.getDecoder().decode(p[7]),Charsets.UTF_8)))
                }.getOrNull()?:continue
                if(out.size>=600)out.removeFirst();out.addLast(entry)
            }
            return Snapshot(active,out.toList())
        }
    }
}
