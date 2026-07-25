package et.android.kharcha.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kharcha")

/**
 * Everything this device remembers locally: which server to talk to, and
 * "who am I" per household/activity (there's no login — the first time you
 * open one, you pick your name from its member/participant list, or add
 * yourself; that choice is what powers the summary screen's owed/owe
 * rollup and defaults who paid on a new expense).
 */
class ConnectionStore(private val context: Context) {
    private val serverBaseUrlKey = stringPreferencesKey("server_base_url")

    val serverBaseUrl: Flow<String?> = context.dataStore.data.map { it[serverBaseUrlKey] }

    suspend fun currentServerBaseUrl(): String? = serverBaseUrl.first()

    suspend fun setServerBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[serverBaseUrlKey] = baseUrl.trimEnd('/') }
    }

    suspend fun clearServerBaseUrl() {
        context.dataStore.edit { it.remove(serverBaseUrlKey) }
    }

    suspend fun myMemberId(householdId: String): String? =
        context.dataStore.data.first()[stringPreferencesKey("my_member_$householdId")]

    suspend fun setMyMemberId(householdId: String, memberId: String) {
        context.dataStore.edit { it[stringPreferencesKey("my_member_$householdId")] = memberId }
    }

    suspend fun myParticipantId(tripId: String): String? =
        context.dataStore.data.first()[stringPreferencesKey("my_participant_$tripId")]

    suspend fun setMyParticipantId(tripId: String, participantId: String) {
        context.dataStore.edit { it[stringPreferencesKey("my_participant_$tripId")] = participantId }
    }
}
