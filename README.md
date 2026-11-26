# OMEGA v0.1.0 — Fase 1

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
