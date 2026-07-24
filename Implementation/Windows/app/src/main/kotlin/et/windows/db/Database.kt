package et.windows.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import et.windows.db.sql.WindowsDatabase
import java.io.File

/**
 * v0 simplification: stores the DB under the user's home directory
 * (`~/.expense-tracker/data.db`) rather than `%APPDATA%` — see
 * Design/Windows/05-tray-lifecycle-and-config.md for the intended
 * Windows-specific location, added once packaging/config loading exists.
 */
fun openDatabase(): WindowsDatabase {
    val dir = File(System.getProperty("user.home"), ".expense-tracker")
    dir.mkdirs()
    val dbFile = File(dir, "data.db")
    val isNewDatabase = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (isNewDatabase) {
        WindowsDatabase.Schema.create(driver)
    }
    return WindowsDatabase(driver)
}
