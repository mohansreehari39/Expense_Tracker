package et.windows.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads the installer for [update] to a temp file and launches it as an
 * independent OS process, then returns — the caller is expected to exit the
 * running app right after so the installer (which replaces this same
 * install directory) isn't fighting a still-running instance of itself.
 */
object UpdateInstaller {
    suspend fun downloadAndLaunch(update: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = HttpClient(CIO)
            val bytes: ByteArray = client.get(update.downloadUrl).body()
            client.close()
            val installerFile = File(System.getProperty("java.io.tmpdir"), "Kharcha-${update.version}-setup.exe")
            installerFile.writeBytes(bytes)
            ProcessBuilder(installerFile.absolutePath).start()
            Unit
        }
    }
}
