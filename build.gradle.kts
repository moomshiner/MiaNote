import org.gradle.api.tasks.compile.JavaCompile

val requiredJava = JavaVersion.VERSION_17
require(JavaVersion.current().isCompatibleWith(requiredJava)) {
    "JDK 17 or newer is required (current: ${JavaVersion.current()})"
}
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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    // Use the JDK 21 toolchain but generate Java 17 bytecode for wider compatibility
    jvmToolchain(21)
}

// Ensure Kotlin compiles to Java 17 bytecode so the application can run on JDK 17
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Compile any Java sources to Java 17 bytecode to align with Kotlin output
    options.release.set(17)
}

compose.desktop {
    application {
        mainClass = "AppKt"
        // keep packaging simple for now; we can add nativeDistributions later
    }
}