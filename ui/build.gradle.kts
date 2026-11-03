import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compose code both apps draw with: the strings, the expressive shapes and buttons, the colour
// scheme generator, screen transitions, the colour picker, the players' panels, the screens
// that look the same on both, and the icons. What each app does its own way (covers, settings,
// navigation, the screens laid out for a phone or a window) stays in app/ and desktop/.
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
        // The strings both apps show (composeResources), packed into the APK.
        androidResources { enable = true }
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
            api(libs.compose.mp.resources)
            implementation(project(":core"))
        }
        // The Android app runs on the Compose and Material 3 of its own build file, newer than
        // the ones Compose Multiplatform names. Compiled against those same ones, a call to
        // anything that changed between them fails here instead of on the phone.
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.ku.pierdoon.ui.resources"
    generateResClass = always
}
