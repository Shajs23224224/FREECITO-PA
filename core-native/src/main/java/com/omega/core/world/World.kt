package com.omega.core.world

import kotlin.math.abs
import kotlin.math.exp

enum class MemoryKind { IMMEDIATE, WORKING, EPISODIC, SEMANTIC, PROCEDURAL, SPATIAL, STRATEGIC, PROSPECTIVE }
data class Recall(val kind:MemoryKind,val key:String,val value:String,val confidence:Float,val atMs:Long,val source:String) {
    init { require(key.length in 1..180 && value.length in 1..2048 && source.length in 1..80 && confidence.isFinite() && confidence in 0f..1f && atMs>=0) }
}
interface MemoryStore : AutoCloseable {
    fun putBatch(scope:String, records:List<Recall>)
    fun recent(scope:String, kind:MemoryKind, limit:Int):List<Recall>
    override fun close() {}
}
class CognitiveMemory(private val scope:String,private val store:MemoryStore) {
    init { require(scope.length in 1..200) }
    fun consolidate(records:List<Recall>) { require(records.size<=128); if(records.isNotEmpty()) store.putBatch(scope,records) }
    fun recent(kind:MemoryKind,limit:Int=32):List<Recall> { require(limit in 1..256); return store.recent(scope,kind,limit) }
    fun retrieve(kind:MemoryKind,terms:Set<String>,nowMs:Long,limit:Int=8):List<Recall> {
        require(limit in 1..32 && nowMs>=0 && terms.size<=32)
        val query=terms.map{it.lowercase()}.toSet()
        return recent(kind,256).filter{it.atMs<=nowMs}.map{r->
            val tokens=(r.key+" "+r.value).lowercase().split(Regex("[^\\p{L}\\p{N}_]+" )).toSet()
            val overlap=if(query.isEmpty())1.0 else query.count{it in tokens}.toDouble()/query.size
            val age=(nowMs-r.atMs)/86_400_000.0
            r to (overlap*r.confidence*(.75+.25*exp(-age/7)))
        }.filter{it.second>0}.sortedWith(compareByDescending<Pair<Recall,Double>>{it.second}.thenByDescending{it.first.atMs}).take(limit).map{it.first}
    }
}
data class Visible(val id:Long,val label:String,val x:Float,val y:Float,val confidence:Float) {
    init { require(id>=0 && label.length in 1..80 && listOf(x,y,confidence).all{it.isFinite()&&it in 0f..1f}) }
}
data class Scene(val frame:Long,val nowNs:Long,val atMs:Long,val entities:List<Visible>,val depth:List<Float>?)
data class SpatialEntity(val visible:Visible,val relativeDepth:Float?,val belief:Float)
data class Relation(val a:Long,val b:Long,val type:String)
data class WorldState(val frame:Long,val entities:List<SpatialEntity>,val relations:List<Relation>,val place:String?,val placeSimilarity:Float,val priorPlace:String?,val placeCount:Int)
/** Image-relative 2.5D observations, not metric 3D or hidden state. */
class WorldModel(private val memory:CognitiveMemory,private val session:String) {
    private var last=-1L
    private var lastPlace:String?=null
    private var sequence=0L
    init { require(session.length in 1..80) }
    private fun similarity(a:Set<String>,b:Set<String>):Float = if(a.isEmpty()||b.isEmpty())0f else a.intersect(b).size.toFloat()/a.union(b).size
    fun observe(s:Scene):WorldState {
        require(s.frame>last && s.frame>=0 && s.nowNs>=0 && s.atMs>=0 && s.entities.size<=32 && s.entities.map{it.id}.toSet().size==s.entities.size)
        require(s.depth==null || (s.depth.size==64 && s.depth.all{it.isFinite()&&it in 0f..1f}))
        val entities=s.entities.map { v -> SpatialEntity(v,s.depth?.get((v.y*8).toInt().coerceAtMost(7)*8+(v.x*8).toInt().coerceAtMost(7)),v.confidence) }
        val relations=mutableListOf<Relation>()
        for(i in entities.indices) for(j in i+1 until entities.size){
            val a=entities[i];val b=entities[j]
            if(abs(a.visible.x-b.visible.x)>.12f)relations+=Relation(a.visible.id,b.visible.id,if(a.visible.x<b.visible.x)"screen_left" else "screen_right")
            if(abs(a.visible.y-b.visible.y)>.12f)relations+=Relation(a.visible.id,b.visible.id,if(a.visible.y<b.visible.y)"screen_above" else "screen_below")
            if(a.relativeDepth!=null&&b.relativeDepth!=null&&abs(a.relativeDepth-b.relativeDepth)>.2f)relations+=Relation(a.visible.id,b.visible.id,if(a.relativeDepth>b.relativeDepth)"larger_relative_depth_value" else "smaller_relative_depth_value")
        }
        val signature=s.entities.filter{it.confidence>=.6f}.map{"${it.label.lowercase().replace(Regex("[^a-z0-9_]"),"_")}_${(it.x*4).toInt().coerceAtMost(3)}_${(it.y*4).toInt().coerceAtMost(3)}"}.toSet()
        val known=memory.recent(MemoryKind.SPATIAL,256).filter{it.key.startsWith("place:")&&it.atMs<=s.atMs}
        val best=known.map{it to similarity(signature,it.value.split(' ').toSet())}.maxByOrNull{it.second}
        val place=if(signature.size<2)null else if(best!=null&&best.second>=.75f)best.first.key else "place:$session:${sequence++}"
        val score=if(place!=null&&best?.first?.key==place)best.second else 0f
        val prior=lastPlace?.takeIf{place!=null&&it!=place}
        val records=mutableListOf<Recall>()
        fun add(k:MemoryKind,key:String,value:String,c:Float,source:String="perception") { records+=Recall(k,key,value,c,s.atMs,source) }
        val summary="entities=${entities.size} relations=${relations.size} depth=${s.depth!=null}"
        add(MemoryKind.IMMEDIATE,"latest",summary,1f)
        add(MemoryKind.WORKING,"context",summary,1f)
        add(MemoryKind.EPISODIC,"observation:$session:${s.frame}",summary,1f)
        s.entities.groupBy{it.label}.forEach{(label,items)->add(MemoryKind.SEMANTIC,"label:$label","observed_general_category",items.maxOf{it.confidence})}
        if(place!=null) {
            add(MemoryKind.SPATIAL,place,signature.sorted().joinToString(" ").take(2048),.5f,"visual_signature_hypothesis")
            if(prior!=null)add(MemoryKind.SPATIAL,"edge:$prior->$place".take(180),"temporal_cooccurrence_not_route",.4f,"observation_sequence")
        }
        memory.consolidate(records)
        last=s.frame;lastPlace=place
        return WorldState(s.frame,entities,relations,place,score,prior,(known.size+if(place!=null&&known.none{it.key==place})1 else 0).coerceAtMost(256))
    }
}
