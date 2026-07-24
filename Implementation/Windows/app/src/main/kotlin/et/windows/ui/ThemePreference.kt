package et.windows.ui

import java.io.File

/** Remembers the user's light/dark choice across launches — `~/.kharcha/theme.txt`. */
object ThemePreference {
    private val file = File(System.getProperty("user.home"), ".kharcha/theme.txt")

    fun load(): Boolean = file.takeIf { it.exists() }?.readText()?.trim() == "dark"

    fun save(darkTheme: Boolean) {
        file.parentFile?.mkdirs()
        file.writeText(if (darkTheme) "dark" else "light")
    }
}
