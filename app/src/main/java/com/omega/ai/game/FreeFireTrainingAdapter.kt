package com.omega.ai.game

import android.app.Activity
import android.os.SystemClock
import com.omega.ai.accessibility.TouchBridge
import com.omega.core.executive.*
import com.omega.core.release.TouchRequest
import com.omega.vision.VisionSnapshot

/** Experimental live screen adapter. No game memory access or client modification. */
internal class FreeFireTrainingAdapter(private val profile:TrainingProfile):AuthorizedGameAdapter,GamePort{
    override val title="Free Fire — entrenamiento"
    override val memoryScope="freefire:training:calibrated:v1"
    override val scope=memoryScope
    override val commandPort:GamePort get()=this
    override val accessibilityTestPackage=FreeFireLauncher.PACKAGE
    @Volatile private var active=true
    @Volatile private var foreground=false
    @Volatile private var verifiedAt=-1L
    private var lastOcr=-1L
    private var consecutive=0
    private var state:GameState?=null
    private var frameNs=-1L
    private var signature:FloatArray?=null
    private var issued=0
    private var lastMessage="Esperando el juego"
    private data class Pending(val command:Command,val before:FloatArray,var deliveredAt:Long?=null,var failed:Boolean=false)
    private var pending:Pending?=null
    fun live()=active
    fun matches(w:Int,h:Int)=profile.matches(w,h)
    override fun launch(activity:Activity){check(FreeFireLauncher.open(activity))}
    override fun captureScopeConfirmed()=active&&foreground
    override fun accessibilityTestScopeConfirmed():Boolean{
        val now=SystemClock.elapsedRealtimeNanos()
        return active&&foreground&&verifiedAt>=0&&now-verifiedAt in 0..2_500_000_000L
    }
    override fun authorized()=active&&foreground
    @Synchronized fun status()=lastMessage
    @Synchronized fun foreground(value:Boolean){
        foreground=value
        if(!value){verifiedAt=-1;consecutive=0;state=null;pending=null;lastMessage="Fuera del juego · gestos desactivados"}
    }
    @Synchronized fun frame(ts:Long,values:FloatArray){
        if(active&&foreground&&ts>frameNs&&values.size==64){frameNs=ts;signature=values.copyOf()}
    }
    @Synchronized fun ingest(s:VisionSnapshot){
        if(!active||!foreground)return
        val t=s.text?:return
        if(t.frameNs<=lastOcr)return
        lastOcr=t.frameNs
        val now=SystemClock.elapsedRealtimeNanos()
        val lines=t.value.mapNotNull{line->line.box?.let{b->HudLine(line.text,(b.left+b.right)/2,(b.top+b.bottom)/2)}}
        val present=now-t.receivedNs in 0..2_500_000_000L && TrainingObservation.markerPresent(profile.marker,profile.markerRegion,lines)
        if(!present){
            consecutive=0;verifiedAt=-1;state=null;lastMessage="Esperando etiqueta calibrada de entrenamiento"
            return
        }
        consecutive=(consecutive+1).coerceAtMost(2)
        if(consecutive<2){lastMessage="Confirmando entrenamiento en dos lecturas";return}
        verifiedAt=t.receivedNs
        val p=pending
        var receipt:Receipt?=null
        if(p!=null){
            val delivered=p.deliveredAt
            if(p.failed){receipt=Receipt(p.command.id,Outcome.FAILURE);pending=null}
            else if(delivered!=null&&frameNs>delivered&&t.receivedNs>delivered){
                val d=signature?.let{TrainingObservation.difference(p.before,it)}
                if(d!=null&&d>=.045f){receipt=Receipt(p.command.id,Outcome.SUCCESS,.1f);pending=null;lastMessage="Cambio visual posterior al gesto (no prueba causal)"}
                else if(now-delivered>1_200_000_000L){receipt=Receipt(p.command.id,Outcome.FAILURE);pending=null}
            }
        }
        val offers=if(pending!=null||receipt!=null)emptyList() else listOf(
            if(issued%3==2)Offer("camera",Family.INSPECT,virtualOnly=true) else Offer("move",Family.EXPLORE,virtualOnly=true))
        state=GameState(scope,t.frameNs,t.receivedNs,emptyList(),offers,receipt)
        if(receipt==null)lastMessage=if(pending==null)"Entrenamiento reconocido · decisión disponible" else "Evaluando resultado visual"
    }
    @Synchronized override fun observe():GameState?=state?.takeIf{accessibilityTestScopeConfirmed()}
    @Synchronized override fun submit(command:Command):Boolean{
        val now=SystemClock.elapsedRealtimeNanos()
        val current=state?:return false
        val before=signature?:return false
        if(!accessibilityTestScopeConfirmed()||pending!=null||command.scope!=scope||command.sequence!=current.sequence||command.expiresNs<=now||now-current.observedNs !in 0..1_500_000_000L||now-frameNs !in 0..1_000_000_000L||current.offers.none{it.id==command.offerId})return false
        val (a,b)=when(command.offerId){"move"->profile.moveStart to profile.moveEnd;"camera"->profile.cameraStart to profile.cameraEnd;else->return false}
        val p=Pending(command,before.copyOf())
        pending=p
        val ok=TouchBridge.submit(TouchRequest(a.x,a.y,b.x,b.y,if(command.offerId=="move")450 else 300,SystemClock.elapsedRealtime())){success->
            synchronized(this){if(pending===p){if(success)p.deliveredAt=SystemClock.elapsedRealtimeNanos() else p.failed=true}}
        }
        if(!ok){pending=null;return false}
        issued++;lastMessage="Gesto solicitado: "+command.offerId;return true
    }
    @Synchronized override fun stop(){active=false;foreground=false;verifiedAt=-1;state=null;pending=null;lastMessage="Sesión detenida";TouchBridge.disarm()}
}
