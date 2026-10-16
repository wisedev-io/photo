package app.dvphoto

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.dvphoto.host.AppRoot
import app.dvphoto.platform.LocaleHelper
import app.dvphoto.platform.Prefs
import app.dvphoto.ui.DvTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Applies the in-app language choice before any resources are read. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs(newBase).language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        setContent {
            DvTheme { AppRoot(vm, version, onRecreate = { recreate() }) }
        }
    }
}
