import com.omega.core.world.*
class TestStore:MemoryStore {
    val records=mutableMapOf<Triple<String,MemoryKind,String>,Recall>()
    override fun putBatch(scope:String,records:List<Recall>){records.forEach{this.records[Triple(scope,it.kind,it.key)]=it}}
    override fun recent(scope:String,kind:MemoryKind,limit:Int)=records.filterKeys{it.first==scope&&it.second==kind}.values.sortedByDescending{it.atMs}.take(limit)
}
fun main(){
    val store=TestStore();val m=CognitiveMemory("game:1",store);val world=WorldModel(m,"s1")
    val a=Visible(1,"crate",.1f,.2f,.8f);val b=Visible(2,"door",.9f,.7f,.9f)
    val depth=List(64){it/63f};val first=world.observe(Scene(1,1,100,listOf(a,b),depth))
    check(first.entities.size==2&&first.relations.any{it.type=="screen_left"}&&first.relations.any{it.type.contains("relative_depth")})
    val second=world.observe(Scene(2,2,101,listOf(a,b),null));check(second.place==first.place&&second.placeSimilarity==1f&&second.entities.all{it.relativeDepth==null})
    check(m.retrieve(MemoryKind.SEMANTIC,setOf("crate"),101).single().key=="label:crate")
    check(CognitiveMemory("game:2",store).recent(MemoryKind.EPISODIC).isEmpty())
    check(m.recent(MemoryKind.PROCEDURAL).isEmpty()&&m.recent(MemoryKind.STRATEGIC).isEmpty()&&m.recent(MemoryKind.PROSPECTIVE).isEmpty())
    for(k in MemoryKind.values())m.consolidate(listOf(Recall(k,"verified_record","test_only",.8f,102,"unit_test")))
    check(MemoryKind.values().all{m.recent(it).any{r->r.key=="verified_record"}})
    val restored=WorldModel(CognitiveMemory("game:1",store),"s2").observe(Scene(1,1,103,listOf(a,b),depth));check(restored.place==first.place)
    val third=world.observe(Scene(3,3,104,listOf(a.copy(label="wall"),b.copy(label="tree")),null));check(third.priorPlace==first.place&&third.place!=first.place)
    check(runCatching{world.observe(Scene(3,4,105,listOf(a),null))}.isFailure)
    check(world.observe(Scene(4,4,106,emptyList(),null)).entities.isEmpty())
    println("PASS: eight memory kinds, scoped recall, scene-relative relations, depth missing, place hypotheses, graph transitions, no invented skills, frame validation")
}
