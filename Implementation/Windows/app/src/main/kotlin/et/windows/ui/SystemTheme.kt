package et.windows.ui

/**
 * Best-effort OS dark-mode detection, used only as the *default* before
 * the user has ever touched the manual toggle (see `ThemePreference`).
 * There's no single cross-platform Java API for this, so each OS is
 * queried in its own way; returns null (caller falls back to light) if
 * the platform isn't recognized or detection fails for any reason.
 */
object SystemTheme {
    fun isDark(): Boolean? {
        val os = System.getProperty("os.name")?.lowercase() ?: return null
        return try {
            when {
                "win" in os -> isWindowsDark()
                "mac" in os -> isMacDark()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isWindowsDark(): Boolean? {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        // Line looks like: "    AppsUseLightTheme    REG_DWORD    0x0" — 0 = dark, 1 = light.
        val match = Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x(\\d+)").find(output) ?: return null
        val lightThemeValue = match.groupValues[1].toIntOrNull(16) ?: return null
        return lightThemeValue == 0
    }

    private fun isMacDark(): Boolean? {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        // Prints "Dark" if dark mode is on; errors out (non-zero exit) if it's light.
        return output.trim().equals("Dark", ignoreCase = true)
    }
}
