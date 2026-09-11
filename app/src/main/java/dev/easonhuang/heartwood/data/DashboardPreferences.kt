package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dashboardDataStore by preferencesDataStore(name = "heartwood_dashboard")

/**
 * The user's Today-dashboard arrangement: the order cards appear in and which are hidden.
 *
 * [order] always contains every [Metric] exactly once, so a metric added in a future release
 * simply appears at the end (visible). [visible] is [order] minus [hidden] — the cards the
 * dashboard actually renders, in order.
 */
data class DashboardLayout(
    val order: List<Metric>,
    val hidden: Set<Metric>,
) {
    val visible: List<Metric> get() = order.filter { it !in hidden }

    companion object {
        val DEFAULT = DashboardLayout(order = Metric.entries.toList(), hidden = emptySet())
    }
}

/**
 * Persists [DashboardLayout] in DataStore. Order is stored as a comma-separated list of metric
 * keys and the hidden set as a string set of keys; both are reconciled against the current
 * [Metric] catalog on read so stale or missing keys can never corrupt the dashboard.
 */
class DashboardPreferences(private val context: Context) {

    private val orderKey = stringPreferencesKey("card_order")
    private val hiddenKey = stringSetPreferencesKey("hidden_cards")

    val layout: Flow<DashboardLayout> = context.dashboardDataStore.data.map { prefs ->
        val stored = prefs[orderKey]
            ?.split(',')
            ?.mapNotNull { Metric.fromKey(it) }
            ?: emptyList()
        // Keep the stored order, then append any catalog metric it doesn't mention (e.g. a
        // metric introduced after the user last customised) so nothing silently disappears.
        val order = (stored + Metric.entries.filter { it !in stored })
        val hidden = prefs[hiddenKey].orEmpty().mapNotNull { Metric.fromKey(it) }.toSet()
        DashboardLayout(order, hidden)
    }

    suspend fun save(order: List<Metric>, hidden: Set<Metric>) {
        context.dashboardDataStore.edit { prefs ->
            prefs[orderKey] = order.joinToString(",") { it.key }
            prefs[hiddenKey] = hidden.map { it.key }.toSet()
        }
    }
}
