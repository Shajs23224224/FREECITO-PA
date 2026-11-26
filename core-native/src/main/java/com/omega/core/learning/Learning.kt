package com.omega.core.learning

import com.omega.core.executive.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.abs

/** Contextual bandit: one-step reward regression, frozen upstream representation. */
data class PolicyHealth(val updates:Long,val rollbacks:Long,val disabled:Boolean)

class ContinuousPolicy(private val scope:String,private val file:File):AdaptivePolicy {
    companion object { const val FEATURES=8; const val WEIGHTS=88; private const val MAGIC=0x4f4d3802; private const val LEGACY=0x4f4d3801 }
    private data class Sample(val family:Family,val x:List<Float>,val reward:Float)
    private data class State(val weights:FloatArray,val samples:List<Sample>,val ids:List<String>,val updates:Long,val rollbacks:Long)
    private var state=State(FloatArray(WEIGHTS),emptyList(),emptyList(),0,0)
    private var disabled=false
    private var suspended=false
    @Synchronized fun setSuspended(value:Boolean){suspended=value}
    @Synchronized fun health()=PolicyHealth(state.updates,state.rollbacks,disabled)
    init { require(scope.length in 1..200); if(file.exists())try{state=read()}catch(e:Exception){disabled=true} }
    private fun eligible(f:Family)=f in listOf(Family.INSPECT,Family.EXPLORE,Family.COLLECT,Family.EXCHANGE_VIRTUAL,Family.COVER,Family.RELOAD,Family.HEAL,Family.ENGAGE,Family.DISENGAGE)
    private fun valid(x:List<Float>)=x.size==FEATURES&&x.all{it.isFinite()&&abs(it)<=1f}
    private fun prediction(w:FloatArray,f:Family,x:List<Float>)=x.indices.sumOf{(w[f.ordinal*FEATURES+it]*x[it]).toDouble()}.toFloat().coerceIn(-1f,1f)
    @Synchronized override fun adjustment(family:Family,features:List<Float>):Float = if(disabled||suspended||!eligible(family)||!valid(features))0f else 30f*prediction(state.weights,family,features)
    @Synchronized fun stats():String="updates=${state.updates} rollback=${state.rollbacks} disabled=$disabled"
    @Synchronized fun weights():FloatArray=state.weights.copyOf()
    @Synchronized override fun confirmed(experience:Experience){
        if(disabled||suspended||experience.scope!=scope||!eligible(experience.family)||!valid(experience.features)||!experience.reward.isFinite()||experience.reward !in -1f..1f||experience.commandId.length !in 1..160||experience.commandId in state.ids)return
        val old=state
        val sample=Sample(experience.family,experience.features.toList(),experience.reward)
        val anchors=old.samples.filter{it.family==sample.family}
        val next=old.weights.copyOf()
        fun loss(w:FloatArray,s:Sample):Float{val d=prediction(w,s.family,s.x)-s.reward;return d*d}
        val batch=listOf(sample)+anchors.takeLast(7)
        val offset=sample.family.ordinal*FEATURES
        for(i in 0 until FEATURES){
            val gradient=batch.map{(prediction(old.weights,it.family,it.x)-it.reward)*it.x[i]}.average().toFloat()
            next[offset+i]=(old.weights[offset+i]-.04f*gradient.coerceIn(-1f,1f)).coerceIn(-.5f,.5f)
        }
        val worsens=anchors.isNotEmpty()&&anchors.map{loss(next,it)}.average()>anchors.map{loss(old.weights,it)}.average()+.03
        val reject=worsens||!next.all{it.isFinite()}||loss(next,sample)>loss(old.weights,sample)+.001f
        val candidate=State(if(reject)old.weights else next,(old.samples.filter{it.family!=sample.family}+ (anchors+sample).takeLast(8)),(old.ids+experience.commandId).takeLast(128),old.updates+if(reject)0 else 1,old.rollbacks+if(reject)1 else 0)
        try{write(candidate);state=candidate}catch(e:Exception){disabled=true;state=old}
    }
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun write(s:State){
        val payload=ByteArrayOutputStream().also{b->DataOutputStream(b).use{o->
            o.writeInt(MAGIC);o.writeUTF(scope);o.writeLong(s.updates);o.writeLong(s.rollbacks)
            s.weights.forEach{o.writeFloat(it)};o.writeInt(s.samples.size)
            s.samples.forEach{v->o.writeInt(v.family.ordinal);v.x.forEach{o.writeFloat(it)};o.writeFloat(v.reward)}
            o.writeInt(s.ids.size);s.ids.forEach{o.writeUTF(it)}
        }}.toByteArray()
        val dir=requireNotNull(file.parentFile);check(dir.isDirectory||dir.mkdirs());val tmp=File.createTempFile("omega-", ".tmp",dir)
        try{FileOutputStream(tmp).use{it.write(payload);it.write(digest(payload));it.fd.sync()};Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}finally{tmp.delete()}
    }
    private fun read():State{
        require(file.length() in 33..131072);val all=file.readBytes();val data=all.copyOfRange(0,all.size-32)
        require(MessageDigest.isEqual(digest(data),all.copyOfRange(all.size-32,all.size)))
        return DataInputStream(ByteArrayInputStream(data)).use{i->
            val magic=i.readInt();require((magic==MAGIC||magic==LEGACY)&&i.readUTF()==scope);val u=i.readLong();val r=i.readLong();require(u>=0&&r>=0)
            val w=FloatArray(WEIGHTS);repeat(if(magic==LEGACY)48 else WEIGHTS){w[it]=i.readFloat()};require(w.all{it.isFinite()&&abs(it)<=.5f})
            val n=i.readInt();require(n in 0..(if(magic==LEGACY)32 else 72));val samples=List(n){val f=Family.values()[i.readInt()];val x=List(FEATURES){i.readFloat()};val v=i.readFloat();require((magic!=LEGACY||f.ordinal<6)&&eligible(f)&&valid(x)&&v.isFinite()&&v in -1f..1f);Sample(f,x,v)}
            val m=i.readInt();require(m in 0..128);val ids=List(m){i.readUTF().also{require(it.length in 1..160)}};require(i.available()==0&&ids.distinct().size==ids.size&&samples.groupBy{it.family}.values.all{it.size<=8});State(w,samples,ids,u,r)
        }
    }
}
