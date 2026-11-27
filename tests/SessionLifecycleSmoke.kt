import com.omega.core.lifecycle.SessionLifecycle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
fun main(){
    val gate=SessionLifecycle()
    check(gate.canStart()&&!gate.accepts(null))
    check(gate.begin("a")&&!gate.canStart()&&!gate.accepts("a"))
    check(!gate.begin("b")&&gate.activate("a")&&gate.accepts("a"))
    check(!gate.retire("old")&&gate.accepts("a"))
    check(gate.retire("a")&&!gate.accepts("a")&&!gate.begin("b"))
    check(!gate.complete("old",true)&&gate.complete("a",true))
    check(gate.begin("b")&&gate.activate("b"))
    check(!gate.accepts("a")&&gate.accepts("b")&&!gate.retire("a"))
    check(gate.retire("b")&&gate.complete("b",false)&&!gate.canStart())
    check(!gate.begin("c")&&!gate.complete("b",true))
    val failedStart=SessionLifecycle()
    check(failedStart.begin("init")&&failedStart.retire("init")&&failedStart.complete("init",true)&&failedStart.canStart())
    repeat(100){
        val raced=SessionLifecycle(); val pool=Executors.newFixedThreadPool(8);val ready=CountDownLatch(8);val go=CountDownLatch(1);val wins=AtomicInteger()
        repeat(8){i->pool.execute{ready.countDown();go.await();if(raced.begin("token-$i"))wins.incrementAndGet()}}
        check(ready.await(5,TimeUnit.SECONDS));go.countDown();pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS));check(wins.get()==1)
        val token=checkNotNull(raced.snapshot().token);check(raced.activate(token)&&raced.retire(token)&&raced.complete(token,true))
    }
    println("PASS: lifecycle admission, stale tokens, failure quarantine, failed startup, 100 races (8 contenders each)")
}
