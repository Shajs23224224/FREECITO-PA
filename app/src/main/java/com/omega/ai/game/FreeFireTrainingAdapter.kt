package com.omega.ai.game

import android.app.Activity
import android.os.SystemClock
import com.omega.ai.accessibility.TouchBridge
import com.omega.core.executive.*
import com.omega.core.release.TouchRequest
import com.omega.vision.*

/** Automatic discovery from live visual evidence. Unknown/ambiguous controls are not guessed. */
internal class FreeFireTrainingAdapter:AuthorizedGameAdapter,GamePort{
    override val title="Free Fire — entrenamiento"
    override val memoryScope="freefire:training:auto-hud:v1"
    override val scope=memoryScope
    override val commandPort:GamePort get()=this
    override val accessibilityTestPackage=FreeFireLauncher.PACKAGE
    @Volatile private var active=true
    @Volatile private var foreground=false
    @Volatile private var verifiedAt=-1L
    @Volatile private var controlAt=-1L
    private var lastOcr=-1L
    private var lastHud=-1L
    private var lastMarker:String?=null
    private var consecutive=0
    private var state:GameState?=null
    private var frameNs=-1L
    private var signature:FloatArray?=null
    private var noise=0f
    private var issued=0
    private var lastMessage="Esperando el juego"
    private val tracker=HudTracker()
    private var joystick:HudCircle?=null
    private var lastReceipt:Receipt?=null
    private var stateSeq=-1L
    private data class Pending(val command:Command,val before:FloatArray,val threshold:Float,var deliveredAt:Long?=null,var failed:Boolean=false,var changes:Int=0,var lastFrame:Long=-1)
    private var pending:Pending?=null
    fun live()=active
    fun matches(w:Int,h:Int)=w>h&&h>0&&w.toFloat()/h in 1.1f..3.5f
    override fun launch(activity:Activity){check(FreeFireLauncher.open(activity))}
    override fun captureScopeConfirmed()=active&&foreground
    override fun accessibilityTestScopeConfirmed():Boolean{
        val now=SystemClock.elapsedRealtimeNanos()
        return active&&foreground&&verifiedAt>=0&&now-verifiedAt in 0..2_500_000_000L&&controlAt>=0&&now-controlAt in 0..2_500_000_000L
    }
    override fun authorized()=active&&foreground
    @Synchronized fun status()=lastMessage
    @Synchronized fun foreground(value:Boolean){
        foreground=value
        if(!value){verifiedAt=-1;controlAt=-1;consecutive=0;lastMarker=null;state=null;pending=null;tracker.clear();joystick=null;lastMessage="Fuera del juego · gestos desactivados"}
    }
    @Synchronized fun frame(ts:Long,values:FloatArray){
        if(!active||!foreground||ts<=frameNs||values.size!=64)return
        if(pending==null)signature?.let{old->TrainingObservation.difference(old,values)?.let{noise=.8f*noise+.2f*it}}
        frameNs=ts;signature=values.copyOf()
        val p=pending
        if(p!=null&&p.deliveredAt?.let{ts>it}==true&&ts>p.lastFrame){
            p.lastFrame=ts
            val d=TrainingObservation.difference(p.before,values)
            if(d!=null&&d>=p.threshold)p.changes++ else p.changes=0
        }
    }
    @Synchronized fun ingest(s:VisionSnapshot){
        if(!active||!foreground)return
        val now=SystemClock.elapsedRealtimeNanos()
        val t=s.text
        if(t!=null&&t.frameNs>lastOcr){
            val prev=lastOcr;lastOcr=t.frameNs
            val lines=t.value.mapNotNull{line->line.box?.let{b->HudLine(line.text,(b.left+b.right)/2,(b.top+b.bottom)/2)}}
            val marker=if(now-t.receivedNs in 0..2_500_000_000L)TrainingObservation.automaticMarker(lines) else null
            if(marker==null){consecutive=0;lastMarker=null;verifiedAt=-1;state=null;TouchBridge.disarm()}
            else{
                consecutive=if(marker==lastMarker&&prev>=0&&t.frameNs-prev<=4_000_000_000L)(consecutive+1).coerceAtMost(2) else 1
                lastMarker=marker
                if(consecutive>=2)verifiedAt=t.receivedNs else verifiedAt=-1
            }
        }
        val h=s.hud
        if(h!=null&&h.frameNs>lastHud){
            lastHud=h.frameNs
            val label=t?.takeIf{now-it.receivedNs in 0..2_500_000_000L}?.value?.filter{TrainingObservation.normalize(it.text) in setOf("joystick","movimiento","movement","direcional","directional pad")}?.singleOrNull()?.box
            val semantic=label?.let{(it.left+it.right)/2 to (it.top+it.bottom)/2}
            joystick=tracker.update(h.frameNs,h.value,semantic)
            controlAt=if(joystick!=null)h.receivedNs else -1L
        }
        if(verifiedAt<0||now-verifiedAt !in 0..2_500_000_000L){
            state=null;lastMessage="Buscando automáticamente zona de entrenamiento";return
        }
        if(controlAt<0||now-controlAt !in 0..2_500_000_000L){
            state=null;TouchBridge.disarm();lastMessage="Reconociendo HUD · sin joystick inequívoco";return
        }
        val p=pending
        if(p!=null){
            val delivered=p.deliveredAt
            if(p.failed){lastReceipt=Receipt(p.command.id,Outcome.FAILURE);pending=null}
            else if(delivered!=null&&p.changes>=2){lastReceipt=Receipt(p.command.id,Outcome.SUCCESS,.1f);pending=null;lastMessage="Cambio visual repetido · recompensa provisional"}
            else if(delivered!=null&&now-delivered>2_500_000_000L){lastReceipt=Receipt(p.command.id,Outcome.UNKNOWN);pending=null;lastMessage="Efecto no confirmado"}
        }
        val seq=maxOf(lastOcr,lastHud,frameNs)
        if(seq<=stateSeq)return
        stateSeq=seq
        val offers=if(pending!=null)emptyList() else listOf(Offer("move",Family.EXPLORE,virtualOnly=true))
        state=GameState(scope,seq,minOf(verifiedAt,controlAt),emptyList(),offers,lastReceipt)
        lastMessage=if(pending==null)"Joystick detectado automáticamente · exploración experimental" else "Observando efecto del movimiento"
    }
    @Synchronized override fun observe():GameState?=state?.takeIf{accessibilityTestScopeConfirmed()}
    @Synchronized override fun submit(command:Command):Boolean{
        val now=SystemClock.elapsedRealtimeNanos();val current=state?:return false;val j=joystick?:return false;val before=signature?:return false
        if(!accessibilityTestScopeConfirmed()||pending!=null||command.scope!=scope||command.sequence!=current.sequence||command.expiresNs<=now||now-current.observedNs !in 0..1_500_000_000L||now-frameNs !in 0..1_000_000_000L||current.offers.none{it.id==command.offerId}||command.offerId!="move")return false
        val (dx,dy)=when(issued%4){0->0f to -1f;1->-1f to 0f;2->1f to 0f;else->0f to 1f}
        val x=j.x+dx*j.radiusX*.75f;val y=j.y+dy*j.radiusY*.75f
        if(x !in 0f..1f||y !in 0f..1f)return false
        val p=Pending(command,before.copyOf(),maxOf(.045f,noise*3f+.02f))
        pending=p;lastReceipt=null
        val ok=TouchBridge.submit(TouchRequest(j.x,j.y,x,y,350,SystemClock.elapsedRealtime())){success->
            synchronized(this){if(pending===p){if(success)p.deliveredAt=SystemClock.elapsedRealtimeNanos() else p.failed=true}}
        }
        if(!ok){pending=null;return false}
        issued++;lastMessage="Exploración desde joystick detectado";return true
    }
    @Synchronized override fun stop(){active=false;foreground=false;verifiedAt=-1;controlAt=-1;state=null;pending=null;tracker.clear();lastMessage="Sesión detenida";TouchBridge.disarm()}
}
