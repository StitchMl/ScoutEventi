package it.buonacaccia.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate

class EventsRepository(
    private val client: OkHttpClient
) {
    private val trustedHosts = setOf("buonacaccia.agesci.it", "buonacaccia.net")
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
        val shardFilters = fallbackFilters(queryParams)

        try {
            val directEvents = fetchDirect(
                all = all,
                queryParams = queryParams,
                enrichPredicate = enrichPredicate
            )
            if (needsShardValidation(directEvents.size, minimumExpectedCount, shardFilters)) {
                Timber.w(
                    "Direct fetch returned %d events for params=%s below expected minimum=%d, validating with shard fallback",
                    directEvents.size,
                    queryParams,
                    minimumExpectedCount
                )
                return@withContext fetchByFilters(
                    filters = requireNotNull(shardFilters),
                    all = all,
                    requireAllSuccess = true,
                    enrichPredicate = enrichPredicate
                )
            }

            return@withContext directEvents
        } catch (ce: CancellationException) {
            throw ce
        } catch (directError: Throwable) {
            return@withContext fetchFallbackOrThrow(
                shardFilters, directError, queryParams, all, enrichPredicate,
            )
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
            .filter { it.isStillRelevant() }
            .sortedWith(compareBy<BcEvent> { it.startDate }.thenBy { it.title.lowercase() })
    }

    private suspend fun fetchFromBase(
        base: String,
        all: Boolean,
        queryParams: Map<String, String>,
        enrichPredicate: (BcEvent) -> Boolean,
    ): List<BcEvent> {
        val today = LocalDate.now()
        val url = buildUrl(base, all, queryParams)
        Timber.d("EventsRepository.fetch url=%s", url)

        requireTrustedUrl(url)
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val ct = resp.header("Content-Type") ?: "?"
            requireTrustedUrl(resp.request.url)
            val body = resp.readHtmlBody()
            val hash = body.sha256()

            Timber.d(
                "EventsRepository.fetch finalUrl=%s resp=%d ct=%s bytes=%d sha256=%s",
                finalUrl, resp.code, ct, body.length, hash.take(12)
            )

            validateResponse(resp.isSuccessful, resp.code, body, finalUrl, ct, hash)

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

            validateParsedEvents(baseEvents, inspection, body, finalUrl, ct, hash)
            if (baseEvents.isEmpty()) return emptyList()
            val enriched = enrichEvents(baseEvents, enrichPredicate)
            return enriched.filter { it.isStillRelevant(today) }
        }
    }

    private fun fallbackFilters(queryParams: Map<String, String>): List<BuonaCacciaFilter>? =
        BuonaCacciaScopes.fallbackFiltersForQueryParams(queryParams)
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless { it.size == 1 && it.single().toQueryParams() == queryParams }

    private fun needsShardValidation(
        count: Int,
        minimum: Int?,
        filters: List<BuonaCacciaFilter>?,
    ): Boolean = minimum != null && count < minimum && filters != null

    private suspend fun fetchFallbackOrThrow(
        filters: List<BuonaCacciaFilter>?,
        directError: Throwable,
        queryParams: Map<String, String>,
        all: Boolean,
        enrichPredicate: (BcEvent) -> Boolean,
    ): List<BcEvent> {
        if (filters == null) throw directError
        Timber.w(directError, "Direct fetch failed params=%s, retrying with shard fallback (%d filters)", queryParams, filters.size)
        return try {
            fetchByFilters(filters, all, requireAllSuccess = true, enrichPredicate)
        } catch (fallbackError: Throwable) {
            if (fallbackError !is CancellationException) fallbackError.addSuppressed(directError)
            throw fallbackError
        }
    }

    private fun validateResponse(
        successful: Boolean,
        code: Int,
        body: String,
        finalUrl: String,
        contentType: String,
        hash: String,
    ) {
        if (!successful) throw IOException("HTTP $code fetching $finalUrl ct=$contentType")
        if (body.isBlank()) throw IOException("Empty body fetching $finalUrl ct=$contentType")
        if (body.contains("Account/Login.aspx", true) || body.contains("Accesso", true)) {
            throw IOException("Got login page fetching $finalUrl ct=$contentType sha256=${hash.take(12)}")
        }
    }

    private fun validateParsedEvents(
        events: List<BcEvent>,
        inspection: HtmlParser.EventsPageInspection,
        body: String,
        finalUrl: String,
        contentType: String,
        hash: String,
    ) {
        if (events.isNotEmpty()) {
            check(!looksSuspiciouslyIncomplete(events.size, inspection)) {
                "Suspiciously incomplete parse from $finalUrl: parsed=${events.size} links=${inspection.uniqueEventLinkCount} rows=${inspection.candidateRowCount}"
            }
            return
        }
        check(inspection.uniqueEventLinkCount < 3) {
            "Detected ${inspection.uniqueEventLinkCount} event links but parsed 0 events from $finalUrl"
        }
        if (!inspection.recognizableTable) {
            val head = body.take(400).replace("\n", " ").replace("\r", " ")
            error("Parsed 0 events from $finalUrl ct=$contentType sha256=${hash.take(12)} head=$head")
        }
        Timber.i("Parsed 0 upcoming events from %s", finalUrl)
    }

    private suspend fun enrichEvents(
        events: List<BcEvent>,
        predicate: (BcEvent) -> Boolean,
    ): List<BcEvent> = coroutineScope {
        events.map { event -> async { enrichEvent(event, predicate) } }.awaitAll()
    }

    private suspend fun enrichEvent(event: BcEvent, predicate: (BcEvent) -> Boolean): BcEvent {
        if (!predicate(event)) return event
        return try {
            val subscriptions = HtmlParser.parseSubscriptions(fetchDetail(event.detailUrl))
            event.copy(
                subsOpenDate = subscriptions.opening,
                subsCloseDate = subscriptions.closing,
                zone = subscriptions.zone,
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (error: Exception) {
            Timber.w(error, "Unable to enrich event id=%s url=%s", event.id, event.detailUrl)
            event
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

        val trustedUrl = detailUrl.toHttpUrl()
        requireTrustedUrl(trustedUrl)
        val req = Request.Builder().url(trustedUrl).get().build()
        client.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val ct = resp.header("Content-Type") ?: "?"
            requireTrustedUrl(resp.request.url)
            val text = resp.readHtmlBody()

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

    private fun requireTrustedUrl(url: okhttp3.HttpUrl) {
        require(url.isHttps && url.host in trustedHosts) { "Untrusted BuonaCaccia URL" }
    }

    private fun okhttp3.Response.readHtmlBody(): String {
        val contentType = body.contentType()
        require(contentType?.type == "text" && contentType.subtype in HTML_SUBTYPES) {
            "Unexpected response content type"
        }
        val declaredLength = body.contentLength()
        require(declaredLength < 0 || declaredLength <= MAX_RESPONSE_BYTES) { "Response too large" }
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size <= MAX_RESPONSE_BYTES) {
            val read = source.read(buffer, minOf(READ_CHUNK_BYTES, MAX_RESPONSE_BYTES + 1 - buffer.size))
            if (read == -1L) return buffer.readUtf8()
        }
        throw IllegalArgumentException("Response too large")
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 5L * 1024L * 1024L
        const val READ_CHUNK_BYTES = 8L * 1024L
        val HTML_SUBTYPES = setOf("html", "xhtml+xml")
    }
}
