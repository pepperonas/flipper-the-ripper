import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

// --- Signing: keystore.properties (local) with env-var fallback (CI). ---
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps =
    Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
val hasSigning = keystorePropsFile.exists() || System.getenv("KEYSTORE_PASSWORD") != null

android {
    namespace = "io.celox.flipperripper"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.celox.flipperripper"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "io.celox.flipperripper.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }

        // yt-dlp native libs ship only for ARM. x86/x86_64 emulators are unsupported.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                if (keystorePropsFile.exists()) {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                } else {
                    storeFile = rootProject.file(System.getenv("KEYSTORE_FILE") ?: "release.jks")
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Serialization / coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Download engine (yt-dlp + ffmpeg + aria2c)
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)
    implementation(libs.youtubedl.android.aria2c)

    // Image loading (thumbnails)
    implementation(libs.coil.compose)

    // --- Unit tests ---
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)

    // --- Instrumentation tests ---
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.work.testing)
    kspAndroidTest(libs.hilt.compiler)
}

kover {
    reports {
        filters {
            excludes {
                // Generated code, Compose UI, and platform-integration classes that are exercised by
                // instrumentation tests (device required) rather than JVM unit tests are excluded so the
                // 80% bound is measured over the testable logic layers (domain, engine policy, data, VMs).
                classes(
                    "*_Factory",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "*.databinding.*",
                    "*.BuildConfig",
                    "*.ComposableSingletons*",
                    "*.*\$\$serializer",
                    // Library/platform integration — covered by androidTest, not unit tests.
                    "io.celox.flipperripper.FlipperApplication",
                    "io.celox.flipperripper.data.engine.YoutubeDlEngine",
                    "io.celox.flipperripper.data.media.*",
                    "io.celox.flipperripper.data.work.*",
                    "io.celox.flipperripper.util.MediaIntents*",
                    // Compose entry points / screens (non-composable helpers only).
                    "io.celox.flipperripper.ui.MainActivity*",
                    "io.celox.flipperripper.ui.FlipperAppKt",
                    "io.celox.flipperripper.ui.home.HomeScreenKt",
                    "io.celox.flipperripper.ui.history.HistoryScreenKt",
                    "io.celox.flipperripper.ui.settings.SettingsScreenKt",
                    "io.celox.flipperripper.ui.util.*"
                )
                packages(
                    "io.celox.flipperripper.ui.theme",
                    "io.celox.flipperripper.ui.components",
                    "io.celox.flipperripper.ui.navigation",
                    "io.celox.flipperripper.di",
                    "hilt_aggregated_deps",
                    "dagger.hilt.internal.aggregatedroot.codegen"
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
