// Top-level build file. Plugin versions are declared here, applied in the
// per-module scripts. The version catalog (gradle/libs.versions.toml) is the
// single source of truth.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.openapi.generator)   apply false
}
