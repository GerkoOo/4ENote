import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Drive sync needs an OAuth "Web client ID" from a Google Cloud project that only the project
// owner can create (see docs/google-drive-setup.md). To avoid hardcoding a real client ID or
// forcing contributors to hand-edit strings.xml, we read it from a gitignored properties file at
// the repo root (matches the existing `/drive-credentials*` entry in .gitignore) and fall back to
// an obviously-fake placeholder so the app still builds and runs structurally for everyone else.
val driveCredentialsFile = rootProject.file("drive-credentials.properties")
val driveCredentialsProps = Properties().apply {
    if (driveCredentialsFile.exists()) {
        driveCredentialsFile.inputStream().use { load(it) }
    }
}
val googleOAuthWebClientId: String =
    (driveCredentialsProps.getProperty("GOOGLE_OAUTH_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() })
        ?: "000000000000-fakeplaceholderclientid00000000.apps.googleusercontent.com"

android {
    namespace = "com.gerko.fourenote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gerko.fourenote"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        resValue("string", "google_oauth_client_id", googleOAuthWebClientId)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        resValues = true
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
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Google Sign-In + Drive sync. Credential Manager is Google's current recommended
    // replacement for the deprecated GoogleSignIn/GoogleSignInClient API; the separate
    // Authorization API (play-services-auth's AuthorizationClient) grants scoped Drive access.
    // See docs/google-drive-setup.md for the full rationale and version sources.
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
