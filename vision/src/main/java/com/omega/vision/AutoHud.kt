package com.omega.vision

import kotlin.math.*

/** Geometry-only hypotheses, not trained game-icon classifications. All locations are normalized. */
data class HudCircle(val x:Float,val y:Float,val radiusY:Float,val radiusX:Float,val score:Float)
data class HudCandidates(val width:Int,val height:Int,val circles:List<HudCircle>)
object AutoHud {
    private data class Ring(val dx:IntArray,val dy:IntArray,val innerX:IntArray,val innerY:IntArray,val outerX:IntArray,val outerY:IntArray)
    private fun ring(r:Int):Ring{
        fun offsets(radius:Int,cosine:Boolean)=IntArray(16){i->val a=2*PI*i/16;((if(cosine)cos(a) else sin(a))*radius).roundToInt()}
        return Ring(offsets(r,true),offsets(r,false),offsets(r-3,true),offsets(r-3,false),offsets(r+3,true),offsets(r+3,false))
    }
    fun scan(pixels:IntArray,w:Int,h:Int):HudCandidates{
        if(w !in 160..1024||h !in 100..720||w<=h||pixels.size!=w*h)return HudCandidates(w,h,emptyList())
        val luma=IntArray(pixels.size){i->val c=pixels[i];((c ushr 16 and 255)*77+(c ushr 8 and 255)*150+(c and 255)*29) shr 8}
        val minR=max(12,(h*.045f).roundToInt());val maxR=min(64,(h*.15f).roundToInt())
        val step=max(2,h/100)
        val result=mutableListOf<HudCircle>()
        fun response(cx:Int,cy:Int,k:Ring):Int{
            var count=0
            for(i in 0 until 16){
                val m=luma[(cy+k.dy[i])*w+cx+k.dx[i]]
                val a=luma[(cy+k.innerY[i])*w+cx+k.innerX[i]]
                val b=luma[(cy+k.outerY[i])*w+cx+k.outerX[i]]
                if((m-a>=23&&m-b>=23)||(a-m>=23&&b-m>=23))count++
            }
            return count
        }
        for(r in minR..maxR step 2){
            val outer=ring(r);val inner=listOf(.43f,.50f,.57f).map{ring(max(5,(r*it).roundToInt()))}
            for(y in r+4 until h-r-4 step step)for(x in r+4 until w-r-4 step step){
                val a=response(x,y,outer);if(a<12)continue
                val b=inner.maxOf{response(x,y,it)};if(b<11)continue
                val score=(a+b)/32f
                val c=HudCircle(x.toFloat()/w,y.toFloat()/h,r.toFloat()/h,r.toFloat()/w,score)
                val duplicate=result.indexOfFirst{abs(it.x-c.x)<max(it.radiusX,c.radiusX)*.65f&&abs(it.y-c.y)<max(it.radiusY,c.radiusY)*.65f}
                if(duplicate>=0){if(result[duplicate].score<c.score)result[duplicate]=c}
                else if(result.size<24)result+=c
                else {val low=result.indices.minByOrNull{result[it].score}?:0;if(result[low].score<c.score)result[low]=c}
            }
        }
        return HudCandidates(w,h,result.sortedByDescending{it.score}.take(12))
    }
}
/** Repeated independent observations; ambiguous or moved hypotheses must be reacquired. */
class HudTracker {
    private var previous:HudCircle?=null
    private var previousFrame=-1L
    private var size=0 to 0
    private var count=0
    fun clear(){previous=null;previousFrame=-1;count=0;size=0 to 0}
    fun update(frame:Long,c:HudCandidates,semantic:Pair<Float,Float>?=null):HudCircle?{
        if(frame<=previousFrame)return null
        if(size!=(c.width to c.height)||previousFrame>=0&&frame-previousFrame>3_000_000_000L)clear()
        previousFrame=frame;size=c.width to c.height
        val eligible=c.circles.filter{it.score>=.81f&&(semantic==null||abs(it.x-semantic.first)<=it.radiusX*1.5f&&abs(it.y-semantic.second)<=it.radiusY*1.5f)}
        val first=eligible.firstOrNull()
        val next=eligible.drop(1).firstOrNull()
        val chosen=first?.takeIf{next==null||it.score-next.score>=.12f}
        if(chosen==null){previous=null;count=0;return null}
        val old=previous
        count=if(old!=null&&abs(old.x-chosen.x)<=.018f&&abs(old.y-chosen.y)<=.025f&&abs(old.radiusY-chosen.radiusY)<=.025f)(count+1).coerceAtMost(3) else 1
        previous=chosen
        return chosen.takeIf{count>=2}
    }
}
