package com.alex.hubplay.data

/**
 * Source of "now" expressed as milliseconds-since-epoch.
 *
 * Why a custom interface and not [java.time.Clock] or
 * [kotlin.time.TimeSource]:
 *  - `java.time.Clock` returns `Instant`, which adds a parse/convert
 *    hop for the only consumer that needs a Long (the
 *    `ProgressReporter` throttle).
 *  - `kotlin.time.TimeSource` is for measuring elapsed durations, not
 *    absolute wall-clock — it returns a `TimeMark` not a millis-since-epoch
 *    value.
 *
 * A 1-method `fun interface` is the smallest thing that lets tests
 * advance virtual time deterministically. Production code uses
 * [SYSTEM]; unit tests pass a mutable fake.
 */
fun interface TimeSource {
    fun nowMs(): Long

    companion object {
        /** Production-time source: forwards to `System.currentTimeMillis()`. */
        val SYSTEM: TimeSource = TimeSource { System.currentTimeMillis() }
    }
}
