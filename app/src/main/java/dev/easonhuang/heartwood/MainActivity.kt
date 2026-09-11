package dev.easonhuang.heartwood

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.easonhuang.heartwood.ui.HeartwoodRoot
import dev.easonhuang.heartwood.ui.theme.HeartwoodTheme

class MainActivity : ComponentActivity() {

    // Deep-link target from a metric widget tap; consumed once navigation happens.
    private var deepLinkMetric by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkMetric = intent?.getStringExtra(EXTRA_METRIC)
        val app = application as HeartwoodApp
        setContent {
            HeartwoodTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HeartwoodRoot(
                        manager = app.healthConnect,
                        goalsRepo = app.goals,
                        exporter = app.exporter,
                        dashboardPrefs = app.dashboardPrefs,
                        deepLinkMetric = deepLinkMetric,
                        onDeepLinkConsumed = { deepLinkMetric = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkMetric = intent.getStringExtra(EXTRA_METRIC)
    }

    companion object {
        const val EXTRA_METRIC = "dev.easonhuang.heartwood.extra.METRIC"
    }
}
