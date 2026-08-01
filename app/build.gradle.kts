import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kurai.musikk"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // Pull secrets out of local.properties (gitignored) instead of baking them
    // into the APK. Missing keys throw at build time.
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }
    val hfApiToken: String = localProps.getProperty("HF_API_TOKEN")
        ?: error("HF_API_TOKEN missing from local.properties")

    defaultConfig {
        applicationId = "com.kurai.musikk"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Retrofit / OkHttp base URLs for Hugging Face API
        buildConfigField("String", "HF_API_BASE_URL", "\"https://api-inference.huggingface.co/\"")
        buildConfigField("String", "R2_ENDPOINT", "\"https://1db1b6e06c6b24d18319b3c66d491c4c.r2.cloudflarestorage.com\"")
        buildConfigField("String", "R2_ACCESS_KEY", "\"62f2de64456e4aa07227a2a7c4ca9486\"")
        buildConfigField("String", "R2_SECRET_KEY", "\"0ecf33ddb3b9bf6b45648cb9383ad0d8a373f914b0e015bf1e224ba36cc48413\"")
        buildConfigField("String", "R2_BUCKET", "\"musikk-stems\"")
        buildConfigField("String", "HF_API_TOKEN", "\"$hfApiToken\"")
    }

    buildTypes {
        debug {
            // Android emulator OR physical device: use the PC's LAN IP.
            // The emulator's NAT can route to external LAN IPs directly, and a
            // real phone reaches the same address over Wi-Fi. No `adb reverse`
            // tunnel needed. Emulator + PC + phone must share the network.
            buildConfigField("String", "LOCAL_STEM_SERVER_URL", "\"http://192.168.1.2:8000\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Physical device: PC + phone must be on the same Wi-Fi.
            // PC's actual LAN IP.
            buildConfigField("String", "LOCAL_STEM_SERVER_URL", "\"http://192.168.1.2:8000\"")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime.livedata)
    debugImplementation(libs.compose.ui.tooling)

    // Activity Compose
    implementation(libs.activity.compose)

    // Navigation Compose
    implementation(libs.navigation.compose)

    // Lifecycle + ViewModel
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel.compose)

    // Firebase BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Google Sign-In
    implementation(libs.play.services.auth)

    // OkHttp3
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Retrofit2 + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)

    // Glide (Compose)
    implementation(libs.glide)

    // AndroidX
    implementation(libs.appcompat)
    implementation(libs.material)

    // Media3/ExoPlayer for audio playback
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    
    // FFmpeg Kit for local audio processing (optional)
    // NOTE: ffmpeg-kit-full artifacts are no longer on Maven Central.
    // Switch to mobile-ffmpeg or build from source if local processing is needed.
    // implementation("com.arthenica:ffmpeg-kit-full:6.0-2")
    
    // TarsosDSP for audio processing (pitch shifting, speed changes, effects)
    // Note: Using pure Kotlin implementation instead of external library
    // implementation("com.github.jaiis:tarsosdsp-android:2.4")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(composeBom)
    debugImplementation(libs.compose.ui.test.manifest)
}
