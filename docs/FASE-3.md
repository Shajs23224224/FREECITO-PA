# Fase 3 — OMEGA Neural Engine v0.3.0

## Implementación y límites

Se materializan 858 módulos factorizados ejecutables: P001–P824, I01–I32 y F01–F02. Cada módulo realiza 16 transformaciones residuales no lineales de dimensión 2048 mediante matrices de rango 4 compartidas. Cada módulo tiene 64 ganancias y 2048 sesgos propios. La entrada y salida son vectores de 2048. El módulo Kotlin/JVM se integra con la sesión Android; recibe características perceptivas mínimas, no acciones. Se ejecuta como máximo una tarea neuronal a la vez, con frecuencia objetivo de hasta 1 Hz y sin cola acumulativa.

| Concepto | Cantidad |
|---|---:|
| Módulos comprimidos ejecutables | 858 |
| Redes densas independientes | 0 |
| Redes entrenadas | 0 |
| Unidades ocultas lógicas | 28.114.944 |
| Parámetros compartidos | 262.144 |
| Parámetros de adaptadores independientes | 1.812.096 |
| Parámetros únicos totales | 2.074.240 |
| Sesgos con API de actualización | 1.757.184 |
| Parámetros FP32, bytes de datos | 8.296.960 |
| Módulos activos con dos dominios | 8 |
| Máximo con cuatro dominios | 14 |

El perfil denso de 53.980.692.480 pesos entre capas ocultas se conserva como referencia, no implementado. No se ha hecho cuantización ni medido RAM Android; el tamaño indicado excluye objetos, activaciones y modelos visuales. La selección por dominio y características es determinista, no aprendida. No se afirma especialización, razonamiento ni capacidad de combate. Las dos salidas finales son embeddings sin semántica de acciones. Las bases se inicializan aleatoriamente de forma reproducible; no se confunden con los modelos visuales preentrenados. La API de gradiente suministrado actualiza únicamente el sesgo de salida y permite restaurarlo en memoria, sin optimizador RL, persistencia de pesos ni entrenamiento en la app. Es una infraestructura para las fases 4–8.

## Verificación

Se ejecutó el test Kotlin/JVM local de todos los 858 módulos, comprobando dimensiones, finitud, conteo, aislamiento de adaptadores, actualización/rollback, enrutamiento acotado y rechazo NaN. No se ejecutó entrenamiento: los datos sintéticos son pruebas unitarias. Android sigue pendiente.

El run 45892451933 de fase 2 falló en setup-android v3: sdkmanager intentó instalar el paquete obsoleto 'tools'. Se cambia a sdkmanager del runner e instalación de versiones específicas. Se ajusta el contrato RectF del detector; OCR convierte Rect a RectF. Se mantiene la descarga de modelos de fase 2 y el flujo de build/artefacto. Se añade prueba neuronal a CI. No se declara APK generado hasta verificar artefactos.

El adaptador autorizado sigue sin configurar; no hay captura de otros juegos ni acciones. No se añade entrenador, simulador ni minijuego. Los 160 agentes se implementarán en fase 4. El perfil móvil comprimido es una decisión de ingeniería para respetar memoria; no equivale a 858 redes densas independientes.

Prueba local: bash scripts/test-neural-host.sh
