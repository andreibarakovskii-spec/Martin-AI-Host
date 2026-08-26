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
        versionCode = 7
        versionName = "0.7.0-production-3d"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources {
        // Godot keeps imported runtime metadata in the hidden .godot asset folder.
        ignoreAssetsPattern = "!.svn:!.git:!.gitignore:!.ds_store:!*.scc:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
}

dependencies {
    implementation("audio.soniqo:speech:0.0.17")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.godotengine:godot:4.7.2.stable")
    implementation("androidx.fragment:fragment:1.8.9")
}
