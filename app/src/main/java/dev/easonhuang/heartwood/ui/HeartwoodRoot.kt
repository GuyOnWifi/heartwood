package dev.easonhuang.heartwood.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.ui.dashboard.DashboardScreen
import dev.easonhuang.heartwood.ui.detail.DetailScreen
import dev.easonhuang.heartwood.ui.onboarding.LoadingScreen
import dev.easonhuang.heartwood.ui.onboarding.OnboardingScreen
import dev.easonhuang.heartwood.ui.onboarding.UnavailableScreen
import dev.easonhuang.heartwood.ui.settings.SettingsScreen
import dev.easonhuang.heartwood.ui.summary.SummaryScreen

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.Today),
    SUMMARY("summary", "Summary", Icons.Rounded.Insights),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
fun HeartwoodRoot(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    if (!manager.isAvailable) {
        UnavailableScreen(onInstall = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                        .setPackage("com.android.vending")
                )
            }
        })
        return
    }

    var granted by remember { mutableStateOf<Set<String>?>(null) }
    // True while the initial setup is chaining its permission requests (data → background).
    var inSetup by remember { mutableStateOf(false) }
    var requestedExtras by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result }

    // Re-read grants every time we return to the app (e.g. after toggling in HC settings).
    LifecycleResumeEffect(Unit) {
        scope.launch { granted = manager.grantedPermissions() }
        onPauseOrDispose { }
    }

    val hasData = (granted ?: emptySet()).any { it in manager.metricPermissions }
    val hasBackground = granted?.contains(HealthConnectManager.PERMISSION_READ_IN_BACKGROUND) == true

    // Setup is one continuous flow: after data is granted, immediately chain the background +
    // history prompt (HC won't allow it in the same request), so widgets work straight away.
    LaunchedEffect(inSetup, granted) {
        if (!inSetup || granted == null) return@LaunchedEffect
        when {
            !hasData -> inSetup = false              // user declined data; back to onboarding
            !hasBackground && !requestedExtras -> {  // data in, now ask for background once
                requestedExtras = true
                permissionLauncher.launch(manager.extraPermissions)
            }
            else -> inSetup = false                  // background resolved → enter the app
        }
    }

    fun startSetup() {
        requestedExtras = false
        inSetup = true
        permissionLauncher.launch(manager.metricPermissions)
    }

    fun manageAccess() {
        val g = granted ?: emptySet()
        when {
            // No data access yet → run the full setup chain again.
            !manager.metricPermissions.any { it in g } -> startSetup()
            // Data granted but background (for widgets) missing → add it now.
            HealthConnectManager.PERMISSION_READ_IN_BACKGROUND !in g ->
                permissionLauncher.launch(manager.extraPermissions)
            // Everything granted → open Health Connect's per-app screen where the OS allows it,
            // otherwise its home screen (App permissions → Heartwood) to review.
            else -> {
                val candidates = listOfNotNull(
                    HealthConnectManager.managePermissionsIntent(context.packageName),
                    HealthConnectManager.settingsIntent(),
                )
                for (intent in candidates) {
                    if (runCatching { context.startActivity(intent) }.isSuccess) return
                }
                permissionLauncher.launch(manager.permissions)
            }
        }
    }

    when {
        granted == null || inSetup -> LoadingScreen()
        !hasData -> OnboardingScreen(onConnect = ::startSetup)
        else -> {
            MainNav(
                manager, goalsRepo, exporter,
                onManagePermissions = ::manageAccess,
                deepLinkMetric = deepLinkMetric,
                onDeepLinkConsumed = onDeepLinkConsumed,
            )
        }
    }
}

@Composable
private fun MainNav(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    exporter: ExportManager,
    onManagePermissions: () -> Unit,
    deepLinkMetric: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = remember { Dest.entries.toList() }
    val showBar = currentRoute in topLevel.map { it.route }

    // Open a metric's detail directly when launched from its widget.
    LaunchedEffect(deepLinkMetric) {
        val metric = deepLinkMetric?.let { Metric.fromKey(it) }
        if (metric != null) {
            navController.navigate("detail/${metric.key}")
            onDeepLinkConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        val bottomInset = inner.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Dest.TODAY.route) {
                DashboardScreen(
                    manager = manager,
                    bottomInset = bottomInset,
                    onOpenMetric = { metric -> navController.navigate("detail/${metric.key}") },
                    onManagePermissions = onManagePermissions,
                )
            }
            composable(Dest.SUMMARY.route) {
                SummaryScreen(manager = manager, goalsRepo = goalsRepo, bottomInset = bottomInset)
            }
            composable(Dest.SETTINGS.route) {
                SettingsScreen(
                    manager = manager,
                    exporter = exporter,
                    bottomInset = bottomInset,
                    onManagePermissions = onManagePermissions,
                )
            }
            composable("detail/{key}") { entry ->
                val metric = entry.arguments?.getString("key")?.let { Metric.fromKey(it) }
                if (metric == null) {
                    navController.popBackStack()
                } else {
                    DetailScreen(
                        manager = manager,
                        metric = metric,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
