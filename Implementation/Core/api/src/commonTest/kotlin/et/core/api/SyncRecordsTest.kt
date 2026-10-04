package et.core.api

import et.core.model.HLC_ZERO
import et.core.model.Hlc
import et.core.model.decodeHlc
import et.core.model.encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRecordsTest {
    private val phoneA = "3f2a9c1e-phone-a"
    private val phoneB = "8b7d0e44-phone-b"

    @Test
    fun encodedStampsSortLikeStamps() {
        val stamps = listOf(
            Hlc(1_791_113_101_000, 0, phoneA),
            Hlc(1_791_113_101_000, 1, phoneA),
            Hlc(1_791_113_101_000, 1, phoneB),
            Hlc(1_791_113_102_000, 0, phoneA),
            Hlc(99, 7, phoneB),
        )
        assertEquals(stamps.sorted(), stamps.sortedBy { it.encode() }.map { decodeHlc(it.encode()) })
    }

    @Test
    fun encodeRoundTripsDeviceIdsWithDashes() {
        val stamp = Hlc(1_791_113_101_000, 12, "a1b2c3d4-e5f6-7890-abcd-ef0123456789")
        assertEquals(stamp, decodeHlc(stamp.encode()))
    }

    @Test
    fun blankOrMalformedDecodesToOldest() {
        assertEquals(HLC_ZERO, decodeHlc(""))
        assertEquals(HLC_ZERO, decodeHlc(null))
        assertEquals(HLC_ZERO, decodeHlc("not-a-stamp"))
        assertTrue(Hlc(1, 0, phoneA) > HLC_ZERO)
    }

    @Test
    fun lastWriteWins() {
        val older = Hlc(1000, 0, phoneA)
        val newer = Hlc(1000, 1, phoneA)
        assertTrue(Lww.incomingWins(newer, older))
        assertFalse(Lww.incomingWins(older, newer))
        assertFalse(Lww.incomingWins(older, older)) // the same version again changes nothing
        assertTrue(Lww.incomingWins(older, null)) // unknown record: take it
        // Same millisecond and counter on two phones: the device id breaks the tie, the same way everywhere.
        assertTrue(Lww.incomingWins(Hlc(1000, 0, phoneB), Hlc(1000, 0, phoneA)))
    }

    @Test
    fun recordsRoundTripThroughJsonWithTheirType() {
        val records: List<SyncRecord> = listOf(
            HouseholdExpenseRecord(
                id = "exp-1",
                updatedAt = Hlc(1000, 0, phoneA),
                householdId = "home",
                categoryId = "groceries",
                amountMinorUnits = 234050,
                currency = "INR",
                paidByMemberId = "asha",
                occurredAt = 1,
                beneficiaries = listOf(ShareLine("b1", personId = "asha", amountMinorUnits = 117025), ShareLine("b2", dependentId = "bruno", amountMinorUnits = 117025)),
                contributions = listOf(ShareLine("c1", personId = "asha", amountMinorUnits = 234050)),
            ),
            MemberRecord("asha", Hlc(1001, 0, phoneA), householdId = "home", displayName = "Asha", deviceId = phoneA),
            TripExpenseRecord("t-exp", Hlc(1002, 0, phoneB), deleted = true, tripId = "goa", amountMinorUnits = 500, currency = "INR", paidByParticipantId = "p1", occurredAt = 2),
        )
        val json = SyncJson.encodeToString(PushRequest.serializer(), PushRequest(records))
        assertTrue("\"type\":\"householdExpense\"" in json)
        assertEquals(records, SyncJson.decodeFromString(PushRequest.serializer(), json).records)
        assertEquals(SyncScope(ScopeKind.TRIP, "goa"), records[2].scope)
    }
}
