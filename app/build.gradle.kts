import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    // Apply the Secrets Gradle Plugin to expose GEMINI_API_KEY via BuildConfig
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

// Read optional AI_PROXY_BASE_URL from local.properties (defaults to secure backend proxy)
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
// Default to the secure backend proxy endpoint; NO raw provider AI_API_KEY is embedded into APK
val aiProxyBaseUrl = localProperties.getProperty("AI_PROXY_BASE_URL") 
    ?: "https://study-mentor-proxy.workers.dev/v1/chat/completions"

android {
    namespace = "com.example.study_mentor"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.study_mentor"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose Backend Proxy Base URL ONLY - Zero raw provider API keys in client APK
        buildConfigField("String", "AI_PROXY_BASE_URL", "\"$aiProxyBaseUrl\"")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Required to generate BuildConfig fields from local.properties via the Secrets Plugin
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.10")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // OpenAI Kotlin client (unofficial, by Mouaad Aallam) — works with any sk- API key
    implementation("com.aallam.openai:openai-client:3.8.2")
    // Ktor HTTP engine for Android (required by openai-client)
    implementation("io.ktor:ktor-client-android:2.3.12")

    // Coil for asynchronous image loading in Jetpack Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // CameraX dependencies (Core, Camera2, Lifecycle, View)
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
}
