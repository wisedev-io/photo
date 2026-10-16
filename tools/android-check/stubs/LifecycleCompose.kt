package androidx.lifecycle.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.LifecycleOwner

val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner> = staticCompositionLocalOf { error("no owner") }
