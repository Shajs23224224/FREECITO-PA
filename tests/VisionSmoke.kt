import com.omega.vision.*
import java.nio.ByteBuffer
import kotlin.math.abs
fun main() {
    val b=ByteBuffer.allocate(24); b.position(4); b.put(byteArrayOf(-1,0,0,-1,0,-1,0,-1,0,0,0,0,0,0,-1,-1,-1,-1,-1,-1)); b.position(4)
    val pixels=Pixels.rgbaToArgb(b,2,2,12,4)
    check(pixels.contentEquals(intArrayOf(0xffff0000.toInt(),0xff00ff00.toInt(),0xff0000ff.toInt(),-1)))
    check(b.position()==4)
    check(runCatching{Pixels.rgbaToArgb(ByteBuffer.allocate(3),2,2,8,4)}.isFailure)
    val t=VisibleTracker(ttlNs=100)
    val d=Detection("object",.8f,Box(.1f,.1f,.4f,.4f))
    val a=t.update(1,listOf(d)).single(); check(t.update(2,listOf(d)).single().id==a.id)
    check(t.update(3,emptyList()).isEmpty()); check(t.update(4,listOf(d)).single().id==a.id)
    check(t.update(105,listOf(d)).single().id!=a.id)
    check(runCatching { t.update(105,listOf(d)) }.isFailure)
    val rgb=Pixels.depthInput(intArrayOf(0xffff0000.toInt()))
    check(abs(rgb[0]-(1-.485f)/.229f)<1e-5 && abs(rgb[1]+.456f/.224f)<1e-5)
    val grid=Pixels.depthGrid(FloatArray(64){it.toFloat()},8,8)!!
    check(grid.first()==0f && grid.last()==1f && grid.size==64)
    check(Pixels.depthGrid(FloatArray(64),8,8)==null)
    check(Pixels.depthGrid(FloatArray(64){Float.NaN},8,8)==null)
    println("PASS: padded RGBA, bounds, tracking ID and expiry, no hidden tracks, monotonic time, normalization and relative depth grid")
}
