package it.buonacaccia.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import androidx.datastore.preferences.core.stringPreferencesKey

private val Context.dataStore by preferencesDataStore("bc_prefs")

object EventStore {
    private val KEY_SUBSCRIBED_IDS = stringSetPreferencesKey("subscribed_ids")

    // --- Theme mode (manual override) ---
    enum class ThemeMode { SYSTEM, LIGHT, DARK }
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    fun themeModeFlow(ctx: Context): Flow<ThemeMode> =
        ctx.dataStore.data.map { pref ->
            when (pref[KEY_THEME_MODE]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(ctx: Context, mode: ThemeMode) {
        ctx.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
        Timber.d("EventStore.setThemeMode=%s", mode)
    }
    private val KEY_SEEN_IDS = stringSetPreferencesKey("seen_ids")
    private val KEY_NOTIFY_TYPES = stringSetPreferencesKey("notify_types")
    private val KEY_NOTIFY_REGIONS = stringSetPreferencesKey("notify_regions")
    private val KEY_MUTE_TYPES = stringSetPreferencesKey("mute_types")
    private val KEY_SENT_REMINDERS = stringSetPreferencesKey("sent_reminders")
    private val KEY_CACHED_EVENTS = stringSetPreferencesKey("cached_events")

    /** Returns decoded cached events. */
    fun cachedEventsFlow(ctx: Context): Flow<List<BcEvent>> =
        ctx.dataStore.data.map { pref ->
            val raw = pref[KEY_CACHED_EVENTS] ?: emptySet()
            raw.mapNotNull { decodeEvent(it) }
                .filter { isActive(it) }
        }

    /** An event is valid if it has not already ended (endDate < today). */
    private fun isActive(e: BcEvent, today: LocalDate = LocalDate.now()): Boolean {
        val end = e.endDate
        return end == null || !end.isBefore(today)
    }

    /** Replaces the entire cache. */
    @Suppress("unused")
    suspend fun setCachedEvents(ctx: Context, events: List<BcEvent>) {
        val filtered = events.filter { isActive(it) }
        ctx.dataStore.edit { pref ->
            pref[KEY_CACHED_EVENTS] = filtered.map { encodeEvent(it) }.toSet()
        }
        Timber.d("EventStore.setCachedEvents size=%d (filtered from %d)", filtered.size, events.size)
    }

    /** Inserts/updates by id (merge), leaving others unaffected. */
    suspend fun upsertEvents(ctx: Context, events: List<BcEvent>) {
        if (events.isEmpty()) return
        ctx.dataStore.edit { pref ->
            val cur = (pref[KEY_CACHED_EVENTS] ?: emptySet()).mapNotNull { decodeEvent(it) }
            val byId = cur.associateBy { it.id }.toMutableMap()

            // Update or remove based on validity (endDate >= today)
            events.forEach { e ->
                if (isActive(e)) {
                    byId[e.id] = e
                } else {
                    // if it's finished, remove it from the cache
                    e.id?.let { byId.remove(it) }
                }
            }

            // Additional safety measure: do not save anything that is already finished now.
            val toSave = byId.values.filter { isActive(it) }
            pref[KEY_CACHED_EVENTS] = toSave.map { encodeEvent(it) }.toSet()
        }
        Timber.d("EventStore.upsertEvents addedOrUpdated=%d (after filtering)", events.size)
    }

    /** Removes from cache events with closing entries < today OR ended events. Returns the removed ids. */
    suspend fun purgeClosed(ctx: Context, today: LocalDate): Set<String> {
        var removed: Set<String> = emptySet()
        ctx.dataStore.edit { pref ->
            val cur = (pref[KEY_CACHED_EVENTS] ?: emptySet()).mapNotNull { decodeEvent(it) }
            val (toKeep, toDrop) = cur.partition { e ->
                val close = e.subsCloseDate
                val end = e.endDate
                val subsOk = (close == null || !close.isBefore(today))
                val endOk = (end == null || !end.isBefore(today))
                subsOk && endOk
            }
            removed = toDrop.mapNotNull { it.id }.toSet()
            pref[KEY_CACHED_EVENTS] = toKeep.map { encodeEvent(it) }.toSet()

            if (removed.isNotEmpty()) {
                val curSeen = pref[KEY_SEEN_IDS] ?: emptySet()
                pref[KEY_SEEN_IDS] = curSeen - removed

                val curSub = pref[KEY_SUBSCRIBED_IDS] ?: emptySet()
                pref[KEY_SUBSCRIBED_IDS] = curSub - removed
            }
        }
        if (removed.isNotEmpty()) Timber.i("EventStore.purgeClosed removed=%s", removed)
        return removed
    }

    // --- Helpers of (de)serialization compatible DataStore Preferences ---
    private fun enc(s: String?): String = URLEncoder.encode(s ?: "", StandardCharsets.UTF_8.name())
    private fun dec(s: String): String? = URLDecoder.decode(s, StandardCharsets.UTF_8.name()).ifBlank { null }

    private fun encDate(d: LocalDate?): String = d?.toString() ?: ""
    private fun decDate(s: String): LocalDate? = s.ifBlank { null }?.let { LocalDate.parse(it) }

    private fun encodeEvent(e: BcEvent): String {
        // stable fields order; pipe separator
        return listOf(
            enc(e.id), enc(e.type), enc(e.title), enc(e.region),
            encDate(e.startDate), encDate(e.endDate),
            enc(e.fee), enc(e.location), enc(e.enrolled),
            enc(e.status), enc(e.detailUrl), enc(e.statusColor),
            enc(e.branch?.name), encDate(e.subsOpenDate), encDate(e.subsCloseDate)
        ).joinToString("|")
    }

    private fun decodeEvent(s: String): BcEvent? = runCatching {
        val p = s.split("|")
        // retro-compat: if the record had fewer fields, we would pad
        val v = if (p.size < 15) p + List(15 - p.size) { "" } else p
        BcEvent(
            id = dec(v[0]),
            type = dec(v[1]),
            title = dec(v[2]) ?: return null,
            region = dec(v[3]),
            startDate = decDate(v[4]),
            endDate = decDate(v[5]),
            fee = dec(v[6]),
            location = dec(v[7]),
            enrolled = dec(v[8]),
            status = dec(v[9]),
            detailUrl = dec(v[10]) ?: return null,
            statusColor = dec(v[11]),
            branch = dec(v[12])?.let { runCatching { Branch.valueOf(it) }.getOrNull() },
            subsOpenDate = decDate(v[13]),
            subsCloseDate = decDate(v[14])
        )
    }.getOrNull()

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
    @Suppress("unused")
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

    /** Stable key for an event: id if present, otherwise detailUrl. */
    fun eventKeyOf(e: BcEvent): String = e.id ?: e.detailUrl

    /** Subscribed events (key set = id|detailUrl). */
    fun subscribedIdsFlow(ctx: Context): Flow<Set<String>> =
        ctx.dataStore.data.map { it[KEY_SUBSCRIBED_IDS] ?: emptySet() }

    /** Sets/shuts down the underwriting of an individual event. */
    suspend fun setSubscribed(ctx: Context, key: String, enabled: Boolean) {
        ctx.dataStore.edit { pref ->
            val cur = pref[KEY_SUBSCRIBED_IDS] ?: emptySet()
            pref[KEY_SUBSCRIBED_IDS] = if (enabled) cur + key else cur - key
        }
        Timber.d("EventStore.setSubscribed key=%s enabled=%s", key, enabled)
    }
}