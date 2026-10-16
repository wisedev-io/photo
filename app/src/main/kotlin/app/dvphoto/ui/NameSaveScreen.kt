package app.dvphoto.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R
import app.dvphoto.core.FileNaming
import app.dvphoto.core.PhotoRole
import app.dvphoto.core.Status

private fun PhotoRole.label(): Int = when (this) {
    PhotoRole.APPLICANT -> R.string.role_applicant
    PhotoRole.SPOUSE -> R.string.role_spouse
    PhotoRole.CHILD1 -> R.string.role_child1
    PhotoRole.CHILD2 -> R.string.role_child2
    PhotoRole.CHILD3 -> R.string.role_child3
    PhotoRole.CHILD4 -> R.string.role_child4
    PhotoRole.OTHER -> R.string.role_other
}

/**
 * Shown right after a photo is finished: pick who it is for, rename the file, choose where to save, save.
 */
@Composable
fun NameSaveScreen(
    result: ResultUi,
    name: NameUi,
    onBack: () -> Unit,
    onText: (String) -> Unit,
    onRole: (PhotoRole) -> Unit,
    onFolder: (SaveFolder) -> Unit,
    onAscii: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
) {
    var showChecks by rememberSaveable { mutableStateOf(!result.ok) }
    Column(Modifier.fillMaxSize()) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTopBar(stringResource(R.string.name_title), onBack, stringResource(R.string.action_back))

        // Photo + verdict
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                result.preview, stringResource(R.string.name_preview_desc), contentScale = ContentScale.Crop,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(20.dp))
                    .border(2.dp, if (result.ok) Pal.Mint else Pal.Coral, RoundedCornerShape(20.dp)),
            )
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.ok) {
                    Pill(stringResource(R.string.name_all_ok), Pal.Mint)
                } else {
                    Pill(stringResource(R.string.result_counts, result.failures, result.warnings), Pal.Coral)
                }
                Text(
                    stringResource(R.string.result_specs, result.side, (result.sizeBytes + 512) / 1024),
                    color = Pal.Muted, fontSize = 13.sp,
                )
                if (result.autoCropped) Text(stringResource(R.string.result_auto_crop), color = Pal.Indigo, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        // Who is it for
        SectionLabel(stringResource(R.string.name_for))
        ChipFlow(Modifier.fillMaxWidth()) {
            PhotoRole.values().forEach { role ->
                ChoiceChip(stringResource(role.label()), role == name.role, { onRole(role) })
            }
        }

        // File name
        SectionLabel(stringResource(R.string.name_file))
        NameField(name.text, onText, onDone = onSave, clearDescription = stringResource(R.string.action_clear))
        Text(
            stringResource(R.string.name_will_be, name.finalName),
            color = Pal.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (name.needsAscii) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.name_ascii_tip), color = Pal.Amber, fontSize = 13.sp, lineHeight = 18.sp)
                    AppButton(stringResource(R.string.name_ascii_fix), onAscii, Modifier.fillMaxWidth(), BtnStyle.Secondary)
                }
            }
        }

        // Where to save
        SectionLabel(stringResource(R.string.name_where))
        Segmented(
            listOf(
                SaveFolder.GALLERY to stringResource(R.string.folder_gallery),
                SaveFolder.DOWNLOADS to stringResource(R.string.folder_downloads),
            ),
            name.folder, onFolder, Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(if (name.folder == SaveFolder.GALLERY) R.string.folder_gallery_path else R.string.folder_downloads_path),
            color = Pal.Muted, fontSize = 12.sp,
        )


        // Details
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.Surface)
                .clickable(role = Role.Button) { showChecks = !showChecks }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.name_checks, result.checks.count { it.status == Status.PASS }, result.checks.size),
                color = Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            Text(if (showChecks) "▴" else "▾", color = Pal.Muted, fontSize = 16.sp)
        }
        if (showChecks) ChecksCard(result.checks)

        if (result.fromUpload) {
            Text(stringResource(R.string.result_upload_note), color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
    }

    // Always-visible actions: the user never has to scroll to find "Save"
    Column(
        Modifier.fillMaxWidth().background(Pal.Ink).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        name.errorRes?.let { Text(stringResource(it), color = Pal.Coral, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        AppButton(
            stringResource(if (name.saving) R.string.action_saving else R.string.action_save),
            onSave, Modifier.fillMaxWidth(), enabled = !name.saving,
            leading = if (name.saving) null else Icons.Default.Check,
        )
        AppButton(stringResource(R.string.action_save_as), onSaveAs, Modifier.fillMaxWidth(), BtnStyle.Ghost, enabled = !name.saving)
    }
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit, clearDescription: String) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(120)) },
        singleLine = true,
        textStyle = TextStyle(color = Pal.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(Pal.Mint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pal.Surface2)
                    .border(1.dp, Pal.Hairline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(FileNaming.DEFAULT_BASE, color = Pal.Muted, fontSize = 18.sp)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(50)).semantics { contentDescription = clearDescription }
                            .clickable(role = Role.Button) { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Close, null, tint = Pal.Muted, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(6.dp))
                }
                Text(".${FileNaming.EXTENSION}", color = Pal.Muted, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        },
    )
}
