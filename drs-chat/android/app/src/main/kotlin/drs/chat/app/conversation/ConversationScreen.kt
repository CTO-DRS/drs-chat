package drs.chat.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * ConversationScreen — 1:1 chat view. Shows message bubbles + status ticks
 * (QUEUED ⊙ / SENT ✓ / DELIVERED ✓✓ / READ ✓✓ blue). For v1 beta: a
 * local-only stub. Send button appends to local state (does not actually
 * encrypt/send through the ChatFacade — wiring is M5+).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    contactHex: String,
    onBack: () -> Unit,
) {
    val messages = remember { mutableStateListOf<StubMessage>() }
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactHex, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(messages) { msg ->
                    MessageBubble(msg)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (draft.isEmpty()) {
                            Text("Message", style = MaterialTheme.typography.bodyMedium)
                        }
                        innerTextField()
                    },
                )
                IconButton(onClick = {
                    if (draft.isNotBlank()) {
                        messages += StubMessage(text = draft, status = "QUEUED")
                        draft = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

private data class StubMessage(val text: String, val status: String)

@Composable
private fun MessageBubble(msg: StubMessage) {
    val tick =
        when (msg.status) {
            "QUEUED" -> "⊙"
            "SENT" -> "✓"
            "DELIVERED" -> "✓✓"
            "READ" -> "✓✓ (blue)"
            else -> "?"
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
            Text(tick, style = MaterialTheme.typography.labelSmall)
        }
    }
}
