import com.omega.vision.*
import kotlin.math.abs
fun main(){
    val p=YoloMath.preprocess(intArrayOf(0xffff0000.toInt(),0xffff0000.toInt()),2,1)
    val n=416*416
    check(p.resizedW==416&&p.resizedH==208)
    check(p.data[0]==0f&&p.data[n]==0f&&p.data[2*n]==255f)
    check(p.data[208*416]==114f)
    val raw=FloatArray(3549*85)
    fun row(i:Int,x:Float,y:Float,cls:Int,score:Float){val o=i*85;raw[o]=x;raw[o+1]=y;raw[o+2]=1f;raw[o+3]=1f;raw[o+4]=.95f;raw[o+5+cls]=score}
    row(10*52+10,.5f,.5f,0,.9f)
    row(10*52+11,-.5f,.5f,0,.8f)
    row(10*52+12,-1.5f,.5f,24,.85f)
    row(45*52+10,.5f,.5f,0,.9f)
    val detections=YoloMath.decode(raw,p)
    check(detections.size==2&&detections.map{it.label}.toSet()==setOf("person","backpack"))
    val box=detections.first().box
    check(abs((box.left+box.right)/2-84f/416)<.001f&&abs((box.top+box.bottom)/2-84f/208)<.001f)
    check(YoloMath.decode(FloatArray(raw.size),p).isEmpty())
    check(runCatching{YoloMath.decode(FloatArray(1),p)}.isFailure)
    println("PASS: YOLOX BGR letterbox, raw grid decode, COCO labels, score multiplication, class-aware NMS, padding rejection")
}
