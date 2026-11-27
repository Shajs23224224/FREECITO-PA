package com.omega.vision

import kotlin.math.*

/** YOLOX-Nano official raw COCO head: 416 square, 80 classes, strides 8/16/32. */
object YoloMath {
    const val SIZE=416
    const val ANCHORS=3549
    const val CHANNELS=85
    val labels="person|bicycle|car|motorcycle|airplane|bus|train|truck|boat|traffic light|fire hydrant|stop sign|parking meter|bench|bird|cat|dog|horse|sheep|cow|elephant|bear|zebra|giraffe|backpack|umbrella|handbag|tie|suitcase|frisbee|skis|snowboard|sports ball|kite|baseball bat|baseball glove|skateboard|surfboard|tennis racket|bottle|wine glass|cup|fork|knife|spoon|bowl|banana|apple|sandwich|orange|broccoli|carrot|hot dog|pizza|donut|cake|chair|couch|potted plant|bed|dining table|toilet|tv|laptop|mouse|remote|keyboard|cell phone|microwave|oven|toaster|sink|refrigerator|book|clock|vase|scissors|teddy bear|hair drier|toothbrush".split("|")
    data class Input(val data:FloatArray,val width:Int,val height:Int,val resizedW:Int,val resizedH:Int)
    fun preprocess(argb:IntArray,w:Int,h:Int):Input {
        require(w in 1..1024&&h in 1..1024&&argb.size==w*h)
        val ratio=min(SIZE.toDouble()/w,SIZE.toDouble()/h)
        val rw=max(1,(w*ratio).toInt());val rh=max(1,(h*ratio).toInt())
        val plane=SIZE*SIZE
        val data=FloatArray(plane*3){114f}
        for(y in 0 until rh){
            val sy=((y+.5)*h/rh-.5).coerceIn(0.0,(h-1).toDouble())
            val y0=sy.toInt();val y1=min(y0+1,h-1);val fy=(sy-y0).toFloat()
            for(x in 0 until rw){
                val sx=((x+.5)*w/rw-.5).coerceIn(0.0,(w-1).toDouble())
                val x0=sx.toInt();val x1=min(x0+1,w-1);val fx=(sx-x0).toFloat()
                val a=argb[y0*w+x0];val b=argb[y0*w+x1];val c=argb[y1*w+x0];val d=argb[y1*w+x1]
                for(ch in 0..2){
                    val shift=ch*8
                    val p0=(a ushr shift and 255)*(1-fx)+(b ushr shift and 255)*fx
                    val p1=(c ushr shift and 255)*(1-fx)+(d ushr shift and 255)*fx
                    data[ch*plane+y*SIZE+x]=p0*(1-fy)+p1*fy
                }
            }
        }
        return Input(data,w,h,rw,rh)
    }
    private data class Cell(val x:Int,val y:Int,val stride:Int)
    private val cells=listOf(8,16,32).flatMap{stride->(0 until SIZE/stride).flatMap{y->(0 until SIZE/stride).map{x->Cell(x,y,stride)}}}
    fun decode(raw:FloatArray,input:Input,threshold:Float=.35f,nms:Float=.45f):List<Detection>{
        require(raw.size==ANCHORS*CHANNELS&&cells.size==ANCHORS&&labels.size==80)
        require(threshold in 0f..1f&&nms in 0f..1f&&input.resizedW in 1..SIZE&&input.resizedH in 1..SIZE)
        val candidates=ArrayList<Detection>()
        for(i in cells.indices){
            val off=i*CHANNELS;val obj=raw[off+4]
            if(!obj.isFinite()||obj !in threshold..1f)continue
            var best=-1;var cls=0f
            for(k in 0 until 80){val p=raw[off+5+k];if(p.isFinite()&&p in 0f..1f&&p>cls){best=k;cls=p}}
            val score=obj*cls
            if(best<0||score<threshold)continue
            val tx=raw[off];val ty=raw[off+1];val tw=raw[off+2];val th=raw[off+3]
            if(listOf(tx,ty,tw,th).any{!it.isFinite()}||abs(tw)>10f||abs(th)>10f)continue
            val cell=cells[i]
            val cx=(tx+cell.x)*cell.stride;val cy=(ty+cell.y)*cell.stride
            val bw=exp(tw)*cell.stride;val bh=exp(th)*cell.stride
            val left=((cx-bw/2)/input.resizedW).coerceIn(0f,1f)
            val right=((cx+bw/2)/input.resizedW).coerceIn(0f,1f)
            val top=((cy-bh/2)/input.resizedH).coerceIn(0f,1f)
            val bottom=((cy+bh/2)/input.resizedH).coerceIn(0f,1f)
            if(right-left<.002f||bottom-top<.002f)continue
            candidates+=Detection(labels[best],score,Box(left,top,right,bottom))
        }
        val kept=ArrayList<Detection>()
        for(d in candidates.sortedByDescending{it.score}.take(256)){
            if(kept.none{it.label==d.label&&it.box.iou(d.box)>nms})kept+=d
            if(kept.size>=32)break
        }
        return kept
    }
}
