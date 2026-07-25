package et.android.kharcha.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kharcha")

/**
 * The only thing left here is the dark-mode override — profile, paired
 * servers, and all household/activity data moved to Room (see
 * [LocalRepository]) once the app became local-first.
 */
class ConnectionStore(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    /** null = follow the system setting; otherwise an explicit user override. */
    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[darkModeKey] }

    suspend fun currentDarkMode(): Boolean? = darkMode.first()

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[darkModeKey] = enabled }
    }
}
