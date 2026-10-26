import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compose code both apps draw with: the expressive shapes and buttons, the colour scheme
// generator, screen transitions, the colour picker, the expanding player and the icons.
// Only Compose and Material 3 in here; strings, settings and screens stay in app/ and desktop/.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(25)

    android {
        namespace = "com.ku.pierdoon.ui.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
            implementation(libs.androidx.graphics.shapes)
        }
    }
}
