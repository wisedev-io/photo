package app.dvphoto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.R
import app.dvphoto.core.DvRules
import app.dvphoto.core.Origin
import app.dvphoto.core.RuleCatalogue
import app.dvphoto.core.RuleInfo
import kotlin.math.roundToInt

private fun pct(v: Float): Any = (v * 100).roundToInt()

private val ruleArgs: Map<Int, Array<Any>> = mapOf(
    R.string.help_rules_2 to arrayOf(pct(DvRules.HEAD_MIN), pct(DvRules.HEAD_MAX), pct(DvRules.EYE_MIN), pct(DvRules.EYE_MAX)),
    R.string.help_rules_1 to arrayOf(DvRules.MIN_SIDE_PX, DvRules.MAX_SIDE_PX, DvRules.MAX_FILE_BYTES / 1024),
)

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenEntry: () -> Unit,
    staleMonths: Int? = null,
    onOpenUrl: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTopBar(stringResource(R.string.help_title), onBack, stringResource(R.string.action_back))

        HelpCard(R.string.help_rules_title, Pal.Mint, listOf(
            R.string.help_rules_1, R.string.help_rules_2, R.string.help_rules_3, R.string.help_rules_4,
            R.string.help_rules_5, R.string.help_rules_6, R.string.help_rules_7,
        ), ruleArgs)
        RuleSourcesCard(staleMonths, onOpenUrl)
        HelpCard(R.string.help_tips_title, Pal.Indigo, listOf(
            R.string.help_tips_1, R.string.help_tips_2, R.string.help_tips_3, R.string.help_tips_4, R.string.help_tips_5,
        ))
        HelpCard(R.string.help_nofilter_title, Pal.Coral, listOf(
            R.string.help_nofilter_1, R.string.help_nofilter_2, R.string.help_nofilter_3,
        ))
        HelpCard(R.string.help_kids_title, Pal.Amber, listOf(
            R.string.help_kids_1, R.string.help_kids_2, R.string.help_kids_3,
        ))
        HelpCard(R.string.help_cant_title, Pal.Muted, listOf(
            R.string.help_cant_1, R.string.help_cant_2, R.string.help_cant_3,
        ))

        AppButton(stringResource(R.string.settings_official), onOpenOfficial, Modifier.fillMaxWidth(), BtnStyle.Secondary)
        AppButton(stringResource(R.string.help_open_entry), onOpenEntry, Modifier.fillMaxWidth(), BtnStyle.Ghost)
        Spacer(Modifier.height(16.dp))
    }
}

/** Which of the app's rules are the State Department's numbers, which are its wording, and the page each one is from. */
@Composable
private fun RuleSourcesCard(staleMonths: Int?, onOpenUrl: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.rules_sources_title), color = Pal.Mint, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.rules_sources_intro), color = Pal.Text, fontSize = 14.sp, lineHeight = 20.sp)
            Text(stringResource(R.string.settings_rules_asof, DvRules.RULES_AS_OF), color = Pal.Muted, fontSize = 13.sp)
            staleMonths?.let {
                Text(stringResource(R.string.rules_stale, it), color = Pal.Amber, fontSize = 13.sp, lineHeight = 18.sp)
            }
            RuleCatalogue.all.forEach { rule -> RuleSourceRow(rule) { onOpenUrl(rule.source.url) } }
            Text(stringResource(R.string.rules_legend), color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun RuleSourceRow(rule: RuleInfo, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(rule.check.res()), color = Pal.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(rule.source.title, color = Pal.Indigo, fontSize = 12.sp)
        }
        Pill(
            stringResource(rule.origin.res()),
            if (rule.origin == Origin.OFFICIAL_NUMBER) Pal.Mint else Pal.Indigo,
            Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun HelpCard(title: Int, accent: Color, lines: List<Int>, args: Map<Int, Array<Any>> = emptyMap()) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), color = accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            lines.forEach { line ->
                Row {
                    Text("•", color = accent, fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(line, *(args[line] ?: emptyArray())), color = Pal.Text, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}
