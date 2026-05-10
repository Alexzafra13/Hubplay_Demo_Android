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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose    = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Pull the OpenAPI-generated sources into the main source set so the IDE
    // resolves them and the compiler picks them up. The path matches the
    // `outputDir` configured below.
    sourceSets["main"].kotlin.srcDirs(
        layout.buildDirectory.dir("generated/openapi/src/main/kotlin"),
    )
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

// Make the Kotlin compiler depend on the generated client so a clean build
// produces a working APK in one shot.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("openApiGenerate")
}

dependencies {
    // ── AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.vm.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

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
