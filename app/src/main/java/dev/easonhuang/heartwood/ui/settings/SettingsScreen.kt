package dev.easonhuang.heartwood.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import dev.easonhuang.heartwood.BuildConfig
import dev.easonhuang.heartwood.data.ExportFormat
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/GuyOnWifi/heartwood"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    manager: HealthConnectManager,
    exporter: ExportManager,
    bottomInset: Dp,
    onManagePermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showFormatDialog by remember { mutableStateOf(false) }

    suspend fun runExport(uri: Uri, format: ExportFormat) {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        val metrics = Metric.entries.filter { manager.permissionFor(it) in granted }
        if (metrics.isEmpty()) {
            snackbar.showSnackbar("No accessible data to export"); return
        }
        exporter.export(uri, metrics, format).fold(
            onSuccess = { count -> snackbar.showSnackbar("Exported $count records as ${format.label}") },
            onFailure = { snackbar.showSnackbar("Export failed: ${it.message}") },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.CSV) } } }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.JSON) } } }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionLabel("Data") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        title = "Manage data access",
                        subtitle = "Choose which health data Heartwood can read",
                        onClick = onManagePermissions,
                    )
                    SettingRow(
                        icon = Icons.Rounded.MonitorHeart,
                        title = "Open Health Connect",
                        subtitle = "Manage all apps, data sources and stored data",
                        onClick = {
                            runCatching { context.startActivity(HealthConnectManager.settingsIntent()) }
                                .onFailure { scope.launch { snackbar.showSnackbar("Couldn't open Health Connect") } }
                        },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Download,
                        title = "Export data",
                        subtitle = "Save your accessible data to a file (CSV or JSON)",
                        onClick = { showFormatDialog = true },
                    )
                }
            }
            item { SectionLabel("About") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = "Heartwood ${BuildConfig.VERSION_NAME}",
                        subtitle = "Private, read-only Health Connect viewer",
                        onClick = {},
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        title = "Open source",
                        subtitle = "GPL-3.0-or-later, view the code",
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            }
                        },
                    )
                }
            }
            item {
                Text(
                    "Heartwood never sends your data anywhere. Everything stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Export format") },
            text = { Text("Choose how to save your data.") },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    csvLauncher.launch("heartwood-export.csv")
                }) { Text("CSV") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    jsonLauncher.launch("heartwood-export.json")
                }) { Text("JSON") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(9.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
