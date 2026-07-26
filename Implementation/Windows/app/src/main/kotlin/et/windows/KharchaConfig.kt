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

    /**
     * `%LOCALAPPDATA%\Kharcha` on a real Windows install — `Local`, not
     * `Roaming`, since a SQLite file has no business being synced across
     * machines by a roaming profile. Falls back to a home-directory
     * dotfolder when `LOCALAPPDATA` isn't set (e.g. developing from
     * WSL/Linux — see CLAUDE.md's testing strategy, the app itself always
     * still *runs* natively on Windows). Dev mode uses a sibling folder so
     * a side-by-side test instance never touches real data.
     */
    fun dataDir(): File {
        val folderName = if (isDev) "Kharcha-dev" else "Kharcha"
        val localAppData = System.getenv("LOCALAPPDATA")
        val base = if (localAppData != null) File(localAppData) else File(System.getProperty("user.home"), ".kharcha-appdata")
        return File(base, folderName)
    }

    /** Shown to other devices during pairing so it's obvious which physical machine they're connecting to — see README V1 "identify the server by computer name." */
    fun serverDisplayName(): String {
        val computerName = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "Kharcha"
        return if (isDev) "Kharcha (dev) — $computerName" else "Kharcha — $computerName"
    }
}
