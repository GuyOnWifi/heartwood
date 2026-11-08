package dev.easonhuang.heartwood.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.heartwood.data.GoalCatalog
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.SeriesPoint
import dev.easonhuang.heartwood.data.UnitPreferences
import dev.easonhuang.heartwood.data.UnitSystem
import dev.easonhuang.heartwood.data.WeeklyStat
import dev.easonhuang.heartwood.data.fromBase
import dev.easonhuang.heartwood.data.toBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SummaryState(
    val loading: Boolean = true,
    val stats: List<WeeklyStat> = emptyList(),
)

/** Daily series per metric, already converted into [system]. */
private data class LoadedSeries(val system: UnitSystem, val byMetric: Map<Metric, List<SeriesPoint>>)

class SummaryViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
    private val units: UnitPreferences,
) : ViewModel() {

    // Null until first load completes.
    private val series = MutableStateFlow<LoadedSeries?>(null)

    val state = combine(series, goalsRepo.goals) { loaded, goals ->
        if (loaded == null) {
            SummaryState(loading = true)
        } else {
            val system = loaded.system
            val stats = GoalCatalog.metrics.mapNotNull { metric ->
                val pts = loaded.byMetric[metric]
                if (pts.isNullOrEmpty()) return@mapNotNull null
                val thisWeek = pts.takeLast(7)
                val lastWeek = pts.dropLast(7).takeLast(7)
                // Goals are stored metric; show them in the same units as the series.
                val goal = goals[metric] ?: GoalCatalog.defaults.getValue(metric)
                WeeklyStat(
                    metric = metric,
                    perDay = thisWeek,
                    thisWeekAvg = thisWeek.map { it.value }.average().toFloat(),
                    lastWeekAvg = if (lastWeek.isEmpty()) 0f else lastWeek.map { it.value }.average().toFloat(),
                    goal = metric.fromBase(goal, system),
                    system = system,
                )
            }
            SummaryState(loading = false, stats = stats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState())

    // Loads on first emission, then again whenever the user switches unit system.
    init { viewModelScope.launch { units.system.collect { load(it) } } }

    fun refresh() {
        viewModelScope.launch { load(units.system.first()) }
    }

    private suspend fun load(system: UnitSystem) {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        series.value = LoadedSeries(
            system,
            GoalCatalog.metrics
                .filter { manager.permissionFor(it) in granted }
                .associateWith { runCatching { manager.readDailySeries(it, 14, system) }.getOrDefault(emptyList()) },
        )
    }

    /** [value] is in [system]'s unit, as typed into the goal dialog. */
    fun setGoal(metric: Metric, value: Float, system: UnitSystem) {
        viewModelScope.launch { goalsRepo.setGoal(metric, metric.toBase(value, system)) }
    }

    companion object {
        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository, units: UnitPreferences) =
            viewModelFactory { initializer { SummaryViewModel(manager, goalsRepo, units) } }
    }
}
