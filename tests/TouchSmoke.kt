import com.omega.core.release.*
fun main(){
 val r=TouchRequest(0f,.5f,1f,.5f,200,100)
 check(TouchGate.allows(true,"test.private","test.private",r,600))
 check(!TouchGate.allows(true,"test.private","test.private",r,601))
 check(!TouchGate.allows(true,"test.private","other",r,200))
 check(!TouchGate.allows(false,"test.private","test.private",r,200))
 check(!TouchGate.allows(true,null,null,r,200))
 check(!TouchGate.allows(true,"test.private","test.private",r,99))
 check(runCatching{r.copy(x=Float.NaN)}.isFailure)
 check(runCatching{r.copy(durationMs=501)}.isFailure)
 println("PASS: touch request bounds, expiry, foreground scope and session gate")
}
