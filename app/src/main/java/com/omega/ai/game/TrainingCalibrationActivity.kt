package com.omega.ai.game

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MotionEvent
import android.widget.*
import android.graphics.Color
import com.omega.core.executive.*
import kotlin.math.*

/** Calibrates from a user-selected real training screenshot; only normalized points are saved. */
class TrainingCalibrationActivity:Activity(){
    private lateinit var image:ImageView
    private lateinit var info:TextView
    private lateinit var marker:EditText
    private lateinit var save:Button
    private var bmp:Bitmap?=null
    private val points=mutableListOf<HudPoint>()
    private val prompts=listOf("Esquina superior izquierda del texto exclusivo del entrenamiento","Esquina inferior derecha del texto","Centro del joystick","Posición de avance del joystick","Inicio de un arrastre de cámara en zona libre","Final del arrastre de cámara")
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,20,20,20)}
        val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
        info=TextView(this).apply{text="Selecciona una captura horizontal real del campo de entrenamiento. Debe mostrar una etiqueta de entrenamiento que siga visible durante la práctica.";textSize=16f}
        root.addView(info)
        root.addView(Button(this).apply{text="Elegir captura";setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),41)}})
        marker=EditText(this).apply{hint="Texto exacto y exclusivo de entrenamiento";isSingleLine=true};root.addView(marker)
        image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(Color.DKGRAY);contentDescription="Captura de calibración"}
        root.addView(image,LinearLayout.LayoutParams(-1,(300*resources.displayMetrics.density).toInt()))
        image.setOnTouchListener{_,event->
            if(event.action==MotionEvent.ACTION_UP){
                val b=bmp
                if(b!=null&&points.size<prompts.size){
                    val k=min(image.width.toFloat()/b.width,image.height.toFloat()/b.height)
                    val w=b.width*k;val h=b.height*k
                    val x=(event.x-(image.width-w)/2)/w;val y=(event.y-(image.height-h)/2)/h
                    if(x in 0f..1f&&y in 0f..1f){points+=HudPoint(x,y);update()}
                }
                image.performClick()
            }
            true
        }
        root.addView(Button(this).apply{text="Repetir puntos";setOnClickListener{points.clear();update()}})
        save=Button(this).apply{text="Guardar calibración";isEnabled=false;setOnClickListener{persist()}};root.addView(save)
    }
    private fun update(){
        info.text=if(bmp==null)"Selecciona una captura horizontal real del entrenamiento" else if(points.size<prompts.size)"Paso "+(points.size+1)+"/"+prompts.size+": "+prompts[points.size] else "Comprueba el texto exacto y guarda los seis puntos."
        save.isEnabled=points.size==prompts.size
    }
    private fun persist(){
        try{
            val b=requireNotNull(bmp);val a=points[0];val z=points[1]
            val profile=TrainingProfile(marker.text.toString().trim(),HudRegion(min(a.x,z.x),min(a.y,z.y),max(a.x,z.x),max(a.y,z.y)),points[2],points[3],points[4],points[5],b.width.toFloat()/b.height)
            profile.save(this);setResult(RESULT_OK);finish()
        }catch(e:Exception){Toast.makeText(this,"Calibración inválida: comprueba etiqueta y puntos",Toast.LENGTH_LONG).show()}
    }
    @Deprecated("Platform activity result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=41||resultCode!=RESULT_OK)return
        try{
            val uri=requireNotNull(data?.data)
            val opt=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,opt)}
            require(opt.outWidth>opt.outHeight&&opt.outHeight>0)
            var sample=1
            while(max(opt.outWidth,opt.outHeight)/sample>2048)sample*=2
            val next=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("imagen")
            image.setImageBitmap(next);bmp?.recycle();bmp=next;points.clear();update()
        }catch(e:Exception){Toast.makeText(this,"No se pudo leer una captura horizontal",Toast.LENGTH_LONG).show()}
    }
    override fun onDestroy(){image.setImageDrawable(null);bmp?.recycle();bmp=null;super.onDestroy()}
}
