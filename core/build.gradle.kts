plugins { id("org.jetbrains.kotlin.jvm") }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { kotlinOptions.jvmTarget = "17" }

dependencies {
    // QR codes des tickets (Apache 2.0, pur Java)
    implementation("com.google.zxing:core:3.5.3")
    // org.json est fourni par Android : seulement pour compiler et tester sur ordinateur
    compileOnly("org.json:json:20240303")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    // Aperçus des modèles (images PNG) : PREVIEW_DIR=/chemin gradle :core:test
    System.getenv("PREVIEW_DIR")?.let { environment("PREVIEW_DIR", it) }
    // Test d'intégration avec le serveur MPro (dossier privé, hors du dépôt) : MPRO_SERVER_DIR=/chemin/ad
    System.getenv("MPRO_SERVER_DIR")?.let { environment("MPRO_SERVER_DIR", it) }
}
