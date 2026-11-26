plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.omega.ai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.omega.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.9.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    androidResources { noCompress += listOf("tflite","onnx") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core-native"))
    implementation(project(":vision"))
}
