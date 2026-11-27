package com.omega.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import java.nio.ByteBuffer
import com.omega.ai.RuntimeSession
import com.omega.ai.SessionDiagnostics
import java.util.concurrent.Executors
import com.omega.ai.game.FreeFireLauncher
import kotlin.math.max
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
    fun connected()=service.get()!=null
    fun startObservation():Boolean=service.get()?.startObservation()?:false
    fun stopObservation(){service.get()?.stopObservation()}
    fun isArmed()=connected()&&armed.get()&&GameBinding.accessibilityTarget()!=null
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
        info.packageNames=arrayOf(FreeFireLauncher.PACKAGE)
        serviceInfo=info
        TouchBridge.attach(this);SessionDiagnostics.event("service","connected",true)
    }
    private val imageWorker=Executors.newSingleThreadExecutor()
    @Volatile private var observing=false
    private var seenGame=false
    private var startedAt=0L
    private var screenshotPending=false
    @Volatile private var epoch=0L
    private var missingSince=-1L
    private var captureErrors=0
    private var requestAt=0L
    private val poll=object:Runnable{override fun run(){
        if(!observing)return
        val adapter=GameBinding.training
        if(adapter==null||!adapter.live()||!RuntimeSession.status().active){finishObservation("session_closed");return}
        val root=rootInActiveWindow
        val target=root?.packageName?.toString()==FreeFireLauncher.PACKAGE
        val bounds=Rect();root?.getBoundsInScreen(bounds)
        val geometry=target&&adapter.matches(bounds.width(),bounds.height())
        adapter.foreground(geometry)
        if(!geometry){
            TouchBridge.disarm()
            val now=SystemClock.elapsedRealtime()
            if(missingSince<0)missingSince=now
            SessionDiagnostics.event("window",if(!target)"waiting_for_game_or_window" else "waiting_for_landscape")
            if(seenGame&&now-missingSince>3500||!seenGame&&now-startedAt>60_000L){finishObservation("foreground_or_geometry_timeout");return}
        }else{
            missingSince=-1;seenGame=true
            SessionDiagnostics.event("window","game_visible ${bounds.width()}x${bounds.height()}")
            if(!RuntimeSession.status().capturing&&!RuntimeSession.beginCapture()){finishObservation("capture_init_failed");return}
            if(!RuntimeSession.resourceHealthy()){finishObservation("resource_stop");return}
            if(screenshotPending&&SystemClock.elapsedRealtime()-requestAt>5000){finishObservation("screenshot_timeout");return}
            if(RuntimeSession.capturePaused())SessionDiagnostics.event("capture","cooldown_session_alive") else if(!screenshotPending)captureFrame(bounds)
        }
        main.postDelayed(this,RuntimeSession.captureDelayMs())
    }}
    internal fun startObservation():Boolean{
        if(Build.VERSION.SDK_INT<30||observing)return false
        observing=true;seenGame=false;missingSince=-1;captureErrors=0;startedAt=SystemClock.elapsedRealtime();epoch++;SessionDiagnostics.event("capture","poll_started",true);main.post(poll);return true
    }
    internal fun stopObservation(){
        observing=false;epoch++;screenshotPending=false;main.removeCallbacks(poll);TouchBridge.disarm()
    }
    private fun finishObservation(reason:String){SessionDiagnostics.event("service",reason,true);stopObservation();RuntimeSession.stop(reason)}
    @android.annotation.TargetApi(30)
    private fun captureFrame(bounds:Rect){
        if(Build.VERSION.SDK_INT<30)return
        screenshotPending=true;requestAt=SystemClock.elapsedRealtime()
        val generation=epoch
        val ts=SystemClock.elapsedRealtimeNanos()
        try{takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,object:TakeScreenshotCallback{
            override fun onSuccess(screenshot:ScreenshotResult){
                val hw=screenshot.hardwareBuffer
                if(!observing||epoch!=generation||rootInActiveWindow?.packageName?.toString()!=FreeFireLauncher.PACKAGE){hw.close();if(epoch==generation)screenshotPending=false;return}
                val current=Rect();rootInActiveWindow?.getBoundsInScreen(current)
                if(current!=bounds){hw.close();screenshotPending=false;SessionDiagnostics.event("capture","geometry_transition");return}
                try{imageWorker.execute{
                    var source:Bitmap?=null;var full:Bitmap?=null;var crop:Bitmap?=null;var small:Bitmap?=null
                    try{
                        if(!observing||epoch!=generation)return@execute
                        source=Bitmap.wrapHardwareBuffer(hw,screenshot.colorSpace)
                        full=source?.copy(Bitmap.Config.ARGB_8888,false)?:error("bitmap_unavailable")
                        if(bounds.left<0||bounds.top<0||bounds.right>full.width||bounds.bottom>full.height)error("bounds_outside_capture")
                        crop=Bitmap.createBitmap(full,bounds.left,bounds.top,bounds.width(),bounds.height())
                        val scale=minOf(1f,1024f/max(crop.width,crop.height))
                        val w=max(1,(crop.width*scale).toInt());val h=max(1,(crop.height*scale).toInt())
                        small=Bitmap.createScaledBitmap(crop,w,h,true)
                        val pixels=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h)
                        val sig=FloatArray(64){i->
                            val x=((i%8+1)*w/10).coerceIn(0,w-1);val y=((i/8+1)*h/10).coerceIn(0,h-1)
                            val c=pixels[y*w+x];(((c shr 16 and 255)+(c shr 8 and 255)+(c and 255))/765f)
                        }
                        val rgba=ByteBuffer.allocateDirect(w*h*4)
                        for(c in pixels){rgba.put((c shr 16).toByte());rgba.put((c shr 8).toByte());rgba.put(c.toByte());rgba.put((c ushr 24).toByte())}
                        rgba.flip()
                        if(observing&&epoch==generation&&rootInActiveWindow?.packageName?.toString()==FreeFireLauncher.PACKAGE){
                            val adapter=GameBinding.training?:return@execute
                            adapter.frame(ts,sig)
                            val accepted=RuntimeSession.submit(rgba,w,h,w*4,4,ts)
                            SessionDiagnostics.event("capture",if(accepted)"frame_accepted" else "frame_rejected")
                            main.post{if(epoch==generation)captureErrors=0}
                        }
                    }catch(e:Exception){SessionDiagnostics.event("capture","processing_error:"+e.javaClass.simpleName,true);main.post{if(epoch==generation)finishObservation("screenshot_processing_error")}}
                    finally{if(small!=null&&small!==crop&&small!==full)small.recycle();if(crop!=null&&crop!==full)crop.recycle();full?.recycle();source?.recycle();hw.close();main.post{if(epoch==generation)screenshotPending=false}}
                }}catch(e:Exception){hw.close();screenshotPending=false;finishObservation("image_worker_unavailable")}
            }
            override fun onFailure(errorCode:Int){if(epoch==generation){
                screenshotPending=false;captureErrors++
                SessionDiagnostics.event("capture","android_screenshot_error:$errorCode attempt:$captureErrors",true)
                if(captureErrors>=3)finishObservation("screenshot_unavailable:"+errorCode)
            }}
        })}catch(e:Exception){screenshotPending=false;finishObservation("screenshot_permission_error:"+e.javaClass.simpleName)}
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?){ /* No event content retained. */ }
    override fun onInterrupt(){finishObservation("accessibility_interrupted")}
    override fun onUnbind(intent:Intent?):Boolean {finishObservation("accessibility_unbound");TouchBridge.detach(this);return super.onUnbind(intent)}
    override fun onDestroy(){finishObservation("accessibility_destroyed");imageWorker.shutdown();TouchBridge.detach(this);super.onDestroy()}
    internal fun submit(request:TouchRequest,token:Long,onDelivery:(Boolean)->Unit):Boolean{
        if(!busy.compareAndSet(false,true)){SessionDiagnostics.event("gesture","busy");return false}
        val finished=AtomicBoolean(false)
        fun finish(ok:Boolean){if(finished.compareAndSet(false,true)){busy.set(false);SessionDiagnostics.event("gesture",if(ok)"delivered" else "rejected_or_cancelled",true);runCatching{onDelivery(ok)}}}
        val posted=main.post{
            try{
                val target=GameBinding.accessibilityTarget()
                if(target==null||!TouchBridge.allowed(token)){finish(false);return@post}
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
                SessionDiagnostics.event("gesture","dispatch_attempt",true)
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
