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
        // Exceptions from SyncRunnable otherwise escape on Android's main thread.
        fun onUi(action:()->Unit){
            val error=AtomicReference<Throwable?>(null)
            runOnMainSync { try{action()}catch(t:Throwable){error.set(t)} }
            error.get()?.let{throw it}
        }
        fun waitForUi(description:String,predicate:()->Boolean){
            val limit=SystemClock.elapsedRealtime()+10000L
            while(SystemClock.elapsedRealtime()<limit){
                var ready=false
                onUi{ready=predicate()}
                if(ready)return
                SystemClock.sleep(100)
            }
            error("Timed out: "+description)
        }
        var passed=false
        try{
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            check(!activity.isFinishing){"MainActivity finishing"}
            report("PASS: OMEGA Activity installed and launched")
            onUi {
                check(RuntimeSession.start()){"Native core start"}
                check(RuntimeSession.status().active){"Native core inactive"}
                check(!RuntimeSession.beginCapture()){"Unexpected capture without authorized adapter"}
                RuntimeSession.stop("emulator_smoke")
                check(!RuntimeSession.status().active){"Native core did not stop"}
            }
            report("PASS: JNI/core start and stop; no unconfigured capture")
            val bridge=Class.forName("com.omega.ai.accessibility.TouchBridge")
            val ref=bridge.getDeclaredField("service").apply{isAccessible=true}.get(null) as AtomicReference<*>
            report("ACCESSIBILITY_READY_FOR_BIND")
            val deadline=SystemClock.elapsedRealtime()+60000
            while(ref.get()==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(250)
            val connected=ref.get() as? OmegaTouchService?:error("Accessibility service not connected")
            service=connected
            var panel:LinearLayout?=null
            onUi{
                // Exercise only overlay construction in our own activity; no game scope is supplied.
                method(connected,"ensurePanel")
                panel=field(connected,"panel") as? LinearLayout?:error("Overlay unavailable")
            }
            // WindowManager.addView schedules attachment on a later UI traversal.
            waitForUi("overlay attachment"){panel?.isAttachedToWindow==true && (panel?.width?:0)>0 && (panel?.height?:0)>0}
            onUi{
                val start=field(connected,"startButton") as Button
                val pause=field(connected,"pauseButton") as Button
                check(start.text.toString()=="Iniciar"&&pause.text.toString()=="Pausar")
                start.performClick()
                check(!RuntimeSession.status().active){"Start escaped game scope"}
                pause.performClick()
                check(!RuntimeSession.status().active)
                method(connected,"removePanel")
            }
            waitForUi("overlay detachment"){panel?.isAttachedToWindow==false}
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
            // Repeated frames at the real capture cadence, not merely a cold-start check.
            v.configureLoad(1,true,false,true,true)
            val startRun=SystemClock.elapsedRealtime()
            var nextOffer=startRun
            var lastDetector=s.detectorCompleted
            var lastText=s.ocrCompleted
            var lastHud=0L
            val detectorMs=mutableListOf<Long>()
            val textMs=mutableListOf<Long>()
            val hudMs=mutableListOf<Long>()
            var probes=0
            var simultaneous=0
            var offered=0
            var staleOcr=0
            var staleHud=0
            val initialHits=s.ocrCacheHits;val initialInferences=s.ocrInferenceCount
            val duration=45000L
            while(SystemClock.elapsedRealtime()-startRun<duration){
                val ms=SystemClock.elapsedRealtime()
                if(ms>=nextOffer){
                    rgba.position(0)
                    v.offer(rgba,w,h,w*4,4,SystemClock.elapsedRealtimeNanos())
                    offered++
                    nextOffer=ms+1000L
                }
                val sample=v.snapshot()
                val now=SystemClock.elapsedRealtimeNanos()
                val tf=sample.text?.let{now-it.receivedNs in 0..2_500_000_000L}==true
                val hf=sample.hud?.let{now-it.receivedNs in 0..1_500_000_000L}==true
                probes++
                if(tf&&hf)simultaneous++
                if(sample.detectorCompleted>lastDetector){
                    lastDetector=sample.detectorCompleted
                    check(sample.detectorBackend=="YOLOX-Nano"&&sample.detectorStatus=="inference_ok"){"Detector backend changed: "+sample.detectorBackend}
                    sample.detectorMs?.let{detectorMs+=it}
                    report("CYCLE detector count="+lastDetector+" ms="+sample.detectorMs+" fresh="+(sample.tracks!=null))
                }
                if(sample.ocrCompleted>lastText){
                    lastText=sample.ocrCompleted
                    check(sample.ocrStatus=="inference_ok"&&(sample.ocrLastCount?:0)>0){"OCR cycle error"}
                    sample.ocrMs?.let{textMs+=it}
                    if(!tf)staleOcr++
                    report("CYCLE OCR count="+lastText+" ms="+sample.ocrMs+" fresh="+tf)
                }
                if(sample.hudCompleted>lastHud){
                    lastHud=sample.hudCompleted
                    check(sample.hudStatus=="inference_ok"){"HUD cycle error"}
                    sample.hudMs?.let{hudMs+=it}
                    if(!hf)staleHud++
                    report("CYCLE HUD count="+lastHud+" ms="+sample.hudMs+" fresh="+hf)
                }
                SystemClock.sleep(100)
            }
            fun summary(xs:List<Long>):String {
                if(xs.isEmpty())return "n=0"
                val a=xs.sorted()
                return "n="+a.size+" min="+a.first()+" median="+a[a.size/2]+" max="+a.last()
            }
            report("PERF YOLOX "+summary(detectorMs))
            report("PERF OCR "+summary(textMs)+" staleAtFirstRead="+staleOcr)
            report("PERF HUD "+summary(hudMs)+" staleAtFirstRead="+staleHud)
            report("PERF framesOffered="+offered+" simultaneousFresh="+simultaneous+"/"+probes+" (timing only; no game semantics)")
            check(detectorMs.size>=2&&textMs.size>=2&&hudMs.size>=2){"Insufficient completed repeated cycles"}
            report("PASS: repeated Android vision pipeline without backend failure")
            val staticEnd=v.snapshot()
            report("PERF STATIC cacheHits="+(staticEnd.ocrCacheHits-initialHits)+" modelCalls="+(staticEnd.ocrInferenceCount-initialInferences)+" lastModelMs="+staticEnd.ocrInferenceMs)
            // Separate changing-content test, with no model reuse.
            v.configureLoad(1,false,false,false,false)
            SystemClock.sleep(8000)
            v.configureLoad(1,true,false,false,false)
            val dynamicTimes=mutableListOf<Long>()
            val dynamicStart=v.snapshot()
            for(code in listOf("ALPHA","BRAVO","CHARLIE")){
                val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                Canvas(b).apply{drawColor(Color.WHITE);drawText(code,50f,130f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=52f})}
                val px=IntArray(w*h);b.getPixels(px,0,w,0,0,w,h);b.recycle()
                val buf=ByteBuffer.allocateDirect(w*h*4)
                for(c in px){buf.put((c shr 16).toByte());buf.put((c shr 8).toByte());buf.put(c.toByte());buf.put((c ushr 24).toByte())};buf.flip()
                val before=v.snapshot().ocrCompleted
                val submitted=SystemClock.elapsedRealtimeNanos()
                v.offer(buf,w,h,w*4,4,submitted)
                val limit=SystemClock.elapsedRealtime()+30000
                while(v.snapshot().ocrCompleted==before&&SystemClock.elapsedRealtime()<limit)SystemClock.sleep(100)
                val after=v.snapshot()
                check(after.ocrCompleted==before+1&&after.ocrStatus=="inference_ok"){"Missing OCR completion for "+code}
                after.ocrInferenceMs?.let{dynamicTimes+=it}
                val age=(SystemClock.elapsedRealtimeNanos()-submitted)/1_000_000L
                val found=after.text?.takeIf{it.frameNs==submitted}?.value?.any{it.text.contains(code,true)}
                if(found!=null)check(found){"Unexpected text for "+code}
                report("PERF DYNAMIC label="+code+" ms="+after.ocrInferenceMs+" ageMs="+age+" contentMatch="+found+" fresh2500="+(found==true&&age<=2500)+" inputPixels="+after.ocrInputPixels+"/"+after.ocrSourcePixels)
                SystemClock.sleep(1200)
            }
            check(v.snapshot().ocrCacheHits==dynamicStart.ocrCacheHits&&v.snapshot().ocrInferenceCount-dynamicStart.ocrInferenceCount==3L){"Unexpected cache or inference count for changing content"}
            report("PERF DYNAMIC model-only "+summary(dynamicTimes)+" cacheHits=0")
            // Verify resource policy with controlled inputs, not by overheating the emulator.
            val clean=com.omega.core.meta.Health(.3,false,0,30.0,80,false,false,25.0)
            val perf=com.omega.core.meta.Performance(0,null,0.0)
            fun thermal(level:Int):com.omega.core.meta.Budget {
                val health=clean.copy(thermal=level)
                return com.omega.core.meta.MetaGovernor.trainingBudget(com.omega.core.meta.MetaGovernor().update(health,perf),health)
            }
            check(thermal(1).commands&&thermal(2).commands)
            check(thermal(3).load==com.omega.core.meta.Load.ECONOMY&&thermal(3).commands&&thermal(3).ocr&&!thermal(3).learning&&!thermal(3).depth)
            check(!thermal(4).commands)
            check(thermal(4).load==com.omega.core.meta.Load.STOP)
            report("PASS: controlled thermal policy; level 3 economy permits bounded commands and OCR, critical thermal protects device")
            passed=true
        }catch(t:Throwable){
            android.util.Log.e("OMEGA_EMULATOR","failure",t)
            results.putString("stream","\nOMEGA_EMULATOR_FAIL: "+t.stackTraceToString()+"\n")
        }finally{
            val cleanup=runCatching{
                vision?.close()
                val a=activity;val srv=service
                onUi{srv?.let{method(it,"removePanel")};RuntimeSession.stop("emulator_cleanup");a?.finish()}
            }
            cleanup.exceptionOrNull()?.let{t->
                passed=false
                android.util.Log.e("OMEGA_EMULATOR","cleanup failure",t)
                results.putString("stream",(results.getString("stream")?:"")+"\nOMEGA_EMULATOR_FAIL: cleanup "+t.stackTraceToString()+"\n")
            }
            if(passed){
                report("OMEGA_EMULATOR_PASS")
                results.putString("stream","\nOMEGA_EMULATOR_PASS\n")
            }
            finish(if(passed)Activity.RESULT_OK else Activity.RESULT_CANCELED,results)
        }
    }
}
