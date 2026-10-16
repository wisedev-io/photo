package app.dvphoto.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Pal {
    val Ink = Color(0xFF0B0F14)
    val Surface = Color(0xFF141A22)
    val Surface2 = Color(0xFF1C2430)
    val Mint = Color(0xFF3DDC97)
    val Amber = Color(0xFFFFB020)
    val Coral = Color(0xFFFF5C6C)
    val Indigo = Color(0xFF6C7BFF)
    val Text = Color(0xFFF2F5F9)
    val Muted = Color(0xFF8C97A6)
}

@Composable
fun DvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Mint, onPrimary = Pal.Ink,
            secondary = Pal.Indigo, background = Pal.Ink, onBackground = Pal.Text,
            surface = Pal.Surface, onSurface = Pal.Text, surfaceVariant = Pal.Surface2,
            error = Pal.Coral,
        ),
        typography = Typography(),
        content = content,
    )
}
