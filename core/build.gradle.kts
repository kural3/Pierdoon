import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code both apps use: plain Kotlin/JVM, so the Android app takes it as it is and nothing in it
// touches Android, Swing or the file layout of either. Platform parts stay in app/ and desktop/.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(25)
    // Android compiles for Java 11 (app/build.gradle.kts); the Windows app runs it on 25.
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // @Immutable and @Stable only, without the Compose runtime: both apps' compilers read them.
    implementation(libs.compose.runtime.annotation)
    testImplementation(libs.junit)
}
