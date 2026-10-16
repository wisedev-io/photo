package androidx.activity

import androidx.lifecycle.ViewModel

open class ComponentActivity : android.app.Activity()

inline fun <reified VM : ViewModel> ComponentActivity.viewModels(): Lazy<VM> = lazy { error("stub") }

class SystemBarStyle private constructor() {
    companion object {
        fun dark(scrim: Int): SystemBarStyle = SystemBarStyle()
    }
}

fun ComponentActivity.enableEdgeToEdge(
    statusBarStyle: SystemBarStyle = SystemBarStyle.dark(0),
    navigationBarStyle: SystemBarStyle = SystemBarStyle.dark(0),
) {}
