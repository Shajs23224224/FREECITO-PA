package com.omega.vision

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Local, CPU-only pretrained detection. No game-specific labels or action authority. */
internal class YoloDetector(context:Context):AutoCloseable {
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession=OrtSession.SessionOptions().use{opt->
        opt.setIntraOpNumThreads(1);opt.setInterOpNumThreads(1)
        val model=context.assets.open("models/yolox_nano.onnx").use{it.readBytes()}
        env.createSession(model,opt).also{s->
            if(s.inputNames!=setOf("images")||s.outputNames!=setOf("output")){s.close();error("YOLOX tensor names")}
        }
    }
    fun detect(pixels:IntArray,w:Int,h:Int):List<Detection>{
        val input=YoloMath.preprocess(pixels,w,h)
        OnnxTensor.createTensor(env,FloatBuffer.wrap(input.data),longArrayOf(1,3,416,416)).use{tensor->
            session.run(mapOf("images" to tensor),setOf("output")).use{result->
                val out=result[0] as? OnnxTensor?:error("YOLOX output type")
                check(out.info.shape.contentEquals(longArrayOf(1,3549,85))){"YOLOX shape"}
                val b=out.floatBuffer
                check(b.remaining()==YoloMath.ANCHORS*YoloMath.CHANNELS)
                val raw=FloatArray(b.remaining());b.get(raw)
                return YoloMath.decode(raw,input)
            }
        }
    }
    override fun close(){session.close()}
}
