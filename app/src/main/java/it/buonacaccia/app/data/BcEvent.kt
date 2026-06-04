package it.buonacaccia.app.data

import java.time.LocalDate

data class BcEvent(
    val id: String?,
    val type: String?,
    val title: String,
    val region: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val fee: String?,
    val location: String?,
    val enrolled: String?,
    val status: String?,
    val detailUrl: String,
    val statusColor: String? = null,
    val branch: Branch? = null,
    val subsOpenDate: LocalDate? = null,
    val subsCloseDate: LocalDate? = null,
    val zone: String? = null
)

fun BcEvent.guessZone(): String? {
    zone?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val titleMatch = Regex(
        "\\b[Zz]ona\\s+([A-Z\\u00C0-\\u00DC][a-zA-Z\\u00C0-\\u00FF']+(?:\\s+(?:di|dei|delle|della|del|da|in|sotto|d')\\s+[A-Z\\u00C0-\\u00DC][a-zA-Z\\u00C0-\\u00FF']+)?(?:\\s+[A-Z\\u00C0-\\u00DC][a-zA-Z\\u00C0-\\u00FF']+)*)"
    ).find(title)
    titleMatch?.groupValues?.getOrNull(1)?.trim()?.let { return it }
    val loc = location ?: return null
    val match = Regex("\\(([^)]+)\\)").find(loc)
    val candidate = match?.groupValues?.getOrNull(1)?.trim() ?: return null
    if (candidate.length <= 2 && candidate.all { it.isUpperCase() || it.isLetter() }) {
        return null
    }
    return candidate
}
