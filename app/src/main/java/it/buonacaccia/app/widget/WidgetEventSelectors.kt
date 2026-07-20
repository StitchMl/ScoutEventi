package it.buonacaccia.app.widget

import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.isStillRelevant
import java.time.LocalDate
import java.util.Locale

internal enum class EventsWidgetKind(
    val title: String,
    val emptyState: String,
    val emptyFollowedState: String
) {
    UPCOMING_OPENINGS(
        title = "Aperture imminenti",
        emptyState = "Nessuna apertura imminente",
        emptyFollowedState = "Nessun evento seguito in apertura"
    ),
    EVENTS_BY_DATE(
        title = "Eventi per data",
        emptyState = "Nessun evento disponibile",
        emptyFollowedState = "Nessun evento seguito disponibile"
    )
}

internal fun selectWidgetEvents(
    kind: EventsWidgetKind,
    events: List<BcEvent>,
    today: LocalDate,
    onlyFollowed: Boolean,
    subscribedKeys: Set<String>
): List<BcEvent> {
    val filtered = events
        .asSequence()
        .filter { event ->
            !onlyFollowed || EventStore.eventKeyOf(event) in subscribedKeys
        }

    return when (kind) {
        EventsWidgetKind.UPCOMING_OPENINGS -> filtered
            .filter { event ->
                val open = event.subsOpenDate
                event.isStillRelevant(today) &&
                    open != null &&
                    !open.isBefore(today)
            }
            .sortedWith(
                compareBy<BcEvent>(
                    { it.subsOpenDate ?: LocalDate.MAX },
                    { it.startDate ?: LocalDate.MAX },
                    { it.title.lowercase(Locale.ROOT) }
                )
            )
            .take(30)
            .toList()

        EventsWidgetKind.EVENTS_BY_DATE -> filtered
            .filter { event -> event.isStillRelevant(today) }
            .sortedWith(
                compareBy<BcEvent>(
                    { it.startDate ?: it.endDate ?: LocalDate.MAX },
                    { it.endDate ?: LocalDate.MAX },
                    { it.title.lowercase(Locale.ROOT) }
                )
            )
            .take(30)
            .toList()
    }
}

internal fun badgeDateFor(kind: EventsWidgetKind, event: BcEvent): LocalDate? =
    when (kind) {
        EventsWidgetKind.UPCOMING_OPENINGS -> event.subsOpenDate
        EventsWidgetKind.EVENTS_BY_DATE -> event.startDate ?: event.endDate
    }
