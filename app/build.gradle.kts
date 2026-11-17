plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wifizone.mikhmon"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.wifizone.mikhmon"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
        // arm64-v8a : téléphones ; x86_64 : émulateur de test en CI
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // libphp.so est un exécutable : il doit être extrait sur disque pour pouvoir être lancé
    packaging { jniLibs { useLegacyPackaging = true } }
    androidResources { noCompress += listOf("zip") }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}
