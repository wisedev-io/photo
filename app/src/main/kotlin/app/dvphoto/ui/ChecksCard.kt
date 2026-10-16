package app.dvphoto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dvphoto.core.Check
import app.dvphoto.core.Status

/** Problems first (failures, then warnings), then the checks that passed. */
fun List<Check>.problemsFirst(): List<Check> =
    sortedWith(compareBy({ it.status == Status.PASS }, { it.status != Status.FAIL }, { it.id.ordinal }))

@Composable
fun ChecksCard(checks: List<Check>, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            checks.problemsFirst().forEach { CheckRow(it) }
        }
    }
}

@Composable
fun CheckRow(c: Check) {
    val color = c.status.color()
    val icon = when (c.status) {
        Status.PASS -> Icons.Default.Check
        Status.WARN -> Icons.Default.Warning
        Status.FAIL -> Icons.Default.Close
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(c.id.res()), color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(c.hintText(), color = if (c.status == Status.PASS) Pal.Muted else color, fontSize = 13.sp, lineHeight = 17.sp)
        }
        c.valueText()?.let { Pill(it, color, Modifier.padding(start = 8.dp)) }
    }
}
