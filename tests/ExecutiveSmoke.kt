import com.omega.core.executive.*
class TestPort:GamePort {
    override val scope="unit:test"
    var permission=true;var state:GameState?=null;val commands=mutableListOf<Command>();var stops=0
    override fun authorized()=permission
    override fun observe()=state
    override fun submit(command:Command):Boolean {check(permission);commands+=command;return true}
    override fun stop(){stops++}
}
fun main(){
    var now=10L;val p=TestPort();val e=Executive(p,"test",{now},100)
    fun state(seq:Long,stock:Int=0,receipt:Receipt?=null)=GameState(p.scope,seq,now,listOf(Resource("item",stock,2),Resource("coin",1,0)),listOf(Offer("paid",Family.COLLECT,"item"),Offer("expensive",Family.EXCHANGE_VIRTUAL,"item","coin",2,true),Offer("collect",Family.COLLECT,"item",virtualOnly=true),Offer("explore",Family.EXPLORE,virtualOnly=true)),receipt)
    p.state=state(1);check(e.tick(false).status=="context_unavailable"&&p.commands.isEmpty())
    now++;p.state=state(2);check(e.tick(true).goal=="COLLECT"&&p.commands.single().offerId=="collect")
    now++;p.state=state(3);check(e.tick(true).status=="awaiting_receipt"&&p.commands.size==1)
    now++;p.state=state(4,1,Receipt(p.commands.single().id,Outcome.SUCCESS));check(e.tick(true).result?.outcome==Outcome.SUCCESS)
    now++;p.state=state(5,2);check(e.tick(true).goal=="EXPLORE")
    now+=101;p.state=null;check(e.tick(true).result?.outcome==Outcome.UNKNOWN);check(e.tick(true).status=="blocked"&&p.commands.size==2&&p.stops==1)
    e.close();e.close();check(p.stops==2)
    val q=TestPort();val z=Executive(q,"other",{now},100);q.permission=false;q.state=state(1);check(z.tick(true).status=="unauthorized"&&q.commands.isEmpty())
    val k=TestPort();val x=Executive(k,"delta",{now},100);k.state=state(1);x.tick(true);now++;k.state=state(2,0,Receipt(k.commands.single().id,Outcome.SUCCESS));check(x.tick(true).result?.outcome==Outcome.UNKNOWN&&x.tick(true).status=="blocked")
    println("PASS: authorization, fresh sequence, inventory deficits, virtual-only, affordability, single in-flight, receipt, delta, timeout, stop")
}
