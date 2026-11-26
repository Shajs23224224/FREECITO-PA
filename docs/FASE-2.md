# OMEGA Vision Engine — v0.2.0 (candidato)

## Alcance y resultados verificables

Esta fase añade un módulo Android independiente `vision` al proyecto de fase 1. Conserva Kotlin/JNI/C++, SQLite, permisos, interfaz iniciar/detener y el manifiesto de 858 redes objetivo / 160 agentes. No añade entrenamiento, juego, simulador, automatización de entradas ni control de otros procesos.

| Componente | Implementación | Verificación |
|---|---|---|
| Detector | MediaPipe Tasks, EfficientDet-Lite0 COCO, 32 detecciones máximo | Adaptador escrito; modelo y Android pendientes |
| OCR | ML Kit Text Recognition Latin bundled | Adaptador escrito; Android pendiente |
| Profundidad | Depth Anything V2 Small cuantizado, ONNX Runtime Android | Adaptador escrito; forma y ejecución reales pendientes |
| Seguimiento | IoU y clase, expiración, observaciones visibles | JVM aprobado |
| Transporte | RGBA con padding/posición, validación y ARGB | JVM aprobado |
| Profundidad | Normalización CHW RGB y reducción 8x8, rechazo NaN/constantes | JVM aprobado |
| Concurrencia | Tres trabajadores, descarte si ocupado, cadencia y cierre | Implementación; pruebas Android pendientes |
| Regresión fase 1 | C++17, JNI y manifiesto | CTest 1/1, JVM JNI y manifiesto aprobados |

El detector generalista no está entrenado para personajes, armas o interfaces de un juego concreto. No clasifica personas como enemigos; el significado de objetos e indicadores necesita una ontología validada del juego. La profundidad es relativa, no una medición en metros, ni un mapa tridimensional completo; el reescalado fijo a 518x518 puede distorsionar la perspectiva. Los identificadores del seguimiento son locales, pueden cambiar con oclusión o resolución y no se predicen objetos fuera de pantalla. OCR reconoce texto latino; no se interpreta de manera fiable un HUD específico aún.

## Arquitectura de datos

Frame RGBA autorizado → validación de buffer y dimensiones → núcleo nativo existente + conversión ARGB → tres rutas de inferencia (detección/IoU, OCR, profundidad relativa) → observaciones temporales con timestamp, límites y expiración. Las rutas no acumulan fotogramas; cuando están ocupadas descartan la nueva solicitud. Cadencias máximas objetivo: detector 5 Hz, OCR cada 1,5 s, profundidad cada 5 s; no representan rendimiento medido. Los resultados permanecen en memoria de la sesión. No se guardan capturas ni texto. No se transmiten fotogramas por código de OMEGA; las políticas de los SDK externos deberán auditarse antes de distribución.

GameBinding sigue sin adaptador; la captura no comienza hasta una integración expresamente autorizada que confirme alcance. La fase 2 no tiene política de juego ni aprendizaje por refuerzo. Las 858 redes del proyecto siguen sin materializarse. Modelos auxiliares no se cuentan como redes principales.

## Dependencias y modelos

- MediaPipe Tasks Vision 0.10.14 — https://github.com/google-ai-edge/mediapipe
- EfficientDet-Lite0 float32 v1 — https://developers.google.com/edge/mediapipe/solutions/vision/object_detector
- ML Kit OCR 16.0.1, modelo latino incluido — https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- ONNX Runtime Android 1.20.0 — https://onnxruntime.ai/docs/get-started/with-mobile.html
- Depth Anything V2 Small ONNX cuantizado (Apache 2.0 según ficha de distribución) — https://huggingface.co/onnx-community/depth-anything-v2-small/blob/main/onnx/model_quantized.onnx
- Preprocesamiento — https://huggingface.co/onnx-community/depth-anything-v2-small/blob/main/preprocessor_config.json

Los modelos EfficientDet y Depth se descargan al preparar la compilación. El primero tiene URL versionada y validación básica del formato TFLite; se registra su SHA256 calculado, no es una validación criptográfica contra un hash independiente. El segundo tiene SHA256 esperado publicado en la ficha. No hay pesos binarios añadidos al repositorio. Revisar licencias de pesos y términos de SDK antes de redistribuir. YOLOX, ByteTrack, PaddleOCR y MobileSAM quedan como candidatos; se usa una ruta menor para facilitar la primera integración.

## Reproducción

```sh
bash scripts/test-host.sh
bash scripts/test-vision-host.sh
bash scripts/fetch-vision-models.sh
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

CI se configura para comprobar las regresiones y descargar los pesos antes de compilar y firmar el APK de depuración. El entorno local no tiene SDK/NDK/Gradle Android ni acceso de red a los modelos; no se afirma que el APK se haya compilado ni que las inferencias hayan sido ejecutadas en Android. La fase 1 mantiene pendiente esa verificación. La próxima fase debe partir de una compilación Android verificada, ejecutar una prueba real de modelos y medir tiempos/memoria en dispositivo compatible.

## Evidencia local

- CTest native_engine 1/1 aprobado.
- JNI Kotlin: PASS (buffers, ciclo de vida, arquitectura).
- Manifiesto: PASS.
- VisionSmoke: PASS (RGBA con padding, límites, ID y expiración, ausencia de tracks ocultos, tiempo monótono, normalización y profundidad relativa).
