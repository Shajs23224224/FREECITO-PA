# Inventario de dependencias

No se incluyen pesos ni bibliotecas de terceros vendorizadas. Gradle resolverá plugins fijados y dependencias transitivas durante la compilación.

| Componente | Versión / alcance | Procedencia |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Google Maven |
| Kotlin Android plugin | 2.0.21 | Gradle Plugin Portal / Maven |
| Gradle | 8.9 | CI setup-gradle |
| Android SDK | 35 / build-tools 34.0.0 | Google SDK Manager |
| NDK | 27.0.12077973 | Google SDK Manager |
| CMake Android | 3.22.1 | Google SDK Manager |
| JDK | 17 en CI | Temurin |
| SQLite, MediaProjection, UI | APIs de plataforma | Android SDK |
| STL C++ | NDK libc++ | Android NDK |

No se ha elegido licencia de distribución para el código. Las dependencias de percepción y aprendizaje del PDF quedan como candidatos.
