package et.android.kharcha.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A rolling plain-text log of sync activity, written to
 * `getExternalFilesDir(null)/logs/sync.log` — no adb/USB debugging needed
 * to read it later, just `adb pull` (or a file manager) against a normal
 * app-external path. This exists because [SyncEngine] previously failed
 * silently on anything other than a clean device-revocation: a stuck sync
 * looked identical to a working one except for a bare "Offline" label,
 * with no way to tell what was actually going wrong. Every heartbeat and
 * every push/pull attempt logs here, success or failure, so a real
 * failure has a trail instead of vanishing into `runCatching`.
 */
object SyncLog {
    private const val MAX_BYTES = 512 * 1024L
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun logFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        return File(dir, "sync.log")
    }

    @Synchronized
    private fun append(context: Context, line: String) {
        val file = logFile(context)
        if (file.exists() && file.length() > MAX_BYTES) {
            val tail = file.readText().takeLast((MAX_BYTES / 2).toInt())
            file.writeText(tail)
        }
        file.appendText("${timestampFormat.format(Date())} $line\n")
    }

    fun info(context: Context, message: String) = append(context, "INFO  $message")

    fun error(context: Context, message: String, throwable: Throwable? = null) {
        val detail = throwable?.let { " (${it::class.simpleName}: ${it.message})" }.orEmpty()
        append(context, "ERROR $message$detail")
    }
}
