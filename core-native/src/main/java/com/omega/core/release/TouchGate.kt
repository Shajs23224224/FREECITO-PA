package com.omega.core.release

data class TouchRequest(val x:Float,val y:Float,val endX:Float,val endY:Float,val durationMs:Long,val createdMs:Long){
    init{require(listOf(x,y,endX,endY).all{it.isFinite()&&it in 0f..1f});require(durationMs in 1..500 && createdMs>=0)}
}
object TouchGate {
    fun allows(armed:Boolean,target:String?,foreground:String?,request:TouchRequest,nowMs:Long):Boolean =
        armed && !target.isNullOrBlank() && target==foreground && nowMs>=request.createdMs && nowMs-request.createdMs<=500
}
