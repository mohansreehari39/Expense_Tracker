package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.HouseholdEntity

/**
 * Who owes whom, as shown on screen. [balances] is keyed by *local*
 * member/participant id; [suggestions] keep the server's remote ids (they
 * only come from the server, and the Settle button sends them straight
 * back to it).
 */
data class BalanceView(
    val balances: Map<String, Long>,
    val suggestions: List<SuggestedTransferDto>,
    val fromServer: Boolean,
)

/**
 * The single place that decides which balances Android shows, so the
 * landing page and the household/activity screens can never disagree.
 *
 * Prefers the paired server's balances, which include recorded settlements
 * (those are only stored on the server). Falls back to the on-device
 * figure from [LocalRepository] when unlinked or unreachable — both are
 * computed by the same Core code, so they only differ by settlements and
 * by anything not yet synced.
 *
 * Resolving a server's address can take seconds (mDNS), so one loader
 * resolves each paired server at most once — create one per screen
 * refresh, not per item.
 */
class BalanceLoader(private val context: Context, private val repo: LocalRepository) {
    private val clients = mutableMapOf<String, ApiClient?>()

    private suspend fun api(pairedServerId: String): ApiClient? = clients.getOrPut(pairedServerId) {
        val server = repo.pairedServer(pairedServerId) ?: return@getOrPut null
        runCatching { SyncEngine.resolveApiClient(context, server, repo) }.getOrNull()
    }

    suspend fun localForActivity(activity: ActivityEntity) = BalanceView(repo.activityBalances(activity.id), emptyList(), fromServer = false)

    suspend fun forActivity(activity: ActivityEntity): BalanceView {
        val pairedServerId = activity.pairedServerId
        val remoteId = activity.remoteId
        val detail = if (pairedServerId != null && remoteId != null) {
            api(pairedServerId)?.let { runCatching { it.trip(remoteId) }.getOrNull() }
        } else {
            null
        }
        if (detail == null) return localForActivity(activity)
        val participants = repo.participants(activity.id)
        val balances = detail.balances.mapNotNull { (remoteParticipantId, money) ->
            val localId = participants.find { it.remoteId == remoteParticipantId }?.id ?: return@mapNotNull null
            localId to money.minorUnits
        }.toMap()
        return BalanceView(balances, detail.suggestedSettlements, fromServer = true)
    }

    suspend fun forHousehold(household: HouseholdEntity): BalanceView {
        if (!household.settlementEnabled) return BalanceView(emptyMap(), emptyList(), fromServer = false)
        val pairedServerId = household.pairedServerId
        val remoteId = household.remoteId
        val response = if (pairedServerId != null && remoteId != null) {
            api(pairedServerId)?.let { runCatching { it.household(remoteId) }.getOrNull() }
        } else {
            null
        }
        if (response == null) return BalanceView(repo.householdBalances(household.id), emptyList(), fromServer = false)
        val members = repo.members(household.id)
        val balances = response.balances.mapNotNull { (remoteMemberId, money) ->
            val localId = members.find { it.remoteId == remoteMemberId }?.id ?: return@mapNotNull null
            localId to money.minorUnits
        }.toMap()
        return BalanceView(balances, response.suggestedSettlements, fromServer = true)
    }
}
