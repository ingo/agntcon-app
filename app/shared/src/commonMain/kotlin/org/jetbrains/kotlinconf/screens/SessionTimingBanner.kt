package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.session_ended
import org.jetbrains.kotlinconf.generated.resources.session_live
import org.jetbrains.kotlinconf.generated.resources.session_starts_in
import org.jetbrains.kotlinconf.generated.resources.session_starts_on
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.generated.resources.UiRes
import org.jetbrains.kotlinconf.ui.generated.resources.clock_24
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.DateTimeFormatting
import kotlin.time.Duration.Companion.minutes

/**
 * At the top of a session's page: how long until it starts (or that it's on, or over) and where,
 * so you can tell at a glance whether you're in the right room for the right session.
 */
@Composable
internal fun SessionTimingBanner(session: SessionCardView, now: LocalDateTime, modifier: Modifier = Modifier) {
    val colors = KotlinConfTheme.colors
    val timing = sessionTiming(session.startsAt, session.endsAt, now)
    val accent = colors.accentText
    val highlighted = (timing is SessionTiming.StartsIn && timing.soon) || timing is SessionTiming.Live

    val headline = when (timing) {
        is SessionTiming.StartsIn -> stringResource(Res.string.session_starts_in, durationText(timing.minutes.minutes))
        is SessionTiming.StartsOn -> stringResource(
            Res.string.session_starts_on,
            DateTimeFormatting.weekdayDate(timing.startsAt.date),
            DateTimeFormatting.time(timing.startsAt),
        )
        is SessionTiming.Live -> stringResource(Res.string.session_live, durationText(timing.minutesLeft.minutes))
        SessionTiming.Ended -> stringResource(Res.string.session_ended)
    }
    val level = roomLevel(session.locationLine)
    val where = roomName(session.locationLine) + (level?.let { ", $it" } ?: "")

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(if (highlighted) accent.copy(alpha = 0.12f) else colors.tileBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Image(
            painter = painterResource(UiRes.drawable.clock_24),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (timing == SessionTiming.Ended) colors.noteText else accent),
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                headline,
                style = KotlinConfTheme.typography.h4,
                color = if (timing == SessionTiming.Ended) colors.noteText else if (highlighted) accent else colors.primaryText,
            )
            Text(where, style = KotlinConfTheme.typography.text2, color = colors.secondaryText)
        }
    }
}
