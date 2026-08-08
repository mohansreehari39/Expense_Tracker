package et.android.kharcha.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Unrelated to [ApiClient] (which only ever talks to a paired Windows server on the LAN) — this hits the public GitHub API to look for a newer release. */
private const val RELEASES_URL = "https://api.github.com/repos/mohansreehari39/Expense_Tracker/releases/latest"

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

data class UpdateInfo(val version: String, val downloadUrl: String, val notes: String)

object UpdateChecker {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    /** Returns the latest GitHub release's info if its tag is newer than [currentVersionName] and it ships an .apk asset, else null (includes "no newer release" and "request failed" — both are just "nothing to offer"). */
    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? {
        val release = runCatching { client.get(RELEASES_URL).body<GithubRelease>() }.getOrNull() ?: return null
        val remoteVersion = release.tagName.removePrefix("v")
        if (!isNewer(remoteVersion, currentVersionName)) return null
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return UpdateInfo(version = remoteVersion, downloadUrl = apkAsset.browserDownloadUrl, notes = release.body.orEmpty())
    }

    /** Component-wise integer comparison ("0.10.0" > "0.9.0"), not a string comparison. Missing components read as 0, so "0.2" beats "0.1.5" and "0.1" doesn't falsely beat "0.1.0". */
    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
