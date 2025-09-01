plugins {
    kotlin("jvm") version "2.0.0"
    // ⬇️ REQUIRED with Kotlin 2.x to use Compose
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("org.jetbrains.compose") version "1.7.0"
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "AppKt"
        // keep packaging simple for now; we can add nativeDistributions later
    }
}
