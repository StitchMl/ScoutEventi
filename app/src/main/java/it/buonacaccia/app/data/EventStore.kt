package it.buonacaccia.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("bc_prefs")

object EventStore {
    private val KEY_SEEN_IDS = stringSetPreferencesKey("seen_ids")
    private val KEY_NOTIFY_TYPES = stringSetPreferencesKey("notify_types")
    private val KEY_NOTIFY_REGIONS = stringSetPreferencesKey("notify_regions")
    private val KEY_MUTE_TYPES = stringSetPreferencesKey("mute_types")

    /** Silenced types for notifications. Blank = no silence (all notified). */
    fun muteTypesFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_MUTE_TYPES] ?: emptySet() }

    suspend fun setMuteTypes(ctx: Context, types: Set<String>) {
        ctx.dataStore.edit { it[KEY_MUTE_TYPES] = types }
    }


    /** Regions selected for notifications. Blank = all. */
    fun notifyRegionsFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_NOTIFY_REGIONS] ?: emptySet() }

    suspend fun setNotifyRegions(ctx: Context, regions: Set<String>) {
        ctx.dataStore.edit { it[KEY_NOTIFY_REGIONS] = regions }
    }

    /** IDs of events already seen (persistent) */
    fun seenIdsFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_SEEN_IDS] ?: emptySet() }

    /** Adds the past ids to the set */
    suspend fun addSeenIds(ctx: Context, ids: Set<String>) {
        ctx.dataStore.edit { pref ->
            val cur = pref[KEY_SEEN_IDS] ?: emptySet()
            pref[KEY_SEEN_IDS] = cur + ids
        }
    }

    /** Replaces the whole set (useful in test/reset) */
    suspend fun setSeenIds(ctx: Context, ids: Set<String>) {
        ctx.dataStore.edit { it[KEY_SEEN_IDS] = ids }
    }

    /** Selected types for notifications. Blank = all. */
    fun notifyTypesFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_NOTIFY_TYPES] ?: emptySet() }

    suspend fun setNotifyTypes(ctx: Context, types: Set<String>) {
        ctx.dataStore.edit { it[KEY_NOTIFY_TYPES] = types }
    }
}