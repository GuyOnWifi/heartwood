package dev.easonhuang.heartwood.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricDetail
import dev.easonhuang.heartwood.data.MetricKind
import dev.easonhuang.heartwood.data.RecordRow
import dev.easonhuang.heartwood.data.formatValue
import dev.easonhuang.heartwood.ui.DetailViewModel
import dev.easonhuang.heartwood.ui.components.BarChart
import dev.easonhuang.heartwood.ui.components.LineChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    manager: HealthConnectManager,
    metric: Metric,
    onBack: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(
        key = metric.key,
        factory = DetailViewModel.factory(manager, metric),
    )
    val detail by vm.detail.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(metric.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValuesFrom(inner),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeaderCard(d) }
            item { ChartCard(d) }
            if (d.stats.isNotEmpty()) item { StatsCard(d) }
            if (d.recent.isNotEmpty()) {
                item {
                    Text(
                        "Recent records",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(d.recent) { row -> RecordItem(row) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun HeaderCard(d: MetricDetail) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(d.metric.accent.copy(alpha = 0.30f), d.metric.accent.copy(alpha = 0.14f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(d.metric.icon, null, tint = d.metric.accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(16.dp))
            Column {
                Text(d.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                d.caption?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChartCard(d: MetricDetail) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp).padding(20.dp)) {
            if (d.metric.kind == MetricKind.DAILY_TOTAL) {
                BarChart(d.points, d.metric.accent, Modifier.fillMaxSize())
            } else {
                LineChart(d.points, d.metric.accent, Modifier.fillMaxSize(), format = d.metric::formatValue)
            }
        }
    }
}

@Composable
private fun StatsCard(d: MetricDetail) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            d.stats.forEachIndexed { i, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                if (i < d.stats.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun RecordItem(row: RecordRow) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(row.secondary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Top inset padding + horizontal/bottom breathing room for the detail list. */
@Composable
private fun PaddingValuesFrom(inner: androidx.compose.foundation.layout.PaddingValues) =
    androidx.compose.foundation.layout.PaddingValues(
        top = inner.calculateTopPadding() + 16.dp,
        bottom = inner.calculateBottomPadding() + 16.dp,
    )
