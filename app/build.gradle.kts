import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.openapi.generator)
    // Play publisher is applied unconditionally — its tasks (publishBundle,
    // publishApks, …) are only INVOKED from the release workflow, which
    // is the only place the service-account JSON lives. Locally `assembleDebug`
    // never touches the plugin's task graph, so applying it is free.
    alias(libs.plugins.play.publisher)
}

// ─── Env-driven release metadata ─────────────────────────────────────────────
//
// All three values are read from the environment so the keystore + signing
// passwords never touch the repo. Local builds without these env vars still
// work: the release buildType falls back to UNSIGNED (handy for diff'ing
// release-mode behaviour against debug without needing the real keystore).
//
//   RELEASE_KEYSTORE_PATH       — absolute path to the .jks file on disk
//                                 (the workflow base64-decodes the secret
//                                 into a temp file and exports this var)
//   RELEASE_KEYSTORE_PASSWORD   — keystore password
//   RELEASE_KEY_ALIAS           — alias inside the keystore (default: upload)
//   RELEASE_KEY_PASSWORD        — password for that key
//   VERSION_CODE                — monotonically-increasing int (Play Store rule);
//                                 the workflow derives it from the git tag
//                                 (v1.2.3 → 10203). Falls back to the hardcoded
//                                 value below when unset, so debug builds work.
//   VERSION_NAME                — display string ("1.2.3"); falls back to the
//                                 hardcoded value when unset.
val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH").orEmpty()
val hasReleaseKeystore  = releaseKeystorePath.isNotBlank() && file(releaseKeystorePath).exists()

android {
    namespace = "com.alex.hubplay"
    compileSdk = 35

    defaultConfig {
        applicationId    = "com.alex.hubplay"
        minSdk           = 26   // Android 8.0 — covers ~95% of devices and unlocks Media3
        targetSdk        = 35
        versionCode      = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 3
        versionName      = System.getenv("VERSION_NAME") ?: "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The release signing config is wired conditionally: present only
        // when the workflow has decoded a keystore into the path pointed
        // at by RELEASE_KEYSTORE_PATH. Otherwise we skip the block so a
        // local `assembleRelease` produces an unsigned AAB rather than
        // failing the configuration phase with a missing-file error.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile     = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias      = System.getenv("RELEASE_KEY_ALIAS") ?: "upload"
                keyPassword   = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
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
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // resolves them and the compiler picks them up. AGP 8.x accepts a
    // Provider<Directory> here without complaining (the strictness
    // landed in AGP 9). The Variant API equivalent
    // (`androidComponents.onVariants { variant.sources.kotlin.add… }`)
    // is more portable across AGP versions but needs an extra wrapper
    // task because openapi-generator's output is `Property<String>`,
    // not `DirectoryProperty`. Sticking with the simple form for now.
    sourceSets["main"].kotlin.srcDirs(
        layout.buildDirectory.dir("generated/openapi/src/main/kotlin"),
    )

    // Tests live in src/test/kotlin rather than the default
    // src/test/java — Kotlin's plugin picks it up automatically with
    // modern AGP, but declaring it explicitly removes any IDE doubt.
    sourceSets["test"].kotlin.srcDirs("src/test/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// ─── OpenAPI client generation ───────────────────────────────────────────────
//
// Reads the spec at <repo>/openapi-cached.yaml and emits a Retrofit + Moshi
// client into build/generated/openapi/. The cached file is checked into the
// repo so the build works offline; `./gradlew refreshOpenApiSpec` updates it
// from the live server when the backend ships new endpoints.

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
    // Spec validation off as belt-and-suspenders (the cached YAML was
    // already cleaned up — see fix(openapi) commit). If the backend
    // ships a regression, refreshOpenApiSpec will pull it; the
    // validator off means the client still generates so we can ship.
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
    implementation(libs.okhttp.sse)            // /me/events SSE stream
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // ── QR generation (login pairing screen — encodes
    //     verification_uri_complete so the web client can scan it)
    implementation(libs.zxing.core)

    // ── Media3 / ExoPlayer (HLS)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // ── Concurrency
    implementation(libs.kotlinx.coroutines.android)

    // ── Image loading (Coil 3 — group is io.coil-kt.coil3, network engine split out)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // NOTE: HeroTrailerView used to depend on Pierfrancesco Soffritti's
    // android-youtube-player wrapper but its onStateChange callbacks
    // never fired reliably on Android TV WebViews (issue: lib expects
    // enableAutomaticInitialization to be set BEFORE the view is added
    // to the layout, which AndroidView doesn't allow). Replaced with a
    // plain WebView embedding the same iframe the web client uses.

    // ── Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// ─── Play Store publishing ───────────────────────────────────────────────────
//
// The `play` extension is configured here but the actual upload tasks
// (`publishReleaseBundle`, `promoteReleaseArtifact`) only run when the
// release workflow invokes them. Local invocations of those tasks fail
// fast with a clear message when PLAY_SERVICE_ACCOUNT_JSON isn't set,
// instead of producing a half-uploaded bundle.
//
// Defaults that nudge us toward the safe path:
//   - track = "internal" so a tag push never lands directly in
//     production. Promotion to alpha → beta → production is a manual
//     button in the Play Console (or a `workflow_dispatch` of release.yml
//     with the `track` input set to something else).
//   - defaultToAppBundles = true so the upload task produces an .aab
//     (Play Store requires it for new apps since Aug 2021), never a
//     legacy .apk.
//   - releaseStatus = "draft" so the upload shows up in the Play Console
//     but isn't pushed to testers until a human reviews and clicks
//     "Send for review". Once you trust the pipeline, flip to "completed".
play {
    val credsPath = System.getenv("PLAY_SERVICE_ACCOUNT_JSON_PATH")
    if (!credsPath.isNullOrBlank() && file(credsPath).exists()) {
        serviceAccountCredentials.set(file(credsPath))
    }
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
}

