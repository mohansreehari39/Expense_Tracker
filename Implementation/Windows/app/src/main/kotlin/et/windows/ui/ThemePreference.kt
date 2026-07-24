package et.windows.ui

import java.io.File

/**
 * Remembers an explicit light/dark choice from the title bar toggle,
 * across launches — `~/.kharcha/theme.txt`. [load] returns null when the
 * user has never touched the toggle, so the caller can fall back to
 * [SystemTheme] instead of silently assuming light.
 */
object ThemePreference {
    private val file = File(System.getProperty("user.home"), ".kharcha/theme.txt")

    fun load(): Boolean? = when (file.takeIf { it.exists() }?.readText()?.trim()) {
        "dark" -> true
        "light" -> false
        else -> null
    }

    fun save(darkTheme: Boolean) {
        file.parentFile?.mkdirs()
        file.writeText(if (darkTheme) "dark" else "light")
    }
}
