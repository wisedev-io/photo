package app.dvphoto.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.AppViewModel
import app.dvphoto.R
import app.dvphoto.ResultData
import app.dvphoto.Source
import app.dvphoto.core.Check
import app.dvphoto.core.Status

@Composable
fun ResultScreen(vm: AppViewModel, data: ResultData, onPickAnother: () -> Unit) {
    val ctx = LocalContext.current
    val image = remember(data) { data.preview.asImageBitmap() }
    val accent = if (data.ok) Pal.Mint else Pal.Coral

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            image, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))
                .border(3.dp, accent, RoundedCornerShape(28.dp)),
        )

        // Verdict
        Column {
            Text(
                stringResource(if (data.ok) R.string.result_ok else R.string.result_bad),
                color = accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(R.string.result_specs, data.side, (data.jpeg.size + 512) / 1024),
                color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
            if (!data.ok) Text(stringResource(R.string.result_bad_sub), color = Pal.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            if (data.autoCropped) Text(stringResource(R.string.result_auto_crop), color = Pal.Indigo, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            if (data.source == Source.UPLOAD) Text(stringResource(R.string.result_upload_note), color = Pal.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // Checklist: problems first
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pal.Surface).padding(vertical = 6.dp),
        ) {
            data.checks.sortedWith(compareBy({ it.status == Status.PASS }, { it.id.ordinal })).forEach { CheckRow(it) }
        }

        // Actions
        val share = {
            vm.save(data) { uri ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, null))
            }
        }
        if (data.ok) {
            Button(
                onClick = { vm.save(data) }, enabled = data.savedUri == null,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Mint, contentColor = Pal.Ink),
            ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            OutlinedButton(onClick = share, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_share), modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Button(
                onClick = { if (data.source == Source.CAMERA) vm.openCamera() else onPickAnother() },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Mint, contentColor = Pal.Ink),
            ) {
                Text(
                    stringResource(if (data.source == Source.CAMERA) R.string.action_retake else R.string.action_pick_other),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
            TextButton(onClick = { vm.save(data) }, enabled = data.savedUri == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save_anyway), color = Pal.Muted)
            }
        }
        TextButton(onClick = vm::goHome, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_home), color = Pal.Text)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CheckRow(c: Check) {
    val color = when (c.status) { Status.PASS -> Pal.Mint; Status.WARN -> Pal.Amber; Status.FAIL -> Pal.Coral }
    val icon = when (c.status) { Status.PASS -> Icons.Default.Check; Status.WARN -> Icons.Default.Warning; Status.FAIL -> Icons.Default.Close }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(c.id.res()), color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(stringResource(c.hint.res()), color = if (c.status == Status.PASS) Pal.Muted else color, fontSize = 13.sp)
        }
    }
}
