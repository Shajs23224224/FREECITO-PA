package com.omega.ai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.omega.ai.accessibility.OmegaTouchService
import com.omega.vision.VisionEngine
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/** Real Android runtime smoke test, with no game or third-party input target. */
class OmegaEmulatorTest : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val results=Bundle()
        var activity:Activity?=null
        var vision:VisionEngine?=null
        var service:OmegaTouchService?=null
        fun report(s:String){android.util.Log.i("OMEGA_EMULATOR",s);sendStatus(0,Bundle().apply{putString("stream","\n"+s+"\n")})}
        fun field(obj:Any,name:String):Any?=obj.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(obj)
        fun method(obj:Any,name:String){obj.javaClass.getDeclaredMethod(name).apply{isAccessible=true}.invoke(obj)}
        try{
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            check(!activity.isFinishing){"MainActivity finishing"}
            report("PASS: OMEGA Activity installed and launched")
            runOnMainSync {
                check(RuntimeSession.start()){"Native core start"}
                check(RuntimeSession.status().active){"Native core inactive"}
                check(!RuntimeSession.beginCapture()){"Unexpected capture without authorized adapter"}
                RuntimeSession.stop("emulator_smoke")
                check(!RuntimeSession.status().active){"Native core did not stop"}
            }
            report("PASS: JNI/core start and stop; no unconfigured capture")
            val bridge=Class.forName("com.omega.ai.accessibility.TouchBridge")
            val ref=bridge.getDeclaredField("service").apply{isAccessible=true}.get(null) as AtomicReference<*>
            val deadline=SystemClock.elapsedRealtime()+15000
            while(ref.get()==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(250)
            val connected=ref.get() as? OmegaTouchService?:error("Accessibility service not connected")
            service=connected
            runOnMainSync {
                // Exercise only overlay construction in our own activity; no game scope is supplied.
                method(connected,"ensurePanel")
                val panel=field(connected,"panel") as? LinearLayout?:error("Overlay unavailable")
                check(panel.isAttachedToWindow){"Overlay not attached"}
                val start=field(connected,"startButton") as Button
                val pause=field(connected,"pauseButton") as Button
                check(start.text.toString()=="Iniciar"&&pause.text.toString()=="Pausar")
                start.performClick()
                check(!RuntimeSession.status().active){"Start escaped game scope"}
                pause.performClick()
                check(!RuntimeSession.status().active)
                method(connected,"removePanel")
                check(!panel.isAttachedToWindow){"Overlay not removed"}
            }
            report("PASS: accessibility connected; floating window attached, labels present, removed; start rejected outside permitted scope")
            val w=640;val h=320
            val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply{drawColor(Color.WHITE);drawText("OMEGA TEST",50f,130f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=52f})}
            val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,0,0,w,h);bitmap.recycle()
            val rgba=ByteBuffer.allocateDirect(w*h*4)
            for(c in pixels){rgba.put((c shr 16).toByte());rgba.put((c shr 8).toByte());rgba.put(c.toByte());rgba.put((c ushr 24).toByte())}
            rgba.flip()
            val v=VisionEngine(targetContext);vision=v
            v.configureLoad(1,true,false,true,false)
            v.offer(rgba,w,h,w*4,4,SystemClock.elapsedRealtimeNanos())
            val end=SystemClock.elapsedRealtime()+45000
            while(SystemClock.elapsedRealtime()<end){
                val s=v.snapshot()
                if(s.detectorCompleted>0&&s.ocrCompleted>0)break
                SystemClock.sleep(100)
            }
            val s=v.snapshot()
            check(s.detectorBackend=="YOLOX-Nano"&&s.detectorStatus=="inference_ok"&&s.detectorCompleted>0){"YOLOX backend="+s.detectorBackend+" status="+s.detectorStatus}
            report("PASS: real YOLOX Android ONNX inference, ms="+s.detectorMs+" detections="+s.detectorLastCount)
            check(s.ocrStatus=="inference_ok"&&s.ocrCompleted>0&&(s.ocrLastCount?:0)>0){"OCR status="+s.ocrStatus}
            report("PASS: ML Kit Android OCR, ms="+s.ocrMs+" lines="+s.ocrLastCount)
            report("OMEGA_EMULATOR_PASS")
            results.putString("stream","\nOMEGA_EMULATOR_PASS\n")
            finish(Activity.RESULT_OK,results)
        }catch(t:Throwable){
            android.util.Log.e("OMEGA_EMULATOR","failure",t)
            results.putString("stream","\nOMEGA_EMULATOR_FAIL: "+t.stackTraceToString()+"\n")
            finish(Activity.RESULT_CANCELED,results)
        }finally{
            vision?.close()
            val a=activity;val srv=service
            runCatching{runOnMainSync{srv?.let{method(it,"removePanel")};RuntimeSession.stop("emulator_cleanup");a?.finish()}}
        }
    }
}
