package com.omega.core.executive

import kotlin.math.abs
import java.text.Normalizer
import java.util.Locale

data class HudPoint(val x:Float,val y:Float){
    init { require(x.isFinite()&&y.isFinite()&&x in 0f..1f&&y in 0f..1f) }
}
data class HudRegion(val left:Float,val top:Float,val right:Float,val bottom:Float){
    init { require(listOf(left,top,right,bottom).all{it.isFinite()&&it in 0f..1f}&&right-left>=.01f&&bottom-top>=.01f) }
    fun contains(x:Float,y:Float)=x in left..right&&y in top..bottom
}
data class HudLine(val text:String,val x:Float,val y:Float)
object TrainingObservation {
    fun normalize(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).trim().replace(Regex("\\s+")," ")
    fun validMarker(s:String):Boolean {
        val t=normalize(s)
        return t.length in 8..80 && listOf("entrenamiento","training","campo de tiro","target range","zona de combate","combat zone","treinamento","zona de tiro").any{t.contains(it)}
    }
    fun markerPresent(marker:String,region:HudRegion,lines:List<HudLine>):Boolean =
        validMarker(marker)&&lines.any{region.contains(it.x,it.y)&&normalize(it.text)==normalize(marker)}
    private val autoMarkers=setOf("campo de tiro","zona de tiro","zona de combate","target range","shooting range","combat zone","campo de treinamento","zona de treinamento","campo de entrenamiento","zona de entrenamiento")
    fun automaticMarker(lines:List<HudLine>):String? =
        lines.map{normalize(it.text)}.filter{it in autoMarkers}.distinct().singleOrNull()
    fun difference(before:FloatArray,after:FloatArray):Float? {
        if(before.size!=64||after.size!=64||before.any{!it.isFinite()||it !in 0f..1f}||after.any{!it.isFinite()||it !in 0f..1f})return null
        return before.indices.map{abs(before[it]-after[it])}.average().toFloat()
    }
}
