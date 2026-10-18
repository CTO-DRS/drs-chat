package drs.chat.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * SettingsScreen — shows the user's fingerprint + version + a "delete
 * session" entry. For v1 beta: shows version + identity placeholder.
 *
 * Full implementation (M5+): fingerprint display, safety-number per-contact
 * verification screen, session-reset button, identity export (for moving
 * to a new device), relay endpoint config.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Identity", style = MaterialTheme.typography.titleMedium)
            Text("(not yet generated — M5+ will display the 8-word fingerprint)", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Text("Status: Queued (no live relay configured)", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("drs chat · v0.1.0-v1-beta", style = MaterialTheme.typography.bodyMedium)
            Text("Phase 1 — UI shell beta", style = MaterialTheme.typography.bodySmall)
        }
    }
}
