package it.buonacaccia.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest

class EventsRepository(
    private val client: OkHttpClient
) {
    private val bases = listOf(
        "https://buonacaccia.agesci.it/Events.aspx",
        "https://buonacaccia.net/Events.aspx",
    )

    suspend fun fetch(
        all: Boolean = true,
        queryParams: Map<String, String> = emptyMap(),
        minimumExpectedCount: Int? = null,
        enrichPredicate: (BcEvent) -> Boolean = { false },
    ): List<BcEvent> = withContext(Dispatchers.IO) {
        val shardFilters = BuonaCacciaScopes.fallbackFiltersForQueryParams(queryParams)
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless { filters ->
                filters.size == 1 && filters.single().toQueryParams() == queryParams
            }

        try {
            val directEvents = fetchDirect(
                all = all,
                queryParams = queryParams,
                enrichPredicate = enrichPredicate
            )
            if (minimumExpectedCount != null &&
                directEvents.size < minimumExpectedCount &&
                shardFilters != null
            ) {
                Timber.w(
                    "Direct fetch returned %d events for params=%s below expected minimum=%d, validating with shard fallback",
                    directEvents.size,
                    queryParams,
                    minimumExpectedCount
                )
                return@withContext fetchByFilters(
                    filters = shardFilters,
                    all = all,
                    requireAllSuccess = true,
                    enrichPredicate = enrichPredicate
                )
            }

            return@withContext directEvents
        } catch (ce: CancellationException) {
            throw ce
        } catch (directError: Throwable) {
            if (shardFilters != null) {
                Timber.w(
                    directError,
                    "Direct fetch failed params=%s, retrying with shard fallback (%d filters)",
                    queryParams,
                    shardFilters.size
                )
                try {
                    return@withContext fetchByFilters(
                        filters = shardFilters,
                        all = all,
                        requireAllSuccess = true,
                        enrichPredicate = enrichPredicate
                    )
                } catch (fallbackError: Throwable) {
                    if (fallbackError !is CancellationException) {
                        fallbackError.addSuppressed(directError)
                    }
                    throw fallbackError
                }
            }

            throw directError
        }
    }

    suspend fun fetchByFilters(
        filters: Collection<BuonaCacciaFilter>,
        all: Boolean = true,
        requireAllSuccess: Boolean = true,
        enrichPredicate: (BcEvent) -> Boolean = { false },
    ): List<BcEvent> = withContext(Dispatchers.IO) {
        val distinctFilters = filters
            .map { it.toQueryParams() }
            .distinct()

        if (distinctFilters.isEmpty()) {
            return@withContext fetch(all = all, enrichPredicate = enrichPredicate)
        }

        val merged = LinkedHashMap<String, BcEvent>()
        var lastError: Throwable? = null
        var successCount = 0
        var failureCount = 0

        for (params in distinctFilters) {
            try {
                val events = fetch(
                    all = all,
                    queryParams = params,
                    enrichPredicate = enrichPredicate
                )
                successCount++
                events.forEach { event ->
                    merged.putIfAbsent(stableEventKeyOf(event), event)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                failureCount++
                lastError = t
                Timber.w(t, "Filtered fetch failed params=%s", params)
            }
        }

        if (successCount == 0) {
            throw (lastError ?: IOException("Unable to fetch filtered events"))
        }
        if (requireAllSuccess && failureCount > 0) {
            throw (lastError ?: IOException("Filtered fetch incomplete: $failureCount failed requests"))
        }

        return@withContext merged.values
            .sortedWith(compareBy<BcEvent> { it.startDate }.thenBy { it.title.lowercase() })
    }

    private suspend fun fetchFromBase(
        base: String,
        all: Boolean,
        queryParams: Map<String, String>,
        enrichPredicate: (BcEvent) -> Boolean,
    ): List<BcEvent> {
        val url = buildUrl(base, all, queryParams)
        Timber.d("EventsRepository.fetch url=%s", url)

        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val ct = resp.header("Content-Type") ?: "?"
            val body = resp.body.string()
            val hash = body.sha256()

            Timber.d(
                "EventsRepository.fetch finalUrl=%s resp=%d ct=%s bytes=%d sha256=%s",
                finalUrl, resp.code, ct, body.length, hash.take(12)
            )

            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} fetching $finalUrl ct=$ct")
            }
            if (body.isBlank()) {
                throw IOException("Empty body fetching $finalUrl ct=$ct")
            }

            if (body.contains("Account/Login.aspx", ignoreCase = true) ||
                body.contains("Accesso", ignoreCase = true)
            ) {
                throw IOException("Got login page fetching $finalUrl ct=$ct sha256=${hash.take(12)}")
            }

            val inspection = HtmlParser.inspectEventsPage(body, finalUrl)
            val baseEvents = HtmlParser.parseEvents(body, finalUrl)
            Timber.d(
                "EventsRepository.inspect finalUrl=%s recognizable=%s links=%d rows=%d webforms=%s parsed=%d",
                finalUrl,
                inspection.recognizableTable,
                inspection.uniqueEventLinkCount,
                inspection.candidateRowCount,
                inspection.hasWebFormsMarkers,
                baseEvents.size
            )

            if (baseEvents.isEmpty()) {
                if (inspection.uniqueEventLinkCount >= 3) {
                    throw IllegalStateException(
                        "Detected ${inspection.uniqueEventLinkCount} event links but parsed 0 events from $finalUrl"
                    )
                }

                if (!inspection.recognizableTable) {
                    val head = body.take(400).replace("\n", " ").replace("\r", " ")
                    throw IllegalStateException(
                        "Parsed 0 events from $finalUrl ct=$ct sha256=${hash.take(12)} head=$head"
                    )
                }

                Timber.i("Parsed 0 upcoming events from %s", finalUrl)
                return emptyList()
            }

            if (looksSuspiciouslyIncomplete(baseEvents.size, inspection)) {
                throw IllegalStateException(
                    "Suspiciously incomplete parse from $finalUrl: parsed=${baseEvents.size} links=${inspection.uniqueEventLinkCount} rows=${inspection.candidateRowCount}"
                )
            }

            return baseEvents.map { ev ->
                if (!enrichPredicate(ev)) return@map ev

                try {
                    val detailHtml = fetchDetail(ev.detailUrl)
                    val subs = HtmlParser.parseSubscriptions(detailHtml)
                    ev.copy(
                        subsOpenDate = subs.opening,
                        subsCloseDate = subs.closing
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Timber.w(e, "Unable to enrich event id=%s url=%s", ev.id, ev.detailUrl)
                    ev
                }
            }
        }
    }

    private suspend fun fetchDirect(
        all: Boolean,
        queryParams: Map<String, String>,
        enrichPredicate: (BcEvent) -> Boolean
    ): List<BcEvent> {
        var lastError: Throwable? = null

        for (base in bases) {
            try {
                return fetchFromBase(
                    base = base,
                    all = all,
                    queryParams = queryParams,
                    enrichPredicate = enrichPredicate
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastError = t
                Timber.w(t, "Fetch failed using base=%s, trying fallback if available", base)
            }
        }

        throw (lastError ?: IOException("Unable to fetch events (unknown error)"))
    }

    suspend fun fetchDetail(detailUrl: String): String = withContext(Dispatchers.IO) {
        Timber.d("EventsRepository.fetchDetail url=%s", detailUrl)

        val req = Request.Builder().url(detailUrl).get().build()
        client.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val ct = resp.header("Content-Type") ?: "?"
            val text = resp.body.string()

            Timber.d(
                "EventsRepository.fetchDetail finalUrl=%s resp=%d ct=%s bytes=%d sha256=%s",
                finalUrl, resp.code, ct, text.length, text.sha256().take(12)
            )

            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $finalUrl ct=$ct")
            if (text.isBlank()) throw IOException("Empty body fetching $finalUrl ct=$ct")

            text
        }
    }

    private fun buildUrl(base: String, all: Boolean, params: Map<String, String>) =
        base.toHttpUrl().newBuilder().apply {
            if (all) addQueryParameter("All", "1")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private fun stableEventKeyOf(event: BcEvent): String =
        event.id?.takeIf { it.isNotBlank() } ?: event.detailUrl

    private fun looksSuspiciouslyIncomplete(
        parsedCount: Int,
        inspection: HtmlParser.EventsPageInspection
    ): Boolean {
        val signals = maxOf(inspection.uniqueEventLinkCount, inspection.candidateRowCount)
        if (signals < 12) return false

        return parsedCount * 2 < signals
    }

    private fun String.sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
