package dev.easonhuang.heartwood.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.easonhuang.heartwood.data.DashboardPreferences
import dev.easonhuang.heartwood.data.Metric
import kotlinx.coroutines.launch

private val RowHeight = 64.dp

/**
 * Lets the user reorder Today-dashboard cards (long-press the handle and drag) and hide the ones
 * they don't care about (the trailing switch). Edits persist immediately via [DashboardPreferences].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDashboardScreen(
    dashboardPrefs: DashboardPreferences,
    bottomInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stored by dashboardPrefs.layout.collectAsStateWithLifecycle(initialValue = null)

    // Local editable copy, seeded once from the persisted layout. Every edit writes straight back
    // to DataStore; we don't re-seed afterwards, so the persisted echo can't clobber a live drag.
    var order by remember { mutableStateOf<List<Metric>?>(null) }
    var hidden by remember { mutableStateOf<Set<Metric>>(emptySet()) }
    LaunchedEffect(stored) {
        val s = stored
        if (order == null && s != null) {
            order = s.order
            hidden = s.hidden
        }
    }

    fun persist() {
        val current = order ?: return
        scope.launch { dashboardPrefs.save(current, hidden) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Customize dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        val list = order ?: return@Scaffold

        // Drag state, tracked by the dragged metric so it survives list mutation mid-drag.
        var dragging by remember { mutableStateOf<Metric?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(list, key = { it.key }) { metric ->
                val isDragging = dragging == metric
                MetricRow(
                    metric = metric,
                    visible = metric !in hidden,
                    onToggleVisible = {
                        hidden = if (metric in hidden) hidden - metric else hidden + metric
                        persist()
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            if (isDragging) translationY = dragOffset
                        }
                        .zIndex(if (isDragging) 1f else 0f),
                    dragHandleModifier = Modifier.pointerInput(metric) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = metric
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                // Read the live order (not the captured `list`): this gesture's
                                // closure is created once, so `list` goes stale after a swap.
                                val current = order ?: return@detectDragGesturesAfterLongPress
                                val cur = current.indexOf(metric)
                                if (cur < 0) return@detectDragGesturesAfterLongPress
                                val threshold = rowHeightPx / 2
                                when {
                                    dragOffset > threshold && cur < current.lastIndex -> {
                                        order = current.toMutableList()
                                            .apply { add(cur + 1, removeAt(cur)) }
                                        dragOffset -= rowHeightPx
                                    }
                                    dragOffset < -threshold && cur > 0 -> {
                                        order = current.toMutableList()
                                            .apply { add(cur - 1, removeAt(cur)) }
                                        dragOffset += rowHeightPx
                                    }
                                }
                            },
                            onDragEnd = {
                                dragging = null
                                dragOffset = 0f
                                persist()
                            },
                            onDragCancel = {
                                dragging = null
                                dragOffset = 0f
                                persist()
                            },
                        )
                    },
                    elevated = isDragging,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    metric: Metric,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    dragHandleModifier: Modifier,
    elevated: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(RowHeight),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (elevated) 6.dp else 0.dp,
        shadowElevation = if (elevated) 6.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Reorder ${metric.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier.padding(end = 8.dp),
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(metric.accent.copy(alpha = 0.30f), metric.accent.copy(alpha = 0.16f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = metric.icon,
                    contentDescription = null,
                    tint = metric.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = metric.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (visible) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = visible, onCheckedChange = { onToggleVisible() })
        }
    }
}
