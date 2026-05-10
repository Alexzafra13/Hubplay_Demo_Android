// Top-level build file. Plugin versions are declared here, applied in the
// per-module scripts. The version catalog (gradle/libs.versions.toml) is the
// single source of truth.
//
// AGP 9.0+ has Kotlin built-in, so `org.jetbrains.kotlin.android` is gone.
// Compose Compiler stays separate (`kotlin-compose` alias).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.openapi.generator)   apply false
}
