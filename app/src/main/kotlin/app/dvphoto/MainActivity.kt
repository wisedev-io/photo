package app.dvphoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import app.dvphoto.ui.AppRoot
import app.dvphoto.ui.DvTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DvTheme { Root() } }
    }

    @Composable
    private fun Root() {
        BackHandler(enabled = vm.screen != Screen.Home) { vm.goHome() }
        AppRoot(vm)
    }
}
