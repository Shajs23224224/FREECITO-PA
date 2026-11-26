import com.omega.core.reasoning.*
fun main(){
 val r=Reasoner();val facts=listOf(Fact("frame",Truth.TRUE,.9,"sensor"),Fact("depth",Truth.TRUE,.9,"depth"),Fact("depth",Truth.FALSE,.8,"other"),Fact("weak",Truth.TRUE,.2,"weak"))
 val rules=listOf(Rule(setOf("frame"),"review"),Rule(setOf("depth"),"spatial"))
 val plans=listOf(Plan("review",listOf(Step("inspect",setOf("review"),.8,.1,.1))),Plan("depth",listOf(Step("compare",setOf("spatial"),1.0,.1,.1))),Plan("wait",listOf(Step("wait",emptySet(),.3,.1,.1))))
 val a=r.reason(facts,rules,plans,mapOf("available" to setOf("frame","review"),"missing" to setOf("spatial")),"depth")
 check(a.facts["depth"]==Truth.CONFLICT&&a.facts["weak"]==Truth.UNKNOWN&&a.facts["review"]==Truth.TRUE&&a.facts["spatial"]==null)
 check(a.selected?.id=="review"&&a.revised&&a.alternatives.last().blocked==setOf("spatial")&&a.hypotheses.single().covers==2)
 check(r.estimate(emptyList())==null&&r.estimate(listOf(true,true,false))==Estimate(.6,3))
 val c=r.associate(listOf(Trial(true,true),Trial(true,true),Trial(false,false)))!!;check(c.difference>0&&c.exposed.samples==2)
 check(r.associate(listOf(Trial(true,true)))==null)
 check(r.counterfactual(r.resolve(facts),mapOf("depth" to Truth.TRUE),rules,plans).first().id=="depth")
 check(r.resolve(facts)["depth"]==Truth.CONFLICT)
 val chain=r.deduce(mapOf("a" to Truth.TRUE,"b" to Truth.FALSE),listOf(Rule(setOf("b"),"c"),Rule(setOf("a"),"b")))
 check(chain["b"]==Truth.CONFLICT&&chain["c"]==null)
 check(runCatching{r.deduce(emptyMap(),listOf(Rule(setOf("a"),"b"),Rule(setOf("b"),"a")))}.isFailure)
 println("PASS: evidence conflict, deduction, beta estimate, association, abduction, hierarchical alternatives, counterfactual, revision")
}
