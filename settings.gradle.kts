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

// Toolchain auto-detection: Gradle scans the standard locations
// (Android Studio's embedded JDK, $JAVA_HOME, common install dirs)
// for a JDK matching the spec in `kotlin { jvmToolchain(17) }`.
// We DON'T add the foojay resolver here because foojay 0.10.0 has an
// IBM_SEMERU compatibility bug against Gradle 9.4.x that breaks
// `:app:compileDebugAndroidTestJavaWithJavac`. Android Studio's
// embedded JBR is JDK 17, so detection alone covers our case;
// re-introduce a resolver only if a fresh dev machine reports
// "no compatible toolchains found".

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HubPlay"
include(":app")
