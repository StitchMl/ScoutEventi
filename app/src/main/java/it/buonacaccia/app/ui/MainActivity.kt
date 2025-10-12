@file:Suppress("DEPRECATION")

package it.buonacaccia.app.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dagger.hilt.android.AndroidEntryPoint
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.ui.components.EventCard
import it.buonacaccia.app.ui.theme.BuonaCacciaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ------- NEW: launcher for permission. -------
    private val requestNotifPerm = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* optional: react to the result */ }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask permission only on API 33+
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestNotifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            BuonaCacciaTheme { MainScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun MainScreen(
    vm: EventsViewModel = hiltViewModel()
) {
    val state = vm.state
    val swipeState = rememberSwipeRefreshState(isRefreshing = state.loading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eventi BuonaCaccia") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                onUnitChange = vm::onUnitChange
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
        placeholder = { Text("Search for events...") },
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
    onUnitChange: (UnitFilter) -> Unit
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
}