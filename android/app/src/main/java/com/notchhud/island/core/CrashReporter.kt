package com.notchhud.island.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the last uncaught exception so the setup screen can show it.
 *
 * An overlay service crashes with nothing on screen to explain it, and getting a
 * logcat off a phone means enabling developer options and plugging into a
 * computer. Writing the trace to a file the app can display turns "it just
 * closes" into something reportable.
 *
 * The previous handler is always called afterwards, so the process still dies the
 * way Android expects — this only records on the way out.
 */
object CrashReporter {

    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Records a failure we caught ourselves, so it shows up the same way. */
    fun record(context: Context, label: String, throwable: Throwable) {
        runCatching { write(context.applicationContext, Thread.currentThread(), throwable, label) }
    }

    fun lastCrash(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText().ifBlank { null } else null
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable, label: String? = null) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

        val report = buildString {
            appendLine("Island HUD $versionName — ${label ?: "uncaught"} on ${thread.name}")
            appendLine("$stamp · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(stack)
        }

        File(context.filesDir, FILE_NAME).writeText(report)
    }
}
