import com.omega.vision.*
import kotlin.math.*
fun main(){
    val w=320;val h=180
    fun screen(cx:Int,cy:Int,r:Int,dual:Boolean=true)=IntArray(w*h){i->
        val d=hypot((i%w-cx).toDouble(),(i/w-cy).toDouble())
        if(abs(d-r)<1.7||dual&&abs(d-r*.5)<1.7)0xffffffff.toInt() else 0xff222222.toInt()
    }
    val a=AutoHud.scan(screen(79,117,20),w,h)
    check(a.circles.any{abs(it.x-79f/w)<.035f&&abs(it.y-117f/h)<.05f}){"no first ring: "+a.circles}
    val b=AutoHud.scan(screen(239,57,20),w,h)
    check(b.circles.any{abs(it.x-239f/w)<.035f&&abs(it.y-57f/h)<.05f}){"no moved ring: "+b.circles}
    check(AutoHud.scan(IntArray(w*h){0xff222222.toInt()},w,h).circles.isEmpty())
    check(AutoHud.scan(screen(79,117,20,false),w,h).circles.isEmpty())
    val t=HudTracker()
    val c=HudCircle(.2f,.6f,.1f,.06f,.94f);val one=HudCandidates(w,h,listOf(c))
    check(t.update(1,one)==null)
    check(t.update(1,one)==null)
    check(t.update(2,one)==c)
    val ambiguous=HudCandidates(w,h,listOf(c,c.copy(x=.8f)))
    check(t.update(3,ambiguous)==null)
    val moved=HudCandidates(w,h,listOf(c.copy(x=.8f)))
    check(t.update(4,moved)==null)
    check(t.update(5,moved)?.x==.8f)
    check(t.update(6,ambiguous,.8f to .6f)?.x==.8f)
    println("PASS: synthetic translated rings, flat and single ring rejection, stable independent frames, ambiguous candidates, semantic disambiguation")
}
