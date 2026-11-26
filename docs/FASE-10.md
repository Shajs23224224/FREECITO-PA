# OMEGA v1.0.0 — integración técnica y auditoría

**Clasificación:** candidato técnico del núcleo integrado, no una IA autónoma capaz de jugar un shooter comercial. No hay adaptador de juego configurado, autorización de un propietario ni partidas reales registradas. No se solicita captura ni se lanza un juego en la configuración publicada. La interfaz conserva un único control iniciar/detener el núcleo, muestra el estado y telemetría. No se añade entrenador, simulador, minijuego ni enseñanza externa.

## Inventario de componentes y límites

| Componente | Implementado | Pendiente |
|---|---|---|
| Infraestructura | Android Kotlin, C++/JNI, SQLite, MediaProjection | prueba física de permisos y ciclo de vida |
| Visión | adaptadores EfficientDet, OCR, profundidad ONNX, seguimiento IoU | inferencia en teléfono, precisión en título concreto |
| Núcleo neuronal | 824+32+2 módulos ejecutables, 16 capas x 2048, rango 4 compartido | entrenamiento y especialización reales |
| Agentes | 160 roles, 16 equipos, Blackboard, 30 ranuras de evidencia condicionales | especialistas individuales completos |
| Memoria | 8 categorías persistibles, SQLite, registros acotados | persistencia real Android y conocimientos en partidas |
| Mundo | modelo 2.5D, relaciones y firmas de lugares | geometría métrica, navegación validada |
| Razonamiento | reglas, estimación Beta, hipótesis, planes de observación | causalidad identificada y estrategia de juego aprendida |
| Ejecutivo | GamePort, selección de ofertas internas válidas, recibos | API propietaria autorizada, acciones reales |
| Aprendizaje | bandido contextual, 48 pesos, 32 elegibles, replay y checkpoint | experiencias reales, RL secuencial y entrenamiento profundo |
| Metacognición | perfiles dinámicos y telemetría Android | rendimiento integral, energía física, calibración móvil |

Parámetros únicos 2.074.288 (núcleo 2.074.240 + cabeza 48, excluye visión externa). 28.114.944 unidades ocultas lógicas; no 858 redes densas independientes. 0 redes principales entrenadas, 0 experiencias reales registradas, 0 especialistas completos. Compilación Android no equivale a funcionamiento en dispositivo.

## Integración y correcciones

SessionGate distingue conexión sin configurar, inválida, solo observación y contrato de comandos. No autoriza por sí misma; exige alcance atestado, permiso del GamePort, perfil de carga y observación visual con edad <=1,5 s. Se valida el alcance antes de captura y cada fotograma, y se vuelve a comprobar antes de enviar un comando o incorporar una recompensa. GameBinding.adapter permanece null; no se fuerza la conexión ni se sortean protecciones. La elección de órdenes sigue restringida a ofertas del propietario autorizadas y virtuales. Se reduce cálculo repetido de las medias de F02 a una vez por ciclo; no se cambia la arquitectura ni la precisión de pesos. Las pruebas de fase 9 conservan las mediciones de JVM, no son métricas Android.

## Pruebas y paquetes

ReleaseSmoke ejecutado localmente mediante Kotlin/JVM con el núcleo de fase 9 y las modificaciones de fase 10: validación de conexión, atestación, observaciones caducadas/futuras, carga adaptativa, inferencia de ocho módulos, recibo en doble de interfaz, actualización y reapertura de checkpoint, revocación antes del envío y parada. ExecutiveSmoke, LearningSmoke, PolicyPauseSmoke y MetaSmoke volvieron a pasar en el mismo JAR. Se incluye el registro. No se probaron partidas, Android físico ni un API propietario. La suite acumulativa de CI vuelve a ejecutar las pruebas C++/JNI, visión, neural, agentes, mundo, razonamiento, ejecutivo, aprendizaje, metacognición y release.

CI: Android 26+, target 35, arm64-v8a únicamente, APK debug firmado con clave de depuración. La verificación incluye apksigner, aapt, versión 1.0.0/código 10, ZIP CRC, librería JNI, presencia de modelos, hash del ONNX y SHA256 del APK, y el commit de origen. Se empaqueta el registro de benchmark y manifiesto; el archivo de fuentes se genera con git archive del commit construido, sin incluir pesos descargados ni JAR Gradle wrapper. Descargar pesos con scripts/fetch-vision-models.sh para reconstruir. No se utiliza clave de firma de producción ni se publica en Play Store. Se conservan las limitaciones de licencias y términos de SDK descritas en la fase 2. No se ha realizado auditoría exhaustiva de terceros ni prueba de compatibilidad en el teléfono del usuario.

Fase 9: build 45904003109 superó las pruebas host y descarga de modelos; consultar resultado final Android. Fase 10 requiere comprobar ejecución y artefacto del commit publicado antes de afirmar compilación exitosa. Rama candidata, no fusionada con main hasta revisión. El criterio de aceptación de autonomía real exige un juego y API autorizados concretos, pruebas en dispositivo y experiencias verificadas, no solo un APK.
