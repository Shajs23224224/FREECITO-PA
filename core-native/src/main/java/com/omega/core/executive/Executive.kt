package com.omega.core.executive

enum class Family { INSPECT, EXPLORE, COLLECT, EQUIP, USE, EXCHANGE_VIRTUAL }
enum class Outcome { SUCCESS, FAILURE, UNKNOWN }
data class Resource(val id:String,val count:Int,val desired:Int) {
    init { require(id.length in 1..64 && count in 0..1000000 && desired in 0..1000000) }
}
data class Offer(val id:String,val family:Family,val resource:String?=null,val costResource:String?=null,val cost:Int=0,val virtualOnly:Boolean=false) {
    init { require(id.length in 1..80 && cost in 0..1000000 && (resource==null||resource.length in 1..64) && (costResource==null||costResource.length in 1..64)) }
}
data class Receipt(val commandId:String,val outcome:Outcome)
data class GameState(val scope:String,val sequence:Long,val observedNs:Long,val resources:List<Resource>,val offers:List<Offer>,val receipt:Receipt?=null) {
    init { require(scope.length in 1..200 && sequence>=0 && observedNs>=0 && resources.size<=64 && offers.size<=32 && resources.map{it.id}.distinct().size==resources.size && offers.map{it.id}.distinct().size==offers.size) }
}
data class Command(val id:String,val scope:String,val sequence:Long,val offerId:String,val expiresNs:Long)
data class Result(val command:Command,val family:Family,val outcome:Outcome,val reason:String)
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
class Executive(private val port:GamePort,private val session:String,private val clock:()->Long,private val ttl:Long=2_000_000_000L) : AutoCloseable {
    private data class Pending(val cmd:Command,val offer:Offer,val before:Map<String,Int>)
    private var pending:Pending?=null
    private var last=-1L
    private var count=0L
    private var stopped=false
    private var uncertain=false
    private val scope=port.scope
    init { require(session.length in 1..80 && scope.length in 1..200 && ttl in 1..10_000_000_000L) }
    @Synchronized fun tick(contextReady:Boolean):Tick {
        if(stopped||uncertain)return Tick("blocked")
        return try { cycle(contextReady) } catch(e:Exception) { halt();Tick("adapter_error") }
    }
    private fun cycle(contextReady:Boolean):Tick {
        if(!port.authorized()||port.scope!=scope){halt();return Tick("unauthorized")}
        val now=clock();require(now>=0 && now<=Long.MAX_VALUE-ttl)
        val old=pending
        val state=port.observe()
        if(state==null || state.scope!=scope || state.observedNs>now || now-state.observedNs>ttl || state.sequence<=last){
            if(old!=null&&now>=old.cmd.expiresNs){halt();return Tick("unconfirmed",result=Result(old.cmd,old.offer.family,Outcome.UNKNOWN,"timeout"))}
            return Tick("waiting_fresh_state")
        }
        last=state.sequence
        val stock=state.resources.associateBy{it.id}
        val deficits=state.resources.count{it.count<it.desired}
        if(old!=null){
            val receipt=state.receipt
            if(receipt?.commandId==old.cmd.id){
                val item=old.offer.resource
                val delta=if(item==null)null else stock[item]?.count?.minus(old.before[item]?:0)
                val verified=when(old.offer.family){
                    Family.COLLECT,Family.EXCHANGE_VIRTUAL -> delta!=null&&delta>0
                    Family.USE -> delta!=null&&delta<0
                    else -> true // owner API receipt is the only evidence for these operations
                }
                val outcome=if(receipt.outcome==Outcome.SUCCESS&&!verified)Outcome.UNKNOWN else receipt.outcome
                pending=null
                if(outcome==Outcome.UNKNOWN)halt()
                return Tick("result",offered=state.offers.size,missing=deficits,result=Result(old.cmd,old.offer.family,outcome,if(verified)"adapter_receipt" else "unverified_delta"))
            }
            if(now>=old.cmd.expiresNs){halt();return Tick("unconfirmed",result=Result(old.cmd,old.offer.family,Outcome.UNKNOWN,"timeout"))}
            return Tick("awaiting_receipt",offered=state.offers.size,missing=deficits)
        }
        if(!contextReady)return Tick("context_unavailable",offered=state.offers.size,missing=deficits)
        fun priority(a:Offer):Int {
            if(!a.virtualOnly)return -1
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
            }
        }
        val offer=state.offers.map{it to priority(it)}.filter{it.second>=0}.sortedWith(compareByDescending<Pair<Offer,Int>>{it.second}.thenBy{it.first.id}).firstOrNull()?.first ?: return Tick("no_eligible_offer",offered=state.offers.size,missing=deficits)
        val dispatchTime=clock()
        if(dispatchTime<now || dispatchTime-state.observedNs>=ttl || !port.authorized() || port.scope!=scope){halt();return Tick("authorization_or_freshness_lost")}
        val cmd=Command("$session:${++count}",scope,state.sequence,offer.id,state.observedNs+ttl)
        // Set pending before calling port: exceptions must never cause a retry of uncertain execution.
        pending=Pending(cmd,offer,stock.mapValues{it.value.count})
        if(!port.submit(cmd)){pending=null;return Tick("rejected",offer.family.name,state.offers.size,deficits)}
        return Tick("submitted",offer.family.name,state.offers.size,deficits)
    }
    private fun halt(){uncertain=true;pending=null;runCatching{port.stop()}}
    @Synchronized override fun close(){if(!stopped){stopped=true;pending=null;runCatching{port.stop()}}}
}
