package et.windows.update

import et.windows.APP_VERSION
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val REPO = "mohansreehari39/Expense_Tracker"

@Serializable
private data class GithubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GithubReleaseAsset> = emptyList(),
)

data class UpdateInfo(val version: String, val downloadUrl: String, val releaseUrl: String)

/**
 * Talks directly to GitHub's public REST API over plain HTTPS — deliberately
 * NOT the `gh` CLI, which only exists in a development/agent environment and
 * would never be present on an end user's machine; the packaged app must be
 * fully self-contained. No auth token needed since release metadata on a
 * public repo is a public, unauthenticated GET.
 */
object UpdateChecker {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    /** Returns null on any failure (offline, rate-limited, no releases published yet, or already on the latest version) — a failed/negative check is always silent, never blocking or erroring visibly. */
    suspend fun checkForUpdate(): UpdateInfo? = runCatching {
        val release = client.get("https://api.github.com/repos/$REPO/releases/latest") {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "Kharcha-App") // GitHub's API rejects requests with no User-Agent at all.
        }.body<GithubRelease>()
        val latestVersion = release.tagName.removePrefix("v")
        if (latestVersion == APP_VERSION) return@runCatching null
        val installerAsset = release.assets.find { it.name.endsWith(".exe") } ?: return@runCatching null
        UpdateInfo(version = latestVersion, downloadUrl = installerAsset.browserDownloadUrl, releaseUrl = release.htmlUrl)
    }.getOrNull()
}
