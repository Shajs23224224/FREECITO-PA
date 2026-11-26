import com.omega.core.executive.*
fun main(){
 val c=CombatContext(true,.8f,10,30,true,false,2,0,.95f)
 check(TacticalPolicy.priority(CombatAction.COVER,c)>0)
 check(TacticalPolicy.priority(CombatAction.ENGAGE,c)<0)
 check(TacticalPolicy.priority(CombatAction.ENGAGE,c.copy(inCover=true))>0)
 check(TacticalPolicy.priority(CombatAction.RELOAD,c.copy(loaded=0,threatVisible=false))>0)
 check(TacticalPolicy.priority(CombatAction.HEAL,c.copy(health=.3f,threatVisible=false))>0)
 check(TacticalPolicy.priority(CombatAction.DISENGAGE,c.copy(health=.2f))>0)
 for(a in CombatAction.values()){check(TacticalPolicy.priority(a,c.copy(trainingAttested=false))<0);check(TacticalPolicy.priority(a,null)<0)}
 check(!TacticalPolicy.confirmed(CombatAction.ENGAGE,c,c))
 check(TacticalPolicy.confirmed(CombatAction.ENGAGE,c,c.copy(hits=1)))
 check(!TacticalPolicy.confirmed(CombatAction.ENGAGE,c,c.copy(hits=null)))
 check(TacticalPolicy.confirmed(CombatAction.COVER,c,c.copy(inCover=true)))
 check(TacticalPolicy.confirmed(CombatAction.RELOAD,c.copy(loaded=0),c))
 check(TacticalPolicy.confirmed(CombatAction.HEAL,c.copy(health=.2f),c))
 check(TacticalPolicy.confirmed(CombatAction.DISENGAGE,c,c.copy(threatVisible=false)))
 check(TacticalPolicy.features(null,0f).size==8&&TacticalPolicy.features(null,0f)[1]==-1f)
 println("PASS: combat context, initial priorities, unknown observations, training attestation, outcome verification, bounded features")
}
