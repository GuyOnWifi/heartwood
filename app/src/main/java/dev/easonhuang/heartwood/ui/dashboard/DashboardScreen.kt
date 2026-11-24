package dev.easonhuang.heartwood.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.DashboardLayout
import dev.easonhuang.heartwood.data.DashboardPreferences
import dev.easonhuang.heartwood.data.GoalCatalog
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.UnitPreferences
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricSummary
import dev.easonhuang.heartwood.data.title
import dev.easonhuang.heartwood.ui.DashboardViewModel
import dev.easonhuang.heartwood.ui.components.MetricCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * The Today grid. Long-pressing a card (or the tune button) switches it into edit mode in place:
 * cards can be dragged around the real grid, hidden with their − button, and brought back with
 * the + on their dimmed tile in the Hidden section below. The check button or back leaves edit
 * mode; every edit is saved as it happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: HealthConnectManager,
    dashboardPrefs: DashboardPreferences,
    units: UnitPreferences,
    goalsRepo: GoalsRepository,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenMetric: (Metric) -> Unit,
    onManagePermissions: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(manager, dashboardPrefs, units, goalsRepo))
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val allSummaries by vm.allSummaries.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val rings by vm.rings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val haptics = LocalHapticFeedback.current

    var editing by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }
    val startEditing = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        editing = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(if (editing) R.string.dashboard_edit_title else R.string.app_name))
                },
                actions = {
                    if (editing) {
                        IconButton(onClick = { editing = false }) {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_done))
                        }
                    } else {
                        IconButton(onClick = startEditing) {
                            Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.dashboard_customize))
                        }
                        IconButton(onClick = vm::refresh) {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        val contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = inner.calculateTopPadding(),
            bottom = bottomInset + 24.dp,
        )
        if (editing) {
            val all = allSummaries
            val stored = layout
            if (all != null && stored != null) {
                EditGrid(all, stored, rings, contentPadding, onSave = vm::saveLayout, onSetRing = vm::setRing)
            }
            return@Scaffold
        }
        val data = summaries
        if (data == null) {
            // initial load handled by spinner in app bar; keep grid space empty
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(data, key = { it.metric.key }) { summary ->
                MetricCard(
                    summary = summary,
                    onClick = {
                        if (summary.granted) onOpenMetric(summary.metric) else onManagePermissions()
                    },
                    onLongClick = startEditing,
                )
            }
        }
    }
}

@Composable
private fun EditGrid(
    all: Map<Metric, MetricSummary>,
    stored: DashboardLayout,
    rings: Set<Metric>,
    contentPadding: PaddingValues,
    onSave: (order: List<Metric>, hidden: Set<Metric>) -> Unit,
    onSetRing: (Metric, Boolean) -> Unit,
) {
    // Local working copy, seeded once when edit mode opens. Edits write straight through to
    // DataStore but we never re-seed from its echo, so a drag in progress can't be clobbered.
    var visible by remember { mutableStateOf(stored.visible) }
    var hidden by remember { mutableStateOf(stored.order.filter { it in stored.hidden }) }
    fun save() = onSave(visible + hidden, hidden.toSet())

    fun move(metric: Metric, by: Int) {
        val from = visible.indexOf(metric)
        val to = from + by
        if (from < 0 || to !in visible.indices) return
        visible = visible.toMutableList().apply { add(to, removeAt(from)) }
        save()
    }
    fun hide(metric: Metric) {
        visible = visible - metric
        hidden = hidden + metric
        save()
    }
    fun show(metric: Metric) {
        // A card brought back lands at the end, where it's easy to spot and drag into place.
        hidden = hidden - metric
        visible = visible + metric
        save()
    }

    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromIndex = visible.indexOfFirst { it.key == from.key }
        val toIndex = visible.indexOfFirst { it.key == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            visible = visible.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.dashboard_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        items(visible, key = { it.key }) { metric ->
            ReorderableItem(reorderState, key = metric.key) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "lift")
                val scale by animateFloatAsState(if (isDragging) 1.04f else 1f, label = "scale")
                EditableCard(
                    summary = all[metric] ?: MetricSummary(metric, "-", null, hasData = false, granted = true),
                    hidden = false,
                    onToggle = { hide(metric) },
                    onMoveEarlier = { move(metric, -1) },
                    onMoveLater = { move(metric, 1) },
                    // Only metrics with a goal can fill a ring.
                    ring = if (metric in GoalCatalog.metrics) metric in rings else null,
                    onToggleRing = { onSetRing(metric, metric !in rings) },
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .shadow(elevation, MaterialTheme.shapes.extraLarge)
                        .longPressDraggableHandle(
                            onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { save() },
                        ),
                )
            }
        }
        if (hidden.isNotEmpty()) {
            item(key = "hidden_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.dashboard_hidden_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
            }
            items(hidden, key = { "hidden_${it.key}" }) { metric ->
                EditableCard(
                    summary = all[metric] ?: MetricSummary(metric, "-", null, hasData = false, granted = true),
                    hidden = true,
                    onToggle = { show(metric) },
                )
            }
        }
    }
}

/**
 * A dashboard card in edit mode: the real tile with its live data, outlined, plus a corner button
 * that hides it (−) or, for a [hidden] card, shows it again (+). Hidden cards are dimmed but keep
 * their data, so they still work as an at-a-glance indicator. A goal metric ([ring] non-null) also
 * gets a button to switch between its graph and a goal ring. TalkBack gets the same actions.
 */
@Composable
private fun EditableCard(
    summary: MetricSummary,
    hidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveEarlier: (() -> Unit)? = null,
    onMoveLater: (() -> Unit)? = null,
    ring: Boolean? = null,
    onToggleRing: () -> Unit = {},
) {
    val title = summary.metric.title
    val toggleLabel = stringResource(if (hidden) R.string.dashboard_show else R.string.dashboard_hide, title)
    val earlierLabel = stringResource(R.string.dashboard_move_earlier)
    val laterLabel = stringResource(R.string.dashboard_move_later)
    val ringLabel = ring?.let { stringResource(if (it) R.string.dashboard_show_graph else R.string.dashboard_show_ring, title) }
    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOfNotNull(
                    onMoveEarlier?.let { CustomAccessibilityAction(earlierLabel) { it(); true } },
                    onMoveLater?.let { CustomAccessibilityAction(laterLabel) { it(); true } },
                    ringLabel?.let { CustomAccessibilityAction(it) { onToggleRing(); true } },
                    CustomAccessibilityAction(toggleLabel) { onToggle(); true },
                )
            },
    ) {
        MetricCard(
            summary = summary,
            onClick = if (hidden) onToggle else ({}),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (hidden) 0.55f else 1f }
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    MaterialTheme.shapes.extraLarge,
                ),
        )
        // Stacked down the edge: side by side, they'd cover the ring card's title.
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (hidden) Icons.Rounded.Add else Icons.Rounded.Remove,
                    contentDescription = toggleLabel,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (ring != null) {
                FilledTonalIconButton(onClick = onToggleRing, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (ring) Icons.AutoMirrored.Rounded.ShowChart else Icons.Rounded.DonutLarge,
                        contentDescription = ringLabel,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
