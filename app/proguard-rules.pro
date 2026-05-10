# Standard rules. Compose, Retrofit, OkHttp, Moshi, and Coroutines all
# ship with their own consumer-proguard rules so we rarely need to add
# anything here. Keep this file for app-specific rules only.

# Moshi: keep generated Kotlin adapter classes. KSP-generated adapters use
# the Kotlin metadata at runtime; without these rules R8 strips them.
-keep class **JsonAdapter { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# OkHttp / Okio: silence warnings for optional Conscrypt / Bouncy Castle.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
