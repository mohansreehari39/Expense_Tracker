package et.windows.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import et.windows.db.sql.WindowsDatabase
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * v0 simplification: stores the DB under the user's home directory
 * (`~/.kharcha/data.db`) rather than `%APPDATA%` — see
 * Design/Windows/05-tray-lifecycle-and-config.md for the intended
 * Windows-specific location, added once packaging/config loading exists.
 */
fun openDatabase(): WindowsDatabase {
    val dir = File(System.getProperty("user.home"), ".kharcha")
    dir.mkdirs()
    val dbFile = File(dir, "data.db")
    val isNewDatabase = !dbFile.exists()
    val url = "jdbc:sqlite:${dbFile.absolutePath}"
    val driver = JdbcSqliteDriver(url)
    if (isNewDatabase) {
        WindowsDatabase.Schema.create(driver)
    } else {
        migrateExistingDatabase(url)
    }
    return WindowsDatabase(driver)
}

/**
 * v0 has no real migration framework — [WindowsDatabase.Schema.create] only
 * runs for a brand-new file, so an install from before a table or column
 * was added would otherwise fail with "no such table"/"no such column"
 * forever. Each new table/column added to Schema.sq needs a matching line
 * here: `CREATE TABLE IF NOT EXISTS` for a new table (already including any
 * columns added since), and [addColumnIfMissing] for a column added to a
 * table that already shipped.
 */
private fun migrateExistingDatabase(url: String) {
    DriverManager.getConnection(url).use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS member (
                    id TEXT NOT NULL PRIMARY KEY,
                    householdId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    deviceId TEXT,
                    isArchived INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
        addColumnIfMissing(connection, "member", "isArchived", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(connection, "tripParticipant", "isArchived", "INTEGER NOT NULL DEFAULT 0")
    }
}

private fun addColumnIfMissing(connection: Connection, table: String, column: String, columnDdl: String) {
    val hasColumn = connection.createStatement().executeQuery("PRAGMA table_info($table)").use { rs ->
        generateSequence { if (rs.next()) rs.getString("name") else null }.any { it.equals(column, ignoreCase = true) }
    }
    if (!hasColumn) {
        connection.createStatement().execute("ALTER TABLE $table ADD COLUMN $column $columnDdl")
    }
}
