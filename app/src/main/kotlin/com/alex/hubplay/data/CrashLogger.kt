package com.alex.hubplay.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to a local file so a user reporting a
 * crash can paste the trace into a support thread.
 *
 * Why on-device + no third parties? Two reasons:
 *
 *  1. Privacy. The user's library titles, server URL, JWT contents
 *     and viewing habits leak into stack traces all the time. Sending
 *     those to a SaaS provider (Crashlytics, Bugsnag) is a Play
 *     Store Data Safety declaration we don't want to make for a
 *     self-hosted client. Local-first matches the rest of HubPlay's
 *     posture.
 *  2. Operator-friendly. The user already trusts their own server —
 *     pasting a stack trace into a Discord / Issue is the natural
 *     escalation path. Settings → Ver logs makes that one tap away.
 *
 * Behaviour:
 *  - On uncaught exception: append a serialised entry to
 *    `context.filesDir/crash-log.txt`, trim to the last [MAX_ENTRIES]
 *    so the file never grows unbounded.
 *  - Then delegate to the system's previous handler so the app still
 *    crashes (we don't suppress — that would let the app keep running
 *    in a broken state and produce stranger bugs).
 *  - Reads are cheap and synchronous: the Settings screen's "View logs"
 *    just loads the file into memory and shows it.
 *
 * Thread safety: setDefaultUncaughtExceptionHandler is called once at
 * HubplayApp.onCreate. The file write inside the handler is on the
 * crashing thread (no coroutines — the process is about to die). We
 * use a small synchronized block to serialise the (extremely rare)
 * case where two threads crash at once.
 */
class CrashLogger private constructor(
    private val logFile:        File,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    private val writeLock = Any()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { appendEntry(thread, throwable) }
            .onFailure { Log.w(TAG, "crash logger failed to write entry", it) }
        // Delegate so the app still crashes — never swallow uncaught
        // exceptions, that hides bugs and produces zombie processes.
        previousHandler?.uncaughtException(thread, throwable)
    }

    private fun appendEntry(thread: Thread, throwable: Throwable) {
        synchronized(writeLock) {
            val entry = buildString {
                append("─── ")
                append(timestampFormat.format(Date()))
                append(" · thread=")
                append(thread.name)
                append(" ───\n")
                append(Log.getStackTraceString(throwable))
                append("\n")
            }
            val existing = if (logFile.exists()) logFile.readText() else ""
            val combined = (existing + entry).takeLastEntries(MAX_ENTRIES)
            logFile.writeText(combined)
        }
    }

    /** Returns the raw log text — newest entry last. Safe to call from any thread. */
    fun read(): String = synchronized(writeLock) {
        if (logFile.exists()) logFile.readText() else ""
    }

    /** Clears the log file. Used by the "borrar logs" action in Settings. */
    fun clear() = synchronized(writeLock) {
        runCatching { logFile.delete() }
    }

    companion object {
        private const val TAG = "CrashLogger"
        private const val MAX_ENTRIES = 10
        private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        private const val ENTRY_SEPARATOR = "─── "

        /**
         * Install the logger as the default uncaught-exception handler.
         * Returns the installed instance so the rest of the app can
         * read / clear the log via [CrashLogger.read] / [CrashLogger.clear].
         */
        fun install(context: Context): CrashLogger {
            val file = File(context.filesDir, "crash-log.txt")
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val logger = CrashLogger(file, previous)
            Thread.setDefaultUncaughtExceptionHandler(logger)
            return logger
        }

        /**
         * Trim a concatenated log text to the last [n] entries.
         * Entries are separated by the [ENTRY_SEPARATOR] header line.
         * Done in-memory because each entry is < 4 KB and we cap at 10.
         */
        private fun String.takeLastEntries(n: Int): String {
            val markers = mutableListOf<Int>()
            var idx = indexOf(ENTRY_SEPARATOR)
            while (idx >= 0) {
                markers += idx
                idx = indexOf(ENTRY_SEPARATOR, idx + 1)
            }
            if (markers.size <= n) return this
            val start = markers[markers.size - n]
            return substring(start)
        }
    }
}
