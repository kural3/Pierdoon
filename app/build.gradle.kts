import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

// Release signing comes from an untracked keystore.properties (storeFile, storePassword,
// keyAlias, keyPassword), in the project root or in Pierdoon-signing/. Without it, release
// builds are simply left unsigned.
val keystorePropsFile = listOf("keystore.properties", "Pierdoon-signing/keystore.properties")
    .map(rootProject::file).firstOrNull { it.exists() }
val keystoreProps = Properties().apply {
    keystorePropsFile?.inputStream()?.use { load(it) }
}
// storeFile may be a path from another computer: then the keystore next to the properties file.
val keystoreFile = keystoreProps.getProperty("storeFile")?.let { path ->
    file(path).takeIf { it.exists() } ?: keystorePropsFile?.resolveSibling(File(path).name)?.takeIf { it.exists() }
}

android {
    namespace = "com.ku.pierdoon"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ku.pierdoon"
        minSdk = 24
        targetSdk = 37
        versionCode = 13
        versionName = "2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // yt-dlp brings its own Python, ~15 MB per ABI; all four made a 68 MB APK.
        // Every phone of the last years is arm64, so that's the only one included.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Same key as releases, so a debug build and a GitHub release install over each other
        // (and the in-app updater works on the development phone too).
        debug {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Instrumented tests on a phone with a release installed, which a debug key can't replace.
            if (providers.gradleProperty("sideBySideTests").isPresent) {
                applicationIdSuffix = ".test"
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        // Release-like (R8, no debuggable) but signed with the debug key, so it can be installed
        // locally to judge real smoothness. Debug Compose builds drop frames on their own.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".profile"
            matchingFallbacks += "release"
        }
        // What :baselineprofile installs and walks to record the profile. Declared here, not left
        // to the plugin, for an id of their own: the plugin's would be the app's, and the run
        // uninstalls what it tested, which would take the installed app's library with it.
        for (base in listOf("release", "profile")) {
            for (prefix in listOf("nonMinified", "benchmark")) {
                create(prefix + base.replaceFirstChar { it.uppercase() }) {
                    initWith(getByName(base))
                    signingConfig = signingConfigs.getByName("debug")
                    applicationIdSuffix = (getByName(base).applicationIdSuffix ?: "") + ".bp"
                    matchingFallbacks += "release"
                    // The profile is recorded under the real names; R8 maps it when the release is built.
                    if (prefix == "nonMinified") optimization { enable = false }
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // youtubedl-android runs Python and QuickJS as executables from the native library folder,
    // so they have to be extracted on install.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildFeatures {
        compose = true
        // VERSION_NAME for the User-Agent and the updater.
        buildConfig = true
    }
    // The language is switched in the app, so a bundle must carry every one of them.
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Recorded by hand on a phone (./gradlew :app:generateBaselineProfile), kept in src/main.
baselineProfile {
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // Writing tags into audio files ("save to file" when editing a track).
    implementation(libs.jaudiotagger)
    // Track downloads: yt-dlp with its Python runtime. No ffmpeg (35 MB): audio is saved as the site serves it.
    implementation(libs.youtubedl)
    // Compiles the baseline profile on install, also for APKs from GitHub rather than Play.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}