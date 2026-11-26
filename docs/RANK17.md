# OMEGA v1.0.4 — ampliación de parámetros

Base v1.0.3: 2.074.240 pesos del núcleo + 88 de la política = 2.074.328. Objetivo +50% = 3.111.492. Implementación: rango compartido de 4 a 17 y ocho compuertas residuales independientes por módulo, una por cada dos capas. Las compuertas multiplican la ruta residual x antes de tanh. Las matrices de subida se inicializan con escala ajustada al rango. Todos los coeficientes forman parte de inferencia; no hay parámetros de relleno.

| Grupo | Conteo |
|---|---:|
| Bases compartidas, 2 x 16 x 2048 x 17 | 1.114.112 |
| Ganancias independientes, 858 x 16 x 17 | 233.376 |
| Sesgos de salida independientes, 858 x 2048 | 1.757.184 |
| Compuertas residuales, 858 x 8 | 6.864 |
| Política adaptativa | 88 |
| Total | 3.111.624 |

Aumento real: 1.037.296 parámetros (+50,0063635%), 132 sobre objetivo. 12.446.496 bytes FP32 de pesos, no memoria total de ejecución. Se mantienen 858 módulos, 16 capas, ancho 2048, 160 roles y ocho memorias. Se mantienen los 72 parámetros de política habilitados para aprendizaje online. El núcleo sigue inicializado sin entrenar; no se amplía el mecanismo de entrenamiento de pesos. La nueva representación F02 no es compatible con la anterior: se conserva el checkpoint previo en su ruta original y se inicia una política en una ruta específica de la arquitectura nueva; no se borra memoria. Se conserva el adaptador de juego sin configurar; no se habilita control público.

Prueba JVM local: inferencia de los 858 módulos, conteo desde tamaños de matrices reales, rutas, aislamiento y restauración de sesgo, rechazo de NaN. PASS. No es un benchmark Android ni una prueba de entrenamiento. CI ejecuta regresiones y genera APK si supera las comprobaciones.
