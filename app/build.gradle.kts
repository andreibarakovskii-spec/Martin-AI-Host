plugins { id("com.android.application") }

android {
    namespace = "com.imagine.martinhost"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.imagine.martinhost"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
