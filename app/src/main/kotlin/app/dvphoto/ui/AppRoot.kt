package app.dvphoto.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dvphoto.AppViewModel
import app.dvphoto.R
import app.dvphoto.Screen

@Composable
fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    val message = vm.message
    LaunchedEffect(message) {
        message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(vm::onPicked) }
    val pick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val history by vm.history.collectAsState()

    Box(Modifier.fillMaxSize().background(Pal.Ink)) {
        Crossfade(targetState = vm.screen, label = "screen") { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(history, onCamera = vm::openCamera, onUpload = pick)
                Screen.Camera -> CameraScreen(vm)
                Screen.Busy -> BusyScreen()
                is Screen.Result -> ResultScreen(vm, screen.data, onPickAnother = pick)
            }
        }
    }
}

@Composable
private fun BusyScreen() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Pal.Mint, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.busy), color = Pal.Muted, modifier = Modifier.padding(top = 20.dp))
    }
}
