import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

group = "com.ku.pierdoon"
version = "1.0.6"

// FFmpeg (decoding every format, remuxing downloads) is pulled in for Windows x64 only:
// the -platform artifacts would bring every OS and CPU along, some 300 MB.
val javacppPlatform = "windows-x86_64"

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.json:json:20260814")
    // Writing tags into audio files ("save to file" when editing a track).
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.bytedeco:javacv:1.5.14") { isTransitive = false }
    implementation("org.bytedeco:javacpp:1.5.14")
    implementation("org.bytedeco:javacpp:1.5.14:$javacppPlatform")
    implementation("org.bytedeco:ffmpeg:8.1.2-1.5.14")
    implementation("org.bytedeco:ffmpeg:8.1.2-1.5.14:$javacppPlatform")
    // Media keys and the system media overlay go through Win32 / WinRT calls.
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.ku.pierdoon.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.ku.pierdoon.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xss4m", "--enable-native-access=ALL-UNNAMED")
        // A music player's live heap is a few dozen MB. The defaults (G1, heap sized from the
        // PC's RAM) kept 300+ MB committed: G1's own bookkeeping alone is ~60 MB. The serial
        // collector has next to no overhead, pauses of a few ms at this size, and with the free
        // ratios hands unused heap back to Windows. Compact headers save ~10% of the heap.
        jvmArgs += listOf(
            "-XX:+UseSerialGC",
            "-Xms24m",
            "-Xmx1g",
            "-XX:MinHeapFreeRatio=15",
            "-XX:MaxHeapFreeRatio=35",
            "-XX:+UseCompactObjectHeaders",
            "-XX:ReservedCodeCacheSize=96m",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Pier.doon"
            packageVersion = project.version.toString()
            description = "Offline music player"
            vendor = "kural3"
            // jdk.accessibility: Compose talks to Windows accessibility through it; jdk.charsets: legacy tag encodings;
            // jdk.httpserver: the phone connection.
            modules("java.instrument", "java.management", "jdk.unsupported", "java.logging", "java.naming", "jdk.accessibility", "jdk.charsets", "jdk.httpserver")
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "Pier.doon"
                upgradeUuid = "6c1f7b8e-3d0a-4e5c-9a57-0b1e2d3c4f5a"
                iconFile.set(project.file("icons/pierdoon.ico"))
            }
        }
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

tasks.test {
    useJUnit()
}

// Renders the app off screen and saves screenshots, driven by a script of steps (see Shots.kt).
// ./gradlew shots -PshotsScript=<file> -PshotsHome=<copy of the app data> -PshotsOut=<folder>
tasks.register<JavaExec>("shots") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.ku.pierdoon.ShotsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    jvmArgs(
        "-Dpierdoon.shots=1",
        "-Dpierdoon.home=${findProperty("shotsHome") ?: layout.buildDirectory.dir("shots/home").get().asFile}",
        "-Dfile.encoding=UTF-8", "-Xss4m", "--enable-native-access=ALL-UNNAMED",
    )
    args(findProperty("shotsScript") ?: "shots.txt", findProperty("shotsOut") ?: layout.buildDirectory.dir("shots").get().asFile)
}

// BuildConfig.VERSION_NAME for the User-Agent and the updater, as on Android.
val generateBuildConfig = tasks.register("generateBuildConfig") {
    val out = layout.buildDirectory.dir("generated/buildconfig")
    val versionName = project.version.toString()
    inputs.property("version", versionName)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/ku/pierdoon/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.ku.pierdoon
            |
            |object BuildConfig {
            |    const val VERSION_NAME = "$versionName"
            |}
            |""".trimMargin()
        )
    }
}
kotlin.sourceSets.main { kotlin.srcDir(generateBuildConfig) }
