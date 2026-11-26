plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.omega.vision"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("tflite","onnx") }
}
dependencies {
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
}
