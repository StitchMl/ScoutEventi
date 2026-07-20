package it.buonacaccia.app.data

import java.time.LocalDate

fun BcEvent.eventReferenceDate(): LocalDate? = startDate ?: endDate

fun BcEvent.isCurrentOrUpcoming(today: LocalDate = LocalDate.now()): Boolean =
    eventReferenceDate()?.let { !it.isBefore(today) } ?: true

fun BcEvent.hasOpenOrUnknownRegistrations(today: LocalDate = LocalDate.now()): Boolean =
    subsCloseDate?.let { !it.isBefore(today) } ?: true

fun BcEvent.isStillRelevant(today: LocalDate = LocalDate.now()): Boolean =
    isCurrentOrUpcoming(today) && hasOpenOrUnknownRegistrations(today)

fun BcEvent.shouldEnrichRegistrationWindow(today: LocalDate = LocalDate.now()): Boolean =
    statusColor == "red" && isCurrentOrUpcoming(today)
