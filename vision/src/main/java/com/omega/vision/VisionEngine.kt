package com.omega.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class TextLine(val text: String, val box: Box?)
data class TimedObservation<T>(val frameNs: Long, val receivedNs: Long, val value: T)
data class VisionSnapshot(
    val tracks: TimedObservation<List<Track>>?,
    val text: TimedObservation<List<TextLine>>?,
    val relativeDepth: TimedObservation<List<Float>>?,
    val detectorStatus: String, val ocrStatus: String, val depthStatus: String,
    val hud:TimedObservation<HudCandidates>?=null, val hudStatus:String="waiting",
    val hudCompleted:Long=0,val hudMs:Long?=null,val hudLastCount:Int?=null,val ocrCompleted:Long=0,val ocrMs:Long?=null,val ocrLastCount:Int?=null,
    val detectorBackend:String="not_loaded",val detectorCompleted:Long=0,val detectorMs:Long?=null,val detectorLastCount:Int?=null,
    val ocrCacheHits:Long=0,val ocrInferenceCount:Long=0,val ocrInferenceMs:Long?=null,
    val ocrInputPixels:Int?=null,val ocrSourcePixels:Int?=null
)

/**
 * Authorized frame consumer only. Three single-worker lanes, no frame backlog,
 * independent cadence, no game actions, no image/text persistence or network.
 * General-domain pretrained models are NOT a verified game-specific ontology.
 */
class VisionEngine(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val closed = AtomicBoolean(false)
    private class Lane<T>(val intervalNs: Long, val ttlNs: Long) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val busy = AtomicBoolean(false)
        val result = AtomicReference<TimedObservation<T>?>(null)
        val status = AtomicReference("waiting_for_authorized_frame")
        var completed=0L
        var durationMs:Long?=null
        var outputCount:Int?=null
        var last = -1L
        var generation=0L
        var enabled=true
        fun configure(value:Boolean){
            if(enabled==value)return
            enabled=value; generation++; result.set(null); last=-1L
            status.set(if(value) "waiting_for_authorized_frame" else "resource_paused")
        }
        fun reserve(now: Long,multiplier:Int): Boolean {
            if (!enabled || (last >= 0 && now-last < intervalNs*multiplier)) return false
            if (!busy.compareAndSet(false,true)) return false
            last=now; return true
        }
        fun current(now: Long): TimedObservation<T>? =
            result.get()?.takeIf { now >= it.receivedNs && now-it.receivedNs <= ttlNs }
    }
    private val fast = Lane<List<Track>>(1_500_000_000L,2_500_000_000L)
    private val text = Lane<List<TextLine>>(1_000_000_000L,4_000_000_000L)
    private val depth = Lane<List<Float>>(5_000_000_000L,15_000_000_000L)
    private val hud = Lane<HudCandidates>(450_000_000L,2_500_000_000L)
    private var lastFrame = -1L
    private var multiplier=1
    @Synchronized fun configureLoad(cadenceMultiplier:Int,ocrEnabled:Boolean,depthEnabled:Boolean,detectorEnabled:Boolean=true,hudEnabled:Boolean=true){
        require(cadenceMultiplier in 1..4)
        multiplier=cadenceMultiplier;text.configure(ocrEnabled);depth.configure(depthEnabled);fast.configure(detectorEnabled);hud.configure(hudEnabled)
    }
    // Each mutable backend and the tracker belong exclusively to its lane.
    private var detector: ObjectDetector? = null
    private var yolo:YoloDetector?=null
    private var yoloFailed=false
    @Volatile private var detectorBackend="not_loaded"
    private var recognizer: TextRecognizer? = null
    private var ocrBitmap:Bitmap?=null
    private val ocrCache=ExactFrameCache<List<TextLine>>(8_000_000_000L)
    private var ocrCacheGeneration=-1L
    private var ocrCacheHits=0L
    private var ocrInferenceCount=0L
    private var ocrInferenceMs:Long?=null
    private var ocrInputPixels:Int?=null
    private var ocrSourcePixels:Int?=null
    private var depthSession: OrtSession? = null
    private var tracker = VisibleTracker(ttlNs=4_000_000_000L)
    private var trackerW=0
    private var trackerH=0

    @Synchronized
    fun offer(buffer: ByteBuffer,w: Int,h: Int,row: Int,pixel: Int,frameNs: Long) {
        if (closed.get() || frameNs <= lastFrame || frameNs < 0) return
        lastFrame=frameNs
        val now=SystemClock.elapsedRealtimeNanos()
        val a=fast.reserve(now,multiplier); val b=text.reserve(now,multiplier); val c=depth.reserve(now,multiplier); val d=hud.reserve(now,multiplier)
        if (!a && !b && !c && !d) return
        val pixels=try { Pixels.rgbaToArgb(buffer,w,h,row,pixel) } catch(e: IllegalArgumentException) {
            if(a)fast.busy.set(false); if(b)text.busy.set(false); if(c)depth.busy.set(false); if(d)hud.busy.set(false)
            return
        }
        if(a) launch(fast,frameNs,now) { detect(pixels,w,h,frameNs) }
        if(b) { val gen=text.generation;launch(text,frameNs,now) { readText(pixels,w,h,frameNs,gen) } }
        if(c) launch(depth,frameNs,now) { estimate(pixels,w,h) }
        if(d) launch(hud,frameNs,now) { HudCandidates(w,h,emptyList(),IconHud.scan(pixels,w,h)) }
    }

    private fun <T> launch(lane: Lane<T>,frameNs: Long,received: Long, work: ()->T?) {
        val generation=lane.generation
        lane.executor.execute {
            try {
                if (!closed.get()) {
                    val started=SystemClock.elapsedRealtimeNanos()
                    val value=work()
                    val elapsed=(SystemClock.elapsedRealtimeNanos()-started)/1_000_000L
                    synchronized(this) {
                        if (!closed.get() && lane.enabled && generation==lane.generation) {
                            lane.completed++;lane.durationMs=elapsed
                            lane.outputCount=when(value){is HudCandidates->value.icons.size;is List<*>->value.size;else->null}
                            lane.result.set(value?.let { TimedObservation(frameNs,received,it) })
                            lane.status.set(if(value==null) "no_valid_output" else "inference_ok")
                        }
                    }
                }
            } catch(e: Exception) {
                synchronized(this) { if(!closed.get() && lane.enabled && generation==lane.generation) { lane.result.set(null); lane.status.set("unavailable:"+e.javaClass.simpleName) } }
            } catch(e: LinkageError) {
                synchronized(this) { if(!closed.get() && lane.enabled && generation==lane.generation) { lane.result.set(null); lane.status.set("backend_unavailable") } }
            } finally { lane.busy.set(false) }
        }
    }
    private fun bitmap(pixels: IntArray,w: Int,h: Int)=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
    private fun normalized(r: RectF,w: Int,h: Int): Box? {
        val l=(r.left.toFloat()/w).coerceIn(0f,1f); val t=(r.top.toFloat()/h).coerceIn(0f,1f)
        val rr=(r.right.toFloat()/w).coerceIn(0f,1f); val bb=(r.bottom.toFloat()/h).coerceIn(0f,1f)
        return if(rr>l && bb>t) Box(l,t,rr,bb) else null
    }
    private fun detect(pixels:IntArray,w:Int,h:Int,ts:Long):List<Track>{
        if(!yoloFailed){
            try{
                val model=yolo?:YoloDetector(app).also{yolo=it}
                val observations=model.detect(pixels,w,h)
                detectorBackend="YOLOX-Nano"
                if(w!=trackerW||h!=trackerH){tracker=VisibleTracker(ttlNs=4_000_000_000L);trackerW=w;trackerH=h}
                return tracker.update(ts,observations)
            }catch(e:Exception){runCatching{yolo?.close()};yolo=null;yoloFailed=true;tracker=VisibleTracker(ttlNs=4_000_000_000L);detectorBackend="EfficientDet fallback:"+e.javaClass.simpleName}
            catch(e:LinkageError){runCatching{yolo?.close()};yolo=null;yoloFailed=true;tracker=VisibleTracker(ttlNs=4_000_000_000L);detectorBackend="EfficientDet fallback:backend"}
        }
        return detectFallback(pixels,w,h,ts)
    }
    private fun detectFallback(pixels: IntArray,w: Int,h: Int,ts: Long): List<Track> {
        val model=detector ?: ObjectDetector.createFromOptions(app,
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/efficientdet.tflite").build())
                .setRunningMode(RunningMode.IMAGE).setScoreThreshold(.35f).setMaxResults(32).build()
        ).also { detector=it }
        val bmp=bitmap(pixels,w,h)
        val image=BitmapImageBuilder(bmp).build()
        try {
            val observations=model.detect(image).detections().mapNotNull { d ->
                val cat=d.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val label=cat.categoryName().ifBlank { "unknown" }
                val box=normalized(d.boundingBox(),w,h) ?: return@mapNotNull null
                if(cat.score().isFinite() && cat.score() in 0f..1f) Detection(label,cat.score(),box) else null
            }
            if(w!=trackerW || h!=trackerH) { tracker=VisibleTracker(ttlNs=4_000_000_000L); trackerW=w; trackerH=h }
            return tracker.update(ts,observations)
        } finally { image.close(); bmp.recycle() }
    }
    private fun readText(pixels:IntArray,w:Int,h:Int,frameNs:Long,generation:Long):List<TextLine>{
        if(ocrCacheGeneration!=generation){ocrCache.clear();ocrCacheGeneration=generation}
        // A new input must be exactly identical; no approximate matching.
        ocrCache.lookup(pixels,w,h,frameNs)?.let { cached ->
            synchronized(this){ocrCacheHits++}
            return cached
        }
        val client=recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer=it }
        val roi=UniformMarginCrop.find(pixels,w,h)
        val started=SystemClock.elapsedRealtimeNanos()
        fun recognize(r:OcrRegion):List<TextLine>{
            val bmp=ocrBitmap?.takeIf{it.width==r.width&&it.height==r.height&&!it.isRecycled}
                ?: Bitmap.createBitmap(r.width,r.height,Bitmap.Config.ARGB_8888).also { ocrBitmap?.recycle();ocrBitmap=it }
            bmp.setPixels(pixels,r.y*w+r.x,w,0,0,r.width,r.height)
            val result=Tasks.await(client.process(InputImage.fromBitmap(bmp,0)))
            return result.textBlocks.flatMap { it.lines }.take(100).map { l ->
                TextLine(l.text.take(256),l.boundingBox?.let { box ->
                    normalized(RectF((box.left+r.x).toFloat(),(box.top+r.y).toFloat(),(box.right+r.x).toFloat(),(box.bottom+r.y).toFloat()),w,h)
                })
            }
        }
        var used=roi
        var lines=recognize(roi)
        // If the smaller input yields no lines, preserve full-frame recognition.
        if(lines.isEmpty()&&(roi.width!=w||roi.height!=h)){
            used=OcrRegion(0,0,w,h);lines=recognize(used)
        }
        synchronized(this){
            ocrInferenceCount++
            ocrInferenceMs=(SystemClock.elapsedRealtimeNanos()-started)/1_000_000L
            ocrInputPixels=used.width*used.height
            ocrSourcePixels=w*h
        }
        ocrCache.store(pixels,w,h,frameNs,lines)
        return lines
    }
    private fun estimate(pixels: IntArray,w: Int,h: Int): List<Float>? {
        val env=OrtEnvironment.getEnvironment()
        val session=depthSession ?: OrtSession.SessionOptions().use { opt ->
            opt.setIntraOpNumThreads(1)
            opt.setInterOpNumThreads(1)
            val bytes=app.assets.open("models/depth.onnx").use { it.readBytes() }
            env.createSession(bytes,opt).also {
                if(!it.inputNames.contains("pixel_values") || !it.outputNames.contains("predicted_depth")) {
                    it.close(); error("Unsupported depth model contract")
                }
                depthSession=it
            }
        }
        // Fixed 518x518 input; aspect-ratio distortion is an experimental approximation.
        val src=bitmap(pixels,w,h)
        val scaled=Bitmap.createScaledBitmap(src,518,518,true)
        val rgb=IntArray(518*518)
        try { scaled.getPixels(rgb,0,518,0,0,518,518) }
        finally { if(scaled!==src)scaled.recycle(); src.recycle() }
        val data=Pixels.depthInput(rgb)
        OnnxTensor.createTensor(env,FloatBuffer.wrap(data),longArrayOf(1,3,518,518)).use { tensor ->
            session.run(mapOf("pixel_values" to tensor),setOf("predicted_depth")).use { result ->
                val out=result[0] as? OnnxTensor ?: return null
                val shape=out.info.shape
                if(shape.size!=3 || shape[0]!=1L || shape[1] !in 8L..1024L || shape[2] !in 8L..1024L) return null
                val oh=shape[1].toInt(); val ow=shape[2].toInt()
                val buffer=out.floatBuffer
                if(buffer.remaining()!=oh*ow)return null
                val values=FloatArray(oh*ow); buffer.get(values)
                return Pixels.depthGrid(values,ow,oh)
            }
        }
    }
    @Synchronized fun snapshot(): VisionSnapshot {
        val now=SystemClock.elapsedRealtimeNanos()
        return VisionSnapshot(fast.current(now),text.current(now),depth.current(now),
            fast.status.get(),text.status.get(),depth.status.get(),hud.current(now),hud.status.get(),hud.completed,hud.durationMs,hud.outputCount,text.completed,text.durationMs,text.outputCount,detectorBackend,fast.completed,fast.durationMs,fast.outputCount,ocrCacheHits,ocrInferenceCount,ocrInferenceMs,ocrInputPixels,ocrSourcePixels)
    }
    @Synchronized override fun close() {
        if(!closed.compareAndSet(false,true))return
        fast.result.set(null); text.result.set(null); depth.result.set(null); hud.result.set(null)
        fast.executor.execute { yolo?.close();yolo=null;detector?.close(); detector=null }
        text.executor.execute { recognizer?.close(); recognizer=null;ocrBitmap?.recycle();ocrBitmap=null;ocrCache.clear() }
        depth.executor.execute { depthSession?.close(); depthSession=null }
        fast.executor.shutdown(); text.executor.shutdown(); depth.executor.shutdown(); hud.executor.shutdown()
    }
}
