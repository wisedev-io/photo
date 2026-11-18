package app.dvphoto.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Pal {
    val Ink = Color(0xFF142924)
    val Surface = Color(0xFFFDFEFF)
    val Surface2 = Color(0xFFE5F0F1)
    val Mint = Color(0xFF176B55)
    val Amber = Color(0xFF795800)
    val Coral = Color(0xFF9B374A)
    val Indigo = Color(0xFF514084)
    val Text = Color(0xFF142924)
    val Muted = Color(0xFF465A5A)
    val MintPanel = Color(0xFFAFF0D8)
    val Lavender = Color(0xFFE6D9FA)
    val Lemon = Color(0xFFFFF3A7)
    val Canvas = Color(0xFFF2F8FC)
    val Backdrop = Brush.linearGradient(listOf(Color(0xFFABEBDF), Canvas, Color(0xFFDDE5F9)))
    val Hairline = Color(0x20142924)
}

@Composable
fun DvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pal.Mint, onPrimary = Color.White,
            secondary = Pal.Indigo, background = Pal.Canvas, onBackground = Pal.Text,
            surface = Pal.Surface, onSurface = Pal.Text, surfaceVariant = Pal.Surface2, onSurfaceVariant = Pal.Muted,
            error = Pal.Coral,
        ),
        typography = Typography(),
        content = content,
    )
}
