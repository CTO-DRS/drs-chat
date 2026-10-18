package drs.chat.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color.White,
        secondary = Color(0xFF558B2F),
        background = Color(0xFFF5F5F5),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF81C784),
        onPrimary = Color.Black,
        secondary = Color(0xFF9CCC65),
        background = Color(0xFF121212),
    )

@Composable
fun DrsChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
