package it.buonacaccia.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("bc_prefs")

object EventStore {
    private val KEY_KNOWN_IDS = stringSetPreferencesKey("known_event_ids")

    suspend fun getKnownIds(context: Context): Set<String> {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_KNOWN_IDS] ?: emptySet()
    }

    suspend fun saveKnownIds(context: Context, ids: Set<String>) {
        context.dataStore.edit { it[KEY_KNOWN_IDS] = ids }
    }
}