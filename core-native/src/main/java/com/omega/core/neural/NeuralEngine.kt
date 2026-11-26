package com.omega.core.neural

import kotlin.math.*
import kotlin.random.Random

data class ModuleSpec(val id: String, val domain: Int, val kind: String)
data class NeuralReport(val modules: Int, val width: Int, val layers: Int, val rank: Int, val uniqueWeights: Long, val adapterWeights: Long, val trainableWeights: Long)
data class NeuralPass(val activeIds: List<String>, val decisionEmbedding: FloatArray, val executionEmbedding: FloatArray)

/** Expanded compressed reference. Random initial weights, no trained policy or game commands. */
class NeuralEngine(seed: Int = 17) {
    companion object { const val WIDTH=2048; const val LAYERS=16; const val RANK=17; const val RESIDUAL_GATES=8 }
    val specs: List<ModuleSpec> = buildList {
        var n=1
        repeat(16) { d -> repeat(if(d<8)52 else 51) { add(ModuleSpec("P"+(n++).toString().padStart(3,'0'),d,"main")) } }
        repeat(32) { add(ModuleSpec("I"+(it+1).toString().padStart(2,'0'),it/2,"intermediate")) }
        repeat(2) { add(ModuleSpec("F0"+(it+1),-1,"final")) }
    }
    private val index=specs.withIndex().associate { it.value.id to it.index }
    private val rng=Random(seed)
    private fun random(n:Int,scale:Float)=FloatArray(n){(rng.nextFloat()*2f-1f)*scale}
    // Each layer maps 2048 -> rank 17 -> 2048, followed by a gated residual and nonlinear activation.
    // Bases are shared; gains, output biases and per-two-layer residual gates are independent per module.
    private val down=Array(LAYERS){random(WIDTH*RANK,1f/sqrt(WIDTH.toFloat()))}
    private val up=Array(LAYERS){random(WIDTH*RANK,.12f*sqrt(4f/RANK))}
    private val gain=Array(specs.size){FloatArray(LAYERS*RANK){.8f+rng.nextFloat()*.4f}}
    private val bias=Array(specs.size){random(WIDTH,.005f)}
    private val residual=Array(specs.size){FloatArray(RESIDUAL_GATES){.98f+rng.nextFloat()*.04f}}
    fun report():NeuralReport {
        val shared=down.sumOf{it.size.toLong()}+up.sumOf{it.size.toLong()}
        val adapters=gain.sumOf{it.size.toLong()}+bias.sumOf{it.size.toLong()}+residual.sumOf{it.size.toLong()}
        return NeuralReport(specs.size,WIDTH,LAYERS,RANK,shared+adapters,adapters,bias.sumOf{it.size.toLong()})
    }
    @Synchronized fun forward(id:String,input:FloatArray):FloatArray {
        val i=requireNotNull(index[id]); require(input.size==WIDTH && input.all{it.isFinite() && abs(it)<=100f})
        var x=input.copyOf(); var y=FloatArray(WIDTH); val z=FloatArray(RANK)
        for(l in 0 until LAYERS){
            val skip=residual[i][l/2]
            for(r in 0 until RANK){ var s=0f; for(j in 0 until WIDTH)s+=down[l][r*WIDTH+j]*x[j]; z[r]=tanh(s)*gain[i][l*RANK+r] }
            for(j in 0 until WIDTH){ var s=0f; for(r in 0 until RANK)s+=up[l][j*RANK+r]*z[r]; y[j]=tanh(skip*x[j]+s) }
            val swap=x; x=y; y=swap
        }
        for(j in 0 until WIDTH)x[j]+=bias[i][j]
        return x
    }
    /** Explicit adapter update for a supplied supervised gradient; not an RL trainer. */
    @Synchronized fun applyOutputGradient(id:String,gradient:FloatArray,rate:Float){
        val i=requireNotNull(index[id]); require(gradient.size==WIDTH && gradient.all{it.isFinite()} && rate.isFinite() && rate in 0f..0.01f)
        for(j in 0 until WIDTH)bias[i][j]=(bias[i][j]-rate*gradient[j].coerceIn(-1f,1f)).coerceIn(-.25f,.25f)
    }
    @Synchronized fun outputAdapter(id:String):FloatArray=bias[requireNotNull(index[id])].copyOf()
    @Synchronized fun restoreOutputAdapter(id:String,values:FloatArray){
        val i=requireNotNull(index[id]); require(values.size==WIDTH && values.all{it.isFinite() && it in -.25f.. .25f}); values.copyInto(bias[i])
    }
    /** Bounded routing by requested domains and deterministic input-derived selection; not learned routing. */
    @Synchronized fun infer(input:FloatArray,domains:List<Int>,budget:Int=2):NeuralPass{
        require(budget in 1..4 && domains.isNotEmpty() && domains.all{it in 0..15} && input.size==WIDTH && input.all{it.isFinite() && abs(it)<=100f})
        val active=mutableListOf<String>(); val fused=FloatArray(WIDTH)
        val selected=domains.distinct().take(budget)
        for(d in selected){
            val options=specs.filter{it.kind=="main" && it.domain==d}
            val pick=options[((abs(input[d])*1000).toInt())%options.size].id
            val p=forward(pick,input); active+=pick
            val a=specs[824+d*2].id; val b=specs[825+d*2].id
            val x=forward(a,p); val y=forward(b,p); active+=a; active+=b
            for(j in fused.indices)fused[j]+=(x[j]+y[j])/(2*selected.size)
        }
        val decision=forward("F01",fused); active+="F01"
        val execution=forward("F02",decision); active+="F02"
        return NeuralPass(active.toList(),decision,execution)
    }
}
