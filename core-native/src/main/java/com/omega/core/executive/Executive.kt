package com.omega.core.executive

enum class Family { INSPECT, EXPLORE, COLLECT, EQUIP, USE, EXCHANGE_VIRTUAL, COVER, RELOAD, HEAL, ENGAGE, DISENGAGE }
enum class Outcome { SUCCESS, FAILURE, UNKNOWN }
data class Resource(val id:String,val count:Int,val desired:Int) {
    init { require(id.length in 1..64 && count in 0..1000000 && desired in 0..1000000) }
}
data class Offer(val id:String,val family:Family,val resource:String?=null,val costResource:String?=null,val cost:Int=0,val virtualOnly:Boolean=false) {
    init { require(id.length in 1..80 && cost in 0..1000000 && (resource==null||resource.length in 1..64) && (costResource==null||costResource.length in 1..64)) }
}
data class Receipt(val commandId:String,val outcome:Outcome)
data class GameState(val scope:String,val sequence:Long,val observedNs:Long,val resources:List<Resource>,val offers:List<Offer>,val receipt:Receipt?=null,val combat:CombatContext?=null) {
    init { require(scope.length in 1..200 && sequence>=0 && observedNs>=0 && resources.size<=64 && offers.size<=32 && resources.map{it.id}.distinct().size==resources.size && offers.map{it.id}.distinct().size==offers.size) }
}
data class Command(val id:String,val scope:String,val sequence:Long,val offerId:String,val expiresNs:Long)
data class Result(val command:Command,val family:Family,val outcome:Outcome,val reason:String)
data class Experience(val commandId:String,val scope:String,val family:Family,val features:List<Float>,val reward:Float)
interface AdaptivePolicy {
    fun adjustment(family:Family,features:List<Float>):Float
    fun confirmed(experience:Experience)
}
data class Tick(val status:String,val goal:String?=null,val offered:Int=0,val missing:Int=0,val result:Result?=null)
/** Only a game-owner-permitted, visible-state API may implement this interface. Calls must be bounded/nonblocking. */
interface GamePort {
    val scope:String
    fun authorized():Boolean
    fun observe():GameState?
    /** Atomically recheck permission, scope, sequence, expiry and offer; false means definitely not accepted. */
    fun submit(command:Command):Boolean
    /** Revoke outstanding commands and session permission. Must be safe on repeated calls. */
    fun stop()
}
class Executive(private val port:GamePort,private val session:String,private val clock:()->Long,private val ttl:Long=2_000_000_000L,private val policy:AdaptivePolicy?=null,private val permissionGate:()->Boolean={true}) : AutoCloseable {
    private data class Pending(val cmd:Command,val offer:Offer,val before:Map<String,Int>,val features:List<Float>,val combat:CombatContext?)
    private var pending:Pending?=null
    private var last=-1L
    private var count=0L
    private var stopped=false
    private var uncertain=false
    private var latestCombat:Pair<Long,CombatContext>?=null
    private val visits=mutableMapOf<Family,Int>()
    @Synchronized fun combatSnapshot()=latestCombat
    private val scope=port.scope
    init { require(session.length in 1..80 && scope.length in 1..200 && ttl in 1..10_000_000_000L) }
    @Synchronized fun tick(contextReady:Boolean,embedding:FloatArray?=null):Tick {
        if(stopped||uncertain)return Tick("blocked")
        return try { cycle(contextReady,embedding) } catch(e:Exception) { halt();Tick("adapter_error") }
    }
    private fun cycle(contextReady:Boolean,embedding:FloatArray?):Tick {
        if(!permissionGate()||!port.authorized()||port.scope!=scope){halt();return Tick("unauthorized")}
        val now=clock();require(now>=0 && now<=Long.MAX_VALUE-ttl)
        val old=pending
        val state=port.observe()
        if(state==null || state.scope!=scope || state.observedNs>now || now-state.observedNs>ttl || state.sequence<=last){
            if(old!=null&&now>=old.cmd.expiresNs){halt();return Tick("unconfirmed",result=Result(old.cmd,old.offer.family,Outcome.UNKNOWN,"timeout"))}
            return Tick("waiting_fresh_state")
        }
        last=state.sequence
        latestCombat=state.combat?.takeIf{it.usable()}?.let{state.observedNs to it}
        val stock=state.resources.associateBy{it.id}
        val deficits=state.resources.count{it.count<it.desired}
        if(old!=null){
            if(now>=old.cmd.expiresNs){halt();return Tick("unconfirmed",result=Result(old.cmd,old.offer.family,Outcome.UNKNOWN,"timeout"))}
            val receipt=state.receipt
            if(receipt?.commandId==old.cmd.id){
                val item=old.offer.resource
                val delta=if(item==null)null else stock[item]?.count?.minus(old.before[item]?:0)
                val verified=when(old.offer.family){
                    Family.COLLECT,Family.EXCHANGE_VIRTUAL -> delta!=null&&delta>0
                    Family.USE -> delta!=null&&delta<0
                    Family.COVER,Family.RELOAD,Family.HEAL,Family.ENGAGE,Family.DISENGAGE -> TacticalPolicy.confirmed(CombatAction.valueOf(old.offer.family.name),old.combat,state.combat)
                    else -> true // owner API receipt is the only evidence for these operations
                }
                val outcome=if(receipt.outcome==Outcome.SUCCESS&&!verified)Outcome.UNKNOWN else receipt.outcome
                pending=null
                if(outcome==Outcome.UNKNOWN)halt()
                else if(permissionGate() && port.authorized() && port.scope==scope){
                    visits[old.offer.family]=(visits[old.offer.family]?:0).coerceAtMost(1_000_000)+1
                    val reward=if(outcome==Outcome.FAILURE)-1f else if(old.offer.family==Family.EXCHANGE_VIRTUAL).9f else 1f
                    policy?.confirmed(Experience(old.cmd.id,scope,old.offer.family,old.features,reward))
                }else halt()
                return Tick("result",offered=state.offers.size,missing=deficits,result=Result(old.cmd,old.offer.family,outcome,if(outcome==Outcome.UNKNOWN&&!verified)"unverified_delta" else "adapter_receipt"))
            }
            if(now>=old.cmd.expiresNs){halt();return Tick("unconfirmed",result=Result(old.cmd,old.offer.family,Outcome.UNKNOWN,"timeout"))}
            return Tick("awaiting_receipt",offered=state.offers.size,missing=deficits)
        }
        if(!contextReady)return Tick("context_unavailable",offered=state.offers.size,missing=deficits)
        fun priority(a:Offer):Int {
            if(!a.virtualOnly)return -1
            val combat=state.combat
            if(a.family.ordinal>=Family.COVER.ordinal)return if(a.cost==0)TacticalPolicy.priority(CombatAction.valueOf(a.family.name),combat) else -1
            if(combat!=null&&(!combat.usable()||combat.threatVisible!=false)&&a.family!=Family.INSPECT)return -1
            val r=stock[a.resource]
            val needs=r!=null&&r.count<r.desired
            val affordable=a.cost==0 || (a.costResource!=null&&(stock[a.costResource]?.count?:-1)>=a.cost)
            if(!affordable)return -1
            return when(a.family){
                Family.COLLECT -> if(needs&&a.cost==0)90 else -1
                Family.EXCHANGE_VIRTUAL -> if(needs&&a.cost>0)70 else -1
                Family.EQUIP,Family.USE -> -1 // no validated need/effect contract yet
                Family.EXPLORE -> if(a.cost==0)40 else -1
                Family.INSPECT -> if(a.cost==0)30 else -1
                else -> -1
            }
        }
        val e=embedding?.takeIf{it.size==2048&&it.all{v->v.isFinite()}}
        fun mean(start:Int):Float {
            if(e==null)return 0f
            var sum=0.0
            for(i in start until start+512)sum+=e[i].coerceIn(-1f,1f)
            return (sum/512.0).toFloat()
        }
        val m0=mean(0);val m1=mean(512);val m2=mean(1024)
        fun features(a:Offer):List<Float>{
            if(a.family.ordinal>=Family.COVER.ordinal)return TacticalPolicy.features(state.combat,m0)
            val r=stock[a.resource]
            val deficit=if(r!=null&&r.desired>0)((r.desired-r.count).toFloat()/r.desired).coerceIn(0f,1f) else 0f
            val cost=if(a.cost==0)0f else (a.cost.toFloat()/(stock[a.costResource]?.count?:1).coerceAtLeast(1)).coerceIn(0f,1f)
            return listOf(1f,deficits/64f,state.offers.size/32f,deficit,cost,m0,m1,m2)
        }
        val offer=state.offers.map{it to priority(it)}.filter{it.second>=0}.map{(a,p)->a to (p+(policy?.adjustment(a.family,features(a))?:0f).let{if(it.isFinite())it.coerceIn(-30f,30f) else 0f}+(if(state.combat?.usable()==true) 6f/(1+(visits[a.family]?:0)) else 0f))}.sortedWith(compareByDescending<Pair<Offer,Float>>{it.second}.thenBy{it.first.id}).firstOrNull()?.first ?: return Tick("no_eligible_offer",offered=state.offers.size,missing=deficits)
        val dispatchTime=clock()
        if(dispatchTime<now || dispatchTime-state.observedNs>=ttl || !permissionGate() || !port.authorized() || port.scope!=scope){halt();return Tick("authorization_or_freshness_lost")}
        val cmd=Command("$session:${++count}",scope,state.sequence,offer.id,state.observedNs+ttl)
        // Set pending before calling port: exceptions must never cause a retry of uncertain execution.
        pending=Pending(cmd,offer,stock.mapValues{it.value.count},features(offer),state.combat)
        if(!port.submit(cmd)){pending=null;return Tick("rejected",offer.family.name,state.offers.size,deficits)}
        return Tick("submitted",offer.family.name,state.offers.size,deficits)
    }
    private fun halt(){uncertain=true;pending=null;latestCombat=null;runCatching{port.stop()}}
    @Synchronized override fun close(){if(!stopped){stopped=true;pending=null;latestCombat=null;runCatching{port.stop()}}}
}
