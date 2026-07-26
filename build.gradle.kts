plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

allprojects {
    apply(
        plugin =
        rootProject.libs.plugins.spotless
            .get()
            .pluginId
    )
    apply(
        plugin =
        rootProject.libs.plugins.detekt
            .get()
            .pluginId
    )

    spotless {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktlint(
                rootProject.libs.versions.ktlint
                    .get()
            ).editorConfigOverride(mapOf("android" to "true"))
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(
                rootProject.libs.versions.ktlint
                    .get()
            )
        }
    }

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline.xml")
        parallel = true
    }
}
