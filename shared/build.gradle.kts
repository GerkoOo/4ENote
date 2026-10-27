plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    // The Android app consumes this module as a plain JVM library, so its bytecode targets Java 17
    // like the app's (CI runs the tests on JDK 17; Java 21 class files fail there).
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    // The iOS app (Compose Multiplatform) will use the same code. These targets can't be built on
    // a Linux host; Gradle skips them there.
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            // Only the JsonElement/JsonObject/buildJsonObject API is used, no compiler plugin (Apache 2.0).
            // api: JsonReader hands out its JsonElement types to the app.
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            // Sync runs in coroutines; Note and Folder carry Room's annotations (only the
            // annotations: the database stays in each app); okio for files and upload streams.
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            api("androidx.room:room-common:2.8.5")
            api("com.squareup.okio:okio:3.9.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
            implementation(kotlin("test"))
        }
    }
}

// Every moved file is platform-independent; nothing had to stay in the app.

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
