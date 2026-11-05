package dev.warden.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The last crash, kept on this phone only. Nothing is ever sent: a person reads the report in Settings
 * and copies it into a bug report themselves, or deletes it. Holds the stack trace and device model,
 * not traffic history, though an exception message could name an app or domain, which is why it's shown first.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "crash_log"
    private const val CLEARED_AT = "cleared_at"
    private const val MAX_CHARS = 16_000
    private const val EXITS = 5

    /** Saves uncaught exceptions, then hands over to the system's handler so the app still dies as normal. */
    fun install(context: Context) {
        val app = context.applicationContext
        val next = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val crashed = "Crashed ${stamp(System.currentTimeMillis())} on thread ${thread.name}"
                File(app.filesDir, FILE).writeText("${header(app)}\n$crashed\n\n${error.stackTraceToString()}")
            }
            next?.uncaughtException(thread, error)
        }
    }

    /** What's saved since the last delete, or null if nothing went wrong. Reads a file, so call off the main thread. */
    fun report(context: Context): String? {
        val saved = File(context.filesDir, FILE).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
        val exits = abnormalExits(context)
        if (saved == null && exits.isEmpty()) return null
        return buildString {
            append(saved ?: header(context))
            if (exits.isNotEmpty()) append("\n\nRecent exits:\n").append(exits.joinToString("\n"))
        }.take(MAX_CHARS)
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(CLEARED_AT, System.currentTimeMillis()).apply()
    }

    /**
     * Android's own record of how the app last died. It covers what an exception handler can't: native
     * crashes (such as in the engine), freezes, and Android 17's memory limiter.
     */
    private fun abnormalExits(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val since = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(CLEARED_AT, 0)
        val am = context.getSystemService(ActivityManager::class.java)
        return runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, EXITS) }.getOrDefault(emptyList())
            .filter { it.timestamp > since }
            .mapNotNull { exit ->
                val why = when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "crash"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
                    ApplicationExitInfo.REASON_ANR -> "not responding"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
                    ApplicationExitInfo.REASON_OTHER -> "other".takeIf { exit.description?.contains("MemoryLimiter") == true }
                    else -> null
                } ?: return@mapNotNull null
                "${stamp(exit.timestamp)}: $why (${exit.description.orEmpty()})"
            }
    }

    private fun header(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return "Warden $version\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    private fun stamp(time: Long) = DateFormat.getDateTimeInstance().format(Date(time))
}
