package dev.easonhuang.heartwood.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.heartwood.data.DashboardLayout
import dev.easonhuang.heartwood.data.DashboardPreferences
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricDetail
import dev.easonhuang.heartwood.data.MetricSummary
import dev.easonhuang.heartwood.data.UnitPreferences
import dev.easonhuang.heartwood.data.UnitSystem
import dev.easonhuang.heartwood.data.fromBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val manager: HealthConnectManager,
    private val prefs: DashboardPreferences,
    private val units: UnitPreferences,
    goalsRepo: GoalsRepository,
) : ViewModel() {
    // All summaries as read from Health Connect, in catalog order (null until first load).
    private val _summaries = MutableStateFlow<List<MetricSummary>?>(null)

    // Goals are joined here rather than at read time so editing one updates the cards live. Only
    // cards set to ring mode get a goal; the rest keep their sparkline. Goals are stored metric, so
    // convert them into the summary's units to match the spark.
    private val withGoals = combine(_summaries, goalsRepo.goals, prefs.rings) { all, goals, rings ->
        all?.map { s ->
            val goal = goals[s.metric]?.takeIf { s.metric in rings }
            s.copy(goal = goal?.let { s.metric.fromBase(it, s.system) })
        }
    }

    /**
     * Summaries arranged for display: reordered to match the user's layout and with hidden
     * cards dropped. Stays null until the first read so the screen can show its loading state.
     */
    val summaries: StateFlow<List<MetricSummary>?> =
        combine(withGoals, prefs.layout) { all, layout ->
            if (all == null) return@combine null
            val byMetric = all.associateBy { it.metric }
            layout.visible.mapNotNull { byMetric[it] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every summary keyed by metric, hidden ones included, for the dashboard's edit mode. */
    val allSummaries: StateFlow<Map<Metric, MetricSummary>?> =
        withGoals.map { all -> all?.associateBy { it.metric } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The persisted card order and hidden set (null until DataStore's first read). */
    val layout: StateFlow<DashboardLayout?> =
        prefs.layout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Goal metrics shown as a ring rather than a graph. */
    val rings: StateFlow<Set<Metric>> =
        prefs.rings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun saveLayout(order: List<Metric>, hidden: Set<Metric>) {
        viewModelScope.launch { prefs.save(order, hidden) }
    }

    fun setRing(metric: Metric, ring: Boolean) {
        viewModelScope.launch { prefs.setRing(metric, ring) }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    // Loads on first emission, then again whenever the user switches unit system.
    init { viewModelScope.launch { units.system.collect { load(it) } } }

    fun refresh() {
        viewModelScope.launch { load(units.system.first()) }
    }

    private suspend fun load(system: UnitSystem) {
        _refreshing.value = true
        _summaries.value = manager.readDashboard(system)
        _refreshing.value = false
    }

    companion object {
        fun factory(manager: HealthConnectManager, prefs: DashboardPreferences, units: UnitPreferences, goalsRepo: GoalsRepository) =
            viewModelFactory { initializer { DashboardViewModel(manager, prefs, units, goalsRepo) } }
    }
}

class DetailViewModel(
    private val manager: HealthConnectManager,
    private val metric: Metric,
    units: UnitPreferences,
) : ViewModel() {
    private val _detail = MutableStateFlow<MetricDetail?>(null)
    val detail = _detail.asStateFlow()

    init {
        viewModelScope.launch {
            units.system.collect { system ->
                val detail = manager.readDetail(metric, system)
                _detail.value = if (metric == Metric.STEPS) detail.copy(stats = detail.stats + stepPeriodStats()) else detail
            }
        }
    }

    private suspend fun stepPeriodStats(): List<Pair<String, String>> = runCatching {
        manager.readStepPeriodStats()
    }.getOrDefault(emptyList())

    companion object {
        fun factory(manager: HealthConnectManager, metric: Metric, units: UnitPreferences) = viewModelFactory {
            initializer { DetailViewModel(manager, metric, units) }
        }
    }
}
