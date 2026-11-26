import com.omega.core.agents.*
import com.omega.core.neural.NeuralEngine
fun main(){
 val roles=AgentRegistry.roles;check(roles.size==160&&roles.map{it.id}.toSet().size==160&&roles.groupBy{it.domain}.values.all{it.size==10})
 check(roles.flatMap{it.modules}.sorted()==(1..824).map{"P"+it.toString().padStart(3,'0')})
 val board=AgentBoard(NeuralEngine(),100);val x=FloatArray(2048);val seen=mutableSetOf<String>()
 for(i in 0 until 20){val now=i.toLong()+100;val r=board.deliberate(i.toLong(),now,x,emptyList(),(0..15).toList(),8);check(r.proposals.all{it.hypothesis=="missing"}&&r.selected.isEmpty()&&r.activeModules.size<=26&&r.execution.all{it.isFinite()});seen.addAll(r.proposals.map{it.agent})}
 check(seen.size==160)
 val key=roles.first().key;val good=Evidence(key,.8f,.9f,"sensor",200)
 val r=board.deliberate(20,200,x,listOf(good),listOf(0),1);check(r.selected.single().agent=="A001"&&r.activeModules.containsAll(listOf("I01","I02","F01","F02")))
 val conflict=board.deliberate(21,201,x,listOf(good,Evidence(key,.1f,.9f,"other",201)),listOf(0),1);check(conflict.proposals.single().hypothesis=="conflict"&&conflict.selected.isEmpty())
 val stale=board.deliberate(22,400,x,listOf(good),listOf(0),8);check(stale.selected.isEmpty())
 check(runCatching{board.deliberate(22,400,x,emptyList(),listOf(0))}.isFailure)
 println("PASS: 160 scheduled roles, 824 module associations, intermediate/final inference, missing/conflicting/stale evidence, bounded routing and history")
}
