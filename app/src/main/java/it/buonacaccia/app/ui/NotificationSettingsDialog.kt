package it.buonacaccia.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.buonacaccia.app.data.NotificationTypeRegionRule

private enum class NotificationSettingsPage { OVERVIEW, TYPES, REGIONS, ZONES, TYPE_RULES, TYPE_RULE_DETAIL }

private val NotificationPurple = Color(0xFF6A1B9A)
private val TypeBlue = Color(0xFF0B57D0)
private val RegionGreen = Color(0xFF2E7D32)
private val ZoneOrange = Color(0xFF9A4D00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSettingsDialog(
    interestedTypes: Set<String>,
    mutedTypes: Set<String>,
    interestedRegions: Set<String>,
    interestedZones: Set<String>,
    typeRegionRules: List<NotificationTypeRegionRule>,
    availableTypes: List<String>,
    availableRegions: List<String>,
    availableZones: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        mode: NotifyMode,
        types: Set<String>,
        regions: Set<String>,
        zones: Set<String>,
        rules: List<NotificationTypeRegionRule>,
    ) -> Unit,
) {
    var page by remember { mutableStateOf(NotificationSettingsPage.OVERVIEW) }
    var query by remember { mutableStateOf("") }
    var mode by remember {
        mutableStateOf(if (mutedTypes.isNotEmpty()) NotifyMode.DENYLIST else NotifyMode.ALLOWLIST)
    }
    var selectedTypes by remember {
        mutableStateOf(if (mode == NotifyMode.DENYLIST) mutedTypes else interestedTypes)
    }
    var selectedRegions by remember { mutableStateOf(interestedRegions) }
    var selectedZones by remember { mutableStateOf(interestedZones) }
    var rulesByType by remember {
        mutableStateOf(typeRegionRules.associate { it.type to it.regions })
    }
    var activeType by remember { mutableStateOf<String?>(null) }

    val regions = remember(availableRegions) { availableRegions.filterNot { it == "Tutte" } }
    val pageTitle = when (page) {
        NotificationSettingsPage.OVERVIEW -> "Impostazioni notifiche"
        NotificationSettingsPage.TYPES -> "Tipi di evento"
        NotificationSettingsPage.REGIONS -> "Regioni predefinite"
        NotificationSettingsPage.ZONES -> "Zone"
        NotificationSettingsPage.TYPE_RULES -> "Regioni per tipo"
        NotificationSettingsPage.TYPE_RULE_DETAIL -> activeType.orEmpty()
    }

    fun navigate(destination: NotificationSettingsPage) {
        query = ""
        page = destination
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            if (page != NotificationSettingsPage.OVERVIEW) {
                                IconButton(onClick = {
                                    navigate(
                                        if (page == NotificationSettingsPage.TYPE_RULE_DETAIL) {
                                            NotificationSettingsPage.TYPE_RULES
                                        } else {
                                            NotificationSettingsPage.OVERVIEW
                                        },
                                    )
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    if (page == NotificationSettingsPage.OVERVIEW) {
                        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onDismiss) { Text("Annulla") }
                                Button(
                                    onClick = {
                                        onSave(
                                            mode,
                                            selectedTypes,
                                            selectedRegions,
                                            selectedZones,
                                            if (mode == NotifyMode.ALLOWLIST) {
                                                rulesByType.filterKeys { it in selectedTypes }
                                                    .map { (type, values) ->
                                                        NotificationTypeRegionRule(type, values)
                                                    }
                                            } else {
                                                emptyList()
                                            },
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Text("Salva impostazioni")
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        NotificationSettingsPage.OVERVIEW -> LazyColumn {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(15.dp),
                                            color = NotificationPurple,
                                        ) {
                                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.NotificationsActive,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Le notifiche che contano per te",
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            Spacer(Modifier.height(5.dp))
                                            Text(
                                                "Personalizza tipi, luoghi ed eccezioni con pochi passaggi.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                OverviewCard(
                                    title = "Tipi di evento",
                                    summary = typeSummary(mode, selectedTypes),
                                    icon = Icons.Default.Category,
                                    accent = NotificationPurple,
                                    onClick = { navigate(NotificationSettingsPage.TYPES) },
                                )
                            }
                            item {
                                OverviewCard(
                                    title = "Regioni predefinite",
                                    summary = selectionSummary(selectedRegions, "Tutta Italia"),
                                    icon = Icons.Default.Public,
                                    accent = RegionGreen,
                                    onClick = { navigate(NotificationSettingsPage.REGIONS) },
                                )
                            }
                            item {
                                OverviewCard(
                                    title = "Zone",
                                    summary = selectionSummary(selectedZones, "Tutte le zone"),
                                    icon = Icons.Default.LocationOn,
                                    accent = ZoneOrange,
                                    onClick = { navigate(NotificationSettingsPage.ZONES) },
                                )
                            }
                            item {
                                OverviewCard(
                                    title = "Regioni specifiche per tipo",
                                    summary = if (rulesByType.isEmpty()) "Nessuna eccezione" else
                                        "${rulesByType.size} regole configurate",
                                    icon = Icons.Default.Tune,
                                    accent = TypeBlue,
                                    enabled = mode == NotifyMode.ALLOWLIST && selectedTypes.isNotEmpty(),
                                    onClick = { navigate(NotificationSettingsPage.TYPE_RULES) },
                                )
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }

                        NotificationSettingsPage.TYPES -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = mode == NotifyMode.ALLOWLIST,
                                    onClick = {
                                        mode = NotifyMode.ALLOWLIST
                                        selectedTypes = interestedTypes
                                    },
                                    label = { Text("Consenti solo") },
                                )
                                FilterChip(
                                    selected = mode == NotifyMode.DENYLIST,
                                    onClick = {
                                        mode = NotifyMode.DENYLIST
                                        selectedTypes = mutedTypes
                                    },
                                    label = { Text("Escludi") },
                                )
                            }
                            SearchField(query, "Cerca tipo", onQueryChange = { query = it })
                            SelectionList(
                                values = availableTypes.filterBy(query),
                                selected = selectedTypes,
                                onToggle = { selectedTypes = selectedTypes.toggle(it) },
                            )
                        }

                        NotificationSettingsPage.REGIONS -> {
                            SearchField(query, "Cerca regione", onQueryChange = { query = it })
                            SelectionList(
                                values = regions.filterBy(query),
                                selected = selectedRegions,
                                onToggle = { selectedRegions = selectedRegions.toggle(it) },
                            )
                        }

                        NotificationSettingsPage.ZONES -> {
                            SearchField(query, "Cerca zona", onQueryChange = { query = it })
                            if (availableZones.isEmpty()) {
                                Text("Nessuna zona rilevata negli eventi attuali.", Modifier.padding(16.dp))
                            } else {
                                SelectionList(
                                    values = availableZones.filterBy(query),
                                    selected = selectedZones,
                                    onToggle = { selectedZones = selectedZones.toggle(it) },
                                )
                            }
                        }

                        NotificationSettingsPage.TYPE_RULES -> {
                            Text(
                                "Apri un tipo per ereditare il filtro generale, consentire tutta Italia " +
                                    "o scegliere regioni specifiche.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            SearchField(query, "Cerca tipo", onQueryChange = { query = it })
                            LazyColumn {
                                items(selectedTypes.sorted().filterBy(query), key = { it }) { type ->
                                    NotificationSettingsRow(
                                        title = type,
                                        summary = ruleSummary(rulesByType[type]),
                                        onClick = {
                                            activeType = type
                                            navigate(NotificationSettingsPage.TYPE_RULE_DETAIL)
                                        },
                                    )
                                }
                            }
                        }

                        NotificationSettingsPage.TYPE_RULE_DETAIL -> {
                            val type = activeType ?: return@Column
                            val configured = rulesByType[type]
                            LazyColumn {
                                item {
                                    RuleModeRow("Eredita regioni predefinite", configured == null) {
                                        rulesByType = rulesByType - type
                                    }
                                }
                                item {
                                    RuleModeRow("Tutta Italia", configured?.isEmpty() == true) {
                                        rulesByType = rulesByType + (type to emptySet())
                                    }
                                }
                                item { HorizontalDivider() }
                                item {
                                    Text(
                                        "Oppure seleziona una o più regioni",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                                items(regions, key = { it }) { region ->
                                    val checked = region in (configured ?: emptySet())
                                    ListItem(
                                        headlineContent = { Text(region) },
                                        leadingContent = { Checkbox(checked, null) },
                                        modifier = Modifier.clickable {
                                            val current = configured ?: emptySet()
                                            rulesByType = rulesByType + (type to current.toggle(region))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    title: String,
    summary: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accent,
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationSettingsRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun SearchField(query: String, label: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun SelectionList(values: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    LazyColumn {
        items(values, key = { it }) { value ->
            val checked = value in selected
            ListItem(
                headlineContent = { Text(value) },
                trailingContent = { Checkbox(checked, null) },
                colors = androidx.compose.material3.ListItemDefaults.colors(
                    containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clickable { onToggle(value) },
            )
        }
    }
}

@Composable
private fun RuleModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

private fun List<String>.filterBy(query: String): List<String> =
    if (query.isBlank()) this else filter { it.contains(query.trim(), ignoreCase = true) }

private fun selectionSummary(values: Set<String>, emptyLabel: String): String =
    if (values.isEmpty()) emptyLabel else values.sorted().take(3).joinToString() +
        if (values.size > 3) " +${values.size - 3}" else ""

private fun typeSummary(mode: NotifyMode, values: Set<String>): String = when {
    values.isEmpty() -> "Tutti i tipi"
    mode == NotifyMode.ALLOWLIST -> "Solo ${selectionSummary(values, "")}" 
    else -> "Esclusi: ${selectionSummary(values, "")}" 
}

private fun ruleSummary(regions: Set<String>?): String = when {
    regions == null -> "Eredita regioni predefinite"
    regions.isEmpty() -> "Tutta Italia"
    else -> selectionSummary(regions, "")
}
