plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    // The Android app consumes this module as a plain JVM library.
    jvm()
    // The iOS app (Compose Multiplatform) will use the same code. These targets can't be built on
    // a Linux host; Gradle skips them there.
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            // Only the JsonElement/JsonObject/buildJsonObject API is used, no compiler plugin (Apache 2.0).
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
            implementation(kotlin("test"))
        }
    }
}

// Every moved file is platform-independent; nothing had to stay in the app.
