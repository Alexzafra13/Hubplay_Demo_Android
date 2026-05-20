// Top-level build file. Plugin versions live in gradle/libs.versions.toml
// (the version catalog) — bump there, not here. The per-module scripts
// apply each plugin via `alias(libs.plugins.<name>)`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.openapi.generator)   apply false
    alias(libs.plugins.play.publisher)      apply false
}
