package androidx.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext: ProvidableCompositionLocal<android.content.Context> = staticCompositionLocalOf { error("no context") }
val LocalView: ProvidableCompositionLocal<android.view.View> = staticCompositionLocalOf { error("no view") }
