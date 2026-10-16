package app.dvphoto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R
import app.dvphoto.core.DvRules
import app.dvphoto.core.Strictness

@Composable
fun SettingsScreen(
    state: SettingsUi,
    onBack: () -> Unit,
    onLanguage: (String) -> Unit,
    onAutoCapture: (Boolean) -> Unit,
    onVoice: (Boolean) -> Unit,
    onMeasurements: (Boolean) -> Unit,
    onOpenOfficial: () -> Unit,
    onStrictness: (Strictness) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTopBar(stringResource(R.string.settings_title), onBack, stringResource(R.string.action_back))

        SectionLabel(stringResource(R.string.settings_strictness))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(
                    options = Strictness.values().map { it to stringResource(it.res()) },
                    selected = state.strictness,
                    onSelect = onStrictness,
                )
                Text(stringResource(state.strictness.descriptionRes()), color = Pal.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        SectionLabel(stringResource(R.string.settings_camera))
        Card(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    stringResource(R.string.settings_auto), stringResource(R.string.settings_auto_sub),
                    state.autoCapture, onAutoCapture,
                )
                Divider()
                SettingRow(
                    stringResource(R.string.settings_voice), stringResource(R.string.settings_voice_sub),
                    state.voice, onVoice,
                )
                Divider()
                SettingRow(
                    stringResource(R.string.settings_measure), stringResource(R.string.settings_measure_sub),
                    state.measurements, onMeasurements,
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_language))
        Card(Modifier.fillMaxWidth()) {
            Column {
                state.languages.forEachIndexed { index, option ->
                    if (index > 0) Divider()
                    val selected = option.tag == state.languageTag
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onLanguage(option.tag) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.label, color = if (selected) Pal.Mint else Pal.Text, fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Pal.Mint))
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_about))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.app_name) + "  " + state.versionName, color = Pal.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(stringResource(R.string.settings_rules_asof, DvRules.RULES_AS_OF), color = Pal.Muted, fontSize = 13.sp)
                state.staleMonths?.let {
                    Text(stringResource(R.string.rules_stale, it), color = Pal.Amber, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Text(stringResource(R.string.settings_disclaimer), color = Pal.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(6.dp))
                AppButton(stringResource(R.string.settings_official), onOpenOfficial, Modifier.fillMaxWidth(), BtnStyle.Secondary)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Hairline))
}
