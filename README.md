# OMEGA v1.0.4 — núcleo integrado (candidato técnico)

Infraestructura Android y núcleo nativo para la futura IA agéntica de videojuegos Shooter 3D autorizados. Incluye adaptadores de modelos perceptivos preentrenados y un núcleo factorizado no entrenado. No hay política de juego entrenada ni adaptador real conectado. El código nativo y JNI se han probado en Linux; la compilación y las pruebas de Android requieren verificación.

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

## Fase 6: razonamiento cognitivo

Razonador simbólico de cuatro valores, deducción acíclica, estimación beta, ranking abductivo, asociación estadística (no identificación causal), análisis hipotético y planificación de observaciones. No ejecuta acciones ni entrena pesos. Prueba local JVM: `bash scripts/test-reason-host.sh`. Véase `docs/FASE-6.md`.

## Fase 7: ejecutivo autónomo

Ciclo de selección de ofertas explícitamente autorizadas, recursos virtuales, validación de frescura, un comando pendiente y recibos. Contrato GamePort documentado, sin adaptador concreto; no toca otras apps ni ejecuta acciones por pantalla. Prueba JVM: `bash scripts/test-executive-host.sh`. Consulte `docs/FASE-7.md` y `docs/GAME-PORT.md`.

## Fase 8: aprendizaje incremental

Bandido contextual de 48 pesos (32 habilitados) sobre inventario y características F02 congeladas. Solo aprende de recibos confirmados del GamePort autorizado; no se han recibido experiencias reales. Checkpoint por juego/versión, SHA-256, reemplazo atómico, replay limitado y rechazo de actualizaciones degradantes. No hay PPO, SAC ni entrenamiento aparte. Véase `docs/FASE-8.md`.

## Fase 9: metacognición

Supervisor con cuatro perfiles de carga, latencia p95, tasa de fallos, telemetría pública Android, presupuesto dinámico, suspensión de aprendizaje y detención crítica. Pruebas y benchmark JVM: `bash scripts/test-meta-host.sh`. Métricas de teléfono, energía física y ejecución real pendientes. Véase `docs/FASE-9.md`.

## Fase 10: integración y empaquetado

Candidato técnico del núcleo integrado, no producto capaz de jugar por sí mismo. Comprobación explícita de conexión, autorización y frescura; prueba conjunta de inferencia, supervisor, ejecutivo y checkpoint. CI verifica APK de depuración, metadatos, modelos, JNI, SHA256 y archivo de fuentes exacto. Consulte `docs/FASE-10.md` y `docs/GAME-PORT.md`. Sin entrenador ni simulador.

## Free Fire (v1.0.1)

Apertura del cliente Android mediante el único botón cuando no existe adaptador. No se inicia captura ni aprendizaje por esta ruta. Véase `docs/FREE-FIRE.md`.

## Puente de accesibilidad (v1.0.2)

Transporte táctil Android experimental acotado a un adaptador de prueba privado y atestado. No habilitado en el cliente público. Véase `docs/ACCESSIBILITY.md`.

## Política táctica experimental

Contexto virtual confirmado por GamePort, cinco familias nuevas, política inicial y aprendizaje incremental con 88 pesos. No se conecta el cliente público. Véase `docs/TACTICAL.md`.

## Ampliación neuronal v1.0.4

Rango 17, ocho compuertas residuales independientes por módulo, 3.111.536 parámetros del núcleo más 88 de la política. Total 3.111.624 (+50,006 %). Ver `docs/RANK17.md`.

## Diagnóstico de accesibilidad v1.0.5

Enlace opcional a Ajustes con explicación de permisos y estados habilitado/conectado/armado separados. Sin adaptador, los gestos permanecen inactivos. Véase `docs/ACCESSIBILITY-UI.md`.

## Entrenamiento real experimental v1.0.6

Adaptador Free Fire con calibración visual de una captura elegida por el usuario, dos lecturas OCR de etiqueta exclusiva, captura local Android 11+ y gestos de movimiento/cámara. No está validado en un teléfono ni completa combate. Consulte `docs/TRAINING-LIVE.md`.

## HUD automático v1.0.7

Sin perfil ni calibración manual. Detección geométrica, OCR y seguimiento automático de candidatos; movimiento experimental, no validado en cliente físico. Véase `docs/AUTO-HUD.md`.
