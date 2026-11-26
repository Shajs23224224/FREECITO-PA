import com.omega.core.neural.*
import kotlin.math.abs
fun main(){
    val n=NeuralEngine(); val r=n.report()
    check(n.specs.map{it.id}.toSet().size==858 && n.specs.count{it.kind=="main"}==824 && n.specs.count{it.kind=="intermediate"}==32)
    check(n.specs.count{it.kind=="final"}==2 && r.layers==16 && r.width==2048 && r.rank==17 && NeuralEngine.RESIDUAL_GATES==8)
    check(r.uniqueWeights==3111536L && r.adapterWeights==1997424L && r.trainableWeights==1757184L)
    check(r.uniqueWeights+88L>=2074328L*3/2 && (r.uniqueWeights+88L).toDouble()/2074328L<1.5001)
    val x=FloatArray(2048){(it%17)/17f}; val a=n.forward("P001",x)
    check(a.size==2048 && a.all{it.isFinite()} && !a.contentEquals(n.forward("P002",x)))
    n.specs.forEach{check(n.forward(it.id,x).all{v->v.isFinite()})}
    val previous=n.outputAdapter("P001"); val other=n.outputAdapter("P002")
    n.applyOutputGradient("P001",FloatArray(2048){1f},.005f)
    val updated=n.forward("P001",x); check(abs((a[0]-updated[0])-.005f)<1e-5f && n.outputAdapter("P002").contentEquals(other))
    n.restoreOutputAdapter("P001",previous); check(n.forward("P001",x).contentEquals(a))
    val p=n.infer(x,listOf(0,1,2),2); check(p.activeIds.size==8 && p.activeIds.takeLast(2)==listOf("F01","F02") && p.executionEmbedding.size==2048)
    check(runCatching{n.forward("P001",FloatArray(2048){Float.NaN})}.isFailure)
    println("PASS: 858 executable modules, rank 17, eight residual gates per module, 3111536 core parameters, gradient isolation and restore")
    println("core=${r.uniqueWeights} head=88 total=${r.uniqueWeights+88L} increase=${100.0*((r.uniqueWeights+88L)/2074328.0-1)}%")
}
