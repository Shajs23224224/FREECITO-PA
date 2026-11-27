import com.omega.core.meta.*
fun main() {
    val mib=1048576L
    val memory=MemoryHeadroom(512*mib,3072*mib,256*mib)
    check(!memory.atThreshold)
    check(memory.copy(availableBytes=256*mib).atThreshold)
    check(memory.copy(availableBytes=0).atThreshold)
    check(runCatching{memory.copy(availableBytes=-1)}.isFailure)
    check(runCatching{memory.copy(thresholdBytes=0)}.isFailure)
    check(runCatching{memory.copy(availableBytes=4000*mib)}.isFailure)
    check(ResourceDelta.counter(null,100)==null)
    check(ResourceDelta.counter(100,null)==null)
    check(ResourceDelta.counter(100,99)==null)
    check(ResourceDelta.counter(100,100)==0L)
    check(ResourceDelta.counter(100,125)==25L)
    check(ResourceDelta.cpuPercent(100,200,0)==null)
    check(ResourceDelta.cpuPercent(100,200,-1)==null)
    check(ResourceDelta.cpuPercent(100,400,200)==150.0)
    println("PASS: memory boundaries, missing/reset counters, CPU one-core units")
    val clean=Health(.06,false,0,35.0,80,false,false,20.0,memory)
    val pressured=clean.copy(memory=memory.copy(availableBytes=256*mib))
    val perf=Performance(0,null,0.0)
    val g=MetaGovernor()
    check(g.update(clean,perf).load==Load.NORMAL)
    check(g.update(pressured,perf).load==Load.LIMITED)
    check(g.decision().causes==listOf(ResourceCause.RAM_THRESHOLD))
    repeat(10){check(g.update(clean.copy(memory=null),perf).load==Load.LIMITED)}
    check(g.decision().causes==listOf(ResourceCause.TELEMETRY_UNAVAILABLE))
    repeat(3){check(g.update(clean,perf).load==Load.LIMITED)}
    check(g.decision().state==ResourceState.RECOVERY)
    repeat(2){check(g.update(clean,perf).load==Load.LIMITED)}
    check(g.update(clean,perf).load==Load.ECONOMY)
    repeat(3){g.update(clean,perf)}
    check(g.decision().state==ResourceState.NORMAL)
    check(g.update(pressured.copy(thermal=4),perf).load==Load.STOP)
    repeat(10){check(g.update(clean,perf).load==Load.STOP)}
    println("PASS: RAM threshold guard, missing telemetry blocks recovery, gradual recovery, thermal STOP retained")
}
