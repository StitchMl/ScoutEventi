package it.buonacaccia.app.data

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventsRepository @Inject constructor(
    private val client: OkHttpClient
) {
    private val base = "https://buonacaccia.net/Events.aspx"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun fetch(all: Boolean = true, queryParams: Map<String, String> = emptyMap()): List<BcEvent> =
        withContext(Dispatchers.IO) {
            val url = buildUrl(all, queryParams)
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful || body.isBlank()) return@use emptyList()
                HtmlParser.parseEvents(body, base)
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