package et.windows.db

import et.windows.db.sql.WindowsDatabase

/**
 * v0 has no onboarding UI yet, so seed a single default household +
 * starter categories on first run so the dashboard isn't empty. Bypasses
 * the domain Repository deliberately — there's no "create household" use
 * case yet (only households that already exist are modeled), and this is
 * a one-time local bootstrap concern, not a synced domain event.
 */
fun bootstrapIfEmpty(db: WindowsDatabase) {
    if (db.schemaQueries.selectHousehold().executeAsOneOrNull() != null) return

    val householdId = "household-1"
    db.schemaQueries.upsertHousehold(householdId, "My Household", System.currentTimeMillis())

    val defaultCategories = listOf(
        "category-groceries" to "Groceries",
        "category-utilities" to "Utilities",
        "category-rent" to "Rent",
        "category-eating-out" to "Eating Out",
        "category-other" to "Other",
    )
    for ((id, name) in defaultCategories) {
        db.schemaQueries.upsertCategory(id, householdId, name, "", 0L)
    }
}
