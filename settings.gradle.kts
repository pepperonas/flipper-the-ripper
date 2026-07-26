pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // youtubedl-android (JunkFood02 fork) is published on JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("io\\.github\\.junkfood02.*") }
        }
    }
}

rootProject.name = "FlipperTheRipper"
include(":app")
