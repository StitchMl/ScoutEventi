import Foundation
import SwiftUI
import UIKit

private struct BcEvent: Identifiable, Codable, Hashable {
    let id: String
    let title: String
    let region: String?
    let startDateIso: String?
    let endDateIso: String?
    let status: String?
    let detailUrl: String

    var subtitle: String {
        let parts = [region, formattedRange, status]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return parts.joined(separator: " | ")
    }

    var formattedRange: String? {
        guard let startDateIso else { return nil }
        let start = Self.outputDateFormatter.string(from: Self.isoDateFormatter.date(from: startDateIso) ?? Date())
        guard
            let endDateIso,
            endDateIso != startDateIso,
            let endDate = Self.isoDateFormatter.date(from: endDateIso)
        else {
            return start
        }
        return "\(start) - \(Self.outputDateFormatter.string(from: endDate))"
    }

    var sortKey: String {
        startDateIso ?? "9999-12-31"
    }

    private static let isoDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let outputDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "it_IT")
        formatter.dateFormat = "dd/MM/yyyy"
        return formatter
    }()
}

private enum EventCache {
    private static let key = "it.buonacaccia.ios.cached-events"

    static func load() -> [BcEvent] {
        guard
            let data = UserDefaults.standard.data(forKey: key),
            let events = try? JSONDecoder().decode([BcEvent].self, from: data)
        else {
            return []
        }
        return events
    }

    static func save(_ events: [BcEvent]) {
        guard let data = try? JSONEncoder().encode(events) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}

private enum RegionCatalog {
    private struct Entry {
        let canonicalName: String
        let aliases: [String]
    }

    private static let entries = [
        Entry(canonicalName: "Abruzzo", aliases: ["abruzzo"]),
        Entry(canonicalName: "Basilicata", aliases: ["basilicata"]),
        Entry(canonicalName: "Calabria", aliases: ["calabria"]),
        Entry(canonicalName: "Campania", aliases: ["campania"]),
        Entry(canonicalName: "Emilia-Romagna", aliases: ["emilia romagna", "emiro"]),
        Entry(canonicalName: "Friuli-Venezia Giulia", aliases: ["friuli venezia giulia", "fvg"]),
        Entry(canonicalName: "Lazio", aliases: ["lazio"]),
        Entry(canonicalName: "Liguria", aliases: ["liguria"]),
        Entry(canonicalName: "Lombardia", aliases: ["lombardia"]),
        Entry(canonicalName: "Marche", aliases: ["marche"]),
        Entry(canonicalName: "Molise", aliases: ["molise"]),
        Entry(canonicalName: "Piemonte", aliases: ["piemonte"]),
        Entry(canonicalName: "Puglia", aliases: ["puglia"]),
        Entry(canonicalName: "Sardegna", aliases: ["sardegna"]),
        Entry(canonicalName: "Sicilia", aliases: ["sicilia"]),
        Entry(canonicalName: "Toscana", aliases: ["toscana"]),
        Entry(canonicalName: "Trentino-Alto Adige", aliases: ["trentino alto adige", "trentino", "alto adige", "taa"]),
        Entry(canonicalName: "Umbria", aliases: ["umbria"]),
        Entry(canonicalName: "Valle d'Aosta", aliases: ["valle d aosta", "val d aosta", "vda", "valdaosta"]),
        Entry(canonicalName: "Veneto", aliases: ["veneto"])
    ]

    static func firstCanonicalName(in text: String) -> String? {
        let normalized = normalize(text)
        let padded = " \(normalized) "
        return entries
            .sorted { longestAliasLength(for: $0) > longestAliasLength(for: $1) }
            .first(where: { entry in
                entry.aliases.contains { alias in
                    padded.contains(" \(normalize(alias)) ")
                }
            })?
            .canonicalName
    }

    private static func longestAliasLength(for entry: Entry) -> Int {
        entry.aliases.map { normalize($0).count }.max() ?? 0
    }

    private static func normalize(_ value: String) -> String {
        value
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "it_IT"))
            .replacingOccurrences(of: "valdaosta", with: "valle d aosta")
            .replacingOccurrences(of: "val d aosta", with: "valle d aosta")
            .replacingOccurrences(of: "[^\\p{L}\\p{N}]+", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }
}

private actor BuonaCacciaService {
    private let candidateBases = [
        "https://buonacaccia.agesci.it/Events.aspx?All=1",
        "https://buonacaccia.net/Events.aspx?All=1"
    ]

    private let linkRegex = try! NSRegularExpression(
        pattern: #"(?is)<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>"#
    )
    private let tagRegex = try! NSRegularExpression(pattern: #"(?is)<[^>]+>"#)
    private let dateRegex = try! NSRegularExpression(pattern: #"\b\d{1,2}/\d{1,2}/\d{4}\b"#)

    func fetchEvents() async throws -> [BcEvent] {
        var lastError: Error?

        for base in candidateBases {
            do {
                return try await fetchEvents(from: base)
            } catch {
                lastError = error
            }
        }

        throw lastError ?? URLError(.badServerResponse)
    }

    private func fetchEvents(from base: String) async throws -> [BcEvent] {
        guard let url = URL(string: base) else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 30
        request.setValue("ScoutEventi-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        request.setValue("it-IT,it;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let http = response as? HTTPURLResponse,
            (200...299).contains(http.statusCode)
        else {
            throw URLError(.badServerResponse)
        }

        let html = String(decoding: data, as: UTF8.self)
        let events = parseEvents(html: html, baseURL: url)

        if events.isEmpty {
            throw URLError(.cannotParseResponse)
        }

        return events
    }

    private func parseEvents(html: String, baseURL: URL) -> [BcEvent] {
        let fullRange = NSRange(location: 0, length: (html as NSString).length)
        let todayIso = isoFormatter.string(from: Date())
        var unique = [String: BcEvent]()

        for match in linkRegex.matches(in: html, options: [], range: fullRange) {
            guard
                let hrefRange = Range(match.range(at: 1), in: html),
                let titleRange = Range(match.range(at: 2), in: html)
            else {
                continue
            }

            let hrefRaw = String(html[hrefRange])
            guard isEventLink(hrefRaw) else { continue }

            let detailUrl = absoluteUrl(from: hrefRaw, baseURL: baseURL)
            let title = cleanHtml(String(html[titleRange]))
            guard !title.isEmpty else { continue }

            let contextHtml = contextualHtml(for: match.range, in: html)
            let contextText = cleanHtml(contextHtml)
            let dates = extractDates(from: contextText)
            let startDateIso = dates.first

            if let startDateIso, startDateIso < todayIso {
                continue
            }

            let key = extractEventId(from: detailUrl) ?? detailUrl
            let event = BcEvent(
                id: key,
                title: title,
                region: RegionCatalog.firstCanonicalName(in: contextText),
                startDateIso: startDateIso,
                endDateIso: dates.count > 1 ? dates[1] : nil,
                status: detectStatus(in: contextText),
                detailUrl: detailUrl
            )

            unique[key] = unique[key] ?? event
        }

        return unique.values.sorted {
            if $0.sortKey == $1.sortKey {
                return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
            }
            return $0.sortKey < $1.sortKey
        }
    }

    private func contextualHtml(for range: NSRange, in html: String) -> String {
        let nsHtml = html as NSString
        let fallbackStart = max(0, range.location - 1200)
        let fallbackLength = min(nsHtml.length - fallbackStart, range.length + 2400)

        let before = nsHtml.substring(to: min(range.location, nsHtml.length))
        let afterLocation = min(range.location + range.length, nsHtml.length)
        let after = nsHtml.substring(from: afterLocation)

        let rowStart = (before as NSString).range(of: "<tr", options: [.caseInsensitive, .backwards])
        let rowEnd = (after as NSString).range(of: "</tr>", options: .caseInsensitive)
        if rowStart.location != NSNotFound, rowEnd.location != NSNotFound {
            let start = rowStart.location
            let end = afterLocation + rowEnd.location + rowEnd.length
            return nsHtml.substring(with: NSRange(location: start, length: end - start))
        }

        return nsHtml.substring(with: NSRange(location: fallbackStart, length: fallbackLength))
    }

    private func extractDates(from text: String) -> [String] {
        let range = NSRange(location: 0, length: (text as NSString).length)
        return dateRegex.matches(in: text, options: [], range: range)
            .compactMap { match -> String? in
                guard let dateRange = Range(match.range, in: text) else { return nil }
                return parseItalianDate(String(text[dateRange]))
            }
            .reduce(into: [String]()) { partial, item in
                if !partial.contains(item) {
                    partial.append(item)
                }
            }
    }

    private func parseItalianDate(_ raw: String) -> String? {
        for formatter in inputFormatters {
            if let date = formatter.date(from: raw) {
                return isoFormatter.string(from: date)
            }
        }
        return nil
    }

    private func detectStatus(in text: String) -> String? {
        let normalized = text.lowercased()
        if normalized.contains("lista d'attesa") || normalized.contains("lista di attesa") {
            return "Lista d'attesa"
        }
        if normalized.contains("chius") {
            return "Chiuso"
        }
        if normalized.contains("apert") {
            return "Aperto"
        }
        return nil
    }

    private func isEventLink(_ href: String) -> Bool {
        let normalized = href.lowercased()
        if normalized.contains("event.aspx") && normalized.contains("e=") {
            return true
        }
        return normalized.range(of: #"/event/\d+"#, options: .regularExpression) != nil
    }

    private func absoluteUrl(from href: String, baseURL: URL) -> String {
        if let absolute = URL(string: href, relativeTo: baseURL)?.absoluteURL {
            return absolute.absoluteString
        }
        return href
    }

    private func extractEventId(from url: String) -> String? {
        if let query = URLComponents(string: url)?.queryItems?.first(where: { $0.name.lowercased() == "e" })?.value {
            return query
        }
        let range = (url as NSString).range(of: #"/event/(\d+)"#, options: .regularExpression)
        guard range.location != NSNotFound else { return nil }
        let match = (url as NSString).substring(with: range)
        return match.components(separatedBy: "/").last
    }

    private func cleanHtml(_ html: String) -> String {
        let nsHtml = html as NSString
        let range = NSRange(location: 0, length: nsHtml.length)
        let stripped = tagRegex.stringByReplacingMatches(in: html, options: [], range: range, withTemplate: " ")
        return decodeHtmlEntities(stripped)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func decodeHtmlEntities(_ text: String) -> String {
        guard let data = text.data(using: .utf8) else { return text }
        if let attributed = try? NSAttributedString(
            data: data,
            options: [
                .documentType: NSAttributedString.DocumentType.html,
                .characterEncoding: String.Encoding.utf8.rawValue
            ],
            documentAttributes: nil
        ) {
            return attributed.string
        }
        return text
    }

    private let inputFormatters: [DateFormatter] = {
        let patterns = ["dd/MM/yyyy", "d/M/yyyy"]
        return patterns.map { pattern in
            let formatter = DateFormatter()
            formatter.calendar = Calendar(identifier: .gregorian)
            formatter.locale = Locale(identifier: "it_IT")
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
            formatter.dateFormat = pattern
            return formatter
        }
    }()

    private let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

@MainActor
private final class EventsViewModel: ObservableObject {
    @Published var events: [BcEvent]
    @Published var query = ""
    @Published var selectedRegion = "Tutte"
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let service = BuonaCacciaService()
    private var hasStarted = false
    private let smokeModeEnabled: Bool

    init() {
        smokeModeEnabled = ProcessInfo.processInfo.arguments.contains("--scouteventi-smoke-data")
        if smokeModeEnabled {
            events = Self.smokeEvents
            hasStarted = true
            print("SCOUTEVENTI_SMOKE_READY count=\(events.count)")
        } else {
            events = EventCache.load()
        }
    }

    var availableRegions: [String] {
        let regions = Set(events.compactMap { $0.region }.filter { !$0.isEmpty })
        return ["Tutte"] + regions.sorted()
    }

    var filteredEvents: [BcEvent] {
        events.filter { event in
            let matchesQuery: Bool
            if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                matchesQuery = true
            } else {
                let haystack = [event.title, event.region, event.status]
                    .compactMap { $0 }
                    .joined(separator: " ")
                    .lowercased()
                matchesQuery = haystack.contains(query.lowercased())
            }

            let matchesRegion = selectedRegion == "Tutte" || event.region == selectedRegion
            return matchesQuery && matchesRegion
        }
    }

    func loadIfNeeded() async {
        guard !hasStarted else { return }
        hasStarted = true
        await refresh()
    }

    func refresh() async {
        if smokeModeEnabled {
            events = Self.smokeEvents
            errorMessage = nil
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let fresh = try await service.fetchEvents()
            events = fresh
            EventCache.save(fresh)
        } catch {
            if events.isEmpty {
                events = EventCache.load()
            }
            errorMessage = "Aggiornamento non riuscito. Riprova dal refresh."
        }
    }

    private static let smokeEvents: [BcEvent] = [
        BcEvent(
            id: "smoke-1",
            title: "Campo RS di prova",
            region: "Lazio",
            startDateIso: "2026-06-12",
            endDateIso: "2026-06-15",
            status: "Aperto",
            detailUrl: "https://buonacaccia.agesci.it/Event.aspx?E=smoke-1"
        ),
        BcEvent(
            id: "smoke-2",
            title: "Cantiere EG simulator",
            region: "Lombardia",
            startDateIso: "2026-07-03",
            endDateIso: "2026-07-06",
            status: "Lista d'attesa",
            detailUrl: "https://buonacaccia.agesci.it/Event.aspx?E=smoke-2"
        ),
        BcEvent(
            id: "smoke-3",
            title: "Evento Capi smoke test",
            region: "Veneto",
            startDateIso: "2026-09-20",
            endDateIso: "2026-09-21",
            status: "Chiuso",
            detailUrl: "https://buonacaccia.agesci.it/Event.aspx?E=smoke-3"
        )
    ]
}

private struct EventRow: View {
    let event: BcEvent

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(event.title)
                .font(.headline)
                .foregroundStyle(.primary)
            if !event.subtitle.isEmpty {
                Text(event.subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct EmptyStateView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "calendar.badge.exclamationmark")
                .font(.system(size: 34))
                .foregroundStyle(.secondary)
            Text("Nessun evento")
                .font(.headline)
            Text("Prova a cambiare ricerca o regione.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .padding(24)
    }
}

struct ContentView: View {
    @StateObject private var viewModel = EventsViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.filteredEvents.isEmpty && viewModel.isLoading && viewModel.events.isEmpty {
                    ProgressView("Sto scaricando gli eventi...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        if let errorMessage = viewModel.errorMessage {
                            Section {
                                Text(errorMessage)
                                    .font(.footnote)
                                    .foregroundStyle(.orange)
                            }
                        }

                        ForEach(viewModel.filteredEvents) { event in
                            if let url = URL(string: event.detailUrl) {
                                Link(destination: url) {
                                    EventRow(event: event)
                                }
                            } else {
                                EventRow(event: event)
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                    .overlay {
                        if viewModel.filteredEvents.isEmpty && !viewModel.isLoading {
                            EmptyStateView()
                        }
                    }
                }
            }
            .navigationTitle("ScoutEventi")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu(viewModel.selectedRegion) {
                        ForEach(viewModel.availableRegions, id: \.self) { region in
                            Button(region) {
                                viewModel.selectedRegion = region
                            }
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await viewModel.refresh() }
                    } label: {
                        if viewModel.isLoading {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                    .disabled(viewModel.isLoading)
                }
            }
            .searchable(text: $viewModel.query, prompt: "Cerca eventi, regioni, stato")
            .refreshable {
                await viewModel.refresh()
            }
            .task {
                await viewModel.loadIfNeeded()
            }
        }
    }
}
