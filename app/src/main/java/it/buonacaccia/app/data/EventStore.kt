package it.buonacaccia.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("bc_prefs")

object EventStore {
    private val KEY_KNOWN_IDS = stringSetPreferencesKey("known_event_ids")
    private val KEY_NOTIFY_TYPES = stringSetPreferencesKey("notify_types")

    fun notifyTypesFlow(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { prefs -> prefs[KEY_NOTIFY_TYPES] ?: emptySet() }

    suspend fun setNotifyTypes(context: Context, types: Set<String>) {
        context.dataStore.edit { it[KEY_NOTIFY_TYPES] = types }
    }

    suspend fun currentNotifyTypes(context: Context): Set<String> = notifyTypesFlow(context).first()
    suspend fun getKnownIds(context: Context): Set<String> {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_KNOWN_IDS] ?: emptySet()
    }

    suspend fun saveKnownIds(context: Context, ids: Set<String>) {
        context.dataStore.edit { it[KEY_KNOWN_IDS] = ids }
    }
}