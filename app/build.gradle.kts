import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    testOptions {
        // WebDavProvider sets WebDAV's request methods (PROPFIND, MKCOL) by reflection, which Android
        // allows; the desktop Java running unit tests needs java.net opened for it.
        unitTests.all { it.jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/sun.net.www.protocol.http=ALL-UNNAMED") }
    }
    namespace = "com.gerko.fourenote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gerko.fourenote"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Google Drive sign-in: the OAuth client id of an Android client with "custom URI scheme"
        // enabled, from local.properties (google.clientId=...). Builds without it hide Google Drive.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${localProperties.getProperty("google.clientId", "")}\"")
        // Tablets are arm64; x86_64 is for the emulator. Leaving out the 32-bit builds of the
        // native library (ONNX Runtime) keeps the APK much smaller.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            // Until a store signing key exists, release builds use the debug key so they install
            // over development builds on test devices without losing notes.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Compress the native libraries in the APK (they're unpacked on install): ONNX Runtime alone
    // is about 35 MB per processor type uncompressed, which matters for sharing the APK.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Copies original PDF pages into exports and draws ink as vectors (Apache 2.0).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Runs the open-source Pix2Text formula model that turns handwritten maths into LaTeX (MIT).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
    // The scanner's in-app camera: live preview, page detection on frames, capture (AndroidX, Apache 2.0).
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    // Syncs once a connection is back, even with the app closed (AndroidX, Apache 2.0).
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // Draws LaTeX as a typeset formula, offline (JLaTeXMath, GPL-2.0-or-later).
    implementation("ru.noties:jlatexmath-android:0.2.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    // Android stubs org.json in local unit tests; use the real implementation there.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
