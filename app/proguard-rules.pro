# R8 / ProGuard rules for the release build.
#
# Compose, OkHttp, Retrofit, Moshi-codegen, Coil and Media3 ship their own
# consumer-proguard rules so we rarely need to repeat them here. This file
# captures the rules WE need on top: app-specific data classes hit via
# reflection, hand-written API surfaces R8 can't trace, and the JNI / native
# bridges in Media3 that minification doesn't reach.
#
# Verify after every dependency bump with:
#   ./gradlew :app:assembleRelease
# and run the resulting AAB through `bundletool build-apks` + install on a
# device to confirm nothing crashes from a stripped class.

# ─── Kotlin metadata + reflection (Moshi reflective adapter fallback) ───────
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class kotlin.Metadata { *; }

# ─── Moshi ─────────────────────────────────────────────────────────────────
# KSP-generated *_JsonAdapter classes — keep names + members or the
# adapter cache can't find them at runtime.
-keep class **JsonAdapter { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
# Our DTOs are hit via the generated adapters, but the @JsonClass-annotated
# classes themselves also need their default constructors preserved so
# adapter instantiation works.
-keep @com.squareup.moshi.JsonClass class * { <init>(...); }
# Reflective adapter (the fallback we keep around for non-codegen DTOs).
-keep class com.squareup.moshi.kotlin.reflect.** { *; }

# ─── Retrofit (suspend + parameterized return types use Type reflection) ───
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Service interfaces are hit via Proxy — keep the interface signatures.
-keep interface com.alex.hubplay.data.api.** { *; }

# ─── OkHttp / Okio ─────────────────────────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
# Server-Sent Events uses a separate EventSource API that Okio resolves
# via service-loader-style lookups in some versions.
-keep class okhttp3.sse.** { *; }

# ─── Media3 / ExoPlayer ────────────────────────────────────────────────────
# Most of Media3 is safe under R8, but the OkHttpDataSource needs its
# factory class kept because we look it up via reflection in some
# extension points.
-keep class androidx.media3.datasource.okhttp.** { *; }
-keep class androidx.media3.exoplayer.hls.** { *; }
# Native bridges (ffmpeg / NDK extensions) — we don't ship any, but the
# AndroidX dependency tree references them and R8 would otherwise warn.
-dontwarn androidx.media3.extractor.text.**
-dontwarn androidx.media3.exoplayer.ext.**

# ─── ZXing (QR encoder, pure Java) ─────────────────────────────────────────
# The encoder doesn't use reflection, but ZXing's optional vendor classes
# (Android-only camera bits we don't pull in) emit dontwarn-able references.
-dontwarn com.google.zxing.client.android.**

# ─── Our own data layer — Moshi adapter generation already covers @JsonClass
#     DTOs, but the sealed types (MeEvent, SeriesResumeMode, …) that we
#     serialize across process boundaries are kept here defensively.
-keep class com.alex.hubplay.data.api.dto.** { *; }
-keepclassmembers class com.alex.hubplay.data.MeEvent$* { *; }

# ─── Compose stability detection (R8 sometimes strips @Immutable annotations
#     before the Compose compiler reads them in incremental builds). Defensive.
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep @androidx.compose.runtime.Stable class *

# ─── Crash reporting: keep our top-level exception classes named so
#     stack traces remain readable in mapping.txt-deobfuscated reports.
-keep public class com.alex.hubplay.HubplayApp
-keep public class com.alex.hubplay.MainActivity { public *; }
