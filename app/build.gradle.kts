import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.application)
    // NOTE: AGP 9.0+ ships Kotlin support built-in — the
    // `org.jetbrains.kotlin.android` plugin is no longer applied here.
    // See https://kotl.in/gradle/agp-built-in-kotlin
    // Compose Compiler is still a separate plugin.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "com.alex.hubplay"
    compileSdk = 36

    defaultConfig {
        applicationId    = "com.alex.hubplay"
        minSdk           = 26   // Android 8.0 — covers ~95% of devices and unlocks Media3
        targetSdk        = 36   // Android 16 (released 2026) — Play Store currency window
        versionCode      = 1
        versionName      = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // NOTE: `compileOptions` and `kotlinOptions` are no longer needed
    // here — the project-level `kotlin { jvmToolchain(17) }` below
    // configures both Java AND Kotlin to compile with JDK 17. AGP 9 +
    // toolchain is the modern, machine-portable way to fix the
    // bytecode level: Gradle auto-detects an installed JDK 17 or
    // downloads one via the foojay resolver in settings.gradle.kts.

    buildFeatures {
        compose    = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ─── Wire OpenAPI-generated sources into the Kotlin compilation ──────────────
//
// AGP 9 deprecated `sourceSets["main"].kotlin.srcDirs(Provider<Directory>)` —
// it can no longer tell whether the directory is generated (read-only, depends
// on a task) or static. The replacement is the Variant API's
// `addGeneratedSourceDirectory`, which wires the source dir AND the task
// dependency in one go.
//
// Wrinkle: openapi-generator's GenerateTask exposes `outputDir` as
// `Property<String>`, but `addGeneratedSourceDirectory` requires a
// `DirectoryProperty`. The trivial bridge is a dummy task whose output IS
// a DirectoryProperty pointing at the same path; we depend it on
// `openApiGenerate` so it doesn't run until the client is actually emitted.
abstract class OpenApiSourcesAggregator : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val output: org.gradle.api.file.DirectoryProperty
}

val openApiSourcesTask = tasks.register<OpenApiSourcesAggregator>("openApiSources") {
    dependsOn("openApiGenerate")
    output.set(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            openApiSourcesTask,
            OpenApiSourcesAggregator::output,
        )
    }
}

// One-line JVM toolchain — Gradle picks (or downloads via foojay) a
// JDK 17 to compile both Java and Kotlin against. Replaces the older
// `compileOptions { sourceCompatibility = ... }` + `kotlin { compilerOptions
// { jvmTarget = ... } }` duo with a single declarative source of truth
// that's also what Android Studio's "Daemon toolchain" hint asks for.
kotlin {
    jvmToolchain(17)
}

// ─── OpenAPI client generation ───────────────────────────────────────────────
//
// Reads the spec at <repo>/openapi-cached.yaml and emits a Retrofit + Moshi
// client into build/generated/openapi/. The cached file is checked into the
// repo so the build works offline; `./gradlew refreshOpenApiSpec` updates it
// from the live server when the backend ships new endpoints.
//
// Why "cached" instead of fetching on every build:
//   - Build is reproducible offline (CI, plane, dev w/o the server up).
//   - PRs show a diff when the backend contract changes — surprises
//     surface in code review, not in production stack traces.

val openApiSpecUrl    = "https://hubplay.duckdns.org/api/v1/openapi.yaml"
val cachedSpecFile    = file("$rootDir/openapi-cached.yaml")
val generatedOutputDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(cachedSpecFile.absolutePath)
    outputDir.set(generatedOutputDir.get().asFile.absolutePath)
    apiPackage.set("com.alex.hubplay.api")
    modelPackage.set("com.alex.hubplay.api.model")
    invokerPackage.set("com.alex.hubplay.api.invoker")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    skipOverwrite.set(false)
    // Bypass strict spec validation. The HubPlay backend's openapi.yaml has
    // a few cosmetic issues the validator flags (descriptions parsed as
    // unexpected attributes, `components.security` instead of the
    // canonical `components.securitySchemes`). The generated client is
    // unaffected by these — they're documentation-level, not contract-
    // level. TODO(backend): tidy up internal/api/handlers/openapi.yaml so
    // we can flip this back to true.
    validateSpec.set(false)
    configOptions.set(
        mapOf(
            "library"          to "jvm-retrofit2",
            "useCoroutines"    to "true",
            "serializationLibrary" to "moshi",
            "dateLibrary"      to "java8",
            "sourceFolder"     to "src/main/kotlin",
        ),
    )
}

// Convenience task: refresh the cached spec from the live server. Run it
// when the backend ships new endpoints; commit the resulting diff so the
// next build picks them up.
tasks.register("refreshOpenApiSpec") {
    group = "openapi"
    description = "Download the latest OpenAPI spec from the live server into openapi-cached.yaml"
    doLast {
        val text = uri(openApiSpecUrl).toURL().readText()
        cachedSpecFile.writeText(text)
        logger.lifecycle("Updated $cachedSpecFile from $openApiSpecUrl (${text.length} bytes)")
    }
}

// NOTE: no manual `dependsOn("openApiGenerate")` needed — the Variant API
// wiring above attaches the task dependency automatically. Adding it
// here too would be a redundant edge in the task graph.

dependencies {
    // ── AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.vm.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // ── Material Design XML resources — needed so the Activity's
    //     android:theme="@style/Theme.HubPlay" can inherit from
    //     Theme.Material3.* during the splash (pre-Compose phase).
    implementation(libs.material)

    // ── Compose (BOM aligns transitive Compose versions)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // ── Media3 / ExoPlayer (HLS)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // ── Concurrency
    implementation(libs.kotlinx.coroutines.android)

    // ── Image loading (Coil 3 — group is io.coil-kt.coil3, network engine split out)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ── Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
