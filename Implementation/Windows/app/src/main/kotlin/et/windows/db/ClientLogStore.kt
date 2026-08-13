package et.windows.db

import et.windows.KharchaConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling per-device log file for diagnostics Android devices report
 * about their own sync attempts — see the matching `SyncLog`/`syncAll` on
 * the Android side. Deliberately a plain file next to the database
 * (`<dataDir>/logs/client-<deviceId>.log`), not a SQL table: this is
 * throwaway diagnostic text, not domain data that needs querying, and a
 * file is trivial to open from the Windows machine directly — no adb, no
 * physical access to the phone, which is the whole point (a real phone in
 * daily use is never going to be plugged into a debugging session).
 */
object ClientLogStore {
    private const val MAX_BYTES = 512 * 1024L
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun logFile(deviceId: String): File {
        val dir = File(KharchaConfig.dataDir(), "logs").apply { mkdirs() }
        val safeName = deviceId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "unknown" }
        return File(dir, "client-$safeName.log")
    }

    @Synchronized
    fun append(deviceId: String, label: String, level: String, message: String) {
        val file = logFile(deviceId)
        if (file.exists() && file.length() > MAX_BYTES) {
            val tail = file.readText().takeLast((MAX_BYTES / 2).toInt())
            file.writeText(tail)
        }
        file.appendText("${timestampFormat.format(Date())} [$label] $level $message\n")
    }
}
