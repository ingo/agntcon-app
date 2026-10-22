package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.my_schedule_free
import org.jetbrains.kotlinconf.generated.resources.my_schedule_free_browse
import org.jetbrains.kotlinconf.generated.resources.up_next_before_start
import org.jetbrains.kotlinconf.generated.resources.up_next_change_floor
import org.jetbrains.kotlinconf.generated.resources.up_next_earlier
import org.jetbrains.kotlinconf.generated.resources.up_next_hours_minutes
import org.jetbrains.kotlinconf.generated.resources.up_next_later
import org.jetbrains.kotlinconf.generated.resources.up_next_min_left
import org.jetbrains.kotlinconf.generated.resources.up_next_minutes
import org.jetbrains.kotlinconf.generated.resources.up_next_next
import org.jetbrains.kotlinconf.generated.resources.up_next_nothing_next
import org.jetbrains.kotlinconf.generated.resources.up_next_nothing_now
import org.jetbrains.kotlinconf.generated.resources.up_next_now
import org.jetbrains.kotlinconf.generated.resources.up_next_over
import org.jetbrains.kotlinconf.generated.resources.up_next_pick_one
import org.jetbrains.kotlinconf.generated.resources.up_next_starts_in
import org.jetbrains.kotlinconf.minus
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.DateTimeFormatting
import org.jetbrains.kotlinconf.utils.bottomInsetPadding
import kotlin.time.Duration

/**
 * "What's up now": the sessions you bookmarked for today, from the current time: what you're in,
 * what's next and where it is, free time you could fill, and the rest of the day.
 */
@Composable
internal fun UpNextContent(
    upNext: UpNext?,
    today: LocalDate,
    onSession: (SessionId) -> Unit,
    onFree: (FreeBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottomInsetPadding())
            .padding(bottom = 24.dp),
    ) {
        if (upNext == null) {
            Note(stringResource(Res.string.up_next_over))
            return@Column
        }
        if (upNext.day.date != today) {
            Note(stringResource(Res.string.up_next_before_start, DateTimeFormatting.date(upNext.day.date)))
        }

        // What's on now; a gap shows as free time instead
        SectionTitle(stringResource(Res.string.up_next_now) + " · " + DateTimeFormatting.time(upNext.at))
        if (upNext.current.isEmpty()) {
            Note(stringResource(Res.string.up_next_nothing_now))
        } else {
            if (upNext.current.size > 1) Note(stringResource(Res.string.up_next_pick_one), warning = true)
            upNext.current.forEach { session ->
                SessionCard(
                    session = session,
                    timeNote = stringResource(Res.string.up_next_min_left, durationText(session.endsAt - upNext.at)),
                    previousLocation = null,
                    onClick = { onSession(session.id) },
                )
            }
        }

        upNext.freeBeforeNext?.let { free -> FreeCard(free, onClick = { onFree(free) }) }

        val next = upNext.next
        if (next.isNotEmpty()) {
            val startsIn = next.first().startsAt - upNext.at
            SectionTitle(stringResource(Res.string.up_next_next) + " · " + stringResource(Res.string.up_next_starts_in, durationText(startsIn)))
            if (next.size > 1) Note(stringResource(Res.string.up_next_pick_one), warning = true)
            // Where you'll be coming from: the session you're in, or the last one today
            val comingFrom = upNext.current.firstOrNull()?.locationLine ?: upNext.earlier.lastOrNull()?.locationLine
            next.forEach { session ->
                SessionCard(
                    session = session,
                    timeNote = null,
                    previousLocation = comingFrom,
                    onClick = { onSession(session.id) },
                )
            }
        } else if (upNext.current.isNotEmpty() || upNext.freeBeforeNext == null) {
            Note(stringResource(Res.string.up_next_nothing_next))
        }

        if (upNext.later.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.up_next_later))
            upNext.later.forEach { CompactRow(it, onClick = { onSession(it.id) }) }
        }

        if (upNext.earlier.isNotEmpty()) {
            var showEarlier by rememberSaveable(upNext.day.date) { mutableStateOf(false) }
            Text(
                stringResource(Res.string.up_next_earlier, upNext.earlier.size) + if (showEarlier) "  ▴" else "  ▾",
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.noteText,
                modifier = Modifier.clickable { showEarlier = !showEarlier }.padding(vertical = 8.dp),
            )
            if (showEarlier) upNext.earlier.forEach { CompactRow(it, onClick = { onSession(it.id) }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = KotlinConfTheme.typography.h3,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String, warning: Boolean = false) {
    Text(
        text,
        style = KotlinConfTheme.typography.text2,
        color = if (warning) KotlinConfTheme.colors.orangeText else KotlinConfTheme.colors.noteText,
    )
}

@Composable
private fun SessionCard(
    session: SessionCardView,
    timeNote: String?,
    previousLocation: String?,
    onClick: () -> Unit,
) {
    val colors = KotlinConfTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(colors.tileBackground)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(session.title, style = KotlinConfTheme.typography.h3, maxLines = 3)
        Text(
            session.shortTimeline + (timeNote?.let { " · $it" } ?: ""),
            style = KotlinConfTheme.typography.text2,
            color = colors.secondaryText,
        )
        // Where to go: the room, and the floor when the event names it
        val level = roomLevel(session.locationLine)
        Text(
            roomName(session.locationLine) + (level?.let { " · $it" } ?: ""),
            style = KotlinConfTheme.typography.text1,
            color = colors.primaryText,
        )
        val previousLevel = previousLocation?.let(::roomLevel)
        if (level != null && previousLevel != null && level != previousLevel) {
            Text(
                stringResource(Res.string.up_next_change_floor, previousLevel, level),
                style = KotlinConfTheme.typography.text2,
                color = colors.accentText,
            )
        }
    }
}

@Composable
private fun FreeCard(free: FreeBlock, onClick: () -> Unit) {
    val colors = KotlinConfTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(colors.mainBackground)
            .clickable(enabled = free.available.isNotEmpty(), onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            stringResource(Res.string.my_schedule_free, DateTimeFormatting.timeToTime(free.startsAt, free.endsAt)),
            style = KotlinConfTheme.typography.text2,
            color = colors.noteText,
        )
        if (free.available.isNotEmpty()) {
            BrowsePill(stringResource(Res.string.my_schedule_free_browse, free.available.size))
        }
    }
}

@Composable
private fun CompactRow(session: SessionCardView, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Text(
            DateTimeFormatting.time(session.startsAt),
            style = KotlinConfTheme.typography.text2,
            color = KotlinConfTheme.colors.noteText,
            modifier = Modifier.width(48.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(session.title, style = KotlinConfTheme.typography.text1, maxLines = 2)
            val level = roomLevel(session.locationLine)
            Text(
                roomName(session.locationLine) + (level?.let { " · $it" } ?: ""),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.noteText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun durationText(duration: Duration): String {
    val minutes = duration.inWholeMinutes.coerceAtLeast(0).toInt()
    return if (minutes < 60) {
        stringResource(Res.string.up_next_minutes, minutes)
    } else {
        stringResource(Res.string.up_next_hours_minutes, minutes / 60, minutes % 60)
    }
}
