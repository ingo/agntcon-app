package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.now_trip_report_text
import org.jetbrains.kotlinconf.generated.resources.now_trip_report_title
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme

/** The way into the trip report from the Now tab: a big card, first thing after the event. */
@Composable
internal fun TripReportCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val white = KotlinConfTheme.colors.primaryTextWhiteFixed
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(KotlinConfTheme.colors.primaryBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(20.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.now_trip_report_title), style = KotlinConfTheme.typography.h3, color = white)
            Text(
                stringResource(Res.string.now_trip_report_text),
                style = KotlinConfTheme.typography.text2,
                color = white.copy(alpha = 0.9f),
            )
        }
        Text("→", style = KotlinConfTheme.typography.h2, color = white)
    }
}
