# 📱 Buona Caccia App

> Applicazione Android per il monitoraggio e la gestione automatica degli eventi **AGESCI – Buona Caccia**, con sistema di notifiche intelligenti e caching locale.

---

## 🚀 Funzionalità principali

### 🧭 Scoperta eventi
- Parsing automatico delle pagine di [BuonaCaccia](https://buonacaccia.net) tramite `Jsoup`.
- Raccolta di:
  - Titolo, tipo e branca dell’evento.
  - Regione, luogo e periodo.
  - Stato iscrizioni (aperte, chiuse, illimitate, ecc.).
  - Date di **apertura** e **chiusura iscrizioni** (dal dettaglio evento).

### 🔔 Sistema di notifiche
Le notifiche vengono generate automaticamente dai **Worker** in background:
| Tipo | Condizione | Descrizione |
|------|-------------|-------------|
| 🆕 **Nuovo evento** | appena scoperto un evento non ancora visto | Notifica istantanea |
| 📅 **Apertura iscrizioni** | 7 giorni prima | “Tra una settimana aprono le iscrizioni” |
| 📅 **Apertura iscrizioni** | 1 giorno prima | “Domani aprono le iscrizioni” |
| 📅 **Apertura iscrizioni** | giorno stesso, prima delle 9:00 | “Iscrizioni aperte!” |
| ⏳ **Chiusura iscrizioni** | 1 giorno prima | “Ultimi giorni per iscriversi!” |

Le notifiche includono un **richiamo grafico all’icona principale dell’app** (badge del logo sull’orologio blu).

---

## 🧠 Architettura

### 🧩 Componenti principali
| Componente | Ruolo |
|-------------|--------|
| `EventsRepository` | Effettua le chiamate HTTP e il parsing HTML. |
| `EventStore` | Gestisce la cache persistente (DataStore Preferences). |
| `NewEventsWorker` | Scopre nuovi eventi e genera notifiche istantanee. |
| `SubscriptionsWorker` | Pianifica e invia i promemoria (apertura/chiusura). |
| `Notifier` | Crea le notifiche con icone personalizzate e deep link. |
| `HtmlParser` | Estrae i campi principali e le date iscrizioni dal DOM. |
| `EventsViewModel` | Espone gli eventi all’interfaccia Compose. |
| `MainActivity` | UI Compose: ricerca, filtri, refresh e dialog di notifica. |

### ⚙️ Stack tecnologico
- **Kotlin** + **Jetpack Compose**
- **WorkManager** per l’esecuzione periodica dei Worker
- **Koin** per l’injection delle dipendenze
- **DataStore Preferences** per cache e configurazioni
- **OkHttp** per il fetch HTML
- **Jsoup** per il parsing
- **Timber** per logging

---

## 💾 Caching & Persistenza

Gli eventi vengono salvati localmente tramite `EventStore`.  
Il sistema:
- evita di ricaricare da zero all’avvio,
- mostra subito gli eventi “in pancia”,
- elimina automaticamente gli eventi **chiusi** (`subsCloseDate < oggi`),
- mantiene un registro di:
  - ID eventi già visti (`seenIds`),
  - notifiche già inviate (`sentReminders`),
  - filtri selezionati (tipi, regioni, modalità notifica).

---

## 🧭 Interfaccia utente

### 🔍 Ricerca e filtri
- Barra di ricerca con icona “❌” per svuotare il testo.
- Filtro per:
  - Regione (dropdown)
  - Branca (Branco / Reparto / Clan / Capi)
  - Solo eventi con iscrizioni aperte

### ⚙️ Gestione notifiche
- Filtro per **tipi di evento** (Allowlist / Denylist)
- Filtro per **regioni**
- Dialog Material3 chiaro e coerente con il tema dell’app

---

## 🕰️ Logica Worker

I due worker principali operano in modo indipendente:

### 🔄 `NewEventsWorker`
- Scarica l’elenco eventi
- Aggiorna la cache
- Purga gli eventi chiusi
- Notifica solo gli eventi nuovi (`id` non presente in `seenIds`)

### 🗓️ `SubscriptionsWorker`
- Legge la cache locale
- Calcola i reminder giornalieri
- Notifica in base a `subsOpenDate` / `subsCloseDate`
- Evita duplicazioni con chiavi `eventId|YYYY-MM-DD|TAG`

---

## 🧩 Notifiche con deep link

Ogni notifica apre l’app filtrando automaticamente l’evento corrispondente (titolo o ID) grazie a `Intent` extras:
```kotlin
putExtra("open_event_id", event.id)
putExtra("open_event_title", event.title)
