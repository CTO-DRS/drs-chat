package drs.chat.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * OnboardingScreen — the first-run screen. Displays the app identity +
 * the generated fingerprint (in a real v1; for the v1-beta stub, just
 * renders the app name + tagline + "Get started" button).
 *
 * The full implementation (M5+) shows:
 *   - The user's 8-word fingerprint (from :core:protocol WordList)
 *   - A QR code of their invite bundle (ZXing + CameraX)
 *   - A "scan a contact's QR" entry point
 *   - Safety-number verification after first contact
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("drs chat", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("offline-first encrypted messenger", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(48.dp))
        Text(
            "v1 beta — UI shell. Real messaging wired in next beta.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onContinue) { Text("Get started") }
    }
}
