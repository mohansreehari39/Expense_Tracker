package et.windows.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import et.core.api.HouseholdExpenseRecord
import et.core.api.PullResponse
import et.core.api.ScopeKind
import et.core.api.SyncScope
import et.core.model.HLC_ZERO
import et.core.model.Hlc
import et.core.model.HlcClock
import et.windows.db.sql.WindowsDatabase
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A database created by the released app (v0.1.2 schema) upgrades in place, keeping every row. */
class MigrationTest {
    @Test
    fun releasedDatabaseUpgradesWithoutLosingData() {
        val file = File.createTempFile("kharcha-v012", ".db").apply { deleteOnExit() }
        val url = "jdbc:sqlite:${file.absolutePath}"
        DriverManager.getConnection(url).use { connection ->
            val oldSchema = javaClass.getResource("/schema-v0.1.2.sql")!!.readText()
            oldSchema.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { connection.createStatement().execute(it) }
            connection.createStatement().execute("INSERT INTO household VALUES ('home', 'Home', 1, 6000000, 'INR', 1)")
            connection.createStatement().execute("INSERT INTO member VALUES ('asha', 'home', 'Asha', 'phone-a', 'a@example.com', '99', 0)")
            connection.createStatement().execute(
                "INSERT INTO householdExpense VALUES ('exp-1', 'home', 'groceries', NULL, 234050, 'INR', 'asha', 1000, 'veg', 'phone-a', 1000)",
            )
        }

        migrateExistingDatabase(url)
        val db = WindowsDatabase(JdbcSqliteDriver(url))
        val store = SyncStore(db, Stamper(HlcClock("server"), db.schemaQueries.maxServerSeq().executeAsOne().maxSeq ?: 0))

        val pulled = store.pull(SyncScope(ScopeKind.HOUSEHOLD, "home"), PullResponse.START)
        assertEquals(setOf("home", "asha", "exp-1"), pulled.records.map { it.id }.toSet())
        val expense = pulled.records.filterIsInstance<HouseholdExpenseRecord>().single()
        assertEquals(234050, expense.amountMinorUnits)
        assertEquals("veg", expense.note)
        assertEquals(HLC_ZERO, expense.updatedAt) // pre-sync rows count as the oldest version
        assertTrue(store.apply(expense.copy(updatedAt = Hlc(1, 0, "phone-a"), note = "vegetables")).accepted)

        // Running the upgrade again (every launch does) changes nothing.
        migrateExistingDatabase(url)
        assertEquals("vegetables", WindowsDatabase(JdbcSqliteDriver(url)).schemaQueries.selectHouseholdExpenseById("exp-1").executeAsOne().note)
    }
}
