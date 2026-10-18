package drs.chat.app.chatlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * ChatListScreen — the home screen. Lists contacts + last message preview.
 * For v1 beta: shows a placeholder contact list (no real contacts yet).
 *
 * Tapping a contact opens the Conversation screen for that contactHex.
 * The settings icon in the top-right opens SettingsScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onContactClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    // v1 beta placeholder contacts — no Room wiring yet
    val contacts = remember { listOf("alice" to "see you tomorrow", "bob" to "encrypted hello", "carol" to "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("drs chat", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(contacts) { (name, preview) ->
                ChatListItem(name = name, preview = preview, onClick = { onContactClick(name) })
            }
        }
    }
}

@Composable
private fun ChatListItem(
    name: String,
    preview: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (preview.isNotEmpty()) {
                Text(preview, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("(no messages)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
