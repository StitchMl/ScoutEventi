package it.buonacaccia.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.buonacaccia.app.data.BcEvent
import it.buonacaccia.app.data.EventsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val loading: Boolean = false,
    val items: List<BcEvent> = emptyList(),
    val error: String? = null,
    val query: String = ""
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repo: EventsRepository
) : ViewModel() {

    var state by mutableStateOf(EventsUiState(loading = true))
        private set

    private var loadJob: Job? = null

    init {
        refresh()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun refresh() {
        loadJob?.cancel()
        state = state.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            runCatching { repo.fetch(all = true) }
                .onSuccess { list -> state = state.copy(loading = false, items = list) }
                .onFailure { e -> state = state.copy(loading = false, error = e.message ?: "Error") }
        }
    }

    fun onQueryChange(q: String) {
        state = state.copy(query = q)
    }

    val filtered: List<BcEvent>
        get() {
            val q = state.query.trim().lowercase()
            if (q.isBlank()) return state.items
            return state.items.filter { ev ->
                ev.title.lowercase().contains(q) ||
                        (ev.region?.lowercase()?.contains(q) == true) ||
                        (ev.type?.lowercase()?.contains(q) == true) ||
                        (ev.location?.lowercase()?.contains(q) == true)
            }
        }
}