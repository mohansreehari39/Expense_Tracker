package et.windows.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import et.windows.KharchaConfig
import et.windows.db.sql.WindowsDatabase
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Data directory resolution lives in [KharchaConfig.dataDir] — `%LOCALAPPDATA%\Kharcha\data.db` on a real install. */
fun openDatabase(): WindowsDatabase {
    val dir = KharchaConfig.dataDir()
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
                    email TEXT,
                    phone TEXT,
                    isArchived INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS pairedDevice (
                    id TEXT NOT NULL PRIMARY KEY,
                    label TEXT NOT NULL,
                    pairingKey TEXT NOT NULL DEFAULT '',
                    pairedAt INTEGER NOT NULL,
                    lastSeenAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS subcategory (
                    id TEXT NOT NULL PRIMARY KEY,
                    categoryId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    isArchived INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS householdSettlement (
                    id TEXT NOT NULL PRIMARY KEY,
                    householdId TEXT NOT NULL,
                    fromMemberId TEXT NOT NULL,
                    toMemberId TEXT NOT NULL,
                    amountMinorUnits INTEGER NOT NULL,
                    currency TEXT NOT NULL,
                    settledAt INTEGER NOT NULL,
                    note TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS householdDependent (
                    id TEXT NOT NULL PRIMARY KEY,
                    householdId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    isArchived INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS householdExpenseBeneficiary (
                    id TEXT NOT NULL PRIMARY KEY,
                    householdExpenseId TEXT NOT NULL,
                    memberId TEXT,
                    dependentId TEXT,
                    amountMinorUnits INTEGER NOT NULL,
                    currency TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS householdExpenseContribution (
                    id TEXT NOT NULL PRIMARY KEY,
                    householdExpenseId TEXT NOT NULL,
                    memberId TEXT NOT NULL,
                    amountMinorUnits INTEGER NOT NULL,
                    currency TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS tripExpenseContribution (
                    id TEXT NOT NULL PRIMARY KEY,
                    tripExpenseId TEXT NOT NULL,
                    participantId TEXT NOT NULL,
                    amountMinorUnits INTEGER NOT NULL,
                    currency TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
        addColumnIfMissing(connection, "member", "isArchived", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(connection, "member", "email", "TEXT")
        addColumnIfMissing(connection, "member", "phone", "TEXT")
        addColumnIfMissing(connection, "tripParticipant", "isArchived", "INTEGER NOT NULL DEFAULT 0")
        // Existing pairedDevice rows from before pairingKey existed get ''
        // (never matches a real client-held key), which is correct: those
        // stale pairings should require a fresh "Add Android Device" scan
        // rather than silently keep heartbeating.
        addColumnIfMissing(connection, "pairedDevice", "pairingKey", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(connection, "household", "settlementEnabled", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(connection, "householdExpense", "subcategoryId", "TEXT")
        addColumnIfMissing(connection, "tripExpense", "subcategoryId", "TEXT")
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
