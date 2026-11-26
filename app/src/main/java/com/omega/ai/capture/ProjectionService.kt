package com.omega.ai.capture

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import com.omega.ai.MainActivity
import com.omega.ai.R
import com.omega.ai.RuntimeSession
import com.omega.ai.game.GameBinding
import kotlin.math.max
import kotlin.math.roundToInt

/** MediaProjection infrastructure; dormant until a real authorized adapter can attest capture scope. */
class ProjectionService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var closing=false
    private var density=0
    private var width=0; private var height=0
    private val scopeGuard = object : Runnable { override fun run() {
        if(!GameBinding.canCapture()) shutdown("scope_lost") else if(!RuntimeSession.resourceHealthy()) shutdown("resource_stop") else main.postDelayed(this,250)
    } }
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { shutdown("projection_revoked") }
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) { if(!isVisible) shutdown("content_not_visible") }
        override fun onCapturedContentResize(w: Int,h: Int) { if(!closing && display!=null && w>0 && h>0) runCatching { resize(w,h) }.onFailure { shutdown("resize_error") } }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int): Int {
        if(intent?.action=="STOP") { shutdown("user_stop"); return START_NOT_STICKY }
        if(projection!=null) return START_NOT_STICKY
        try {
            val nm=getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("omega_capture","OMEGA · sesión",NotificationManager.IMPORTANCE_LOW))
            val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop=PendingIntent.getService(this,2,Intent(this,ProjectionService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification=Notification.Builder(this,"omega_capture").setContentTitle("OMEGA · observación activa").setContentText("Pulsa Detener para cerrar la sesión").setSmallIcon(R.drawable.ic_omega).setOngoing(true).setContentIntent(open).addAction(Notification.Action.Builder(null,"Detener",stop).build()).build()
            if(Build.VERSION.SDK_INT>=29) startForeground(101,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(101,notification)
            if(!GameBinding.canCapture() || !RuntimeSession.status().active) { shutdown("scope_unavailable"); return START_NOT_STICKY }
            @Suppress("DEPRECATION") val consent=requireNotNull(intent).getParcelableExtra<Intent>("consent") ?: error("Missing consent")
            val result=requireNotNull(intent).getIntExtra("result",Activity.RESULT_CANCELED)
            check(result==Activity.RESULT_OK)
            val mgr=getSystemService(MediaProjectionManager::class.java)
            projection=mgr.getMediaProjection(result,consent) ?: error("Projection unavailable")
            projection!!.registerCallback(callback,main)
            thread=HandlerThread("omega-capture").apply { start() }; worker=Handler(thread!!.looper)
            val metrics=resources.displayMetrics; density=metrics.densityDpi
            val wm=getSystemService(WindowManager::class.java)
            val (w,h)=if(Build.VERSION.SDK_INT>=30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() } else metrics.widthPixels to metrics.heightPixels
            val (sw,sh)=bounded(w,h); width=sw; height=sh
            reader=createReader(sw,sh)
            check(RuntimeSession.beginCapture())
            display=projection!!.createVirtualDisplay("OMEGA",sw,sh,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,main) ?: error("Virtual display unavailable")
            main.post(scopeGuard)
        } catch(e: Exception) { android.util.Log.e("OMEGA","projection",e); shutdown("projection_error") }
        return START_NOT_STICKY
    }
    private fun bounded(w:Int,h:Int):Pair<Int,Int> { val scale=minOf(1.0,640.0/max(w,h)); return max(1,(w*scale).roundToInt()) to max(1,(h*scale).roundToInt()) }
    private fun createReader(w:Int,h:Int): ImageReader = ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2).also { r ->
        r.setOnImageAvailableListener({ src ->
            try {
                src.acquireLatestImage()?.use { image ->
                    if(!GameBinding.canCapture()) { main.post { shutdown("scope_lost") }; return@use }
                    val p=image.planes.firstOrNull() ?: return@use
                    RuntimeSession.submit(p.buffer,image.width,image.height,p.rowStride,p.pixelStride,image.timestamp)
                }
            } catch(e: Exception) { main.post { shutdown("frame_error") } }
        },worker)
    }
    private fun resize(w:Int,h:Int) {
        val (sw,sh)=bounded(w,h); if(sw==width && sh==height)return
        val next=createReader(sw,sh); val old=reader
        try { display?.resize(sw,sh,density); display?.setSurface(next.surface); reader=next; width=sw; height=sh }
        catch(e: Exception) { next.close(); throw e }
        old?.setOnImageAvailableListener(null,null); old?.close()
    }
    private fun shutdown(reason:String) {
        if(closing)return; closing=true; main.removeCallbacks(scopeGuard)
        RuntimeSession.stop(reason)
        runCatching { reader?.setOnImageAvailableListener(null,null) }
        runCatching { display?.release() }; display=null
        runCatching { reader?.close() }; reader=null
        runCatching { projection?.unregisterCallback(callback); projection?.stop() }; projection=null
        thread?.quitSafely(); thread=null; worker=null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { shutdown("service_destroyed"); super.onDestroy() }
}
