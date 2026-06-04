@file:Suppress("AssignedValueIsNeverRead")

package it.buonacaccia.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import it.buonacaccia.app.R
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventStore
import it.buonacaccia.app.data.guessZone
import it.buonacaccia.app.ui.components.EventCard
import it.buonacaccia.app.ui.theme.BuonaCacciaTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val requestNotifPerm = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* optional: react to the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 🆕 Show battery/Huawei tips only on first startup
        val showBatteryHints = consumeBatteryHintsFlag()
        val deepTitle = intent?.getStringExtra("open_event_title")
        val forceRefresh = intent?.getBooleanExtra("force_refresh", false) == true
        val openInfo = intent?.getBooleanExtra("open_info", false) == true

        setContent {
            // Read theme preference and calculate boolean for Theme wrapper
            val ctx = this
            val themeMode by EventStore.themeModeFlow(ctx).collectAsState(initial = EventStore.ThemeMode.SYSTEM)
            val dark = when (themeMode) {
                EventStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                EventStore.ThemeMode.DARK   -> true
                EventStore.ThemeMode.LIGHT  -> false
            }

            // ✅ scope Compose to launch coroutines from the composable
            val activityScope = rememberCoroutineScope()

            BuonaCacciaTheme(darkTheme = dark) {
                MainScreen(
                    deepLinkTitle = deepTitle,
                    themeMode = themeMode,
                    onChangeTheme = { mode -> activityScope.launch { EventStore.setThemeMode(ctx, mode) } },
                    forceRefresh = forceRefresh,
                    initialShowInfo = openInfo,
                    initialShowBatteryHints = showBatteryHints,
                    onOpenBatterySettings = { openBatteryOptimizationSettings(ctx) },
                    onOpenAutostartSettings = { openHuaweiAutostart(ctx) }
                )
            }
        }
    }

    // === BATTERY OPTIMIZATION & HUAWEI AUTO-START HANDLING (Play policy-safe) ===

    private fun consumeBatteryHintsFlag(): Boolean {
        val prefs = getSharedPreferences("bc_prefs", MODE_PRIVATE)
        val shown = prefs.getBoolean("battery_hints_shown", false)
        if (shown) return false

        prefs.edit { putBoolean("battery_hints_shown", true) }
        return true
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
@Composable
private fun MainScreen(
    vm: EventsViewModel = koinViewModel(),
    deepLinkTitle: String? = null,
    themeMode: EventStore.ThemeMode,
    onChangeTheme: (EventStore.ThemeMode) -> Unit,
    forceRefresh: Boolean = false,
    initialShowInfo: Boolean = false,
    initialShowBatteryHints: Boolean = false,
    onOpenBatterySettings: () -> Unit = {},
    onOpenAutostartSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val cached by EventStore.cachedEventsFlow(context).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    // Events followed (bell)
    val subscribedIds by EventStore.subscribedIdsFlow(context).collectAsState(initial = emptySet())

    // "Followed only" filter (UI status).
    var onlyFollowed by remember { mutableStateOf(false) }

    // Preferences: types selected for notifications
    val interestedTypes by EventStore
        .notifyTypesFlow(context)
        .collectAsState(initial = emptySet())
    val interestedRegions by EventStore
        .notifyRegionsFlow(context)
        .collectAsState(initial = emptySet())
    val interestedZones by EventStore
        .notifyZonesFlow(context)
        .collectAsState(initial = emptySet())

    val state = vm.state
    LaunchedEffect(cached) {
        if (cached.isNotEmpty()) vm.seedFromCache(cached)
    }
    LaunchedEffect(deepLinkTitle) {
        val title = deepLinkTitle?.takeIf { it.isNotBlank() }
        if (title != null) {
            vm.onQueryChange(title)
        }
    }
    val pullRefreshState = rememberPullToRefreshState()

    // Multi-selection dialog types
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(initialShowInfo) }
    var showBatteryHints by remember { mutableStateOf(initialShowBatteryHints) }

    // List of available types (derived from current events)
    val availableTypes = remember(state.items) {
        state.items.mapNotNull { it.type?.trim() }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
            .toList()
    }
    val availableZones = remember(state.items) {
        state.items.mapNotNull { it.guessZone() }
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
                title = { Text("Buona Caccia") },
                actions = {
                    IconButton(
                        onClick = { if (!state.loading) vm.refresh() },
                        enabled = !state.loading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { if (!showNotificationSettings) showNotificationSettings = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Impostazioni notifiche")
                    }

                    IconButton(onClick = { if (!showInfo) showInfo = true }) {
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
                zones = vm.zones,
                selectedZone = state.zone ?: "Tutte",
                onZoneChange = { vm.onZoneChange(if (it == "Tutte") null else it) },
                selectedUnit = state.unit,
                onUnitChange = vm::onUnitChange,
                onlyOpen = state.onlyOpen,
                onOnlyOpenChange = vm::onOnlyOpenChange,
                onlyFollowed = onlyFollowed,
                onOnlyFollowedChange = { onlyFollowed = it }
            )

            state.error?.let { message ->
                ErrorBanner(message = message)
            }

            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                state = pullRefreshState,
                isRefreshing = state.loading,
                onRefresh = { if (!state.loading) vm.refresh() },
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        state = pullRefreshState,
                        isRefreshing = state.loading,
                        containerColor = MaterialTheme.colorScheme.surface,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                val base: List<BcEvent> = vm.filtered

                val events: List<BcEvent> =
                    if (onlyFollowed) {
                        base
                            .asSequence()
                            .filter { ev -> EventStore.eventKeyOf(ev) in subscribedIds }
                            // ✅ Order by event date (nearest first). Null at the bottom.
                            .sortedWith(
                                compareBy(
                                    { it.startDate ?: it.endDate ?: LocalDate.MAX },
                                    { it.endDate ?: LocalDate.MAX },
                                    { it.title.lowercase() }
                                )
                            )
                            .toList()
                    } else base
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
                        items(events, key = { ev -> EventStore.eventKeyOf(ev) }) { ev ->
                            EventCard(ev = ev)
                        }
                        item { Spacer(Modifier.height(56.dp)) }
                    }
                }
            }
        }
    }

    // 🆕 Show full-screen info screen
    if (showInfo) {
        InfoScreen(
            onClose = { showInfo = false },
            themeMode = themeMode,
            onChangeTheme = onChangeTheme
        )
        return
    }

    if (showNotificationSettings) {
        var mode by remember(interestedTypes, mutedTypes) {
            mutableStateOf(if (mutedTypes.isNotEmpty()) NotifyMode.DENYLIST else NotifyMode.ALLOWLIST)
        }

        var localTypes by remember(interestedTypes, mutedTypes, availableTypes) {
            mutableStateOf(
                (if (mode == NotifyMode.DENYLIST) mutedTypes else interestedTypes)
                    .intersect(availableTypes.toSet())
            )
        }

        var localRegions by remember(interestedRegions) {
            mutableStateOf(interestedRegions.intersect(vm.regions.toSet()))
        }

        var localZones by remember(interestedZones) {
            mutableStateOf(interestedZones.intersect(availableZones.toSet()))
        }

        AlertDialog(
            onDismissRequest = { showNotificationSettings = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (mode) {
                            NotifyMode.ALLOWLIST -> {
                                EventStore.setNotifyTypes(context, localTypes)
                                EventStore.setMuteTypes(context, emptySet())
                            }
                            NotifyMode.DENYLIST -> {
                                EventStore.setMuteTypes(context, localTypes)
                                EventStore.setNotifyTypes(context, emptySet())
                            }
                        }
                        EventStore.setNotifyRegions(context, localRegions)
                        EventStore.setNotifyZones(context, localZones)
                    }
                    showNotificationSettings = false
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationSettings = false }) { Text("Annulla") }
            },
            title = { Text("Impostazioni notifiche") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Filtro per tipo di evento", style = MaterialTheme.typography.titleMedium)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == NotifyMode.ALLOWLIST,
                            onClick = {
                                mode = NotifyMode.ALLOWLIST
                                localTypes = interestedTypes.intersect(availableTypes.toSet())
                            },
                            label = { Text("Consenti solo") }
                        )
                        FilterChip(
                            selected = mode == NotifyMode.DENYLIST,
                            onClick = {
                                mode = NotifyMode.DENYLIST
                                localTypes = mutedTypes.intersect(availableTypes.toSet())
                            },
                            label = { Text("Escludi") }
                        )
                    }

                    Text(
                        when (mode) {
                            NotifyMode.ALLOWLIST ->
                                "Riceverai notifiche solo per i tipi selezionati. Se non selezioni nulla, le riceverai per tutti."
                            NotifyMode.DENYLIST  ->
                                "Riceverai notifiche per tutti tranne quelli selezionati. Se non selezioni nulla, le riceverai per tutti."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTypes.forEach { t ->
                            val selected = t in localTypes
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    localTypes = if (selected) localTypes - t else localTypes + t
                                },
                                label = { Text(t) }
                            )
                        }
                    }

                    val allSelected = localTypes.size == availableTypes.size && availableTypes.isNotEmpty()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            localTypes = if (allSelected) emptySet() else availableTypes.toSet()
                        }) {
                            Text(if (allSelected) "Deseleziona tutto" else "Seleziona tutto")
                        }
                    }

                    HorizontalDivider()

                    Text("Filtro per regione", style = MaterialTheme.typography.titleMedium)
                    Text("Riceverai notifiche per le regioni selezionate (nessuna = tutte).", style = MaterialTheme.typography.bodySmall)

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vm.regions.filter { it != "Tutte" }.forEach { r ->
                            val selected = r in localRegions
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    localRegions = if (selected) localRegions - r else localRegions + r
                                },
                                label = { Text(r) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("Filtro per zona/provincia", style = MaterialTheme.typography.titleMedium)
                    Text("Riceverai notifiche per le zone/sigle selezionate (nessuna = tutte).", style = MaterialTheme.typography.bodySmall)

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (availableZones.isEmpty()) {
                            Text("Nessuna zona rilevata negli eventi attuali.", style = MaterialTheme.typography.labelMedium)
                        } else {
                            availableZones.forEach { z ->
                                val selected = z in localZones
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        localZones = if (selected) localZones - z else localZones + z
                                    },
                                    label = { Text(z) }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
    if (showBatteryHints) {
        AlertDialog(
            onDismissRequest = { showBatteryHints = false },
            confirmButton = {
                TextButton(onClick = { showBatteryHints = false }) { Text("Chiudi") }
            },
            title = { Text("Ottimizzazione batteria") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Per mantenere notifiche e refresh affidabili, conviene escludere l'app dai risparmi energetici aggressivi."
                    )
                    TextButton(onClick = onOpenBatterySettings) {
                        Text("Apri impostazioni batteria")
                    }
                    TextButton(onClick = onOpenAutostartSettings) {
                        Text("Apri avvio automatico Huawei")
                    }
                }
            }
        )
    }
    LaunchedEffect(forceRefresh) {
        if (forceRefresh) vm.refresh()
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = "Aggiornamento non riuscito: $message",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
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
    zones: List<String>,
    selectedZone: String,
    onZoneChange: (String) -> Unit,
    selectedUnit: UnitFilter,
    onUnitChange: (UnitFilter) -> Unit,
    onlyOpen: Boolean,
    onOnlyOpenChange: (Boolean) -> Unit,
    onlyFollowed: Boolean,
    onOnlyFollowedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    value = selectedRegion,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
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

            // ZONE (same width as the others)
            var expandedZ by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedZ,
                onExpandedChange = { expandedZ = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    value = selectedZone,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Zona") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedZ) }
                )
                ExposedDropdownMenu(expanded = expandedZ, onDismissRequest = { expandedZ = false }) {
                    zones.forEach { z ->
                        DropdownMenuItem(
                            text = { Text(z) },
                            onClick = { onZoneChange(z); expandedZ = false }
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    value = unitLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
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

            // Empty spacer to balance the row layout
            Spacer(modifier = Modifier.weight(1f))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterToggle(
            label = "Aperti",
            icon = Icons.Filled.EventAvailable,
            checked = onlyOpen,
            onCheckedChange = onOnlyOpenChange
        )

        FilterToggle(
            label = "Pin",
            icon = Icons.Filled.NotificationsActive,
            checked = onlyFollowed,
            onCheckedChange = onOnlyFollowedChange
        )
    }
}

@Composable
private fun FilterToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) },
        tonalElevation = if (checked) 4.dp else 0.dp,
        color = if (checked)
            MaterialTheme.colorScheme.primaryContainer
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (checked)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                color = if (checked)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                ),
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScreen(
    onClose: () -> Unit,
    themeMode: EventStore.ThemeMode,
    onChangeTheme: (EventStore.ThemeMode) -> Unit
) {
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
        val scroll = rememberScrollState()
        val ctx = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tema
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            ThemeChooserRow(
                current = themeMode,
                onChange = onChangeTheme
            )

            HorizontalDivider()

            // Legenda colori eventi
            Text("Legenda colori eventi", style = MaterialTheme.typography.titleMedium)
            LegendRow(colorHex = "#4CAF50", label = "Iscrizioni aperte (verde)")
            LegendRow(colorHex = "#FFEB3B", label = "Attenzione / quasi pieno (giallo)")
            LegendRow(colorHex = "#9C27B0", label = "Lista d’attesa (viola)")
            LegendRow(colorHex = "#F44336", label = "Iscrizioni chiuse (rosso)")

            HorizontalDivider()

            // How to follow an event
            Text("Come seguire un evento", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Tocca la campanella nella card per seguire / smettere di seguire",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            Text(
                "Gli eventi seguiti possono essere filtrati con il toggle \"Pin\" nella barra dei filtri.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            // How notifications work
            Text("Come funzionano le notifiche", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Nuovo evento: quando viene rilevato dal parser.", style = MaterialTheme.typography.bodySmall)
                    Text("• Apertura iscrizioni: 7 giorni prima, 1 giorno prima e il giorno stesso (prima delle 9:00).", style = MaterialTheme.typography.bodySmall)
                    Text("• Chiusura iscrizioni: 1 giorno prima.", style = MaterialTheme.typography.bodySmall)
                    Text("Puoi limitare i tipi/regioni per le notifiche dal pulsante a campanella nella top bar.", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            // Scaricamento
            Text("Scaricamento iniziale", style = MaterialTheme.typography.titleMedium)
            Text(
                "Se non vedi ancora eventi, l’app sta scaricando la lista. Rimani online: appena pronti compariranno in automatico. " +
                        "Gli eventi scaduti vengono rimossi automaticamente dalla memoria.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            // Suggest improvements
            Text("Suggerisci miglioramenti", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hai un’idea o hai trovato un bug? Scrivimi: ogni feedback aiuta a migliorare l’app!",
                style = MaterialTheme.typography.bodySmall
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Custom button with gradient and consistent colors
                Button(
                    onClick = {
                        val to = "matteo.lagioia@gmail.com"
                        val subject = Uri.encode("Suggerimento per BuonaCaccia app")
                        val body = Uri.encode(
                            "Ciao, vorrei suggerire...\n\n" +
                                    "Versione app: <inserisci>\nDispositivo: <inserisci>\n"
                        )
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            "mailto:$to?subject=$subject&body=$body".toUri()
                        )
                        try { ctx.startActivity(intent) } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .height(52.dp)
                        .widthIn(min = 240.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Scrivi un suggerimento",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp)) // deep breath
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

@Composable
private fun ThemeChooserRow(
    current: EventStore.ThemeMode,
    onChange: (EventStore.ThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeOptionRow("Sistema (predefinito)", EventStore.ThemeMode.SYSTEM, current, onChange)
        ThemeOptionRow("Chiaro", EventStore.ThemeMode.LIGHT, current, onChange)
        ThemeOptionRow("Scuro", EventStore.ThemeMode.DARK, current, onChange)
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    value: EventStore.ThemeMode,
    current: EventStore.ThemeMode,
    onChange: (EventStore.ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = (value == current),
            onClick = { onChange(value) },
            colors = RadioButtonDefaults.colors()
        )
        Text(label)
    }
}
