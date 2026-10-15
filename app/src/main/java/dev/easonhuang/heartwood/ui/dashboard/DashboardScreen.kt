package dev.easonhuang.heartwood.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
 * cards can be dragged around the real grid, hidden with their − button, and brought back from
 * the Hidden section below. Done or back leaves edit mode; every edit is saved as it happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: HealthConnectManager,
    dashboardPrefs: DashboardPreferences,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenMetric: (Metric) -> Unit,
    onManagePermissions: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(manager, dashboardPrefs))
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val allSummaries by vm.allSummaries.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
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
                        TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_done)) }
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
                EditGrid(all, stored, contentPadding, onSave = vm::saveLayout)
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
    contentPadding: PaddingValues,
    onSave: (order: List<Metric>, hidden: Set<Metric>) -> Unit,
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
                    onHide = { hide(metric) },
                    onMoveEarlier = { move(metric, -1) },
                    onMoveLater = { move(metric, 1) },
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
                HiddenCard(metric, onShow = { show(metric) })
            }
        }
    }
}

/** A dashboard card in edit mode: outlined, with a − button and TalkBack move/hide actions. */
@Composable
private fun EditableCard(
    summary: MetricSummary,
    onHide: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = summary.metric.title
    val hideLabel = stringResource(R.string.dashboard_hide, title)
    val earlierLabel = stringResource(R.string.dashboard_move_earlier)
    val laterLabel = stringResource(R.string.dashboard_move_later)
    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(earlierLabel) { onMoveEarlier(); true },
                    CustomAccessibilityAction(laterLabel) { onMoveLater(); true },
                    CustomAccessibilityAction(hideLabel) { onHide(); true },
                )
            },
    ) {
        MetricCard(
            summary = summary,
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    MaterialTheme.shapes.extraLarge,
                ),
        )
        FilledTonalIconButton(
            onClick = onHide,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp),
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = hideLabel, modifier = Modifier.size(18.dp))
        }
    }
}

/** A hidden metric in edit mode's Hidden section; tapping it puts it back on the dashboard. */
@Composable
private fun HiddenCard(metric: Metric, onShow: () -> Unit) {
    val title = metric.title
    OutlinedCard(onClick = onShow, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(metric.accent.copy(alpha = 0.30f), metric.accent.copy(alpha = 0.16f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(metric.icon, contentDescription = null, tint = metric.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.dashboard_show, title),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
