import com.omega.vision.*
fun main(){
    val w=640;val h=288
    check(IconHud.scan(IntArray(w*h){0xff777777.toInt()},w,h).isEmpty())
    val icon=HudIcon.values().mapIndexed{i,k->IconMatch(k,.15f+i*.12f,.7f,.02f,.045f,.94f)}
    val tracker=IconTracker()
    check(tracker.update(1,icon).isEmpty())
    check(tracker.update(2,icon).size==6)
    val duplicate=icon+icon.first().copy(x=.8f)
    check(HudIcon.MOVE !in tracker.update(3,duplicate))
    check(tracker.update(4,icon)[HudIcon.MOVE]==null)
    check(tracker.update(5,icon).size==6)
    val available=icon.map{it.kind}.toSet()
    val seq=(0..9).map{HudActionPolicy.next(available,it,1f,0)}
    check(listOf(HudIcon.MOVE,HudIcon.SPRINT,HudIcon.JUMP,HudIcon.CROUCH,HudIcon.PRONE).all{it in seq})
    check(HudActionPolicy.next(available,0,.3f,2)==HudIcon.HEAL)
    check(HudActionPolicy.next(available,0,.3f,0)!=HudIcon.HEAL)
    check(HudActionPolicy.next(available,0,null,2)!=HudIcon.HEAL)
    check(HudActionPolicy.next(available,0,1f,2)!=HudIcon.HEAL)
    check(HudActionPolicy.next(setOf(HudIcon.HEAL),0,1f,0)==null)
    for(i in icon){val g=HudActionPolicy.gesture(i,0)!!;check(g.x==i.x&&g.y==i.y);if(i.kind==HudIcon.MOVE)check(g.endY<g.y&&g.durationMs==450L)else check(g.x==g.endX&&g.y==g.endY&&g.durationMs==80L)}
    check(HudActionPolicy.gesture(icon.first().copy(y=.01f),0)==null)
    println("PASS: six action mappings, two-frame stability, ambiguity rejection, bounded gestures, healing prerequisites, uniform image rejection")
}
