import com.omega.core.executive.*
import com.omega.core.learning.*
import java.io.File
import java.nio.file.Files
fun main(){
 val dir=Files.createTempDirectory("omega-learning-test").toFile()
 try{
 val f=File(dir,"checkpoint");val p=ContinuousPolicy("unit:test",f);val x=List(8){if(it==0)1f else .2f};val before=p.weights()
 val good=Experience("one","unit:test",Family.COLLECT,x,1f);p.confirmed(good)
 check(f.exists()&&!before.contentEquals(p.weights())&&p.adjustment(Family.COLLECT,x)>0f&&p.adjustment(Family.EXPLORE,x)==0f)
 val first=p.weights();p.confirmed(good);p.confirmed(good.copy(commandId="two",scope="other"));check(first.contentEquals(p.weights()))
 val restored=ContinuousPolicy("unit:test",f);check(first.contentEquals(restored.weights()));restored.confirmed(good);check(first.contentEquals(restored.weights()))
 val q=TestPort();var now=1L;val e=Executive(q,"learn",{now},100,restored)
 q.state=GameState(q.scope,1,now,listOf(Resource("item",0,2)),listOf(Offer("collect",Family.COLLECT,"item",virtualOnly=true)))
 check(e.tick(true,FloatArray(2048)).status=="submitted");now++
 q.state=GameState(q.scope,2,now,listOf(Resource("item",1,2)),emptyList(),Receipt(q.commands.single().id,Outcome.SUCCESS))
 check(e.tick(true).result?.outcome==Outcome.SUCCESS&&!first.contentEquals(restored.weights()))
 val last=restored.weights()
 val late=TestPort();val delayed=Executive(late,"late",{now},100,restored)
 late.state=GameState(late.scope,1,now,listOf(Resource("item",0,2)),listOf(Offer("collect",Family.COLLECT,"item",virtualOnly=true)))
 check(delayed.tick(true).status=="submitted");now+=101
 late.state=GameState(late.scope,2,now,listOf(Resource("item",1,2)),emptyList(),Receipt(late.commands.single().id,Outcome.SUCCESS))
 check(delayed.tick(true).result?.outcome==Outcome.UNKNOWN&&last.contentEquals(restored.weights()))
 val rank=ContinuousPolicy("unit:test",File(dir,"rank"));val signal=listOf(1f,1f/64f,2f/32f,1f,0f,0f,0f,0f)
 repeat(80){rank.confirmed(Experience("c$it","unit:test",Family.COLLECT,signal,-1f));rank.confirmed(Experience("e$it","unit:test",Family.EXCHANGE_VIRTUAL,signal,1f))}
 val select=TestPort();val ex=Executive(select,"rank",{now},100,rank)
 select.state=GameState(select.scope,1,now,listOf(Resource("item",0,2),Resource("coin",10,0)),listOf(Offer("collect",Family.COLLECT,"item",virtualOnly=true),Offer("exchange",Family.EXCHANGE_VIRTUAL,"item","coin",1,true)))
 check(ex.tick(true).goal=="EXCHANGE_VIRTUAL")
 val guard=ContinuousPolicy("unit:test",File(dir,"guard"));repeat(8){guard.confirmed(Experience("pos$it","unit:test",Family.COLLECT,signal,1f))};val anchored=guard.weights();guard.confirmed(Experience("neg","unit:test",Family.COLLECT,signal,-1f));check(anchored.contentEquals(guard.weights())&&guard.stats().contains("rollback=1"))
 val bad=ContinuousPolicy("other",f);check(bad.stats().contains("disabled=true"));bad.confirmed(good.copy(scope="other",commandId="three"));check(last.contentEquals(ContinuousPolicy("unit:test",f).weights()))
 f.appendBytes(byteArrayOf(1));val corrupt=ContinuousPolicy("unit:test",f);check(corrupt.health().checkpointStatus==CheckpointStatus.RECOVERED && !corrupt.health().disabled && first.contentEquals(corrupt.weights()))
 val blocked=File(dir,"not-dir");blocked.writeText("x");val failure=ContinuousPolicy("unit:test",File(blocked,"checkpoint"));failure.confirmed(good);check(failure.weights().all{it==0f}&&failure.stats().contains("disabled=true"))
 println("PASS: online reward update, family isolation, deduplication, checkpoint reopen, confirmed executive receipt, scope and checksum rejection, rollback on write failure, late receipt rejection, learned offer ranking, replay guard")
 }finally{dir.deleteRecursively()}
}
