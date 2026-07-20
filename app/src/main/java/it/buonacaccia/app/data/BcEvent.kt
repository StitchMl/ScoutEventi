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
    val zone: String? = null,
)

fun BcEvent.guessZone(): String? {
    ZoneCatalog.normalize(zone)?.let { return it }
    ZoneCatalog.fromTitle(title)?.let { return it }
    val locationValue = location ?: return null
    val parenthesized = Regex("\\(([^)]+)\\)")
        .find(locationValue)
        ?.groupValues
        ?.getOrNull(1)
    val candidate = ZoneCatalog.normalize(parenthesized) ?: return null
    if (candidate.length <= 2 && candidate.all { it.isUpperCase() || it.isLetter() }) return null
    return candidate
}

object ZoneCatalog {
    private val separator = Regex("\\s+(?:[-\\u2013\\u2014|])\\s+|_")
    private val whitespace = Regex("\\s+")

    fun normalize(raw: String?): String? {
        val cleaned = raw
            ?.replace('\u00A0', ' ')
            ?.replace(whitespace, " ")
            ?.trim()
            ?.replaceFirst(Regex("(?i)^zona\\s+"), "")
            ?.split(separator, limit = 2)
            ?.firstOrNull()
            ?.trim(' ', ':', ';', ',', '.', '-', '(', ')')
            ?.takeIf { it.length >= 3 }
            ?: return null

        if (cleaned.equals("da definire", ignoreCase = true) ||
            cleaned.equals("non definita", ignoreCase = true)
        ) return null

        val readableCase = if (cleaned.any(Char::isLetter) &&
            cleaned.filter(Char::isLetter).all(Char::isUpperCase)
        ) {
            cleaned.lowercase().split(' ').joinToString(" ") { word ->
                word.replaceFirstChar(Char::titlecase)
            }
        } else {
            cleaned
        }
        val normalizedApostrophe = readableCase.replace(
            Regex("(?i)\\bd(['\\u2019])([a-zà-ÿ])"),
        ) { match -> "d${match.groupValues[1]}${match.groupValues[2].uppercase()}" }

        return normalizedApostrophe.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    fun fromTitle(title: String): String? {
        val match = Regex(
            "(?i)\\bzona\\s+(.+?)(?=\\s+(?:[-\\u2013\\u2014|])\\s+|_|$)",
        ).find(title) ?: return null
        return normalize(match.groupValues.getOrNull(1))
    }
}
