package it.buonacaccia.app.data

import android.os.Build
import androidx.annotation.RequiresApi
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
    // Primary: unified address. Fallback: historical.
    private val bases = listOf(
        "https://buonacaccia.agesci.it/Events.aspx",
        "https://buonacaccia.net/Events.aspx",
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun fetch(
        all: Boolean = true,
        queryParams: Map<String, String> = emptyMap(),
        enrichPredicate: (BcEvent) -> Boolean = { false }, // ✅ default: no N+1 on details
    ): List<BcEvent> = withContext(Dispatchers.IO) {

        var lastError: Throwable? = null

        for (base in bases) {
            try {
                return@withContext fetchFromBase(
                    base = base,
                    all = all,
                    queryParams = queryParams,
                    enrichPredicate = enrichPredicate
                )
            } catch (t: Throwable) {
                lastError = t
                Timber.w(t, "Fetch failed using base=%s, trying fallback if available…", base)
            }
        }

        throw (lastError ?: IOException("Unable to fetch events (unknown error)"))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
            val finalUrl = resp.request.url.toString() // ✅ Final URL after redirect
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

            // Heuristics: if login/challenge arrives instead of the list
            if (body.contains("Account/Login.aspx", ignoreCase = true) ||
                body.contains("Accesso", ignoreCase = true)
            ) {
                throw IOException("Got login page fetching $finalUrl ct=$ct sha256=${hash.take(12)}")
            }

            val baseEvents = HtmlParser.parseEvents(body, finalUrl)
            if (baseEvents.isEmpty()) {
                val head = body.take(400).replace("\n", " ")
                throw IllegalStateException(
                    "Parsed 0 events from $finalUrl ct=$ct sha256=${hash.take(12)} head=$head"
                )
            }

            // ✅ Enrichment ONLY for the events you need (e.g., those you "follow")
            return baseEvents.map { ev ->
                if (!enrichPredicate(ev)) return@map ev

                runCatching {
                    val detailHtml = fetchDetail(ev.detailUrl)
                    val subs = HtmlParser.parseSubscriptions(detailHtml)
                    ev.copy(
                        subsOpenDate = subs.opening,
                        subsCloseDate = subs.closing
                    )
                }.onFailure {
                    Timber.w(it, "Unable to enrich event id=%s url=%s", ev.id, ev.detailUrl)
                }.getOrElse { ev }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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

    private fun String.sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}