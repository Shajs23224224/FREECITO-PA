package com.omega.vision
fun main(){
 val w=640;val h=320;val full=OcrRegion(0,0,w,h)
 val p=IntArray(w*h){-1}
 check(UniformMarginCrop.find(p,w,h)==full)
 for(y in 80..120)for(x in 50..200)p[y*w+x]=0xff000000.toInt()
 val r=UniformMarginCrop.find(p,w,h)
 check(r.width*r.height<w*h&&r.width>=256&&r.height>=128)
 for(y in 0 until h)for(x in 0 until w)if(p[y*w+x]!=-1)check(x in r.x until r.x+r.width&&y in r.y until r.y+r.height)
 p[0]=0;check(UniformMarginCrop.find(p,w,h)==full)
 p[0]=-1;p[1]=0;p[w*(h-1)+w-2]=0
 check(UniformMarginCrop.find(p,w,h)==full)
 check(UniformMarginCrop.find(IntArray(10*10),10,10)==OcrRegion(0,0,10,10))
 println("PASS: uniform-margin OCR crop preserves all non-background pixels and resolution, bounded crop, full-frame fallback")
}
