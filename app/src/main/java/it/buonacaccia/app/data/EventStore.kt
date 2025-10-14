package it.buonacaccia.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.dataStore by preferencesDataStore("bc_prefs")

object EventStore {
    private val KEY_SEEN_IDS = stringSetPreferencesKey("seen_ids")
    private val KEY_NOTIFY_TYPES = stringSetPreferencesKey("notify_types")
    private val KEY_NOTIFY_REGIONS = stringSetPreferencesKey("notify_regions")
    private val KEY_MUTE_TYPES = stringSetPreferencesKey("mute_types")
    private val KEY_SENT_REMINDERS = stringSetPreferencesKey("sent_reminders")

    /** Returns the set of reminders already sent (key: e.g. "12345|2025-10-30|OPEN-1"). */
    fun sentRemindersFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_SENT_REMINDERS] ?: emptySet() }

    suspend fun addSentReminder(ctx: Context, keys: Set<String>) {
        ctx.dataStore.edit { pref ->
            val cur = pref[KEY_SENT_REMINDERS] ?: emptySet()
            pref[KEY_SENT_REMINDERS] = cur + keys
        }
        Timber.d("EventStore.addSentReminder added=%d", keys.size)
    }


    /** Silenced types for notifications. Blank = no silence (all notified). */
    fun muteTypesFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_MUTE_TYPES] ?: emptySet() }

    suspend fun setMuteTypes(ctx: Context, types: Set<String>) {
        ctx.dataStore.edit { it[KEY_MUTE_TYPES] = types }
        Timber.d("EventStore.setMuteTypes %s", types)
    }


    /** Regions selected for notifications. Blank = all. */
    fun notifyRegionsFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_NOTIFY_REGIONS] ?: emptySet() }

    suspend fun setNotifyRegions(ctx: Context, regions: Set<String>) {
        ctx.dataStore.edit { it[KEY_NOTIFY_REGIONS] = regions }
        Timber.d("EventStore.setNotifyRegions %s", regions)
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
        Timber.d("EventStore.addSeenIds added=%d", ids.size)
    }

    /** Replaces the whole set (useful in test/reset) */
    suspend fun setSeenIds(ctx: Context, ids: Set<String>) {
        ctx.dataStore.edit { it[KEY_SEEN_IDS] = ids }
        Timber.d("EventStore.setSeenIds size=%d", ids.size)
    }

    /** Selected types for notifications. Blank = all. */
    fun notifyTypesFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_NOTIFY_TYPES] ?: emptySet() }

    suspend fun setNotifyTypes(ctx: Context, types: Set<String>) {
        ctx.dataStore.edit { it[KEY_NOTIFY_TYPES] = types }
        Timber.d("EventStore.setNotifyTypes %s", types)
    }
}