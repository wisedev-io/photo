package app.dvphoto.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R

/** Shown only when a photo has problems; photos that pass go straight to the name-and-save screen. */
@Composable
fun ResultScreen(
    result: ResultUi,
    onRetry: () -> Unit,
    onSaveAnyway: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTopBar(stringResource(R.string.result_title), onHome, stringResource(R.string.action_back))

        Image(
            result.preview, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))
                .border(3.dp, Pal.Coral, RoundedCornerShape(28.dp)),
        )

        Column {
            Text(stringResource(R.string.result_bad), color = Pal.Coral, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.result_counts, result.failures, result.warnings),
                color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
            Text(stringResource(R.string.result_bad_sub), color = Pal.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
            if (result.autoCropped) {
                Text(stringResource(R.string.result_auto_crop), color = Pal.Indigo, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        ChecksCard(result.checks)

        AppButton(
            stringResource(if (result.fromUpload) R.string.action_pick_other else R.string.action_retake),
            onRetry, Modifier.fillMaxWidth(),
        )
        AppButton(stringResource(R.string.action_save_anyway), onSaveAnyway, Modifier.fillMaxWidth(), BtnStyle.Ghost)
        Text(
            stringResource(R.string.result_risk), color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
    }
}
