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
    fun contextualMarker(lines:List<HudLine>):String? {
        automaticMarker(lines)?.let{return it}
        val normalized=lines.map{normalize(it.text)}
        if(normalized.any{it in setOf("battle royale","duelo de escuadras","clasificatoria","ranked","booyah","derrota","victoria")})return null
        val quick=normalized.any{it in setOf("ajustes rapidos","quick settings","ajustes rapidos de entrenamiento")}
        val distances=lines.mapNotNull{line->Regex("^([1-9][0-9])\\s*m$").matchEntire(normalize(line.text))?.groupValues?.get(1)?.toIntOrNull()?.takeIf{it in 10..90&&it%10==0&&line.y<.65f}?.let{it to line.x}}
        if(quick&&distances.map{it.first}.distinct().size>=2&&distances.maxOf{it.second}-distances.minOf{it.second}>.08f)return "range_context"
        return null
    }
    fun health(lines:List<HudLine>):Float? {
        val values=lines.mapNotNull{line->Regex("^hp\\s*(\\d{1,4})\\s*/\\s*(\\d{1,4})$").matchEntire(normalize(line.text))?.let{m->
            val a=m.groupValues[1].toInt();val b=m.groupValues[2].toInt();if(b in 1..1000&&a in 0..b)a.toFloat()/b else null}}
        return values.distinct().singleOrNull()
    }
    fun medkits(lines:List<HudLine>,x:Float,y:Float,rx:Float,ry:Float):Int? =
        lines.filter{abs(it.x-x)<=rx*1.8f&&abs(it.y-y)<=ry*1.8f}.mapNotNull{normalize(it.text).toIntOrNull()?.takeIf{v->v in 0..99}}.distinct().singleOrNull()
    fun difference(before:FloatArray,after:FloatArray):Float? {
        if(before.size!=64||after.size!=64||before.any{!it.isFinite()||it !in 0f..1f}||after.any{!it.isFinite()||it !in 0f..1f})return null
        return before.indices.map{abs(before[it]-after[it])}.average().toFloat()
    }
}
