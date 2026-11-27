import com.omega.core.executive.*
fun main(){
    val r=HudRegion(.1f,.1f,.5f,.3f)
    check(TrainingObservation.markerPresent("Campo de entrenamiento",r,listOf(HudLine("CAMPO DE ENTRENAMIENTO",.2f,.2f))))
    check(!TrainingObservation.markerPresent("Campo de entrenamiento",r,listOf(HudLine("CAMPO DE ENTRENAMIENTO",.8f,.2f))))
    check(!TrainingObservation.markerPresent("Campo de entrenamiento",r,listOf(HudLine("Entrenamiento",.2f,.2f))))
    check(!TrainingObservation.validMarker("Inicio"))
    check(TrainingObservation.difference(FloatArray(64),FloatArray(64){.1f})!!>.045f)
    check(TrainingObservation.difference(FloatArray(64),FloatArray(64))==0f)
    check(TrainingObservation.difference(FloatArray(64){Float.NaN},FloatArray(64))==null)
    check(TrainingObservation.automaticMarker(listOf(HudLine("CAMPO DE TIRO",.8f,.8f)))=="campo de tiro")
    check(TrainingObservation.automaticMarker(listOf(HudLine("Entrenamiento",.2f,.2f)))==null)
    check(TrainingObservation.automaticMarker(listOf(HudLine("Campo de tiro",.1f,.1f),HudLine("Zona de combate",.2f,.2f)))==null)
    println("PASS: exact calibrated training label, ROI, no generic marker, visual change proxy")
}
