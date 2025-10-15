package it.buonacaccia.app.data

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class EventsRepository(
    private val client: OkHttpClient
) {
    private val base = "https://buonacaccia.net/Events.aspx"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun fetch(all: Boolean = true, queryParams: Map<String, String> = emptyMap()): List<BcEvent> =
        withContext(Dispatchers.IO) {
            val url = buildUrl(all, queryParams)
            Timber.d("EventsRepository.fetch url=%s", url)
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                Timber.d("EventsRepository.fetch resp=%s bytes=%d", resp.code, body.length)
                if (!resp.isSuccessful || body.isBlank()) return@use emptyList()

                // 1) parse base list
                val baseEvents = HtmlParser.parseEvents(body, base)

                // 2) enrich with enrollment dates taken from detailUrl
                //    (sequential for simplicity/robustness; you can parallelize in the future)
                val enriched = baseEvents.map { ev ->
                    runCatching {
                        val detailHtml = fetchDetail(ev.detailUrl)
                        val subs = HtmlParser.parseSubscriptions(detailHtml)
                        val copy = ev.copy(
                            subsOpenDate = subs.opening,
                            subsCloseDate = subs.closing
                        )
                        Timber.d(
                            "Subs dates for id=%s title=%s open=%s close=%s",
                            copy.id, copy.title, copy.subsOpenDate, copy.subsCloseDate
                        )
                        copy
                    }.onFailure {
                        Timber.w(it, "Unable to enrich event %s (%s)", ev.id, ev.detailUrl)
                    }.getOrElse { ev } // in case of an error, returns the base event
                }

                enriched
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun fetchDetail(detailUrl: String): String =
        withContext(Dispatchers.IO) {
            Timber.d("EventsRepository.fetchDetail url=%s", detailUrl)
            val req = Request.Builder().url(detailUrl).get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body.string()
                Timber.d(
                    "EventsRepository.fetchDetail resp=%s bytes=%d",
                    resp.code, text.length
                )
                text
            }
        }

    private fun buildUrl(all: Boolean, params: Map<String, String>): String {
        val qp = buildString {
            if (all) append("All=1")
            params.forEach { (k, v) ->
                if (isNotEmpty()) append("&")
                append("$k=$v")
            }
        }
        return if (qp.isNotEmpty()) "$base?$qp" else base
    }
}