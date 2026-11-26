import com.omega.core.executive.*
import com.omega.core.learning.*
import java.nio.file.Files
import java.io.File
fun main(){
 val dir=Files.createTempDirectory("omega-tactical-test").toFile()
 try{
 val p=TestPort();var now=100L;val policy=ContinuousPolicy(p.scope,File(dir,"policy"));val e=Executive(p,"tactical",{now},100,policy)
 val c=CombatContext(true,.8f,10,30,true,false,2,0,.95f)
 fun s(seq:Long,ctx:CombatContext,receipt:Receipt?=null)=GameState(p.scope,seq,now,emptyList(),listOf(Offer("cover",Family.COVER,virtualOnly=true),Offer("engage",Family.ENGAGE,virtualOnly=true),Offer("walk",Family.EXPLORE,virtualOnly=true)),receipt,ctx)
 p.state=s(1,c);check(e.tick(true).goal=="COVER");now++
 p.state=s(2,c.copy(inCover=true),Receipt(p.commands.last().id,Outcome.SUCCESS));check(e.tick(true).result?.outcome==Outcome.SUCCESS&&policy.health().updates==1L)
 now++;p.state=s(3,c.copy(inCover=true));check(e.tick(true).goal=="ENGAGE")
 now++;p.state=s(4,c.copy(inCover=true),Receipt(p.commands.last().id,Outcome.SUCCESS));check(e.tick(true).result?.outcome==Outcome.UNKNOWN&&policy.health().updates==1L)
 check(policy.weights().size==88&&ContinuousPolicy(p.scope,File(dir,"policy")).weights().contentEquals(policy.weights()))
 println("PASS: executive tactical priority, confirmed effect learning, unverified hit rejected, checkpoint reopen")
 }finally{dir.deleteRecursively()}
}
