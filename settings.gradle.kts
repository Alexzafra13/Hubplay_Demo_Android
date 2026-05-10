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

// Foojay toolchain resolver — lets Gradle auto-discover an installed JDK
// matching the toolchain spec (`kotlin { jvmToolchain(17) }` in app/build
// .gradle.kts) and download one from adoptium.net if none is found
// locally. Aligns the JDK that the IDE and the CLI use, so a coworker on
// a fresh machine can `./gradlew assembleDebug` without manually
// installing JDK 17.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HubPlay"
include(":app")
