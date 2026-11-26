package com.omega.core.reasoning

enum class Truth { TRUE, FALSE, UNKNOWN, CONFLICT }
data class Fact(val key:String,val truth:Truth,val confidence:Double,val source:String) {
    init { require(key.length in 1..80 && source.length in 1..80 && confidence.isFinite() && confidence in 0.0..1.0) }
}
data class Rule(val premises:Set<String>,val conclusion:String) {
    init { require(premises.isNotEmpty() && premises.size<=8 && conclusion.isNotBlank()) }
}
data class Estimate(val probability:Double,val samples:Int)
data class Trial(val exposure:Boolean,val outcome:Boolean)
data class Association(val exposed:Estimate,val unexposed:Estimate,val difference:Double)
data class Explanation(val hypothesis:String,val covers:Int,val total:Int)
data class Step(val id:String,val requires:Set<String>,val benefit:Double,val cost:Double,val uncertainty:Double) {
    init { require(id.isNotBlank() && listOf(benefit,cost,uncertainty).all{it.isFinite()&&it in 0.0..1.0}) }
}
data class Plan(val id:String,val steps:List<Step>) { init { require(id.isNotBlank()&&steps.size in 1..8) } }
data class Evaluation(val id:String,val score:Double?,val blocked:Set<String>,val steps:List<String>)
data class ReasoningResult(val facts:Map<String,Truth>,val hypotheses:List<Explanation>,val alternatives:List<Evaluation>,val selected:Evaluation?,val revised:Boolean)

/** Symbolic observation planning only; no environment simulation, action dispatch, or training. */
class Reasoner {
    fun resolve(facts:List<Fact>):Map<String,Truth> {
        require(facts.size<=256)
        return facts.groupBy{it.key}.mapValues{(_,v)->
            val reliable=v.filter{it.confidence>=.6}.map{it.truth}.toSet()
            when { Truth.CONFLICT in reliable || (Truth.TRUE in reliable && Truth.FALSE in reliable)->Truth.CONFLICT
                Truth.TRUE in reliable->Truth.TRUE; Truth.FALSE in reliable->Truth.FALSE; else->Truth.UNKNOWN }
        }
    }
    fun deduce(base:Map<String,Truth>,rules:List<Rule>):Map<String,Truth> {
        require(base.size<=256&&rules.size<=64)
        // Evaluate a dependency DAG in order; reject cycles rather than propagate unsupported facts.
        val out=base.toMutableMap()
        val pending=rules.groupBy{it.conclusion}.toMutableMap()
        while(pending.isNotEmpty()) {
            val ready=pending.keys.filter{k->pending.getValue(k).all{r->r.premises.none{it in pending}}}
            require(ready.isNotEmpty()){ "Cyclic rule dependencies" }
            for(k in ready){
                val implied=pending.getValue(k).any{r->r.premises.all{out[it]==Truth.TRUE}}
                if(implied)out[k]=when(out[k]){Truth.FALSE,Truth.CONFLICT->Truth.CONFLICT;else->Truth.TRUE}
                pending.remove(k)
            }
        }
        return out.toMap()
    }

    /** Beta(1,1) posterior mean for binary observations; no fabricated trials. */
    fun estimate(values:List<Boolean>):Estimate? { require(values.size<=256);return if(values.isEmpty())null else Estimate((values.count{it}+1.0)/(values.size+2.0),values.size) }
    /** Association only, not an identified causal effect. */
    fun associate(trials:List<Trial>):Association? {
        require(trials.size<=256);val a=estimate(trials.filter{it.exposure}.map{it.outcome})?:return null
        val b=estimate(trials.filter{!it.exposure}.map{it.outcome})?:return null
        return Association(a,b,a.probability-b.probability)
    }
    fun explain(observed:Set<String>,candidates:Map<String,Set<String>>):List<Explanation> {
        require(observed.size<=64&&candidates.size<=32)
        return candidates.map{(h,e)->Explanation(h,e.intersect(observed).size,observed.size)}.filter{it.covers>0}.sortedWith(compareByDescending<Explanation>{it.covers}.thenBy{it.hypothesis})
    }
    fun evaluate(facts:Map<String,Truth>,plans:List<Plan>):List<Evaluation> {
        require(plans.size<=16&&plans.map{it.id}.toSet().size==plans.size)
        return plans.map{p->val blocked=p.steps.flatMap{it.requires}.filter{facts[it]!=Truth.TRUE}.toSet()
            Evaluation(p.id,if(blocked.isEmpty())p.steps.sumOf{it.benefit-it.cost-it.uncertainty}/p.steps.size else null,blocked,p.steps.map{it.id})
        }.sortedWith(compareByDescending<Evaluation>{it.score?:Double.NEGATIVE_INFINITY}.thenBy{it.id})
    }
    /** What-if under explicitly supplied premises, not a prediction of an actual game transition. */
    fun counterfactual(base:Map<String,Truth>,assumptions:Map<String,Truth>,rules:List<Rule>,plans:List<Plan>):List<Evaluation> = evaluate(deduce(base+assumptions,rules),plans)
    fun reason(facts:List<Fact>,rules:List<Rule>,plans:List<Plan>,explanations:Map<String,Set<String>>,previous:String?):ReasoningResult {
        val resolved=deduce(resolve(facts),rules)
        val alternatives=evaluate(resolved,plans)
        val selected=alternatives.firstOrNull{it.score!=null && it.score>=0.0}
        return ReasoningResult(resolved,explain(resolved.filterValues{it==Truth.TRUE}.keys,explanations),alternatives,selected,previous!=null&&previous!=selected?.id)
    }
}
