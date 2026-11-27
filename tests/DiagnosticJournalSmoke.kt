import com.omega.core.diagnostics.DiagnosticJournal
import com.omega.core.diagnostics.DiagnosticJournal.Severity
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

fun main(){
    var now=0L
    val j=DiagnosticJournal(wall={1000L+now},monotonic={now},maxSessions=3,maxEntries=4)
    j.begin("session-a","1.0.16")
    check(j.append("memory","sample",Severity.DEBUG,"heap=1"))
    check(!j.append("memory","sample",Severity.DEBUG,"heap=1"))
    now=1000;check(j.append("memory","sample",Severity.DEBUG,"heap=2"))
    check(!j.append("memory","sample",Severity.DEBUG,"heap=2"))
    check(j.append("memory","sample",Severity.ERROR,"failure"))
    j.end("user_stop")
    j.begin("session-b","1.0.16")
    check(!j.append("test","stale",Severity.ERROR,expectedSession="session-a"))
    check(j.append("test","escape",Severity.INFO,"quote=\" slash=\\ newline=\n",true,"session-b"))
    val snap=j.snapshot();check(snap.entries.any{it.session=="session-a"});check(snap.entries.count{it.session=="session-a"}==4)
    val decoded=DiagnosticJournal.decode(DiagnosticJournal.encode(snap));check(decoded==snap)
    check(decoded.jsonLines().contains("quote=\\\" slash=\\\\"))
    val recovered=DiagnosticJournal(maxSessions=3,maxEntries=4);recovered.restore(decoded)
    check(recovered.snapshot().active==null);check(recovered.snapshot().entries.any{it.session=="session-b"&&it.event=="previous_process_interrupted"})
    recovered.begin("session-c","1.0.16");recovered.end("done");recovered.begin("session-d","1.0.16");recovered.end("done")
    check(recovered.snapshot().entries.map{it.session}.distinct().size==3);check(recovered.snapshot().entries.none{it.session=="session-a"})
    val error=IllegalStateException("secret account 123",IllegalArgumentException("secret path"))
    val safe=DiagnosticJournal.exceptionDetail(error);check("IllegalStateException" in safe&&"IllegalArgumentException" in safe&&"secret" !in safe)
    val concurrent=DiagnosticJournal();concurrent.begin("session-thread","1.0.16")
    val gate=CountDownLatch(1);val threads=(1..8).map{n->thread{gate.await();repeat(100){concurrent.append("worker","test",Severity.ERROR,"worker=$n",true,"session-thread")}}};gate.countDown();threads.forEach{it.join()}
    val entries=concurrent.snapshot().entries;check(entries.size==120&&entries.map{it.id}.toSet().size==120)
    check(runCatching{DiagnosticJournal.decode("bad header")}.isFailure)
    check(DiagnosticJournal.decode(DiagnosticJournal.encode(snap)+"broken\n")==snap)
    println("PASS: rate limiting, error priority, bounds, scope retention, token rejection, persistence roundtrip, interrupted session, JSON escaping, exception privacy, 800 concurrent inserts, corrupt records")
    println(decoded.jsonLines())
}
