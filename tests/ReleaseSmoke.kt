import com.omega.core.release.*
import com.omega.core.neural.NeuralEngine
import com.omega.core.meta.*
import com.omega.core.executive.*
import com.omega.core.learning.*
import java.nio.file.Files
import java.io.File
fun main(){
    check(SessionGate.connection(null,null)==Connection.UNCONFIGURED)
    check(SessionGate.connection("",null)==Connection.INVALID)
    check(SessionGate.connection("game:1","game:2")==Connection.INVALID)
    val read=SessionGate.connection("game:1",null);val ready=SessionGate.connection("game:1","game:1")
    check(SessionGate.captureAllowed(read,true)&&!SessionGate.commandAllowed(read,true,true,1,2))
    check(!SessionGate.captureAllowed(ready,false)&&!SessionGate.commandAllowed(ready,false,true,1,2))
    check(!SessionGate.commandAllowed(ready,true,true,null,2)&&!SessionGate.commandAllowed(ready,true,true,3,2))
    check(!SessionGate.commandAllowed(ready,true,true,0,1_500_000_001L))
    check(SessionGate.commandAllowed(ready,true,true,1,2)&&!SessionGate.commandAllowed(ready,true,false,1,2))
    val dir=Files.createTempDirectory("omega-release-test").toFile()
    try {
        val n=NeuralEngine();val p=n.infer(FloatArray(2048){.1f},listOf(0,1),2)
        check(p.activeIds.size==8&&p.executionEmbedding.all{it.isFinite()})
        val port=TestPort();var now=10L;val learner=ContinuousPolicy(port.scope,File(dir,"policy"));val exec=Executive(port,"release",{now},100,learner)
        val mode=MetaGovernor.budget(Load.LIMITED);learner.setSuspended(!mode.learning)
        fun state(seq:Long,stock:Int,receipt:Receipt?=null)=GameState(port.scope,seq,now,listOf(Resource("item",stock,2)),listOf(Offer("collect",Family.COLLECT,"item",virtualOnly=true)),receipt)
        port.state=state(1,0);check(exec.tick(SessionGate.commandAllowed(ready,true,mode.commands,now,now),p.executionEmbedding).status=="context_unavailable")
        check(port.commands.isEmpty()&&learner.weights().all{it==0f})
        now++;learner.setSuspended(false);port.state=state(2,0)
        check(exec.tick(SessionGate.commandAllowed(ready,true,true,now,now),p.executionEmbedding).status=="submitted")
        now++;port.state=state(3,1,Receipt(port.commands.single().id,Outcome.SUCCESS))
        check(exec.tick(false,p.executionEmbedding).result?.outcome==Outcome.SUCCESS)
        check(learner.health().updates==1L&&ContinuousPolicy(port.scope,File(dir,"policy")).weights().contentEquals(learner.weights()))
        exec.close();check(port.stops==1)
        val revoked=TestPort();var calls=0
        val guarded=Executive(revoked,"revoked",{now},100,permissionGate={++calls==1})
        revoked.state=state(1,0)
        check(guarded.tick(true,p.executionEmbedding).status=="authorization_or_freshness_lost"&&revoked.commands.isEmpty())
        guarded.close()
        println("PASS: connection gate, attestation, stale/future observations, adaptive load, real neural embedding, authorized test-port receipt, persistent policy, revocation before dispatch, stop")
    }finally{dir.deleteRecursively()}
}
