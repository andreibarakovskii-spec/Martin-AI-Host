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
        versionCode = 91
        versionName = "0.9.1-diagnostics"
    }
    sourceSets.getByName("main") {
        assets.exclude("martin*")
        res.exclude("drawable-nodpi/*.jpg")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.gitignore:!.ds_store:!*.scc:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
}

dependencies {
    implementation("audio.soniqo:speech:0.0.17")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.fragment:fragment:1.8.9")
    testImplementation("junit:junit:4.13.2")

    // Front-camera face tracking. Camera frames are processed locally and never stored.
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
}
