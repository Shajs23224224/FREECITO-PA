import com.omega.core.neural.NeuralEngine
import com.omega.core.meta.CycleMetrics
import java.lang.management.ManagementFactory
fun main(){
    val n=NeuralEngine();val x=FloatArray(2048){(it%17)/17f};val cpu=ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
    println("JVM ${System.getProperty("java.version")} arch=${System.getProperty("os.arch")} processors=${Runtime.getRuntime().availableProcessors()}")
    repeat(20){n.infer(x,listOf(0,1,2,3),4)}
    for(b in listOf(1,2,4)){
        val metrics=CycleMetrics();val before=cpu?.processCpuTime;val start=System.nanoTime();var total=0L
        repeat(32){val t=System.nanoTime();val p=n.infer(x,listOf(0,1,2,3),b);val elapsed=System.nanoTime()-t;check(p.activeIds.size==3*b+2 && p.executionEmbedding.all{it.isFinite()});metrics.record(elapsed,true);total+=elapsed}
        val wall=System.nanoTime()-start;val after=cpu?.processCpuTime;val cpuMs=if(before!=null&&after!=null)(after-before)/1e6 else null
        println("domains=$b modules=${3*b+2} n=32 mean_ms=${total/32.0/1e6} p95_ms=${metrics.snapshot().p95Ms} wall_ms=${wall/1e6} process_cpu_ms=$cpuMs")
    }
}
