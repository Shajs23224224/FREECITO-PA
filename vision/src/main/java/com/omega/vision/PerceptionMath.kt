package com.omega.vision

import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } && right > left && bottom > top) }
    fun iou(o: Box): Float {
        val inter = max(0f,min(right,o.right)-max(left,o.left))*max(0f,min(bottom,o.bottom)-max(top,o.top))
        return inter / ((right-left)*(bottom-top)+(o.right-o.left)*(o.bottom-o.top)-inter)
    }
}
data class Detection(val label: String, val score: Float, val box: Box) {
    init { require(label.isNotBlank() && score.isFinite() && score in 0f..1f) }
}
data class Track(val id: Long, val detection: Detection, val seenNs: Long)

/** Matches only currently observed detections; never returns extrapolated or hidden entities. */
class VisibleTracker(private val ttlNs: Long = 1_000_000_000L, private val threshold: Float = .35f) {
    private var last = -1L
    private var next = 1L
    private var previous = emptyList<Track>()
    fun update(now: Long, observations: List<Detection>): List<Track> {
        require(now >= 0 && now > last)
        last = now
        val eligible = previous.filter { now-it.seenNs <= ttlNs }.toMutableList()
        val result = observations.sortedByDescending { it.score }.take(32).map { d ->
            val match = eligible.filter { it.detection.label == d.label }.maxByOrNull { it.detection.box.iou(d.box) }
            if (match != null && match.detection.box.iou(d.box) >= threshold) {
                eligible.remove(match); Track(match.id,d,now)
            } else Track(next++,d,now)
        }
        previous = (result + eligible).take(64)
        return result
    }
}

object Pixels {
    fun rgbaToArgb(src: ByteBuffer, w: Int, h: Int, row: Int, pixel: Int): IntArray {
        require(w in 1..1024 && h in 1..1024 && pixel == 4 && row >= w*4)
        val b=src.slice(); require((h-1L)*row+w*4L <= b.remaining())
        return IntArray(w*h) { i ->
            val k=(i/w)*row+(i%w)*4
            fun c(j: Int) = b.get(k+j).toInt() and 255
            (c(3) shl 24) or (c(0) shl 16) or (c(1) shl 8) or c(2)
        }
    }
    fun depthInput(argb: IntArray): FloatArray {
        val mean=floatArrayOf(.485f,.456f,.406f); val std=floatArrayOf(.229f,.224f,.225f)
        return FloatArray(argb.size*3) { i ->
            val channel=i/argb.size; val v=(argb[i%argb.size] ushr (16-channel*8)) and 255
            (v/255f-mean[channel])/std[channel]
        }
    }
    /** Relative within one frame only; no metric distance or cross-frame comparison. */
    fun depthGrid(values: FloatArray, w: Int, h: Int): List<Float>? {
        require(w in 8..1024 && h in 8..1024 && values.size == w*h)
        if (values.any { !it.isFinite() }) return null
        val lo=values.minOrNull()!!; val hi=values.maxOrNull()!!
        val range=hi-lo; if (!range.isFinite() || range <= 1e-6f) return null
        return List(64) { i ->
            val x0=(i%8)*w/8; val x1=(i%8+1)*w/8; val y0=(i/8)*h/8; val y1=(i/8+1)*h/8
            var sum=0.0; var n=0
            for(y in y0 until y1) for(x in x0 until x1) { sum+=(values[y*w+x]-lo)/range; n++ }
            (sum/n).toFloat().coerceIn(0f,1f)
        }
    }
}
