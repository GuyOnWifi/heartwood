package dev.easonhuang.heartwood.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.MetricSummary
import dev.easonhuang.heartwood.data.title
import dev.easonhuang.heartwood.data.unit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricCard(
    summary: MetricSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val accent = summary.metric.accent
    val locked = !summary.granted
    val shape = MaterialTheme.shapes.extraLarge
    Card(
        modifier = modifier
            .height(168.dp)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        val goal = summary.ringGoal()
        if (goal != null) {
            GoalBody(summary, goal)
            return@Card
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricIcon(summary, locked)
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = summary.metric.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (locked) stringResource(R.string.card_locked) else summary.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (!locked && summary.hasData) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = summary.metric.unit(summary.system),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (!locked && summary.spark.size > 1) {
                Sparkline(summary.spark, accent, Modifier.fillMaxWidth().height(28.dp))
            } else {
                Text(
                    text = if (locked) stringResource(R.string.card_tap_to_grant) else (summary.caption ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The goal when the card should draw its ring, else null (graph mode, locked, no data, no goal). */
private fun MetricSummary.ringGoal(): Float? =
    goal?.takeIf { granted && hasData && spark.isNotEmpty() && it > 0f }

@Composable
private fun MetricIcon(summary: MetricSummary, locked: Boolean) {
    val accent = summary.metric.accent
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.16f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (locked) Icons.Rounded.Lock else summary.metric.icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Goal card in ring mode: today's total sits inside a ring filled toward the goal. */
@Composable
private fun GoalBody(summary: MetricSummary, goal: Float) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MetricIcon(summary, locked = false)
            Spacer(Modifier.size(8.dp))
            Text(
                text = summary.metric.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        GoalRing(
            progress = summary.spark.last() / goal,
            color = summary.metric.accent,
            diameter = 88.dp,
            stroke = 7.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Shrinks rather than spilling past the ring at large font scales.
                Text(
                    text = summary.value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Text(
                    text = summary.metric.unit(summary.system),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
