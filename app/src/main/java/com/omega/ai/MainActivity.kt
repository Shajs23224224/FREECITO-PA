package com.omega.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
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
        root.addView(label("v1.0.1 · Apertura del juego",12f,Color.LTGRAY).apply { setPadding(0,dp(16),0,0) })
        setContentView(root); render()
    }
    private fun dp(v: Int) = (v*resources.displayMetrics.density+0.5f).toInt()
    private fun label(t:String,s:Float,c:Int) = TextView(this).apply { text=t; textSize=s; setTextColor(c); gravity=Gravity.CENTER }
    private fun render() {
        var s=RuntimeSession.status()
        // Also show resource telemetry in the idle-core session, even without a game adapter.
        if(s.active && !s.capturing){
            if(!RuntimeSession.resourceHealthy())RuntimeSession.stop("resource_stop")
            s=RuntimeSession.status()
        }
        status.text = if(s.capturing) "${s.message}\nFotogramas procesados: ${s.stats?.processed ?: 0}" else s.message
        if (s.capturing) s.perception?.let { p ->
            status.append("\nVisor: ${p.detectorStatus} · OCR: ${p.ocrStatus} · profundidad: ${p.depthStatus}")
        }
        s.neuralStatus?.let { status.append("\n$it") }
        s.resources?.let { status.append("\n$it") }
        if(!s.active && GameBinding.connection()==Connection.UNCONFIGURED){
            status.text=launchMessage ?: if(FreeFireLauncher.available(this)) "Free Fire disponible · apertura desde OMEGA\nControl y aprendizaje en partida no habilitados" else "Free Fire no encontrado · instala el cliente oficial"
            button.text="ABRIR FREE FIRE"
        }else button.text=if(s.active) "DETENER IA" else "INICIAR IA"
        button.isEnabled=!awaitingConsent
    }
    private fun toggle() {
        if(!RuntimeSession.status().active && GameBinding.connection()==Connection.UNCONFIGURED){
            launchMessage=if(FreeFireLauncher.open(this)) null else "No se pudo abrir Free Fire. Comprueba que esté instalado desde Google Play."
            render();return
        }
        if(RuntimeSession.status().active) {
            stopService(Intent(this,ProjectionService::class.java)); RuntimeSession.stop("user_stop")
        } else if(RuntimeSession.start()) {
            if(GameBinding.adapter != null) {
                try {
                    awaitingConsent=true
                    val mgr=getSystemService(MediaProjectionManager::class.java)
                    @Suppress("DEPRECATION") startActivityForResult(mgr.createScreenCaptureIntent(), 901)
                } catch(e: Exception) { awaitingConsent=false; RuntimeSession.stop("consent_error") }
            }
        }
        render()
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
    override fun onStart() { super.onStart(); handler.post(tick) }
    override fun onStop() { handler.removeCallbacks(tick); if(!isChangingConfigurations && !awaitingConsent && !RuntimeSession.status().capturing && GameBinding.adapter==null) RuntimeSession.stop("left_app"); super.onStop() }
}
