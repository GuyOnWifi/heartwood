package dev.easonhuang.heartwood.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.WeeklyStat
import dev.easonhuang.heartwood.data.formatValue
import dev.easonhuang.heartwood.ui.components.BarChart
import dev.easonhuang.heartwood.ui.components.GoalRing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val vm: SummaryViewModel = viewModel(factory = SummaryViewModel.factory(manager, goalsRepo))
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var editing by remember { mutableStateOf<WeeklyStat?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("This week") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Daily averages vs your goals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (!state.loading && state.stats.isEmpty()) {
                item { EmptyState() }
            }
            items(state.stats, key = { it.metric.key }) { stat ->
                WeeklyCard(stat, onEdit = { editing = stat })
            }
        }
    }

    editing?.let { stat ->
        GoalEditDialog(
            stat = stat,
            onDismiss = { editing = null },
            onSave = { value -> vm.setGoal(stat.metric, value); editing = null },
        )
    }
}

@Composable
private fun WeeklyCard(stat: WeeklyStat, onEdit: () -> Unit) {
    val accent = stat.metric.accent
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(progress = stat.progress, color = accent) {
                    Text(
                        "${(stat.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stat.metric.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${stat.metric.formatValue(stat.thisWeekAvg)} / ${stat.metric.formatValue(stat.goal)} ${stat.metric.unit} avg/day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    DeltaChip(stat)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit goal", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            val days = stat.perDay.size
            val daysMet = stat.perDay.count { it.value >= stat.goal }
            val chartDescription = "${stat.metric.title} over the last $days days" +
                if (stat.goal > 0f) ": goal met on $daysMet of $days days" else ""
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clearAndSetSemantics { contentDescription = chartDescription },
            ) {
                BarChart(stat.perDay, accent, Modifier.fillMaxSize(), goal = stat.goal)
            }
        }
    }
}

@Composable
private fun DeltaChip(stat: WeeklyStat) {
    val delta = stat.deltaPercent
    val (icon, tint, label) = when {
        delta == null -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, "New this week")
        delta >= 1f -> Triple(Icons.AutoMirrored.Rounded.TrendingUp, stat.metric.accent, "+${delta.roundToInt()}% vs last week")
        delta <= -1f -> Triple(Icons.AutoMirrored.Rounded.TrendingDown, MaterialTheme.colorScheme.error, "${delta.roundToInt()}% vs last week")
        else -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, "Steady vs last week")
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No goal-tracked data yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Grant access to steps, energy, distance, exercise, hydration or floors to see weekly progress.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
