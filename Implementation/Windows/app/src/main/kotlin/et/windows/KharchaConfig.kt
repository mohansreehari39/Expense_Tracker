package et.windows

import java.io.File

/**
 * Central place for anything that must differ between a real installed
 * instance and a developer's side-by-side test instance running from
 * Gradle — see README "Pending for production" / V1 "Separate prod/dev
 * port + data directory". The installed app (`setup.exe`) never sets
 * `kharcha.dev` and gets the real port/data directory with zero external
 * configuration; only the `runDev` Gradle task passes
 * `-Dkharcha.dev=true` as a JVM system property (never an OS environment
 * variable) to opt into the dev port/data directory instead.
 */
object KharchaConfig {
    private const val PROD_PORT = 47321
    private const val DEV_PORT = 47399

    val isDev: Boolean = System.getProperty("kharcha.dev") == "true"

    val port: Int get() = if (isDev) DEV_PORT else PROD_PORT

    private fun defaultBase(): File {
        val localAppData = System.getenv("LOCALAPPDATA")
        return if (localAppData != null) File(localAppData) else File(System.getProperty("user.home"), ".kharcha-appdata")
    }

    /**
     * A fixed, never-overridden location (`%LOCALAPPDATA%\Kharcha\datadir.cfg`)
     * whose *contents* — if present — name the real data directory
     * elsewhere. This is how [dataDir] supports a user-chosen location
     * (see [et.windows.ui.DataLocationDialog]) without a chicken-and-egg
     * problem: the pointer file itself always lives at the one well-known
     * default path, only the data it *points to* moves.
     */
    private fun pointerFile(): File = File(defaultBase(), "Kharcha/datadir.cfg")

    /** Currently-effective data directory, following the pointer file if one exists — see [pointerFile]. */
    fun dataDir(): File {
        val folderName = if (isDev) "Kharcha-dev" else "Kharcha"
        val default = File(defaultBase(), folderName)
        if (isDev) return default // dev mode never honors the override — always the sibling folder, so a side-by-side test instance can't collide with real data.
        val override = pointerFile().takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        return if (override != null) File(override) else default
    }

    /** Default data directory the user-chosen override sits in front of — what [DataLocationDialog] shows/resets to. */
    fun defaultDataDir(): File = File(defaultBase(), "Kharcha")

    /** Persists a user-chosen data directory — see [pointerFile]. Takes effect on next launch; the already-open database connection isn't moved. */
    fun setDataDirOverride(dir: File?) {
        val pointer = pointerFile()
        if (dir == null) {
            pointer.delete()
        } else {
            pointer.parentFile?.mkdirs()
            pointer.writeText(dir.absolutePath)
        }
    }

    /** Shown to other devices during pairing so it's obvious which physical machine they're connecting to — see README V1 "identify the server by computer name." */
    fun serverDisplayName(): String {
        val computerName = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "Kharcha"
        return if (isDev) "Kharcha (dev) — $computerName" else "Kharcha — $computerName"
    }
}
