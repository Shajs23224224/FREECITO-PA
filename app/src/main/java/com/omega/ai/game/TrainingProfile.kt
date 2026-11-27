package com.omega.ai.game

import android.content.Context
import com.omega.core.executive.HudPoint
import com.omega.core.executive.HudRegion
import com.omega.core.executive.TrainingObservation
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.abs

data class TrainingProfile(val marker:String,val markerRegion:HudRegion,val moveStart:HudPoint,val moveEnd:HudPoint,val cameraStart:HudPoint,val cameraEnd:HudPoint,val aspect:Float){
    init{require(TrainingObservation.validMarker(marker)&&aspect.isFinite()&&aspect in 1.1f..3.5f)
        require(abs(moveStart.x-moveEnd.x)+abs(moveStart.y-moveEnd.y)>.025f)
        require(abs(cameraStart.x-cameraEnd.x)+abs(cameraStart.y-cameraEnd.y)>.025f)}
    fun matches(w:Int,h:Int)=w>0&&h>0&&abs(w.toFloat()/h-aspect)<.045f
    fun save(context:Context){
        fun p(v:HudPoint)=JSONArray().put(v.x.toDouble()).put(v.y.toDouble())
        val r=markerRegion
        val json=JSONObject().put("marker",marker).put("region",JSONArray().put(r.left.toDouble()).put(r.top.toDouble()).put(r.right.toDouble()).put(r.bottom.toDouble())).put("moveStart",p(moveStart)).put("moveEnd",p(moveEnd)).put("cameraStart",p(cameraStart)).put("cameraEnd",p(cameraEnd)).put("aspect",aspect.toDouble())
        context.getSharedPreferences("training_hud",Context.MODE_PRIVATE).edit().putString("profile",json.toString()).apply()
    }
    companion object{
        fun load(context:Context):TrainingProfile?=runCatching{
            val raw=context.getSharedPreferences("training_hud",Context.MODE_PRIVATE).getString("profile",null)?:return null
            val j=JSONObject(raw);val r=j.getJSONArray("region")
            fun p(k:String):HudPoint{val a=j.getJSONArray(k);return HudPoint(a.getDouble(0).toFloat(),a.getDouble(1).toFloat())}
            TrainingProfile(j.getString("marker"),HudRegion(r.getDouble(0).toFloat(),r.getDouble(1).toFloat(),r.getDouble(2).toFloat(),r.getDouble(3).toFloat()),p("moveStart"),p("moveEnd"),p("cameraStart"),p("cameraEnd"),j.getDouble("aspect").toFloat())
        }.getOrNull()
    }
}
