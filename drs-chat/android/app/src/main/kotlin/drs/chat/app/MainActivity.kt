package drs.chat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * MainActivity — M4-phase placeholder UI. Renders a single screen with the
 * app identity + version. The real chat UI (Compose M3 screens per PLAN §5.2
 * — Onboarding, ChatList, Conversation, VerifySafetyNumber, Settings) is
 * M5 scope. This minimal activity lets :app:assembleDebug produce a real,
 * installable APK so the package-builder step has a deliverable.
 *
 * Layout: single-Activity + Navigation Compose (M5). For now, one screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DrsChatSplash()
                }
            }
        }
    }
}

@Suppress("FunctionNaming") // Compose convention: PascalCase composable functions
@Composable
private fun DrsChatSplash() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("drs chat", style = MaterialTheme.typography.headlineMedium)
        Text("Phase 1 · M4 scaffold", style = MaterialTheme.typography.bodyMedium)
        Text("version 0.1.0-m4", style = MaterialTheme.typography.bodySmall)
    }
}
