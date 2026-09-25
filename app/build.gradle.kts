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
        versionCode = 32
        versionName = "1.10.0"

        testInstrumentationRunner = "io.celox.flipperripper.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }
        // ABIs are governed by the `splits.abi` block below (arm64-v8a only). yt-dlp native libs
        // ship for ARM only — x86/x86_64 emulators are unsupported.

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

    // One APK, 64-bit ARM only. The 32-bit armeabi-v7a build was dropped in 1.9.0: across every
    // release it drew under a tenth of the downloads, a genuinely 32-bit phone on Android 7+ is a
    // budget device from 2014–2016, and offering two files meant people picked the wrong one — the
    // 32-bit APK carries the lower versionCode, so on a 64-bit phone with the app already installed
    // it fails as a downgrade ("App not installed"). One file removes the choice. 1.8.3 remains the
    // last release for 32-bit devices, and the in-app update notice stays quiet there (UpdatePolicy).
    // Still a split rather than a plain build so the native payload is the one architecture's only.
    // Android 13+ offers a per-app language (Settings → Apps → Flipper the Ripper → Language). The
    // list is generated from the values-xx folders, so a new translation appears there by itself.
    androidResources {
        generateLocaleConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
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
        // AGP 8.7's bundled lint crashes with IncompatibleClassChangeError when analysing the newer
        // Compose 1.11 metadata — a tool bug, not a project issue. Disabling the detector below fixed
        // one such crash; `ComposableFlowOperatorDetector` still brings `./gradlew lintDebug` down,
        // and disabling a detector does not help there because the driver itself dies.
        //
        // So: lint does NOT run in CI (ci.yml runs spotlessCheck, detekt, testDebugUnitTest,
        // koverVerifyDebug, assembleDebug and the connected tests). Static analysis is carried by
        // detekt + Spotless. Verified 2026-09-20 that the crash predates the 1.9.2 work — the same
        // detector brings the pre-1.9.2 tree down too.
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

// Every APK the build writes is named like the one the release publishes: flipper-the-ripper-v1.10.0.apk
// (release) or flipper-the-ripper-v1.10.0-debug.apk — not the split's app-arm64-v8a-release.apk. The
// release workflow picks the file up under the tag's name, so a tag that does not match versionName
// fails the job on a missing file instead of publishing a mislabelled APK.
android.applicationVariants.configureEach {
    val name = versionName
    val suffix = if (buildType.name == "release") "" else "-${buildType.name}"
    outputs.configureEach {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            "flipper-the-ripper-v$name$suffix.apk"
    }
}

// The versionCode Android sees is `versionCode * 10 + 2`, and that scheme MUST stay even though only
// one ABI is built now. It dates from the two-ABI era (armeabi +1, arm64 +2, so a newer build always
// out-ranked an older one), and every installed copy carries a code from it — 1.8.3 is 282. Dropping
// the multiplier would make 1.9.0 report 29, which Android treats as a downgrade and refuses to
// install over 282. Keeping the scheme costs nothing; removing it strands every existing user.
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

// The drift guards in src/test read these files straight from disk. Gradle does not know that, so
// without this an edit to the README or the release workflow left the test task "up to date" and
// the guard never ran — found when a mutation probe reported six pins blind that were not.
tasks.withType<Test>().configureEach {
    inputs.files(
        rootProject.file("README.md"),
        rootProject.file("CHANGELOG.md"),
        rootProject.file("SECURITY.md"),
        rootProject.file("CONTRIBUTING.md"),
        rootProject.file(".github/workflows/release.yml"),
        rootProject.file("scripts/release-notes.sh"),
        file("src/main/res/xml/shortcuts.xml")
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.sharetarget)
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
    implementation(libs.reorderable)
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
                    // Network + wiring around updates; the decisions live in the covered
                    // AppVersions/AppReleaseParser.
                    "io.celox.flipperripper.data.update.AppUpdateChecker*",
                    "io.celox.flipperripper.data.update.UpdateCoordinator*",
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
                    "io.celox.flipperripper.ui.settings.AboutSectionKt",
                    // Builds Compose EnterTransition/ExitTransition from the theme springs — only
                    // exercisable inside a composition; the pure geometry (ScreenMotion) is unit-tested.
                    "io.celox.flipperripper.ui.motion.ScreenTransitions*",
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
