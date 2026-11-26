# OMEGA v0.5.0 — Fase 5 (candidato)

Infraestructura Android y núcleo nativo para la futura IA agéntica de videojuegos Shooter 3D autorizados. No contiene modelos entrenados ni política autónoma de juego. El código nativo y JNI se han probado en Linux; la compilación y las pruebas de Android requieren verificación.

## Función de esta versión

La pantalla tiene una sola función: iniciar/detener el núcleo. Iniciar crea una sesión nativa, un trabajador C++ y un registro SQLite. Detener cancela el trabajo y guarda estadísticas. No hay entrenador, simulador, minijuego, macros, inyección de entradas, selección de juegos ni pagos.

La captura MediaProjection está implementada pero desactivada: GameBinding.adapter es null hasta identificar un juego y una interfaz autorizada. No se inicia captura ni se abre otro juego por defecto. La captura exige consentimiento y un adaptador que confirme su alcance.

## Compilación

JDK 17, Gradle 8.9, Android SDK 35, build-tools 34.0.0, NDK 27.0.12077973, CMake 3.22.1. Android Gradle Plugin 8.7.3 y Kotlin 2.0.21. minSdk 26, targetSdk 35, ABI arm64-v8a inicialmente. No se incluye Gradle Wrapper JAR.

```sh
sdkmanager 'platforms;android-35' 'build-tools;34.0.0' 'ndk;27.0.12077973' 'cmake;3.22.1'
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

El APK de depuración, si se compila, queda en app/build/outputs/apk/debug/app-debug.apk. El workflow de GitHub Actions publica el APK y su SHA-256 al superar las comprobaciones.

## Pruebas del núcleo

En Linux con CMake, C++17, JDK, Kotlin CLI y Python 3:

```sh
bash scripts/test-host.sh
```

Los pequeños buffers RGBA de prueba son exclusivamente comprobaciones de transporte de datos; no constituyen experiencias de entrenamiento ni un videojuego.

## Estructura

- app: actividad Android, servicio MediaProjection, persistencia y enlace autorizado.
- core-native: biblioteca C++, JNI, contratos y envoltorio Kotlin.
- config: manifiesto de arquitectura objetivo.
- tests y scripts: pruebas y validación de requisitos.
- .github/workflows: compilación automatizada.
- docs: hoja de ruta, resultados y dependencias.

La especificación PDF y el ZIP original se entregaron aparte; el conector GitHub utilizado permite escritura de texto, no carga directa de esos binarios. No se afirma instalación en Android ni funcionamiento de la IA en partidas.

## Fase 2: visión

El módulo `vision` implementa inferencia local con tres rutas: EfficientDet-Lite0, ML Kit OCR y Depth Anything V2 Small ONNX. Ejecuta `bash scripts/fetch-vision-models.sh` antes de compilar; CI realiza ese paso automáticamente. Las dependencias y pesos no fueron descargados en el entorno local sin acceso a red. No hay prueba Android ni validación en partida. Seguimiento IoU y preprocesamiento sí fueron probados en JVM. No se ha activado el adaptador de juego. Consulte `docs/FASE-2.md`.

## Fase 3: núcleo neuronal factorizado

858 módulos ejecutables con 16 capas de 2048 unidades, rango 4 compartido, parámetros FP32 inicializados aleatoriamente y adaptadores independientes. No son redes densas independientes ni modelos especializados entrenados. No hay política de juego ni aprendizaje por refuerzo. Prueba local: `bash scripts/test-neural-host.sh`. Véase `docs/FASE-3.md`.

## Fase 4: tablero cognitivo

160 roles ejecutables con el registro de roles del PDF, 16 equipos, asociaciones a los 824 módulos principales, rotación de módulos, validación de evidencia temporal y conflictos, propuestas de observación, memoria de trabajo de 32 rondas y fusión intermedia/final. Los roles comparten un evaluador general; la especialización semántica de cada dominio sigue pendiente. Se conectan ocho indicadores perceptivos; los demás roles requieren datos de fases futuras. Las salidas neuronales no entrenadas no determinan confianza ni acciones. Prueba local: `bash scripts/test-agents-host.sh`. Véase `docs/FASE-4.md`.

## Fase 5: modelo del mundo y memorias

WorldModel 2.5D relativo a la cámara, relaciones entre entidades visibles, hipótesis de lugares por firma, aristas temporales, memoria SQLite transaccional y recuperación contextual. Ocho categorías persistibles; cinco reciben observaciones automáticamente, tres esperan habilidades, estrategias y objetivos reales. El adaptador autorizado debe proporcionar ámbito de juego y versión. Prueba local JVM: `bash scripts/test-world-host.sh`; SQLite Android y validación en partida pendientes. Véase `docs/FASE-5.md`.
