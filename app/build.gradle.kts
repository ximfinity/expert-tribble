plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Renders the control panel to PNGs in JVM tests (layoutlib), for UI review without a device.
    id("app.cash.paparazzi")
}

// Release CI passes -PappVersionName / -PappVersionCode from the git tag and run number.
val appVersionName = (findProperty("appVersionName") as String?) ?: "1.0.0"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
val supportUrl = (findProperty("phantomSupportUrl") as String?) ?: "https://itch.io"
val updateRepo = (findProperty("phantomUpdateRepo") as String?) ?: ""

// Release signing comes from the environment (GitHub secrets in CI), never from the repo.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "com.phantomfloor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phantomfloor"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "SUPPORT_URL", "\"$supportUrl\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")

        // ML Kit ships large native libraries for every ABI. Projector-driving phones and
        // Android TV boxes are ARM, so drop x86 (add "x86_64" back to run on an emulator).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // A fixed, public debug key (standard "android" passwords) so every CI test build is
        // signed the same way and installs over the previous one. It can't sign release builds.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // Android framework calls (Paint, GLES30) become no-ops in JVM tests, so game logic can be
        // play-tested headlessly (see src/test/.../playtest).
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Scenes, games, sound synthesis and the frame loop, shared with the browser build
    implementation(project(":shared"))

    // AndroidX + UI
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")

    // Coroutines (+ Task.await() for ML Kit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // CameraX (analysis only, no preview surface needed)
    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")

    // ML Kit: pose (precise feet, 1 person) + object detection (multiple bodies)
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:object-detection:17.0.2")

    // Network (RTSP) cameras such as Wyze or IR security cameras, as an alternative tracking source
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")

    testImplementation("junit:junit:4.13.2")
}
