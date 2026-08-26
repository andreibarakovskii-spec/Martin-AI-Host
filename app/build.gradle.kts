plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.imagine.martinhost"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.imagine.martinhost.fixed"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0-fixed"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("audio.soniqo:speech:0.0.17")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
