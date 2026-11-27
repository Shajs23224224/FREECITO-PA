package com.omega.vision

internal data class OcrRegion(val x:Int,val y:Int,val width:Int,val height:Int)
/** Remove only exact, uniform outer background. Preserve every non-background pixel,
 * original resolution, at least 256x128 where possible, and 32px context margin.
 * Full image for nonuniform borders, small savings or entirely uniform inputs.
 */
internal object UniformMarginCrop {
    fun find(pixels:IntArray,w:Int,h:Int):OcrRegion{
        require(w>0&&h>0&&w.toLong()*h==pixels.size.toLong())
        val full=OcrRegion(0,0,w,h)
        if(w<256||h<128)return full
        val bg=pixels[0]
        if(pixels[w-1]!=bg||pixels[(h-1)*w]!=bg||pixels.last()!=bg)return full
        var l=w;var t=h;var r=-1;var b=-1
        for(y in 0 until h)for(x in 0 until w){
            if(pixels[y*w+x]!=bg){if(x<l)l=x;if(x>r)r=x;if(y<t)t=y;if(y>b)b=y}
        }
        if(r<0)return full
        val cw=minOf(w,maxOf(256,r-l+1+64))
        val ch=minOf(h,maxOf(128,b-t+1+64))
        if(cw.toLong()*ch*10>w.toLong()*h*7)return full
        val x=((l+r+1-cw)/2).coerceIn(0,w-cw)
        val y=((t+b+1-ch)/2).coerceIn(0,h-ch)
        if(x>l||y>t||x+cw<=r||y+ch<=b)return full
        return OcrRegion(x,y,cw,ch)
    }
}
