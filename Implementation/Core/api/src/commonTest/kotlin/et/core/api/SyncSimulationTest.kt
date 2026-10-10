package et.core.api

import et.core.model.HLC_ZERO
import et.core.model.StableIds
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plays the corner cases in Test/Sync/corner-cases.md across a simulated
 * Windows server and phones ([SyncSimulator.kt]). Each test names its case.
 */
class SyncSimulationTest {
    private val time = SimTime()
    private val server = SimServer(time)
    private val p1 = SimPhone("phone-1", time, personId = "m1")
    private val p2 = SimPhone("phone-2", time, personId = "m2")

    private val household = HouseholdRecord(id = "h", updatedAt = HLC_ZERO, name = "Home", createdAt = 0, currency = "INR")
    private val category = CategoryRecord(id = "c", updatedAt = HLC_ZERO, householdId = "h", name = "Food")

    init {
        // The household is set up on Windows and both phones have joined.
        server.edit(household)
        server.edit(category)
        server.edit(MemberRecord(id = "m1", updatedAt = HLC_ZERO, householdId = "h", displayName = "Asha", deviceId = "phone-1"))
        server.edit(MemberRecord(id = "m2", updatedAt = HLC_ZERO, householdId = "h", displayName = "Ravi", deviceId = "phone-2"))
        step()
        p1.syncWithServer(server).getOrThrow()
        p2.syncWithServer(server).getOrThrow()
        step()
    }

    private fun step() = time.advance()

    private fun expense(id: String, owner: String?, amount: Long = 10_000, note: String = "") = HouseholdExpenseRecord(
        id = id,
        updatedAt = HLC_ZERO,
        householdId = "h",
        categoryId = "c",
        amountMinorUnits = amount,
        currency = "INR",
        paidByMemberId = owner ?: "m1",
        occurredAt = 0,
        note = note,
        ownerId = owner,
    )

    private inline fun <reified T : SyncRecord> SimPhone.get(id: String): T = records.getValue(id) as T

    private inline fun <reified T : SyncRecord> SimServer.get(id: String): T = records.getValue(id) as T

    private fun assertConverged(vararg phones: SimPhone) {
        for (phone in phones) {
            assertEquals(server.records, phone.records, "${phone.deviceId} should match the server")
            assertTrue(phone.pending().isEmpty(), "${phone.deviceId} should have nothing pending")
        }
    }

    private fun syncAll(vararg phones: SimPhone) {
        repeat(2) { phones.forEach { it.syncWithServer(server).getOrThrow() } }
    }

    @Test
    fun usersScenarioTwoPhonesThenServer() {
        // 1–2: each phone adds an expense offline.
        p1.edit(expense("e1", owner = "m1", amount = 100_00))
        p2.edit(expense("e2", owner = "m2", amount = 200_00))
        step()
        // 3: the phones sync with each other.
        p1.syncWithPhone(p2)
        step()
        // 4: phone 2 tries to change both; only its own goes through (S8).
        assertFalse(p2.edit(p2.get<HouseholdExpenseRecord>("e1").copy(amountMinorUnits = 150_00)))
        assertTrue(p2.edit(p2.get<HouseholdExpenseRecord>("e2").copy(amountMinorUnits = 250_00)))
        step()
        // 5–6: they sync again, then each reaches the server.
        p1.syncWithPhone(p2)
        p2.syncWithServer(server).getOrThrow()
        p1.syncWithServer(server).getOrThrow()
        syncAll(p1, p2)

        assertEquals(100_00, server.get<HouseholdExpenseRecord>("e1").amountMinorUnits)
        assertEquals(250_00, server.get<HouseholdExpenseRecord>("e2").amountMinorUnits)
        assertEquals(2, server.records.values.count { it is HouseholdExpenseRecord }, "no duplicates")
        assertConverged(p1, p2)
    }

    @Test
    fun s14ExpenseReachesServerThroughPeerWhenOwnerPhoneIsLost() {
        p1.edit(expense("e1", owner = "m1"))
        step()
        p1.syncWithPhone(p2)
        // Phone 1 is lost. Phone 2 still holds e1 as pending — it was never confirmed by a server.
        assertTrue(p2.pending().any { it.id == "e1" })
        p2.syncWithServer(server).getOrThrow()

        assertEquals("m1", server.get<HouseholdExpenseRecord>("e1").ownerId)
        assertConverged(p2)
    }

    @Test
    fun s7DifferentFieldsBothKept() {
        p1.edit(household.copy(name = "Our Home"))
        step()
        p2.edit(p2.get<HouseholdRecord>("h").copy(defaultBudgetMinorUnits = 50_000_00))
        step()
        p1.syncWithServer(server).getOrThrow()
        p2.syncWithServer(server).getOrThrow()
        syncAll(p1, p2)

        val merged = server.get<HouseholdRecord>("h")
        assertEquals("Our Home", merged.name)
        assertEquals(50_000_00, merged.defaultBudgetMinorUnits)
        assertConverged(p1, p2)
    }

    @Test
    fun s7SameFieldLaterChangeWins() {
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "Groceries"))
        step()
        p2.edit(p2.get<CategoryRecord>("c").copy(name = "Food & Drink"))
        step()
        p2.syncWithServer(server).getOrThrow()
        p1.syncWithServer(server).getOrThrow()
        syncAll(p1, p2)

        assertEquals("Food & Drink", server.get<CategoryRecord>("c").name)
        assertConverged(p1, p2)
    }

    @Test
    fun s10LateStalePushDoesNotUndoNewerChange() {
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "Old idea"))
        step()
        p2.edit(p2.get<CategoryRecord>("c").copy(name = "Newer idea"))
        p2.syncWithServer(server).getOrThrow()
        step()
        // Phone 1 comes online much later with its older edit.
        repeat(60) { step() }
        p1.syncWithServer(server).getOrThrow()
        syncAll(p1, p2)

        assertEquals("Newer idea", server.get<CategoryRecord>("c").name)
        assertConverged(p1, p2)
    }

    @Test
    fun windowsWinsAClashButNotALaterEdit() {
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "Phone name"))
        step()
        // Windows changes the same field afterwards — and also when it's earlier, it still wins the clash.
        server.edit(server.get<CategoryRecord>("c").copy(name = "Windows name"))
        step()
        p1.syncWithServer(server).getOrThrow()
        assertEquals("Windows name", server.get<CategoryRecord>("c").name)
        assertEquals("Windows name", p1.get<CategoryRecord>("c").name)

        // Having seen Windows' change, the phone's next edit is not a clash and applies.
        step()
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "Phone again"))
        p1.syncWithServer(server).getOrThrow()
        assertEquals("Phone again", server.get<CategoryRecord>("c").name)
        assertConverged(p1)
    }

    @Test
    fun windowsWinsClashEvenWhenPhoneEditIsLater() {
        server.edit(server.get<CategoryRecord>("c").copy(name = "Windows name"))
        step()
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "Later phone name"))
        step()
        p1.syncWithServer(server).getOrThrow()

        assertEquals("Windows name", server.get<CategoryRecord>("c").name)
        assertConverged(p1)
    }

    @Test
    fun ownerAndWindowsEditDifferentFieldsOfAnExpense() {
        p1.edit(expense("e1", owner = "m1"))
        p1.syncWithServer(server).getOrThrow()
        step()
        p1.edit(p1.get<HouseholdExpenseRecord>("e1").copy(note = "dinner"))
        server.edit(server.get<HouseholdExpenseRecord>("e1").copy(amountMinorUnits = 120_00))
        step()
        p1.syncWithServer(server).getOrThrow()
        syncAll(p1)

        val merged = server.get<HouseholdExpenseRecord>("e1")
        assertEquals("dinner", merged.note)
        assertEquals(120_00, merged.amountMinorUnits)
        assertConverged(p1)
    }

    @Test
    fun s8ServerRefusesChangeToSomeoneElsesExpense() {
        p1.edit(expense("e1", owner = "m1"))
        p1.syncWithServer(server).getOrThrow()
        p2.syncWithServer(server).getOrThrow()
        step()
        // A buggy or old phone 2 changes e1 anyway (the app itself refuses — see the scenario test).
        val sneaky = RecordMerge.edited(
            p2.get<HouseholdExpenseRecord>("e1"),
            p2.get<HouseholdExpenseRecord>("e1").copy(amountMinorUnits = 1),
            p2.tick(),
        )
        p2.local["e1"] = SimPhone.Local(sneaky, p2.local.getValue("e1").synced)
        p2.syncWithServer(server).getOrThrow()

        assertEquals(100_00, server.get<HouseholdExpenseRecord>("e1").amountMinorUnits)
        assertEquals(100_00, p2.get<HouseholdExpenseRecord>("e1").amountMinorUnits, "phone 2 takes the server's answer")
        assertConverged(p2)
    }

    @Test
    fun s8OnlyOwnerMayAddAnExpenseInTheirName() {
        // Phone 2 tries to add an expense owned by m1: the app refuses …
        assertFalse(p2.edit(expense("e9", owner = "m1")))
        // … and so does the server, if one arrives anyway.
        val forged = RecordMerge.edited(null, expense("e9", owner = "m1"), p2.tick())
        val result = server.push(PushRequest(listOf(forged))).getOrThrow().results.single()
        assertFalse(result.accepted)
        assertEquals(SimRejection.NOT_OWNER, result.rejected)
        assertNull(server.records["e9"])
    }

    @Test
    fun s12EditDuringPushIsKeptAndSentNext() {
        p1.edit(expense("e1", owner = "m1"))
        step()
        val request = p1.preparePush()
        // The user edits again while the push is in flight.
        p1.edit(p1.get<HouseholdExpenseRecord>("e1").copy(note = "edited in flight"))
        p1.receivePush(request, server.push(request).getOrThrow())

        assertEquals("edited in flight", p1.get<HouseholdExpenseRecord>("e1").note)
        assertTrue(p1.pending().any { it.id == "e1" })
        p1.syncWithServer(server).getOrThrow()
        assertEquals("edited in flight", server.get<HouseholdExpenseRecord>("e1").note)
        assertConverged(p1)
    }

    @Test
    fun s17LostReplyResendsWithoutDuplicates() {
        p1.edit(expense("e1", owner = "m1"))
        step()
        p1.syncWithServer(server, dropReply = true).getOrThrow()
        assertTrue(p1.pending().any { it.id == "e1" }, "no reply, so still pending")
        val before = server.pull(PullResponse.START).cursor
        p1.syncWithServer(server).getOrThrow()

        assertEquals(before, server.pull(PullResponse.START).cursor, "the resend changed nothing on the server")
        assertEquals(1, server.records.values.count { it is HouseholdExpenseRecord })
        assertConverged(p1)
    }

    @Test
    fun s20RestoredServerGetsLostChangesBackFromPhones() {
        val backup = server.backup()
        step()
        p1.edit(expense("e1", owner = "m1"))
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "Groceries"))
        p1.syncWithServer(server).getOrThrow()
        p2.syncWithServer(server).getOrThrow()
        step()

        server.restore(backup)
        assertNull(server.records["e1"])
        assertEquals("Food", server.get<CategoryRecord>("c").name)

        p2.syncWithServer(server).getOrThrow()
        p1.syncWithServer(server).getOrThrow()
        syncAll(p1, p2)
        assertNotNull(server.records["e1"])
        assertEquals("Groceries", server.get<CategoryRecord>("c").name)
        assertConverged(p1, p2)
    }

    @Test
    fun s11PhoneClockTooFarAheadIsRefused() {
        p1.skewMillis = SyncProtocol.MAX_FUTURE_SKEW_MS + 60_000
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "From the future"))
        val error = assertNotNull(p1.syncWithServer(server).exceptionOrNull() as? SimSyncException).error

        assertEquals(SyncProtocol.ERROR_CLOCK_AHEAD, error.code)
        assertEquals("Food", server.get<CategoryRecord>("c").name)
    }

    @Test
    fun s11SmallSkewIsAccepted() {
        p1.skewMillis = 60_000
        p1.edit(p1.get<CategoryRecord>("c").copy(name = "A minute ahead"))
        p1.syncWithServer(server).getOrThrow()
        assertEquals("A minute ahead", server.get<CategoryRecord>("c").name)
    }

    @Test
    fun s30OldProtocolIsToldToUpdate() {
        p1.protocolVersion = 1
        p1.edit(expense("e1", owner = "m1"))
        val error = assertNotNull(p1.syncWithServer(server).exceptionOrNull() as? SimSyncException).error
        assertEquals(SyncProtocol.ERROR_UPDATE_REQUIRED, error.code)
        assertNull(server.records["e1"])
    }

    @Test
    fun s3SameCategoryAddedOnTwoPhonesBecomesOne() {
        val id1 = StableIds.category("h", "Pets")
        val id2 = StableIds.category("h", "  pets ")
        assertEquals(id1, id2)
        p1.edit(CategoryRecord(id = id1, updatedAt = HLC_ZERO, householdId = "h", name = "Pets"))
        step()
        p2.edit(CategoryRecord(id = id2, updatedAt = HLC_ZERO, householdId = "h", name = "pets"))
        step()
        p1.syncWithServer(server).getOrThrow()
        p2.syncWithServer(server).getOrThrow()
        syncAll(p1, p2)

        assertEquals(1, server.records.values.count { it is CategoryRecord && it.name.equals("pets", ignoreCase = true) })
        assertConverged(p1, p2)
    }

    @Test
    fun randomizedRunsConvergeAndKeepOwnership() {
        repeat(200) { seed -> randomRun(Random(seed), seed) }
    }

    private fun randomRun(random: Random, seed: Int) {
        val time = SimTime()
        val server = SimServer(time)
        server.edit(household)
        server.edit(category)
        val phones = (1..3).map { SimPhone("phone-$it", time, personId = "m$it") }
        phones.forEach { server.edit(MemberRecord(id = it.personId, updatedAt = HLC_ZERO, householdId = "h", displayName = it.personId, deviceId = it.deviceId)) }
        time.advance()
        phones.forEach { it.syncWithServer(server).getOrThrow() }
        var backup: Map<String, Pair<SyncRecord, Long>>? = null
        var nextExpense = 0

        repeat(60) {
            time.advance(random.nextLong(1, 5_000))
            val phone = phones.random(random)
            when (random.nextInt(100)) {
                in 0..19 -> phone.edit(expense("e${nextExpense++}", owner = phone.personId, amount = random.nextLong(1, 1_000)))
                in 20..39 -> {
                    val target = phone.records.values.filterIsInstance<HouseholdExpenseRecord>().randomOrNull(random) ?: return@repeat
                    val changed = when (random.nextInt(3)) {
                        0 -> target.copy(amountMinorUnits = random.nextLong(1, 1_000))
                        1 -> target.copy(note = "n${random.nextInt(100)}")
                        else -> target.copy(deleted = !target.deleted)
                    }
                    val allowed = phone.edit(changed)
                    assertEquals(target.ownerId == phone.personId, allowed, "seed $seed: only the owner may edit")
                }
                in 40..49 -> phone.edit(phone.get<CategoryRecord>("c").copy(name = "cat${random.nextInt(10)}"))
                in 50..54 -> phone.edit(phone.get<HouseholdRecord>("h").copy(defaultBudgetMinorUnits = random.nextLong(1, 100)))
                in 55..69 -> phone.syncWithPhone(phones.filter { it !== phone }.random(random))
                in 70..84 -> phone.syncWithServer(server, dropReply = random.nextInt(5) == 0).getOrThrow()
                in 85..91 -> {
                    val target = server.records.values.filterIsInstance<HouseholdExpenseRecord>().randomOrNull(random)
                    if (target != null) server.edit(target.copy(note = "windows${random.nextInt(100)}"))
                    else server.edit(expense("w${nextExpense++}", owner = null))
                }
                in 92..95 -> server.edit((server.records.getValue("c") as CategoryRecord).copy(name = "win${random.nextInt(10)}"))
                in 96..97 -> backup = server.backup()
                else -> backup?.let { server.restore(it); backup = null }
            }
        }

        repeat(3) { phones.forEach { it.syncWithServer(server).getOrThrow() } }
        for (phone in phones) {
            assertEquals(server.records, phone.records, "seed $seed: ${phone.deviceId} should match the server")
            assertTrue(phone.pending().isEmpty(), "seed $seed: ${phone.deviceId} should have nothing pending")
        }
        val deviceOf = phones.associate { it.personId to it.deviceId }
        for (expense in server.records.values.filterIsInstance<HouseholdExpenseRecord>()) {
            val allowed = setOfNotNull(server.deviceId, deviceOf[expense.ownerId])
            for ((field, stamp) in RecordMerge.allStamps(expense)) {
                assertTrue(stamp.deviceId in allowed, "seed $seed: ${expense.id}.$field changed by ${stamp.deviceId}")
            }
        }
    }
}
