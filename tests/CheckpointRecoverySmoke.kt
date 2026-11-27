import com.omega.core.learning.*
import com.omega.core.executive.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.nio.ByteBuffer
fun main(){
 val dir=Files.createTempDirectory("omega-checkpoint").toFile()
 try {
  val f=File(dir,"policy"); val b=File(f.path+".previous"); val q=File(f.path+".corrupt")
  val p=ContinuousPolicy("synthetic:1",f)
  val e=Experience("a","synthetic:1",Family.INSPECT,List(8){.2f},.8f)
  check(p.health().checkpointStatus==CheckpointStatus.NEW)
  p.confirmed(e); val first=p.weights(); val bytes=f.readBytes(); check(!b.exists())
  p.confirmed(e.copy(commandId="b")); check(b.readBytes().contentEquals(bytes)); check(!first.contentEquals(p.weights()))
  val valid=f.readBytes(); f.appendBytes(byteArrayOf(7)); val invalid=f.readBytes()
  val recovered=ContinuousPolicy("synthetic:1",f)
  check(recovered.health().checkpointStatus==CheckpointStatus.RECOVERED && !recovered.health().disabled)
  check(recovered.weights().contentEquals(first) && q.readBytes().contentEquals(invalid))
  check(b.readBytes().contentEquals(bytes) && ContinuousPolicy("synthetic:1",f).weights().contentEquals(first))
  recovered.confirmed(e); check(recovered.weights().contentEquals(first))
  f.delete(); check(ContinuousPolicy("synthetic:1",f).health().checkpointStatus==CheckpointStatus.RECOVERED)
  val payload=valid.copyOfRange(0,valid.size-32); ByteBuffer.wrap(payload).putInt(0x4f4d3899)
  val future=payload+MessageDigest.getInstance("SHA-256").digest(payload); f.writeBytes(future)
  check(ContinuousPolicy("synthetic:1",f).health().checkpointStatus==CheckpointStatus.INCOMPATIBLE);check(f.readBytes().contentEquals(future))
  f.writeBytes(valid);check(ContinuousPolicy("other",f).health().checkpointStatus==CheckpointStatus.INCOMPATIBLE);check(f.readBytes().contentEquals(valid))
  f.writeText("invalid"); b.writeText("invalid")
  val broken=ContinuousPolicy("synthetic:1",f);check(broken.health().disabled&&broken.health().checkpointStatus==CheckpointStatus.INVALID)
  check(f.readText()=="invalid" && b.readText()=="invalid")
  val lone=File(dir,"lone");lone.writeText("invalid");check(ContinuousPolicy("synthetic:1",lone).health().disabled)
  println("PASS: previous generation, checksum recovery, quarantine, missing primary, deduplication retained in backup, future/scope mismatch preserved, no valid copy fail-closed")
 }finally{dir.deleteRecursively()}
}
