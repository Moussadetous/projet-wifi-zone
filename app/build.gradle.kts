plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signature stable (les mises à jour s'installent par-dessus) : fournie par la CI, voir .github/workflows/build.yml.
val keystorePath: String? = System.getenv("MIKHMON_KEYSTORE")
val keystorePass: String? = System.getenv("MIKHMON_KEYSTORE_PASSWORD")

android {
    namespace = "com.wifizone.mikhmon"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.wifizone.mikhmon"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.5.1"
        // arm64-v8a : téléphones ; x86_64 : émulateur de test en CI
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }
    signingConfigs {
        if (keystorePath != null && keystorePass != null && file(keystorePath).exists()) {
            create("stable") {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                keyAlias = System.getenv("MIKHMON_KEY_ALIAS") ?: "mikhmon"
                keyPassword = System.getenv("MIKHMON_KEY_PASSWORD") ?: keystorePass
            }
        }
    }
    buildTypes {
        val stable = signingConfigs.findByName("stable")
        getByName("debug") { if (stable != null) signingConfig = stable }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = stable ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    // libphp.so est un exécutable : il doit être extrait sur disque pour pouvoir être lancé
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    androidResources { noCompress += listOf("zip") }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.browser:browser:1.8.0")
    // Accès à distance (Back To Home de MikroTik) : tunnel WireGuard officiel, intégré à l'application (Apache 2.0).
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
