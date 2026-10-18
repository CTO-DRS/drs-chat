package drs.chat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import drs.chat.app.theme.DrsChatTheme
import drs.chat.app.ui.DrsChatNavGraph

/**
 * MainActivity — single-Activity host for the Compose Navigation graph.
 * Hilt injects via @AndroidEntryPoint. The NavGraph routes to:
 *   - onboarding → ChatList → Conversation → Settings
 *
 * NavGraph defined in [drs.chat.app.ui.DrsChatNavGraph].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrsChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DrsChatNavGraph()
                }
            }
        }
    }
}
