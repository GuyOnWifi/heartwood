package dev.easonhuang.heartwood.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import dev.easonhuang.heartwood.R

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun OnboardingScreen(onConnect: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.12f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.onboarding_connect), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun UnavailableScreen(onInstall: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_hc_needed_title), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_hc_needed_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.onboarding_get_hc))
        }
    }
}
