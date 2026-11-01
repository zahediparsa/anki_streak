package app.ankistreak

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Saves the error when the app crashes, so the next launch can show it on screen
 * (instead of the app just closing) and it can be copied and reported.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val ctx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                save(ctx, error, "Crash in thread \"${thread.name}\"")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun save(context: Context, error: Throwable, where: String) {
        val ctx = context.applicationContext
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Throwable) {
            "?"
        }
        val text = buildString {
            appendLine(where)
            appendLine(
                "App $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                    "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            appendLine()
            append(Log.getStackTraceString(error).ifEmpty { error.toString() })
        }
        File(ctx.filesDir, FILE).writeText(text)
    }

    fun read(context: Context): String? =
        File(context.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE).delete()
    }
}
