package com.omega.vision

/** Lane-confined, single-entry cache. No approximate matching or persisted frames.
 * Age is measured from the original model input, never extended on cache hits.
 */
internal class ExactFrameCache<T:Any>(private val maxAgeNs:Long) {
    init { require(maxAgeNs>0) }
    private data class Entry<T>(val pixels:IntArray,val w:Int,val h:Int,val frameNs:Long,val value:T)
    private var entry:Entry<T>?=null
    fun lookup(pixels:IntArray,w:Int,h:Int,frameNs:Long):T? {
        val e=entry?:return null
        if(frameNs<=e.frameNs||frameNs-e.frameNs>maxAgeNs||w!=e.w||h!=e.h||!pixels.contentEquals(e.pixels))return null
        return e.value
    }
    fun store(pixels:IntArray,w:Int,h:Int,frameNs:Long,value:T){
        require(w>0&&h>0&&w.toLong()*h==pixels.size.toLong()&&frameNs>=0)
        entry=Entry(pixels.copyOf(),w,h,frameNs,value)
    }
    fun clear(){entry=null}
}
