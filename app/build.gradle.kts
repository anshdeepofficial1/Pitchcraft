import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.inferno.gallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anshdeep.pitchcraft"
        // Allow Android 8.0+ devices; CI checks real install/launch on older Android.
        // Android 11 and below were blocked by the previous minSdk = 31.
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // One universal APK containing all supported native ABIs.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("key.properties")
            if (propsFile.exists()) {
                val ks = Properties()
                ks.load(FileInputStream(propsFile))
                val keystorePath = ks["storeFile"] as String
                val rootKsFile = rootProject.file(keystorePath)
                storeFile = if (rootKsFile.exists()) rootKsFile else file(keystorePath)
                storePassword = ks["storePassword"] as String
                keyAlias = ks["keyAlias"] as String
                keyPassword = ks["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // v1.0.1 debug APKs used this exact application ID suffix.
            // Keep it stable so a correctly signed v1.0.2 can update in place.
            applicationIdSuffix = ".debug"

            // CI's default debug.keystore is regenerated on fresh runners, so it
            // cannot reliably update an APK signed by a previous runner.
            // An explicit upgrade build MUST use the original v1.0.1 key.
            if (providers.gradleProperty("pitchcraftUpgradeSigning").orNull == "true") {
                require(rootProject.file("key.properties").isFile) {
                    "Upgrade signing requested but key.properties is missing; refusing to build an incompatible APK."
                }
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    // Force Kotlin codegen — avoids 'unexpected jvm signature V' KSP bug with suspend Unit DAOs
    arg("room.generateKotlin", "true")
}

dependencies {
    // ── Compose BOM (aligns UI / Foundation / Runtime versions) ──
    implementation(platform(libs.androidx.compose.bom))

    // ── AndroidX Core ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ── Lifecycle ──
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── DataStore ──
    implementation(libs.androidx.datastore.preferences)

    // ── Compose UI ──
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // ── Material 3 Expressive (explicit version ≥ 1.4.0, overrides BOM) ──
    implementation(libs.androidx.compose.material3)

    // ── Compose Animation (SharedTransitionScope, spring physics) ──
    implementation(libs.androidx.compose.animation)

    // ── Navigation ──
    implementation(libs.androidx.navigation.compose)

    // ── Graphics Shapes (RoundedPolygon, Morph — for shape morphing) ──
    implementation(libs.androidx.graphics.shapes)

    // ── Coil 3 — Image Loading ──
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    // ── EXIF ──
    implementation(libs.androidx.exifinterface)

    // ── Room — Database (stable 2.6.1, FTS5 support) ──
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // ── Paging 3 ──
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // ── ML Kit (OCR & Face Detection) ──
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.face.detection)

    // ── ONNX Runtime ──
    implementation(libs.onnxruntime.android)


    // ── WorkManager ──
    implementation(libs.androidx.work.runtime.ktx)

    // ── Testing ──
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Media3 (ExoPlayer) ──
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // ── Biometric (Private Space auth) ──
    implementation(libs.androidx.biometric)

    // ── osmdroid (OpenStreetMap — Photo Map) ──
    implementation(libs.osmdroid)


    // ── Lottie Animations ──
    implementation(libs.lottie.compose)

    implementation(libs.material)

    // ── MaterialKolor — Kotlin-native M3 dynamic color engine ──
    implementation("com.materialkolor:material-kolor:4.1.1")
}