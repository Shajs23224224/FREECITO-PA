import com.omega.core.learning.*
import com.omega.core.executive.*
import java.nio.file.Files
import java.io.File
fun main(){val dir=Files.createTempDirectory("omega-pause-test").toFile();try{
 val p=ContinuousPolicy("unit:test",File(dir,"policy"));val x=List(8){.2f};val e=Experience("a","unit:test",Family.COLLECT,x,1f)
 p.confirmed(e);val before=p.weights();check(p.health().updates==1L&&p.adjustment(Family.COLLECT,x)>0)
 p.setSuspended(true);p.confirmed(e.copy(commandId="b"));check(p.weights().contentEquals(before)&&p.adjustment(Family.COLLECT,x)==0f&&p.health().updates==1L)
 p.setSuspended(false);p.confirmed(e.copy(commandId="c"));check(p.health().updates==2L&&!p.weights().contentEquals(before))
 println("PASS: policy suspension freezes updates and learned priority; recovery preserves weights")
}finally{dir.deleteRecursively()}}
