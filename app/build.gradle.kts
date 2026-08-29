import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Official multi-ABI Android package with statically linked ONNX Runtime.
val sherpaAar = layout.buildDirectory.file("verified-deps/sherpa-onnx-1.13.2.aar")
val fetchSherpa = tasks.register("fetchSherpa") {
    val checksum = "9b2a290b8c7f31bd0aba35abb4628e87fe8d0eb71796a98aa12f3acd089ceaed"
    inputs.property("sha256", checksum)
    outputs.file(sherpaAar)
    doLast {
        val out = sherpaAar.get().asFile
        out.parentFile.mkdirs()
        val tmp = File(out.parentFile, out.name + ".part")
        try {
            val connection = URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-static-link-onnxruntime-1.13.2.aar").toURL().openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.getInputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == checksum) { "Sherpa AAR checksum mismatch" }
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { tmp.delete() }
    }
}

android {
    namespace = "com.imagine.martinhost"
    compileSdk = 36
    defaultConfig {
        applicationId = if (providers.gradleProperty("diagnosticCopy").orNull == "true") "com.imagine.martinhost.diagnostics" else "com.imagine.martinhost.fixed"
        manifestPlaceholders["appLabel"] = if (providers.gradleProperty("diagnosticCopy").orNull == "true") "Сергей — тест диалога" else "Сергей AI Ведущий"
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 96
        versionName = "0.9.6-sergey-yandex"
    }
    System.getenv("MARTIN_DEBUG_KEYSTORE")?.let { keyPath ->
        signingConfigs.getByName("debug") { storeFile = file(keyPath) }
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
    implementation(files(sherpaAar).builtBy(fetchSherpa))
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.fragment:fragment:1.8.9")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // Front-camera face tracking. Camera frames are processed locally and never stored.
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
}
