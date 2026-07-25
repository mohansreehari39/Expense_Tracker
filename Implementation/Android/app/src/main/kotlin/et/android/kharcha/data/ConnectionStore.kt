package et.android.kharcha.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kharcha")

/**
 * Everything this device remembers locally: the one-time signup name (see
 * [myName]), which server to talk to, per-household/activity member/
 * participant ids this profile resolves to (auto-resolved once via
 * [IdentityResolver] — no per-screen prompt), and the dark-mode override.
 */
class ConnectionStore(private val context: Context) {
    private val serverBaseUrlKey = stringPreferencesKey("server_base_url")
    private val myNameKey = stringPreferencesKey("my_name")
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    val serverBaseUrl: Flow<String?> = context.dataStore.data.map { it[serverBaseUrlKey] }

    suspend fun currentServerBaseUrl(): String? = serverBaseUrl.first()

    suspend fun setServerBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[serverBaseUrlKey] = baseUrl.trimEnd('/') }
    }

    suspend fun clearServerBaseUrl() {
        context.dataStore.edit { it.remove(serverBaseUrlKey) }
    }

    /** Set once at signup, reused everywhere as this device's identity — see [IdentityResolver]. */
    val myName: Flow<String?> = context.dataStore.data.map { it[myNameKey] }

    suspend fun currentMyName(): String? = myName.first()

    suspend fun setMyName(name: String) {
        context.dataStore.edit { it[myNameKey] = name.trim() }
    }

    /** null = follow the system setting; otherwise an explicit user override. */
    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[darkModeKey] }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[darkModeKey] = enabled }
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
