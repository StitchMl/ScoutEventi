@file:Suppress("DEPRECATION")

package it.buonacaccia.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.ui.components.EventCard
import it.buonacaccia.app.ui.theme.BuonaCacciaTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

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
                title = { Text("Eventi BuonaCaccia") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showTypesDialog = true }) {
                        Icon(Icons.Default.AddAlert, contentDescription = "Filtri notifiche")
                    }
                    IconButton(onClick = { showRegionsDialog = true }) {
                        Icon(Icons.Default.AddLocation, contentDescription = "Regioni notifiche")
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
                onRefresh = { vm.refresh() },
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