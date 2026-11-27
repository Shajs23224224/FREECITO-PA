package com.omega.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import com.omega.ai.accessibility.OmegaTouchService
import com.omega.ai.accessibility.TouchBridge
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.omega.ai.capture.ProjectionService
import com.omega.ai.game.GameBinding
import com.omega.ai.game.FreeFireLauncher
import com.omega.core.release.Connection

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var button: Button
    private lateinit var accessStatus: TextView
    private lateinit var accessLink: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var awaitingConsent = false
    private var launchMessage:String?=null
    private val tick = object : Runnable { override fun run() { render(); handler.postDelayed(this,500) } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        awaitingConsent = state?.getBoolean("awaiting") ?: false
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(16,24,39)); setPadding(dp(24),dp(24),dp(24),dp(24)) }
        root.setOnApplyWindowInsetsListener { v, ins ->
            @Suppress("DEPRECATION") v.setPadding(dp(24)+ins.systemWindowInsetLeft,dp(24)+ins.systemWindowInsetTop,dp(24)+ins.systemWindowInsetRight,dp(24)+ins.systemWindowInsetBottom); ins
        }
        root.addView(ImageView(this).apply { setImageResource(com.omega.ai.R.drawable.ic_omega); contentDescription="Logotipo OMEGA" }, LinearLayout.LayoutParams(dp(104),dp(104)))
        root.addView(label("O M E G A",30f,Color.WHITE))
        root.addView(label("OMEGA · FREE FIRE",12f,Color.rgb(144,217,235)))
        status = label("Sistema inactivo · sin adaptador de juego",16f,Color.WHITE).apply { setPadding(0,dp(24),0,dp(24)) }; root.addView(status)
        button = Button(this).apply { isAllCaps=false; textSize=18f; minHeight=dp(56); setOnClickListener { toggle() } }
        root.addView(button,LinearLayout.LayoutParams(-1,-2))
        accessStatus=label("Accesibilidad: comprobando",14f,Color.LTGRAY).apply{setPadding(0,dp(16),0,0)}
        root.addView(accessStatus)
        accessLink=label("Configurar accesibilidad",15f,Color.rgb(144,217,235)).apply{
            minHeight=dp(48);isClickable=true;isFocusable=true;setOnClickListener{showAccessSettings()}
        }
        root.addView(accessLink,LinearLayout.LayoutParams(-1,-2))
        root.addView(label("v1.0.12 · Control flotante",12f,Color.LTGRAY).apply { setPadding(0,dp(16),0,0) })
        root.addView(Button(this).apply{text="Ver diagnóstico de última sesión";isAllCaps=false;setOnClickListener{showDiagnostic()}})
        val scroll=android.widget.ScrollView(this).apply{isFillViewport=true;addView(root)}
        setContentView(scroll); render()
    }
    private fun showDiagnostic(){
        val report=SessionDiagnostics.report()
        AlertDialog.Builder(this).setTitle("Diagnóstico OMEGA").setMessage(report)
            .setPositiveButton("Cerrar",null).setNeutralButton("Copiar"){_,_->
                val clipboard=getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("OMEGA diagnóstico",report))
            }.show()
    }
    private fun dp(v: Int) = (v*resources.displayMetrics.density+0.5f).toInt()
    private fun label(t:String,s:Float,c:Int) = TextView(this).apply { text=t; textSize=s; setTextColor(c); gravity=Gravity.CENTER }
    private fun accessibilityEnabled():Boolean? = runCatching {
        val manager=requireNotNull(getSystemService(AccessibilityManager::class.java))
        val expected=ComponentName(this,OmegaTouchService::class.java)
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info=it.resolveInfo.serviceInfo
            ComponentName(info.packageName,info.name)==expected
        }
    }.getOrNull()
    private fun showAccessSettings(){
        AlertDialog.Builder(this).setTitle("Accesibilidad de OMEGA")
            .setMessage("OMEGA usa capturas locales y gestos en el campo de entrenamiento reconocido visualmente de Free Fire. No guarda imágenes ni accede a otros juegos. Activa el servicio solo para las pruebas que has autorizado. Puedes detenerlo regresando a OMEGA o deshabilitarlo en Ajustes.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Abrir ajustes"){_,_->
                try{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
                catch(e:Exception){launchMessage="No se pudieron abrir los ajustes de accesibilidad";render()}
            }.show()
    }
    private fun render() {
        var s=RuntimeSession.status()
        // Also show resource telemetry in the idle-core session, even without a game adapter.
        if(s.active && !s.capturing){
            if(!RuntimeSession.resourceHealthy())RuntimeSession.stop("resource_stop")
            s=RuntimeSession.status()
        }
        status.text = if(s.capturing) "${s.message}\nFotogramas procesados: ${s.stats?.processed ?: 0}" else s.message
        if (s.capturing) s.perception?.let { p ->
            status.append("\nVisor: ${p.detectorBackend} / ${p.detectorStatus} · OCR: ${p.ocrStatus} · profundidad: ${p.depthStatus} · HUD: ${p.hudStatus}")
        }
        s.neuralStatus?.let { status.append("\n$it") }
        s.resources?.let { status.append("\n$it") }
        if(!s.active && GameBinding.connection()==Connection.UNCONFIGURED){
            status.text=launchMessage ?: if(!FreeFireLauncher.available(this)) "Free Fire no encontrado" else if(s.message.startsWith("Sistema detenido"))s.message else "HUD automático · entra al entrenamiento autorizado"
            button.text="INICIAR IA"
        }else button.text=if(s.active) "DETENER IA" else "INICIAR IA"
        GameBinding.training?.let{status.append("\n"+it.status())}
        val enabled=accessibilityEnabled()
        val connected=TouchBridge.connected()
        val armed=TouchBridge.isArmed()
        accessStatus.text=when(enabled){true->"Accesibilidad: habilitada";false->"Accesibilidad: deshabilitada";null->"Accesibilidad: estado desconocido"}+" · "+(if(connected)"servicio conectado" else "servicio desconectado")+"\n"+(if(armed)"Transporte de prueba activo" else "Gestos inactivos")
        accessLink.text=if(enabled==true)"Administrar accesibilidad" else "Configurar accesibilidad"
        button.isEnabled=!awaitingConsent
    }
    private fun toggle(){
        if(RuntimeSession.status().active){RuntimeSession.stop("user_stop");render();return}
        if(Build.VERSION.SDK_INT<30){launchMessage="La captura local requiere Android 11 o superior";render();return}
        if(accessibilityEnabled()!=true||!TouchBridge.connected()){showAccessSettings();return}
        if(!FreeFireLauncher.available(this)){launchMessage="Free Fire no encontrado";render();return}
        AlertDialog.Builder(this).setTitle("Iniciar sesión de entrenamiento")
            .setMessage("Entra manualmente en el campo de entrenamiento autorizado. OMEGA reconoce automáticamente la zona y busca controles en la pantalla. No necesitas marcar puntos ni seleccionar una captura. No inicia partidas ni compra objetos. Usa el menú flotante Iniciar/Pausar o regresa a esta aplicación para detener la sesión. La detección experimental puede fallar.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Iniciar"){_,_->
                GameBinding.beginTraining()
                if(RuntimeSession.start()&&FreeFireLauncher.open(this)&&TouchBridge.startObservation()){launchMessage=null}
                else{RuntimeSession.stop("start_failed");GameBinding.endTraining();launchMessage="No se pudo iniciar la sesión"}
                render()
            }.show()
    }
    @Deprecated("Activity result used without external AndroidX dependency")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=901)return
        awaitingConsent=false
        if(resultCode!=RESULT_OK || data==null || GameBinding.adapter==null || !RuntimeSession.status().active) { RuntimeSession.stop("consent_denied"); render(); return }
        try {
            // A real adapter must supply launch and scope attestation before enabling this path.
            startForegroundService(Intent(this,ProjectionService::class.java).putExtra("result",resultCode).putExtra("consent",data))
            GameBinding.adapter?.launch(this)
        } catch(e: Exception) { RuntimeSession.stop("launch_error") }
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("awaiting",awaitingConsent); super.onSaveInstanceState(outState) }
    override fun onResume(){super.onResume();TouchBridge.dismissPanel();if(GameBinding.training!=null&&RuntimeSession.status().active)RuntimeSession.stop("returned_to_omega");render()}
    override fun onStart() { super.onStart(); handler.post(tick) }
    override fun onStop() { handler.removeCallbacks(tick); if(!isChangingConfigurations && !awaitingConsent && !RuntimeSession.status().capturing && GameBinding.adapter==null) RuntimeSession.stop("left_app"); super.onStop() }
}
