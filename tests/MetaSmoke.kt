import com.omega.core.meta.*
fun main(){
 val clean=Health(.3,false,0,30.0,80,false,false,25.0);val p=Performance(0,null,0.0);val g=MetaGovernor()
 check(g.update(clean,p).load==Load.NORMAL)
 val e=g.update(clean.copy(powerSave=true),p);check(e.load==Load.ECONOMY&&e.roles==2&&!e.depth&&!e.commands&&!e.learning)
 repeat(2){check(g.update(clean,p).load==Load.ECONOMY)};check(g.update(clean,p).load==Load.NORMAL)
 val l=g.update(clean.copy(lowMemory=true),p);check(l.load==Load.LIMITED&&l.roles==1&&!l.ocr)
 check(g.update(clean.copy(thermal=4),p).load==Load.STOP);repeat(10){check(g.update(clean,p).load==Load.STOP)}
 check(MetaGovernor().update(clean.copy(cpuPct=190.0),p).load==Load.ECONOMY)
 check(MetaGovernor().update(clean.copy(batteryC=43.0),p).load==Load.LIMITED)
 val m=CycleMetrics();repeat(40){m.record(100_000_000L,true)};repeat(16){m.record(1_500_000_000L,false)}
 val v=m.snapshot();check(v.samples==32&&v.p95Ms==1500.0&&v.failureRatio==.5)
 check(MetaGovernor().update(clean,v).load==Load.LIMITED)
 check(!MetaGovernor.policyHealthy(2,4,false)&&!MetaGovernor.policyHealthy(8,0,true)&&MetaGovernor.policyHealthy(8,1,false))
 check(runCatching{clean.copy(heapRatio=Double.NaN)}.isFailure)
 println("PASS: resource tiers, hysteresis, latched stop, CPU/thermal/battery/memory guards, rolling p95, failure rate, policy health")
}
