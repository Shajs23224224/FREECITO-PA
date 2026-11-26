package com.omega.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import com.omega.ai.game.GameBinding
import com.omega.core.release.TouchGate
import com.omega.core.release.TouchRequest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/** Internal transport. A delivery callback is not a verified game outcome. */
internal object TouchBridge {
    private val service=AtomicReference<OmegaTouchService?>(null)
    private val armed=AtomicBoolean(false)
    private val generation=AtomicLong(0)
    fun attach(s:OmegaTouchService){service.set(s)}
    fun detach(s:OmegaTouchService){if(service.compareAndSet(s,null))disarm()}
    fun arm(){if(GameBinding.accessibilityTarget()!=null && service.get()!=null)armed.compareAndSet(false,true)}
    fun disarm(){armed.set(false);generation.incrementAndGet()}
    fun token()=generation.get()
    fun allowed(token:Long)=armed.get()&&generation.get()==token
    /** Returns only whether work was accepted for asynchronous validation. */
    fun submit(request:TouchRequest,onDelivery:(Boolean)->Unit):Boolean {
        if(!armed.get()||GameBinding.accessibilityTarget()==null)return false
        return service.get()?.submit(request,token(),onDelivery)?:false
    }
}
class OmegaTouchService:AccessibilityService(){
    private val main=Handler(Looper.getMainLooper())
    private val busy=AtomicBoolean(false)
    override fun onServiceConnected(){
        super.onServiceConnected()
        val info=serviceInfo
        info.packageNames=arrayOf(GameBinding.accessibilityTarget()?:"com.omega.ai.disabled")
        serviceInfo=info
        TouchBridge.attach(this)
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?){ /* No event content retained. */ }
    override fun onInterrupt(){TouchBridge.disarm()}
    override fun onUnbind(intent:Intent?):Boolean {TouchBridge.detach(this);return super.onUnbind(intent)}
    override fun onDestroy(){TouchBridge.detach(this);super.onDestroy()}
    internal fun submit(request:TouchRequest,token:Long,onDelivery:(Boolean)->Unit):Boolean{
        if(!busy.compareAndSet(false,true))return false
        val finished=AtomicBoolean(false)
        fun finish(ok:Boolean){if(finished.compareAndSet(false,true)){busy.set(false);runCatching{onDelivery(ok)}}}
        val posted=main.post{
            try{
                val target=GameBinding.accessibilityTarget()
                val root=rootInActiveWindow
                val foreground=root?.packageName?.toString()
                val bounds=Rect()
                root?.getBoundsInScreen(bounds)
                if(!TouchGate.allows(TouchBridge.allowed(token),target,foreground,request,SystemClock.elapsedRealtime())||bounds.width()<2||bounds.height()<2){finish(false);return@post}
                val path=Path().apply{
                    moveTo(bounds.left+request.x*(bounds.width()-1),bounds.top+request.y*(bounds.height()-1))
                    lineTo(bounds.left+request.endX*(bounds.width()-1),bounds.top+request.endY*(bounds.height()-1))
                }
                val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,request.durationMs)).build()
                if(!TouchBridge.allowed(token)||GameBinding.accessibilityTarget()!=target){finish(false);return@post}
                if(!dispatchGesture(gesture,object:GestureResultCallback(){
                    override fun onCompleted(gestureDescription:GestureDescription){finish(TouchBridge.allowed(token))}
                    override fun onCancelled(gestureDescription:GestureDescription){finish(false)}
                },main)){finish(false);return@post}
                main.postDelayed({if(!finished.get()){TouchBridge.disarm();finish(false)}},request.durationMs+1000)
            }catch(e:Exception){finish(false)}
        }
        if(!posted)busy.set(false)
        return posted
    }
}
