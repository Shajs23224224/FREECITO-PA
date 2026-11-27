package com.omega.vision
import kotlin.math.*
enum class HudIcon { MOVE, SPRINT, JUMP, CROUCH, PRONE, HEAL }
data class IconMatch(val kind:HudIcon,val x:Float,val y:Float,val radiusX:Float,val radiusY:Float,val score:Float)
object IconHud {
    private val patterns=listOf(
        "556666666666 556666678888 666677776666 666788887666 657888888756 657888888756 657777777756 657777777656 666777777566 665667766566 666655656666 666666666666",
        "433333323322 3338b3311111 3326d8511111 11119eca2111 1285cedb8711 33499dd81111 4449cde51111 444c75c73333 4556858c8555 4455a6559854 444674444864 544445555555",
        "644444444446 444457444444 45448e754444 55566bed9666 55555cecb655 556aabec4555 55559dee5555 5556d8db5555 55556ce85555 555557ba5555 5555555a5555 755555565556",
        "765555555557 655555555555 555555965555 555556ea5555 5799cced9555 5555abdee755 555558cdec55 55555adeed55 55555b6dea55 5555989d8a55 555686555555 655555555556",
        "665555555577 655555555557 555555555555 455555555555 5657e6555555 58bcedba8877 557addee7888 5546545cb875 455555544555 555555555555 654555555556 665555555577",
        "654444444456 544444444445 444455554444 45b8dcbdaa74 47caa457bba4 49ca58b59bb4 46ca67a5ab84 45cac66abb74 44a8cddda968 444444444248 444444444323 644444444434")
    private val templates=patterns.map{p->p.replace(" ","").map{it.digitToInt(16).toFloat()}.toFloatArray()}
    private data class Candidate(val x:Int,val y:Int,val r:Int,val score:Float)
    private fun normalize(a:FloatArray):FloatArray {val mean=a.average().toFloat();val norm=sqrt(a.sumOf{((it-mean)*(it-mean)).toDouble()}).toFloat().coerceAtLeast(.001f);return FloatArray(a.size){(a[it]-mean)/norm}}
    private val fine=templates.map{normalize(it)}
    private val coarse=templates.map{t->normalize(FloatArray(36){i->val y=i/6*2;val x=i%6*2;(t[y*12+x]+t[y*12+x+1]+t[(y+1)*12+x]+t[(y+1)*12+x+1])/4})}
    fun scan(pixels:IntArray,width:Int,height:Int):List<IconMatch>{
        if(width<160||height<100||width<=height||pixels.size!=width*height)return emptyList()
        val w=min(480,width);val h=(height.toFloat()*w/width).roundToInt()
        if(h<70)return emptyList()
        val l=FloatArray(w*h){i->
            val x=i%w;val y=i/w;var sum=0f;var count=0
            for(yy in y*height/h until max(y*height/h+1,(y+1)*height/h))for(xx in x*width/w until max(x*width/w+1,(x+1)*width/w)){
                val c=pixels[yy*width+xx];sum+=((c ushr 16 and 255)*77+(c ushr 8 and 255)*150+(c and 255)*29)/256f;count++
            };sum/count
        }
        val best=Array(6){mutableListOf<Candidate>()}
        val sample=FloatArray(36)
        val lo=max(5,(h*.027f).roundToInt());val hi=max(lo+1,(h*.085f).roundToInt())
        for(r in lo..hi step 2){
            val off=IntArray(6){i->((i+.5f)*2*r/6-r).roundToInt()}
            for(y in r+1 until h-r-1 step 2)for(x in r+1 until w-r-1 step 2){
                var sum=0f;var sq=0f
                for(i in 0 until 36){val v=l[(y+off[i/6])*w+x+off[i%6]];sample[i]=v;sum+=v;sq+=v*v}
                val energy=sq-sum*sum/36
                if(energy<36*25f)continue
                val norm=sqrt(energy)
                for(k in 0 until 6){var dot=0f;val t=coarse[k];for(i in 0 until 36)dot+=sample[i]*t[i];val score=dot/norm
                    if(score<.63f)continue
                    val list=best[k];val idx=list.indexOfFirst{abs(it.x-x)<r&&abs(it.y-y)<r}
                    if(idx>=0){if(list[idx].score<score)list[idx]=Candidate(x,y,r,score)} else if(list.size<12)list+=Candidate(x,y,r,score) else {val low=list.indices.minByOrNull{list[it].score}!!;if(list[low].score<score)list[low]=Candidate(x,y,r,score)}
                }
            }
        }
        fun score(k:Int,x:Int,y:Int,r:Int):Float{
            if(x-r<0||x+r>=w||y-r<0||y+r>=h)return -1f
            var sum=0f;var sq=0f;var dot=0f;val t=fine[k]
            for(i in 0 until 144){val dx=((i%12+.5f)*2*r/12-r).roundToInt();val dy=((i/12+.5f)*2*r/12-r).roundToInt();val v=l[(y+dy)*w+x+dx];sum+=v;sq+=v*v;dot+=v*t[i]}
            val e=sq-sum*sum/144;return if(e<144*16f)-1f else dot/sqrt(e)
        }
        return best.mapIndexedNotNull{k,list->
            val refined=list.map{c->var b=Candidate(c.x,c.y,c.r,-1f);for(r in max(4,c.r-2)..c.r+2)for(y in c.y-2..c.y+2)for(x in c.x-2..c.x+2){val s=score(k,x,y,r);if(s>b.score)b=Candidate(x,y,r,s)};b}.sortedByDescending{it.score}
            val top=refined.firstOrNull()?:return@mapIndexedNotNull null
            val other=refined.firstOrNull{abs(it.x-top.x)>top.r||abs(it.y-top.y)>top.r}
            if(top.score<(if(k==0).78f else .80f)||other!=null&&top.score-other.score<.045f)return@mapIndexedNotNull null
            IconMatch(HudIcon.values()[k],top.x.toFloat()/w,top.y.toFloat()/h,top.r.toFloat()/w,top.r.toFloat()/h,top.score)
        }
    }
}
class IconTracker {
    private var previous=emptyMap<HudIcon,IconMatch>()
    private var last=-1L
    fun clear(){previous=emptyMap();last=-1}
    fun update(frame:Long,icons:List<IconMatch>):Map<HudIcon,IconMatch>{
        if(frame<=last)return emptyMap()
        if(last>=0&&frame-last>4_000_000_000L)clear()
        val unique=icons.groupBy{it.kind}.filterValues{it.size==1}.mapValues{it.value.single()}
        val good=unique.filterValues{a->previous[a.kind]?.let{b->abs(a.x-b.x)<.018f&&abs(a.y-b.y)<.025f&&abs(a.radiusY-b.radiusY)<.025f}==true}
        previous=unique;last=frame;return good
    }
}
data class HudGesture(val x:Float,val y:Float,val endX:Float,val endY:Float,val durationMs:Long)
object HudActionPolicy {
    private val sequence=listOf(HudIcon.MOVE,HudIcon.SPRINT,HudIcon.MOVE,HudIcon.JUMP,HudIcon.MOVE,HudIcon.CROUCH,HudIcon.MOVE,HudIcon.CROUCH,HudIcon.PRONE,HudIcon.PRONE)
    fun next(icons:Set<HudIcon>,issued:Int,health:Float?,medkits:Int?):HudIcon? {
        if(HudIcon.HEAL in icons&&health!=null&&health<.45f&&(medkits?:0)>0)return HudIcon.HEAL
        return (0 until sequence.size).map{sequence[(issued+it)%sequence.size]}.firstOrNull{it in icons}
    }
    fun gesture(icon:IconMatch,issued:Int):HudGesture?{
        val (dx,dy)=if(icon.kind==HudIcon.MOVE)when(issued%4){0->0f to -1f;1->-1f to 0f;2->1f to 0f;else->0f to 1f} else 0f to 0f
        val x=icon.x+dx*icon.radiusX*1.5f;val y=icon.y+dy*icon.radiusY*1.5f
        if(listOf(icon.x,icon.y,x,y).any{!it.isFinite()||it !in 0f..1f})return null
        return HudGesture(icon.x,icon.y,x,y,if(icon.kind==HudIcon.MOVE)450L else 80L)
    }
}
