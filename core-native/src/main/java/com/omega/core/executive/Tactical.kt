package com.omega.core.executive

/** State supplied by an authorized virtual-game port; null means not observed. */
data class CombatContext(val trainingAttested:Boolean,val health:Float?,val loaded:Int?,val reserve:Int?,val threatVisible:Boolean?,val inCover:Boolean?,val healingItems:Int?,val hits:Long?,val confidence:Float){
    init { require(health==null||health.isFinite()&&health in 0f..1f);require(listOfNotNull(loaded,reserve,healingItems).all{it in 0..1000000});require(hits==null||hits>=0);require(confidence.isFinite()&&confidence in 0f..1f) }
    fun usable()=trainingAttested&&confidence>=.8f
}
enum class CombatAction { COVER, RELOAD, HEAL, ENGAGE, DISENGAGE }
object TacticalPolicy {
    /** Priorities are initial hypotheses for virtual gameplay, not a trained policy. */
    fun priority(action:CombatAction,c:CombatContext?):Int {
        if(c==null||!c.usable())return -1
        val health=c.health;val ammo=c.loaded
        return when(action){
            CombatAction.COVER->if(c.threatVisible==true&&c.inCover==false)120 else -1
            CombatAction.RELOAD->if(ammo==0&&(c.reserve?:0)>0&&c.threatVisible==false)110 else -1
            CombatAction.HEAL->if(health!=null&&health<.45f&&(c.healingItems?:0)>0&&c.threatVisible!=true)115 else -1
            CombatAction.ENGAGE->if(c.threatVisible==true&&c.inCover==true&&health!=null&&health>=.45f&&(ammo?:0)>0)105 else -1
            CombatAction.DISENGAGE->if(c.threatVisible==true&&(health!=null&&health<.45f||ammo==0))125 else -1
        }
    }
    fun confirmed(action:CombatAction,before:CombatContext?,after:CombatContext?):Boolean {
        if(before==null||after==null||!before.usable()||!after.usable())return false
        return when(action){
            CombatAction.COVER->before.inCover==false&&after.inCover==true
            CombatAction.RELOAD->before.loaded!=null&&after.loaded!=null&&after.loaded>before.loaded
            CombatAction.HEAL->before.health!=null&&after.health!=null&&after.health>before.health
            CombatAction.ENGAGE->before.hits!=null&&after.hits!=null&&after.hits>before.hits
            CombatAction.DISENGAGE->before.threatVisible==true&&after.threatVisible==false
        }
    }
    fun features(c:CombatContext?,embeddingMean:Float):List<Float> = listOf(1f,c?.health?:-1f,c?.loaded?.let{it.coerceAtMost(100)/100f}?:-1f,c?.reserve?.let{it.coerceAtMost(100)/100f}?:-1f,flag(c?.threatVisible),flag(c?.inCover),c?.confidence?:0f,embeddingMean.coerceIn(-1f,1f))
    private fun flag(v:Boolean?)=when(v){true->1f;false->0f;null->-1f}
}
