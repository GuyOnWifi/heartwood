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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.easonhuang.heartwood.data.DashboardPreferences
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.GoalsRepository
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.ui.dashboard.CustomizeDashboardScreen
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
    dashboardPrefs: DashboardPreferences,
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
    // Setup state is saveable so the chain survives recreation while HC's permission screen is up.
    var inSetup by rememberSaveable { mutableStateOf(false) }
    var requestedExtras by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Counts permission-request answers, and the count when the setup step now in flight started.
    // Setup only moves on once its own request has been answered: reacting to `granted` alone
    // would judge the grants from *before* the prompt and cancel setup straight away.
    var permissionResults by rememberSaveable { mutableIntStateOf(0) }
    var awaitingAfter by rememberSaveable { mutableIntStateOf(0) }

    // The result only lists the permissions in that request, not everything held, so re-read the
    // full set rather than assigning it (which would e.g. drop background access after a data-only
    // request that was already granted and returned without a screen or resume).
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch {
            granted = manager.grantedPermissions()
            permissionResults++
        }
    }

    // Re-read grants every time we return to the app (e.g. after toggling in HC settings).
    LifecycleResumeEffect(Unit) {
        scope.launch { granted = manager.grantedPermissions() }
        onPauseOrDispose { }
    }

    val hasData = (granted ?: emptySet()).any { it in manager.metricPermissions }
    val hasBackground = granted?.contains(HealthConnectManager.PERMISSION_READ_IN_BACKGROUND) == true

    fun requestInSetup(permissions: Set<String>) {
        awaitingAfter = permissionResults
        permissionLauncher.launch(permissions)
    }

    // Setup is one continuous flow: after data is granted, immediately chain the background +
    // history prompt (HC won't allow it in the same request), so widgets work straight away.
    LaunchedEffect(inSetup, permissionResults) {
        if (!inSetup || permissionResults == awaitingAfter) return@LaunchedEffect
        when {
            !hasData -> inSetup = false              // user declined data; back to onboarding
            !hasBackground && !requestedExtras -> {  // data in, now ask for background once
                requestedExtras = true
                requestInSetup(manager.extraPermissions)
            }
            else -> inSetup = false                  // background resolved → enter the app
        }
    }

    fun startSetup() {
        requestedExtras = false
        inSetup = true
        requestInSetup(manager.metricPermissions)
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
                manager, goalsRepo, exporter, dashboardPrefs,
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
    dashboardPrefs: DashboardPreferences,
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
                    dashboardPrefs = dashboardPrefs,
                    bottomInset = bottomInset,
                    onOpenMetric = { metric -> navController.navigate("detail/${metric.key}") },
                    onManagePermissions = onManagePermissions,
                    onCustomize = { navController.navigate("customize") },
                )
            }
            composable("customize") {
                CustomizeDashboardScreen(
                    dashboardPrefs = dashboardPrefs,
                    bottomInset = bottomInset,
                    onBack = { navController.popBackStack() },
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
