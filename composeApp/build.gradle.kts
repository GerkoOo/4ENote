// The iPad app's Kotlin side (Compose Multiplatform). The Xcode project in iosApp/ links the
// FourENote framework built here; the desktop target only lets Linux compile and render the UI.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val composeVersion = "1.12.2"

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "FourENote"
            isStatic = true
        }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    jvmToolchain(21)

    // expect/actual classes (NoteFiles, AppLock) are still marked beta.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
        }
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
            implementation("junit:junit:4.13.2")
            implementation(kotlin("test"))
        }
    }
}
