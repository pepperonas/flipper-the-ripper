import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
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

// --- Optional server backend defaults (git-ignored backend.properties or env). Empty by default:
// the public build ships with no server and the user configures one in Settings. ---
val backendPropsFile = rootProject.file("backend.properties")
val backendProps =
    Properties().apply {
        if (backendPropsFile.exists()) backendPropsFile.inputStream().use { load(it) }
    }
val defaultBackendUrl = backendProps.getProperty("BACKEND_URL") ?: System.getenv("BACKEND_URL") ?: ""
val defaultBackendKey = backendProps.getProperty("BACKEND_KEY") ?: System.getenv("BACKEND_KEY") ?: ""

android {
    namespace = "io.celox.flipperripper"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.celox.flipperripper"
        minSdk = 24
        targetSdk = 35
        versionCode = 17
        versionName = "1.2.12"

        testInstrumentationRunner = "io.celox.flipperripper.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }
        // ABIs are governed by the `splits.abi` block below (arm64-v8a + armeabi-v7a only). yt-dlp
        // native libs ship for ARM only — x86/x86_64 emulators are unsupported.

        buildConfigField("String", "DEFAULT_BACKEND_URL", "\"$defaultBackendUrl\"")
        buildConfigField("String", "DEFAULT_BACKEND_KEY", "\"$defaultBackendKey\"")
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

    // Ship one APK per ABI instead of a single fat APK carrying both architectures' native libs
    // (yt-dlp/ffmpeg/Python). Each user downloads only their architecture — roughly halves the size —
    // and BOTH architectures stay supported. No universal APK: arm64-v8a covers virtually all modern
    // devices, armeabi-v7a covers older 32-bit ones.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs +=
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                // Material 3 Expressive APIs (MotionScheme, MaterialShapes, LoadingIndicator,
                // ButtonGroup, MaterialExpressiveTheme) are module-wide opted-in here.
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
            )
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
        jniLibs {
            // REQUIRED by youtubedl-android: it ships Python/ffmpeg as lib*.zip.so payloads that it
            // unzips from the on-disk native library dir at runtime. With the modern default
            // (extractNativeLibs=false) those .so files are mmap'd from the APK and never written to
            // disk, so YoutubeDL.init() fails with "failed to initialize". Legacy packaging extracts
            // them on install so the engine can initialise.
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // AGP 8.7's bundled lint crashes (IncompatibleClassChangeError in NonNullableMutableLiveData
        // detector) when analysing the newer Compose 1.11 metadata — a tool bug, not a project issue.
        // Skip the crashing release-vital pass; static analysis is still enforced by detekt + Spotless,
        // and `./gradlew lint` (debug) runs in CI.
        disable += "NonNullableMutableLiveData"
        checkReleaseBuilds = false
        abortOnError = false
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }
}

// Give each per-ABI APK a distinct, ordered versionCode so an install always sees a higher code for a
// newer build (arm64 > armeabi within a release), the convention for split distribution.
androidComponents {
    val abiOffsets = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2)
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == ABI }?.identifier
            val offset = abiOffsets[abi] ?: 0
            val base = output.versionCode.orNull ?: 0
            output.versionCode.set(base * 10 + offset)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (explicitly versioned — no BOM, see gradle/libs.versions.toml)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.graphics.shapes)
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

    // Download engine (yt-dlp + ffmpeg). aria2c is intentionally omitted — the app never invokes the
    // aria2c downloader, so bundling it only added ~6 MB per ABI of dead weight.
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    // Image loading (thumbnails)
    implementation(libs.coil.compose)
    // Decodes a frame from a downloaded video file, used as the history thumbnail.
    implementation(libs.coil.video)

    // HTTP client for the optional server backend
    implementation(libs.okhttp)

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
                    // Trailing * also excludes the synthetic coroutine/lambda classes ($fetchInfo$2 …).
                    "io.celox.flipperripper.data.engine.YoutubeDlEngine*",
                    "io.celox.flipperripper.data.engine.RemoteYtDlpEngine*",
                    "io.celox.flipperripper.data.engine.RoutingYtDlpEngine*",
                    "io.celox.flipperripper.data.engine.WebViewExtractor*",
                    "io.celox.flipperripper.data.engine.WebViewYtDlpEngine*",
                    "io.celox.flipperripper.data.engine.InstagramSession*",
                    "io.celox.flipperripper.ui.login.*",
                    "io.celox.flipperripper.data.repository.BackendConfigRepositoryImpl*",
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
