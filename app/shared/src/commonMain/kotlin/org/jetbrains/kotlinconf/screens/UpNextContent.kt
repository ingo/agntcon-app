package org.jetbrains.kotlinconf.screens

import org.jetbrains.kotlinconf.generated.resources.now_starting_next
import org.jetbrains.kotlinconf.generated.resources.now_on_now
import org.jetbrains.kotlinconf.generated.resources.now_after
import org.jetbrains.kotlinconf.generated.resources.now_between
import org.jetbrains.kotlinconf.generated.resources.now_before
import org.jetbrains.kotlinconf.generated.resources.now_welcome_hint
import org.jetbrains.kotlinconf.generated.resources.now_good_evening
import org.jetbrains.kotlinconf.generated.resources.now_good_afternoon
import org.jetbrains.kotlinconf.generated.resources.now_good_morning
import org.jetbrains.kotlinconf.generated.resources.my_schedule_bookmark
import org.jetbrains.kotlinconf.ui.generated.resources.bookmark_24
import org.jetbrains.kotlinconf.ui.generated.resources.UiRes
import org.jetbrains.kotlinconf.ui.components.TopMenuButton
import org.jetbrains.kotlinconf.event.EventLogo
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.generated.resources.up_next_clash
import org.jetbrains.kotlinconf.generated.resources.clash_16
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
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
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.my_schedule_free
import org.jetbrains.kotlinconf.generated.resources.my_schedule_free_browse
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
    now: LocalDateTime,
    onSession: (SessionId) -> Unit,
    onFree: (FreeBlock) -> Unit,
    onBookmark: (SessionId, Boolean) -> Unit,
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
        GreetingCard(upNext, now)
        if (upNext == null) return@Column

        if (upNext.nothingBookmarked) {
            // Nothing planned yet: what's going on across the conference, to pick from
            if (upNext.happening.isNotEmpty()) {
                SectionTitle(stringResource(Res.string.now_on_now))
                upNext.happening.forEach { BrowseRow(it, onSession, onBookmark) }
            }
            val nextRound = upNext.startingNext.firstOrNull()
            if (nextRound != null) {
                SectionTitle(stringResource(Res.string.now_starting_next, DateTimeFormatting.time(nextRound.startsAt)))
                upNext.startingNext.forEach { BrowseRow(it, onSession, onBookmark) }
            }
            return@Column
        }

        // Bookmarked sessions that overlap another one get a marker
        val clashing = upNext.day.booked.filter { it.overlapsWith.isNotEmpty() }.map { it.session.id }.toSet()

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
                    clash = session.id in clashing,
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
                    clash = session.id in clashing,
                    previousLocation = comingFrom,
                    onClick = { onSession(session.id) },
                )
            }
        } else if (upNext.current.isNotEmpty() || upNext.freeBeforeNext == null) {
            Note(stringResource(Res.string.up_next_nothing_next))
        }

        if (upNext.later.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.up_next_later))
            upNext.later.forEach { CompactRow(it, clash = it.id in clashing, onClick = { onSession(it.id) }) }
        }

        if (upNext.earlier.isNotEmpty()) {
            var showEarlier by rememberSaveable(upNext.day.date) { mutableStateOf(false) }
            Text(
                stringResource(Res.string.up_next_earlier, upNext.earlier.size) + if (showEarlier) "  ▴" else "  ▾",
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.noteText,
                modifier = Modifier.clickable { showEarlier = !showEarlier }.padding(vertical = 8.dp),
            )
            if (showEarlier) upNext.earlier.forEach { CompactRow(it, clash = it.id in clashing, onClick = { onSession(it.id) }) }
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
    clash: Boolean,
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (clash) ClashIcon()
            Text(
                session.shortTimeline + (timeNote?.let { " · $it" } ?: ""),
                style = KotlinConfTheme.typography.text2,
                color = colors.secondaryText,
            )
        }
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
private fun CompactRow(session: SessionCardView, clash: Boolean, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(64.dp),
        ) {
            Text(
                DateTimeFormatting.time(session.startsAt),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.noteText,
            )
            if (clash) ClashIcon()
        }
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

/** Marks a session that overlaps another bookmarked session. */
@Composable
private fun ClashIcon() {
    Image(
        painter = painterResource(Res.drawable.clash_16),
        contentDescription = stringResource(Res.string.up_next_clash),
        colorFilter = ColorFilter.tint(KotlinConfTheme.colors.orangeText),
        modifier = Modifier.size(14.dp),
    )
}

/** A friendly welcome: greeting, the event, and where the day stands. */
@Composable
private fun GreetingCard(upNext: UpNext?, now: LocalDateTime) {
    val white = KotlinConfTheme.colors.primaryTextWhiteFixed
    val event = ActiveEvent
    val greeting = when (now.hour) {
        in 0..11 -> Res.string.now_good_morning
        in 12..16 -> Res.string.now_good_afternoon
        else -> Res.string.now_good_evening
    }
    val status: String? = when {
        upNext == null -> stringResource(Res.string.now_after, event.name)
        upNext.moment == ConferenceMoment.BeforeConference ->
            stringResource(Res.string.now_before, event.name, DateTimeFormatting.weekdayDate(upNext.day.date))
        upNext.moment == ConferenceMoment.BetweenDays ->
            stringResource(Res.string.now_between, DateTimeFormatting.weekdayDate(upNext.day.date))
        else -> null
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(KotlinConfTheme.colors.headerGradient)
            .padding(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            event.theme?.let { EventLogo(it, size = 44.dp, tint = white) }
            Column {
                Text(stringResource(greeting), style = KotlinConfTheme.typography.h2, color = white)
                Text(
                    DateTimeFormatting.weekdayDate(now.date) + " · " + DateTimeFormatting.time(now),
                    style = KotlinConfTheme.typography.text2,
                    color = white.copy(alpha = 0.8f),
                )
            }
        }
        Text(event.name, style = KotlinConfTheme.typography.text1, color = white, modifier = Modifier.padding(top = 6.dp))
        if (status != null) {
            Text(status, style = KotlinConfTheme.typography.text2, color = white.copy(alpha = 0.9f))
        }
        if (upNext != null && upNext.nothingBookmarked) {
            Text(stringResource(Res.string.now_welcome_hint), style = KotlinConfTheme.typography.text2, color = white.copy(alpha = 0.9f))
        }
    }
}

/** A session to pick, with a bookmark button. */
@Composable
private fun BrowseRow(session: SessionCardView, onSession: (SessionId) -> Unit, onBookmark: (SessionId, Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(KotlinConfTheme.colors.tileBackground)
            .clickable { onSession(session.id) }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.title, style = KotlinConfTheme.typography.text1, maxLines = 2)
            val level = roomLevel(session.locationLine)
            Text(
                session.shortTimeline + " · " + roomName(session.locationLine) + (level?.let { " · $it" } ?: ""),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.noteText,
                maxLines = 1,
            )
        }
        TopMenuButton(
            icon = UiRes.drawable.bookmark_24,
            contentDescription = stringResource(Res.string.my_schedule_bookmark),
            selected = session.isFavorite,
            onToggle = { onBookmark(session.id, it) },
        )
    }
}

