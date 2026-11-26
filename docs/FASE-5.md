# OMEGA 0.5.0 — World Model y memoria

El núcleo WorldModel representa objetos visibles mediante centro normalizado x/y y profundidad relativa z de una rejilla 8x8, únicamente cuando ambas modalidades provienen del mismo fotograma. No representa coordenadas métricas, geometría oculta, orientación global ni una malla 3D reconstruida. La confianza de detección es un estimador heurístico de creencia, no una probabilidad calibrada ni un filtro bayesiano. Se calculan relaciones izquierda/derecha, arriba/abajo y diferencia de valor de profundidad relativa. No se presupone que un valor alto de profundidad signifique metros o distancia real.

Las escenas con al menos dos firmas de categoría/posición se comparan por Jaccard con hasta 256 registros espaciales; umbral experimental 0,75. Coincidencias son hipótesis de lugares, no localización verificada. Aristas representan sucesión temporal de observaciones, nunca una ruta transitable demostrada. El modelo no devuelve entidades fuera de pantalla ni inventa recursos o adversarios.

## Memoria

Ocho categorías persistibles: inmediata, trabajo, episódica, semántica, procedimental, espacial, estratégica, prospectiva. Un SQLite privado, versionado, transaccional, con 256 registros por categoría/ámbito y 4096 globales. Claves únicas y rechazo de reemplazos más antiguos. La recuperación contextual usa coincidencias léxicas, confianza y recencia, no embeddings entrenados. Los registros inmediatos y de trabajo se reemplazan por su último valor. Se consolidan resúmenes de observaciones, categorías detectadas y firmas de escenas; no píxeles, OCR bruto, chats, ni información oculta. Las memorias procedimental, estratégica y prospectiva tienen contrato y almacenamiento pero esperan experiencias, planes y objetivos reales de fases posteriores. Se exige un ámbito explícito de juego y versión a la futura integración; no se mezclan títulos.

## Integración

El trabajador neuronal procesa observaciones nuevas; no acumula cola. WorldModel consume únicamente una detección nueva, usa profundidad sincronizada cuando existe y añade características de escena y memoria al vector de 2048 unidades y al Blackboard existente. No se activan los 858 módulos simultáneamente. Los 160 roles conservan el evaluador común; algunos reciben nuevas señales, no son agentes plenamente especializados. La captura sigue bloqueada hasta un adaptador autorizado. No hay actualización de pesos durante esta fase ni política de juego. No se añade entrenador, simulador ni minijuego.

## Pruebas y CI

WorldSmoke ejecutado localmente en Kotlin/JVM con almacén en memoria de prueba: ocho categorías, recuperación y aislamiento por ámbito, relaciones, profundidad ausente, reconocimiento de firma y transiciones, no invención de habilidades, rechazo de fotogramas repetidos. La reapertura lógica usa el mismo almacén de prueba; no demuestra persistencia SQLite en Android. Se incorpora la prueba al CI. El APK de fase 4 (run 45893286776, artefacto 15152826560) superó compilación, lint, firma y publicación. No hubo errores de compilación de fase 4 por corregir. El APK 0.5.0, prueba SQLite en dispositivo, percepción y rendimiento reales en partida quedan pendientes de validación.

Prueba host: bash scripts/test-world-host.sh
