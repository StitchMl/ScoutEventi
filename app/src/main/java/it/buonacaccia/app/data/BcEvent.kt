package it.buonacaccia.app.data

import java.time.LocalDate

enum class Branch { LC, EG, RS, CAPI }

data class BcEvent(
    val id: String?,            // es. "12345" da event.aspx?e=12345 (se presente)
    val type: String?,
    val title: String,
    val region: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val fee: String?,
    val location: String?,
    val enrolled: String?,      // es. "34/40"
    val status: String?,        // es. "Iscrizioni aperte"
    val detailUrl: String,
    val branch: Branch? = null
)