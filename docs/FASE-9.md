# OMEGA v0.9.0 — Meta-Cognition Engine

Se conserva la arquitectura acumulativa. MetaGovernor regula carga de forma heurística según heap Java, indicador lowMemory del sistema, estado térmico Android (API 29+), temperatura y porcentaje de batería, ahorro de energía, porcentaje de CPU del proceso relativo a un núcleo y latencia p95/tasa de fallos de las últimas 32 tareas cognitivas. ResourceProbe toma muestras como máximo cada dos segundos; PSS y RAM disponible son métricas informativas adicionales, no equivalen al heap. El estado térmico ausente se registra como desconocido; no se asume una temperatura de CPU en grados. El porcentaje de CPU puede superar 100% en sistemas multinúcleo. No se mide energía en W ni J: los indicadores de batería/ahorro son proxies. Fallo de lectura completo conduce a carga limitada.

| Perfil | Roles | Intervalo cognitivo mínimo | Cadencia visor | OCR | Profundidad | Nuevas órdenes / aprendizaje |
|---|---:|---:|---:|---|---|---|
| NORMAL | 4 | 1 s | base | sí | sí | condicional |
| ECONOMY | 2 | 2 s | mitad | sí | no | no |
| LIMITED | 1 | 3 s | tercio | no | no | no |
| STOP | 0 | sin ciclos | detenido por servicio | no | no | no |

La cadencia es objetivo, no FPS medidos; la captura sigue limitada a 640 píxeles por lado y se deciman fotogramas antes del núcleo. No se cambian prioridades de hilos del sistema ni se fuerzan GC. Al desactivar una ruta visual se descarta la observación y se invalida el resultado tardío; no se garantiza cancelar inmediatamente trabajo nativo ya iniciado ni liberar memoria del modelo hasta detener la sesión. Un comando ya enviado no se deshace; se mantiene la validación de recibo/expiración. Reducción inmediata, recuperación escalonada con tres muestras favorables consecutivas; STOP queda enclavado hasta cerrar sesión. Umbrales heurísticos: heap >=70/83/94%, estado térmico >=2/3/4, temperatura batería >=43/47 °C para limitado/detención, batería <=15% sin cargar, ahorro de energía, CPU proceso >=180% de un núcleo y latencia/fallos elevados. No son límites certificados del dispositivo.

La política contextual se suspende sin borrar pesos cuando el perfil no permite aprendizaje o si hay un checkpoint deshabilitado o al menos cuatro rollbacks y más rollbacks que actualizaciones aceptadas. Se conserva selección heurística; no se afirma aprendizaje estable en partidas. Se incorporan tres entradas de evidencia metacognitiva (latencia, tasa de fallo y salud de política) y se ajusta presupuesto del Blackboard. Roles todavía usan evaluador común. No se añade entrenamiento, simulador ni minijuego. El GameBinding sigue sin adaptador concreto. No se modifica el perfil FP32 del núcleo porque la distribución de embeddings F02 afecta a los checkpoints y requiere calibración con datos reales. Se conserva el modelo de profundidad ya cuantizado.

## Pruebas ejecutadas localmente

MetaSmoke (Kotlin/JVM): perfiles, histéresis, detención enclavada, CPU, temperatura, batería, memoria, p95, fallos y salud de política. PolicyPauseSmoke: suspensión congela ajustes y prioridad aprendida, reanudación conserva pesos. Regresión ExecutiveSmoke y LearningSmoke aprobada. Benchmark NeuralBench: inferencia real del núcleo factorizado con 32 mediciones por presupuesto tras 20 calentamientos. Linux amd64, JVM 21.0.12.1, 4 procesadores lógicos disponibles:

| Módulos activados | Media ms | p95 ms |
|---:|---:|---:|
| 5 | 4,51 | 6,65 |
| 8 | 6,20 | 7,20 |
| 14 | 10,25 | 11,76 |

Es una medición del método NeuralEngine.infer con datos de prueba, no de todo Blackboard, visor, aplicación Android ni partida. No se usan los datos para entrenamiento. El entorno móvil, latencia integrada, memoria nativa, temperatura y consumo físico siguen sin verificar. CI ejecuta pruebas y benchmark y empaqueta registro junto con APK si compila. Fase 7 CI 45903090511 aprobada; fase 8 CI 45903480982 aún compilando durante desarrollo de esta fase. 
