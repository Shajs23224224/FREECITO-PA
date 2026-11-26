import com.omega.core.*
import java.nio.ByteBuffer
fun main() {
    check(Architecture.total==858 && Architecture.hiddenUnits==28114944L && Architecture.denseHiddenWeights==53980692480L)
    val e=NativeEngine()
    check(e.start()); check(!e.start()); check(e.observe())
    check(!e.submit(ByteBuffer.allocate(16),2,2,8,4,1))
    val b=ByteBuffer.allocateDirect(20); b.position(4); repeat(16){b.put(255.toByte())}; b.position(4)
    check(e.submit(b,2,2,8,4,1)); check(!e.submit(b,2,2,8,4,1))
    repeat(100){if(e.stats().processed==0L)Thread.sleep(2)}
    check(e.stats().processed==1L && e.stats().checksum==1020L)
    e.stop(); check(e.stats().state==3L); e.close(); e.close(); check(!e.start())
    println("PASS: Kotlin/JNI real, direct buffer slice, stats, lifecycle, architecture arithmetic")
}
