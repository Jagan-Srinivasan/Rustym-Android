plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rustym.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rustym.mobile"
        minSdk = 26          // Android 8.0+ : covers practically every phone in use
        targetSdk = 35
        versionCode = 120
        versionName = "1.2.0"
        // No native code anywhere, so a single APK runs on every CPU (arm64, armv7, x86, x86_64).
    }

    signingConfigs {
        // A fixed key checked into the repo so every CI build is signed the same way and can
        // update the previous install. Replace with your own key for Play Store releases.
        create("rustym") {
            storeFile = file("rustym.keystore")
            storePassword = "rustym123"
            keyAlias = "rustym"
            keyPassword = "rustym123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("rustym")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rustym")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor uses java.time / java.nio APIs that older Android versions lack.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "**/*.proto"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    // YouTube / YouTube Music access (search + stream URLs) - no API key, no Google account needed
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.27.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // encrypted storage for the Google session

    // Playback: ExoPlayer in a foreground MediaSession service (lock screen, notification, headset keys, Bluetooth)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-datasource:1.5.1")
    implementation("androidx.media3:media3-database:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("com.google.guava:guava:33.3.1-android")

    // UI
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
