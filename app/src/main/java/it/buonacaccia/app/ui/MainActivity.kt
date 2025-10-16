@file:Suppress("DEPRECATION")

package it.buonacaccia.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import it.buonacaccia.app.R
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.ui.components.EventCard
import it.buonacaccia.app.ui.theme.BuonaCacciaTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    private val requestNotifPerm = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* optional: react to the result */ }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        // 🆕 Show battery/Huawei tips only on first startup
        showBatteryHintsOnce()


        // Ask permission only on API 33+
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val deepId = intent?.getStringExtra("open_event_id")
        val deepTitle = intent?.getStringExtra("open_event_title")

        setContent {
            BuonaCacciaTheme {
                MainScreen(deepLinkEventId = deepId, deepLinkTitle = deepTitle)
            }
        }
    }

    // === BATTERY OPTIMIZATION & HUAWEI AUTO-START HANDLING (Play policy-safe) ===

    private fun showBatteryHintsOnce() {
        val prefs = getSharedPreferences("bc_prefs", MODE_PRIVATE)
        val shown = prefs.getBoolean("battery_hints_shown", false)
        if (!shown) {
            openBatteryOptimizationSettings(this)
            openHuaweiAutostart(this)
            prefs.edit { putBoolean("battery_hints_shown", true) }
        }
    }

    private fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // Some OEMs do not have this activity
        }
    }

    private fun openHuaweiAutostart(context: Context) {
        val intents = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            ),
            Intent("huawei.intent.action.HSM_PROTECTED_APPS")
        )
        for (i in intents) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            } catch (_: Exception) { /* move on to the next */ }
        }
    }
}

enum class NotifyMode { ALLOWLIST, DENYLIST }
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun MainScreen(
    vm: EventsViewModel = koinViewModel(),
    deepLinkEventId: String? = null,
    deepLinkTitle: String? = null
) {
    val context = LocalContext.current
    val cached by EventStore.cachedEventsFlow(context).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Preferences: types selected for notifications
    val interestedTypes by EventStore
        .notifyTypesFlow(context)
        .collectAsState(initial = emptySet())
    val interestedRegions by EventStore
        .notifyRegionsFlow(context)
        .collectAsState(initial = emptySet())

    val state = vm.state
    LaunchedEffect(cached) {
        if (cached.isNotEmpty()) vm.seedFromCache(cached)
    }
    LaunchedEffect(deepLinkEventId, deepLinkTitle) {
        val title = deepLinkTitle?.takeIf { it.isNotBlank() }
        if (title != null) {
            vm.onQueryChange(title)
        }
    }
    val swipeState = rememberSwipeRefreshState(isRefreshing = state.loading)

    // Multi-selection dialog types
    var showTypesDialog by remember { mutableStateOf(false) }
    var showRegionsDialog by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    // List of available types (derived from current events)
    val availableTypes = remember(state.items) {
        state.items.mapNotNull { it.type?.trim() }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
            .toList()
    }
    val mutedTypes by EventStore.muteTypesFlow(context).collectAsState(initial = emptySet())

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "App icon",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                title = { Text("Eventi Buona Caccia") },
                actions = {
                    IconButton(
                        onClick = { if (!state.loading) vm.refresh() },
                        enabled = !state.loading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showTypesDialog = true }) {
                        Icon(Icons.Default.AddAlert, contentDescription = "Filtri notifiche")
                    }
                    IconButton(onClick = { showRegionsDialog = true }) {
                        Icon(Icons.Default.AddLocation, contentDescription = "Regioni notifiche")
                    }
                    // 🆕 Info button
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 🆕 Notice: I am downloading (only if loading and empty cache)
            if (state.loading && cached.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text("Sto scaricando gli eventi…")
                }
            }

            SearchBarField(
                value = state.query,
                onValueChange = vm::onQueryChange
            )

            FiltersRow(
                regions = vm.regions,
                selectedRegion = state.region ?: "Tutte",
                onRegionChange = { vm.onRegionChange(if (it == "Tutte") null else it) },
                selectedUnit = state.unit,
                onUnitChange = vm::onUnitChange,
                onlyOpen = state.onlyOpen,                     // 🆕
                onOnlyOpenChange = vm::onOnlyOpenChange        // 🆕
            )

            if (state.error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.error}")
                }
                return@Column
            }

            SwipeRefresh(
                state = swipeState,
                onRefresh = { if (!state.loading) vm.refresh() },
            ) {
                val events: List<BcEvent> = vm.filtered
                if (events.isEmpty() && !state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No events found")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(events) { ev -> EventCard(ev = ev) }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    // 🆕 Show full-screen info screen
    if (showInfo) {
        InfoScreen(onClose = { showInfo = false })
        return
    }

    if (showTypesDialog) {
        // Initial mode: if there is an active denylist, start from DENYLIST; otherwise ALLOWLIST
        var mode by remember(interestedTypes, mutedTypes) {
            mutableStateOf(if (mutedTypes.isNotEmpty()) NotifyMode.DENYLIST else NotifyMode.ALLOWLIST)
        }

        // Initial selection consistent with the current mode
        var localSelection by remember(interestedTypes, mutedTypes, availableTypes) {
            mutableStateOf(
                (if (mode == NotifyMode.DENYLIST) mutedTypes else interestedTypes)
                    .intersect(availableTypes.toSet())
            )
        }

        AlertDialog(
            onDismissRequest = { showTypesDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (mode) {
                            NotifyMode.ALLOWLIST -> {
                                EventStore.setNotifyTypes(context, localSelection)
                                EventStore.setMuteTypes(context, emptySet())    // empty the other set
                            }
                            NotifyMode.DENYLIST -> {
                                EventStore.setMuteTypes(context, localSelection)
                                EventStore.setNotifyTypes(context, emptySet())   // empty the other set
                            }
                        }
                    }
                    showTypesDialog = false
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { showTypesDialog = false }) { Text("Annulla") } },
            title = { Text("Filtri notifiche (tipi)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Toggle mode (exclusive choice)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == NotifyMode.ALLOWLIST,
                            onClick = {
                                mode = NotifyMode.ALLOWLIST
                                localSelection = interestedTypes.intersect(availableTypes.toSet())
                            },
                            label = { Text("Consenti solo") }
                        )
                        FilterChip(
                            selected = mode == NotifyMode.DENYLIST,
                            onClick = {
                                mode = NotifyMode.DENYLIST
                                localSelection = mutedTypes.intersect(availableTypes.toSet())
                            },
                            label = { Text("Escludi") }
                        )
                    }

                    Text(
                        when (mode) {
                            NotifyMode.ALLOWLIST ->
                                "Riceverai notifiche solo per i tipi selezionati. Se non selezioni nulla, riceverai notifiche per tutti i tipi (inclusi nuovi)."
                            NotifyMode.DENYLIST  ->
                                "Riceverai notifiche per tutti i tipi tranne quelli selezionati. Se non selezioni nulla, riceverai notifiche per tutti i tipi."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Chips types available
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTypes.forEach { t ->
                            val selected = t in localSelection
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    localSelection = if (selected) localSelection - t else localSelection + t
                                },
                                label = { Text(t) }
                            )
                        }
                    }

                    // Select/Deselect All
                    val allSelected = localSelection.size == availableTypes.size && availableTypes.isNotEmpty()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            localSelection = if (allSelected) emptySet() else availableTypes.toSet()
                        }) {
                            Text(if (allSelected) "Deseleziona tutto" else "Seleziona tutto")
                        }
                    }
                }
            }
        )
    }

    if (showRegionsDialog) {
        var localSelection by remember(interestedRegions) {
            mutableStateOf(interestedRegions.intersect(vm.regions.toSet()))
        }

        AlertDialog(
            onDismissRequest = { showRegionsDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { EventStore.setNotifyRegions(context, localSelection) }
                    showRegionsDialog = false
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showRegionsDialog = false }) { Text("Annulla") }
            },
            title = { Text("Regioni per le notifiche") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se non selezioni nulla, riceverai notifiche per tutte le regioni.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.regions.filter { it != "Tutte" }.forEach { r ->
                            val selected = r in localSelection
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    localSelection =
                                        if (selected) localSelection - r else localSelection + r
                                },
                                label = { Text(r) }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SearchBarField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var tf by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) { if (value != tf.text) tf = tf.copy(text = value) }

    OutlinedTextField(
        value = tf,
        onValueChange = {
            tf = it
            onValueChange(it.text)
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (tf.text.isNotEmpty()) {
                IconButton(onClick = {
                    tf = TextFieldValue("")
                    onValueChange("")
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        placeholder = { Text("Cerca eventi...") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersRow(
    regions: List<String>,
    selectedRegion: String,
    onRegionChange: (String) -> Unit,
    selectedUnit: UnitFilter,
    onUnitChange: (UnitFilter) -> Unit,
    onlyOpen: Boolean,                         // 🆕
    onOnlyOpenChange: (Boolean) -> Unit        // 🆕
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // REGION (weight goes on the Box, not just the TextField)
        var expandedR by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expandedR,
            onExpandedChange = { expandedR = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor(),
                value = selectedRegion,
                onValueChange = {},
                readOnly = true,
                label = { Text("Regione") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedR) }
            )
            ExposedDropdownMenu(expanded = expandedR, onDismissRequest = { expandedR = false }) {
                regions.forEach { r ->
                    DropdownMenuItem(
                        text = { Text(r) },
                        onClick = { onRegionChange(r); expandedR = false }
                    )
                }
            }
        }

        // UNIT (same width as the first)
        var expandedU by remember { mutableStateOf(false) }
        val unitLabel = when (selectedUnit) {
            UnitFilter.TUTTE -> "Tutte"
            UnitFilter.BRANCO -> "Branco"
            UnitFilter.REPARTO -> "Reparto"
            UnitFilter.CLAN -> "Clan"
            UnitFilter.CAPI -> "Capi"
        }
        ExposedDropdownMenuBox(
            expanded = expandedU,
            onExpandedChange = { expandedU = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor(),
                value = unitLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Unità") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedU) }
            )
            ExposedDropdownMenu(expanded = expandedU, onDismissRequest = { expandedU = false }) {
                listOf(
                    UnitFilter.TUTTE to "Tutte",
                    UnitFilter.BRANCO to "Branco",
                    UnitFilter.REPARTO to "Reparto",
                    UnitFilter.CLAN to "Clan",
                    UnitFilter.CAPI to "Capi"
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onUnitChange(value); expandedU = false }
                    )
                }
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text("Solo iscrivibili")
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = onlyOpen,
            onCheckedChange = onOnlyOpenChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScreen(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi")
                    }
                },
                title = { Text("Informazioni") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Legenda colori eventi", style = MaterialTheme.typography.titleMedium)
            LegendRow(colorHex = "#4CAF50", label = "Iscrizioni aperte (verde)")
            LegendRow(colorHex = "#FFEB3B", label = "Attenzione / quasi pieno (giallo)")
            LegendRow(colorHex = "#9C27B0", label = "Lista d’attesa (viola)")
            LegendRow(colorHex = "#F44336", label = "Iscrizioni chiuse (rosso)")

            Spacer(Modifier.height(8.dp))
            Text("Suggerimenti", style = MaterialTheme.typography.titleMedium)
            Text("• Filtra per Regione e Branca.\n• La barra di ricerca ha la “X” per cancellare.\n• Notifiche: nuovi eventi; 7/1 giorno prima e il giorno di apertura (prima delle 9:00); 1 giorno prima della chiusura.")

            Spacer(Modifier.height(8.dp))
            Text("Scaricamento iniziale", style = MaterialTheme.typography.titleMedium)
            Text("Se non vedi ancora eventi, l’app sta scaricando la lista. Rimani online: appena pronti compariranno in automatico.")
        }
    }
}

@Composable
private fun LegendRow(colorHex: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(16.dp)
                .background(color = Color(colorHex.toColorInt()), shape = CircleShape)
        )
        Text(label)
    }
}