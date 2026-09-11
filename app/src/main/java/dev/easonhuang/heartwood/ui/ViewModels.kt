package dev.easonhuang.heartwood.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.heartwood.data.DashboardPreferences
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricDetail
import dev.easonhuang.heartwood.data.MetricSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val manager: HealthConnectManager,
    prefs: DashboardPreferences,
) : ViewModel() {
    // All summaries as read from Health Connect, in catalog order (null until first load).
    private val _summaries = MutableStateFlow<List<MetricSummary>?>(null)

    /**
     * Summaries arranged for display: reordered to match the user's layout and with hidden
     * cards dropped. Stays null until the first read so the screen can show its loading state.
     */
    val summaries: StateFlow<List<MetricSummary>?> =
        combine(_summaries, prefs.layout) { all, layout ->
            if (all == null) return@combine null
            val byMetric = all.associateBy { it.metric }
            layout.visible.mapNotNull { byMetric[it] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _summaries.value = manager.readDashboard()
            _refreshing.value = false
        }
    }

    companion object {
        fun factory(manager: HealthConnectManager, prefs: DashboardPreferences) = viewModelFactory {
            initializer { DashboardViewModel(manager, prefs) }
        }
    }
}

class DetailViewModel(
    private val manager: HealthConnectManager,
    private val metric: Metric,
) : ViewModel() {
    private val _detail = MutableStateFlow<MetricDetail?>(null)
    val detail = _detail.asStateFlow()

    init {
        viewModelScope.launch { _detail.value = manager.readDetail(metric) }
    }

    companion object {
        fun factory(manager: HealthConnectManager, metric: Metric) = viewModelFactory {
            initializer { DetailViewModel(manager, metric) }
        }
    }
}
