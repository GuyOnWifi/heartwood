package dev.easonhuang.heartwood.ui.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.WeeklyStat
import dev.easonhuang.heartwood.data.title
import dev.easonhuang.heartwood.data.unit

@Composable
fun GoalEditDialog(
    stat: WeeklyStat,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
) {
    var text by remember {
        mutableStateOf(
            if (stat.goal % 1f == 0f) stat.goal.toInt().toString() else stat.goal.toString()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_dialog_title, stat.metric.title)) },
        text = {
            Column {
                Text(stringResource(R.string.goal_dialog_daily_target, stat.metric.unit))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(stat.metric.unit) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toFloatOrNull()?.let { onSave(it) } ?: onDismiss() },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
