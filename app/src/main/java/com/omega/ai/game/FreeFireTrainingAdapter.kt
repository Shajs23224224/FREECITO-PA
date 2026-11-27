package com.omega.ai.game
import android.app.Activity
import android.os.SystemClock
import com.omega.ai.accessibility.TouchBridge
import com.omega.ai.SessionDiagnostics
import com.omega.core.executive.*
import com.omega.core.release.TouchRequest
import com.omega.vision.*

/** Screen-derived hypotheses only; no fixed HUD coordinates. */
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
    private val tracker=IconTracker()
    private var icons=emptyMap<HudIcon,IconMatch>()
    private var lastReceipt:Receipt?=null
    private var receiptKind:HudIcon?=null
    private var stateSeq=-1L
    private var health:Float?=null
    private var medkits:Int?=null
    private data class Pending(val command:Command,val kind:HudIcon,val before:FloatArray,val threshold:Float,val beforeHealth:Float?,var deliveredAt:Long?=null,var failed:Boolean=false,var changes:Int=0,var lastFrame:Long=-1)
    private var pending:Pending?=null
    fun live()=active
    fun matches(w:Int,h:Int)=w>h&&h>0&&w.toFloat()/h in 1.1f..3.5f
    override fun launch(activity:Activity){check(FreeFireLauncher.open(activity))}
    override fun captureScopeConfirmed()=active&&foreground
    override fun accessibilityTestScopeConfirmed():Boolean{
        val now=SystemClock.elapsedRealtimeNanos()
        return active&&foreground&&verifiedAt>=0&&now-verifiedAt in 0..2_500_000_000L&&controlAt>=0&&now-controlAt in 0..1_500_000_000L
    }
    override fun authorized()=active&&foreground
    @Synchronized fun status()=lastMessage
    @Synchronized fun foreground(value:Boolean){
        foreground=value
        if(!value){verifiedAt=-1;controlAt=-1;consecutive=0;lastMarker=null;state=null;pending=null;tracker.clear();icons=emptyMap();health=null;medkits=null;lastReceipt=null;receiptKind=null;lastMessage="Fuera del juego · gestos desactivados"}
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
        val h=s.hud
        if(h!=null&&h.frameNs>lastHud){
            lastHud=h.frameNs
            icons=tracker.update(h.frameNs,h.value.icons)
            controlAt=if(icons.isNotEmpty())h.receivedNs else -1L
            SessionDiagnostics.event("hud_check","found="+h.value.icons.joinToString(","){it.kind.name}+" stable="+icons.keys.joinToString(","))
        }
        val t=s.text
        if(t!=null&&t.frameNs>lastOcr){
            val prev=lastOcr;lastOcr=t.frameNs
            val lines=t.value.mapNotNull{line->line.box?.let{b->HudLine(line.text,(b.left+b.right)/2,(b.top+b.bottom)/2)}}
            val fresh=now-t.receivedNs in 0..2_500_000_000L
            val marker=if(fresh)TrainingObservation.automaticMarker(lines)?:TrainingObservation.contextualMarker(lines) else null
            health=if(fresh)TrainingObservation.health(lines) else null
            medkits=if(fresh)icons[HudIcon.HEAL]?.let{TrainingObservation.medkits(lines,it.x,it.y,it.radiusX,it.radiusY)} else null
            SessionDiagnostics.event("ocr_check","zone="+(marker?: "none")+" hp_known="+(health!=null)+" kits="+(medkits?.toString()?:"unknown"))
            if(marker==null){consecutive=0;lastMarker=null;verifiedAt=-1;state=null;TouchBridge.disarm()}
            else{
                consecutive=if(marker==lastMarker&&prev>=0&&t.frameNs-prev<=4_000_000_000L)(consecutive+1).coerceAtMost(2) else 1
                lastMarker=marker
                if(consecutive>=2)verifiedAt=t.receivedNs else verifiedAt=-1
            }
        }
        if(verifiedAt<0||now-verifiedAt !in 0..2_500_000_000L){
            state=null;lastMessage="Buscando automáticamente zona de entrenamiento";SessionDiagnostics.event("adapter","waiting_training_marker consecutive=$consecutive");return
        }
        if(controlAt<0||now-controlAt !in 0..1_500_000_000L){
            state=null;TouchBridge.disarm();lastMessage="Reconociendo HUD · sin controles estables";SessionDiagnostics.event("adapter","waiting_hud");return
        }
        val p=pending
        if(p!=null){
            val delivered=p.deliveredAt
            val healed=p.kind==HudIcon.HEAL&&p.beforeHealth!=null&&health?.let{it>p.beforeHealth+.01f}==true&&delivered!=null&&lastOcr>delivered
            val changed=p.kind==HudIcon.MOVE&&p.changes>=2
            val outcome=when{p.failed->Outcome.FAILURE;healed||changed->Outcome.SUCCESS;delivered!=null&&now-delivered>(if(p.kind==HudIcon.HEAL)5_000_000_000L else 2_000_000_000L)->Outcome.UNKNOWN;else->null}
            if(outcome!=null){
                lastReceipt=Receipt(p.command.id,outcome,if(changed).1f else null,continueAfterUnknown=delivered!=null)
                receiptKind=p.kind;pending=null
                SessionDiagnostics.event("adapter","effect "+p.kind.name+" "+outcome.name,true)
            }
        }
        val seq=maxOf(lastOcr,lastHud,frameNs)
        if(seq<=stateSeq)return
        stateSeq=seq
        val choice=if(pending==null)HudActionPolicy.next(icons.keys,issued,health,medkits) else null
        val offers=choice?.let{listOf(Offer(it.name.lowercase(java.util.Locale.ROOT),if(it==HudIcon.HEAL)Family.HEAL else Family.EXPLORE,virtualOnly=true))}?:emptyList()
        val combat=if(choice==HudIcon.HEAL||pending?.kind==HudIcon.HEAL||receiptKind==HudIcon.HEAL)CombatContext(true,health,null,null,null,null,medkits,null,.8f) else null
        state=GameState(scope,seq,minOf(verifiedAt,controlAt),emptyList(),offers,lastReceipt,combat)
        lastMessage=if(pending==null)"HUD: "+icons.keys.joinToString(",")+" · "+(choice?.name?:"sin acción") else "Observando efecto de "+pending?.kind
        SessionDiagnostics.event("adapter",if(pending==null)"offer_ready "+choice?.name else "pending_effect")
    }
    @Synchronized override fun observe():GameState?=state?.takeIf{accessibilityTestScopeConfirmed()}
    @Synchronized override fun submit(command:Command):Boolean{
        val now=SystemClock.elapsedRealtimeNanos();val current=state?:return false;val before=signature?:return false
        if(!accessibilityTestScopeConfirmed()||pending!=null||command.scope!=scope||command.sequence!=current.sequence||command.expiresNs<=now||now-current.observedNs !in 0..1_500_000_000L||now-frameNs !in 0..1_000_000_000L||current.offers.none{it.id==command.offerId}){SessionDiagnostics.event("adapter","submit_rejected_scope_sequence_or_freshness",true);return false}
        val kind=HudIcon.values().firstOrNull{it.name.equals(command.offerId,true)}?:return false
        val icon=icons[kind]?:return false
        if(kind==HudIcon.HEAL&&(health==null||health!!>=.45f||(medkits?:0)<=0))return false
        val gesture=HudActionPolicy.gesture(icon,issued)?:return false
        val p=Pending(command,kind,before.copyOf(),maxOf(.045f,noise*3f+.02f),health)
        pending=p;lastReceipt=null;receiptKind=null
        val ok=TouchBridge.submit(TouchRequest(gesture.x,gesture.y,gesture.endX,gesture.endY,gesture.durationMs,SystemClock.elapsedRealtime())){success->
            synchronized(this){if(pending===p){if(success)p.deliveredAt=SystemClock.elapsedRealtimeNanos() else p.failed=true}}
        }
        if(!ok){pending=null;SessionDiagnostics.event("adapter","touch_bridge_rejected",true);return false}
        issued++;SessionDiagnostics.event("adapter","gesture_queued "+kind.name+" count=$issued",true);lastMessage="Gesto solicitado: "+kind.name;return true
    }
    @Synchronized override fun stop(){active=false;foreground=false;verifiedAt=-1;controlAt=-1;state=null;pending=null;tracker.clear();icons=emptyMap();lastMessage="Sesión detenida";TouchBridge.disarm()}
}
