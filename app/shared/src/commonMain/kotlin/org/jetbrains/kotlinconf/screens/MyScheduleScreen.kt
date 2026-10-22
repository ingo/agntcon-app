package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.EVENT_TIME_ZONE
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.my_schedule_all_day
import org.jetbrains.kotlinconf.generated.resources.my_schedule_bookmark
import org.jetbrains.kotlinconf.generated.resources.my_schedule_clash
import org.jetbrains.kotlinconf.generated.resources.my_schedule_close
import org.jetbrains.kotlinconf.generated.resources.my_schedule_free
import org.jetbrains.kotlinconf.generated.resources.my_schedule_free_fits
import org.jetbrains.kotlinconf.generated.resources.my_schedule_no_sessions
import org.jetbrains.kotlinconf.generated.resources.my_schedule_summary
import org.jetbrains.kotlinconf.generated.resources.my_schedule_title
import org.jetbrains.kotlinconf.generated.resources.sync_my_schedule_link
import org.jetbrains.kotlinconf.generated.resources.sync_my_schedule_linked
import org.jetbrains.kotlinconf.minus
import org.jetbrains.kotlinconf.ui.components.HorizontalDivider
import org.jetbrains.kotlinconf.ui.components.MainHeaderTitleBar
import org.jetbrains.kotlinconf.ui.components.Switcher
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.components.TopMenuButton
import org.jetbrains.kotlinconf.ui.generated.resources.bookmark_24
import org.jetbrains.kotlinconf.ui.generated.resources.close_24
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.DateTimeFormatting
import org.jetbrains.kotlinconf.utils.bottomInsetPadding
import org.jetbrains.kotlinconf.utils.topInsetPadding
import kotlin.time.Clock
import org.jetbrains.kotlinconf.ui.generated.resources.UiRes

private val MinuteHeight = 2.dp
private val GutterWidth = 48.dp
private val MinBlockHeight = 22.dp
private val BlockVerticalPadding = 2.dp

/**
 * The bookmarked sessions of one day on a timeline: overlapping sessions side by side and marked
 * as clashes, free time between them with the sessions that would fit, all-day items above.
 */
@Composable
fun MyScheduleScreen(
    onSession: (SessionId) -> Unit,
    onSync: (() -> Unit)? = null,
    viewModel: MyScheduleViewModel = metroViewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val today = remember { Clock.System.now().toLocalDateTime(EVENT_TIME_ZONE).date }
    val day = days.find { it.date.toString() == selectedDate }
        ?: days.find { it.date == today }
        ?: days.firstOrNull()
    var freeWindow by remember(day?.date) { mutableStateOf<Pair<LocalDateTime, LocalDateTime>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(KotlinConfTheme.colors.mainBackground)
            .padding(topInsetPadding())
    ) {
        MainHeaderTitleBar(title = stringResource(Res.string.my_schedule_title))
        HorizontalDivider(thickness = 1.dp, color = KotlinConfTheme.colors.strokePale)

        if (day == null) {
            Text(
                stringResource(Res.string.my_schedule_no_sessions),
                color = KotlinConfTheme.colors.noteText,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        Switcher(
            items = remember(days) { days.map { DateTimeFormatting.date(it.date) } },
            shortItems = remember(days) { days.map { DateTimeFormatting.shortDate(it.date) } },
            selectedIndex = days.indexOf(day),
            onSelect = { index -> selectedDate = days[index].date.toString() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            stringResource(Res.string.my_schedule_summary, day.booked.size + day.allDay.size, day.clashCount),
            style = KotlinConfTheme.typography.text2,
            color = if (day.clashCount > 0) KotlinConfTheme.colors.orangeText else KotlinConfTheme.colors.noteText,
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
        )
        if (onSync != null) {
            val linked = viewModel.syncLinked.collectAsStateWithLifecycle().value
            Text(
                stringResource(if (linked) Res.string.sync_my_schedule_linked else Res.string.sync_my_schedule_link),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.accentText,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clickable(onClick = onSync)
                    .padding(bottom = 8.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val scrollState = rememberScrollState()
            val density = LocalDensity.current
            LaunchedEffect(day.date) {
                // Start at the first bookmarked session rather than at the top of the day
                val first = day.booked.firstOrNull()?.session?.startsAt ?: return@LaunchedEffect
                val y = with(density) { (MinuteHeight * (first - day.startsAt).inWholeMinutes.toInt()).roundToPx() }
                scrollState.scrollTo((y - with(density) { 24.dp.roundToPx() }).coerceAtLeast(0))
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp)
                    .padding(bottomInsetPadding())
            ) {
                if (day.allDay.isNotEmpty()) {
                    AllDayStrip(day.allDay, onSession)
                }
                Timeline(day, onSession, onFree = { freeWindow = it.startsAt to it.endsAt })
            }

            val window = freeWindow
            if (window != null) {
                FreeTimePanel(
                    day = day,
                    window = window,
                    onClose = { freeWindow = null },
                    onSession = onSession,
                    onBookmark = viewModel::setBookmark,
                )
            }
        }
    }
}

@Composable
private fun AllDayStrip(sessions: List<SessionCardView>, onSession: (SessionId) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            stringResource(Res.string.my_schedule_all_day),
            style = KotlinConfTheme.typography.text2,
            color = KotlinConfTheme.colors.noteText,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            sessions.forEach { session ->
                Column(
                    Modifier
                        .clip(KotlinConfTheme.shapes.roundedCornerSm)
                        .background(KotlinConfTheme.colors.tileBackground)
                        .clickable { onSession(session.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(session.title, style = KotlinConfTheme.typography.text2, maxLines = 1)
                    Text(session.shortTimeline, style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.noteText)
                }
            }
        }
    }
}

@Composable
private fun Timeline(
    day: MyScheduleDay,
    onSession: (SessionId) -> Unit,
    onFree: (FreeBlock) -> Unit,
) {
    fun minutesFromStart(time: LocalDateTime): Int = (time - day.startsAt).inWholeMinutes.toInt()
    fun heightOf(start: LocalDateTime, end: LocalDateTime): Dp = MinuteHeight * (end - start).inWholeMinutes.toInt()

    val totalMinutes = minutesFromStart(day.endsAt)

    // Blocks keep a minimum height to stay readable, so a very short session can run into the next
    // one; later blocks then start just below it instead of overlapping.
    val bookedPlacement = remember(day.booked) {
        var fullWidthBottom = 0.dp
        val columnBottoms = mutableMapOf<Int, Dp>()
        day.booked.associateWith { block ->
            val natural = MinuteHeight * minutesFromStart(block.session.startsAt)
            val floor = if (block.columns == 1) {
                maxOf(fullWidthBottom, columnBottoms.values.maxOrNull() ?: 0.dp)
            } else {
                maxOf(fullWidthBottom, columnBottoms[block.column] ?: 0.dp)
            }
            val top = maxOf(natural, floor)
            val height = max(heightOf(block.session.startsAt, block.session.endsAt), MinBlockHeight)
            if (block.columns == 1) fullWidthBottom = top + height else columnBottoms[block.column] = top + height
            top to height
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(MinuteHeight * totalMinutes + 16.dp)) {
        val laneWidth = maxWidth - GutterWidth

        for (hour in 0..totalMinutes / 60) {
            val y = MinuteHeight * (hour * 60)
            Text(
                "${(day.startsAt.hour + hour).toString().padStart(2, '0')}:00",
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.noteText,
                modifier = Modifier.offset(y = y - 8.dp),
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = KotlinConfTheme.colors.strokePale,
                modifier = Modifier.padding(start = GutterWidth).offset(y = y),
            )
        }

        day.free.forEach { free ->
            val previousBottom = bookedPlacement
                .filterKeys { it.session.endsAt <= free.startsAt }
                .values
                .maxOfOrNull { (top, height) -> top + height } ?: 0.dp
            val top = maxOf(MinuteHeight * minutesFromStart(free.startsAt), previousBottom)
            val height = MinuteHeight * minutesFromStart(free.endsAt) - top
            if (height >= MinBlockHeight) {
                FreeSlot(
                    free = free,
                    lines = linesIn(height),
                    onClick = { onFree(free) },
                    modifier = Modifier
                        .offset(x = GutterWidth, y = top)
                        .width(laneWidth)
                        .height(height),
                )
            }
        }

        day.booked.forEach { block ->
            val (top, height) = bookedPlacement.getValue(block)
            val columnWidth = laneWidth / block.columns
            BookedSlot(
                block = block,
                lines = linesIn(height),
                onClick = { onSession(block.session.id) },
                modifier = Modifier
                    .offset(x = GutterWidth + columnWidth * block.column, y = top)
                    .width(columnWidth)
                    .height(height),
            )
        }
    }
}

@Composable
private fun BookedSlot(block: BookedBlock, lines: Int, onClick: () -> Unit, modifier: Modifier) {
    // Show only what fits: title, then time and room, then a second title line, then the clash label.
    // Clashes always get the orange border.
    val clash = block.overlapsWith.isNotEmpty()
    val showTime = lines >= 2
    val titleLines = if (lines >= 3) 2 else 1
    val showClashLabel = clash && lines >= titleLines + 2
    val shape = KotlinConfTheme.shapes.roundedCornerSm
    Column(
        modifier
            .padding(2.dp)
            .clip(shape)
            .background(KotlinConfTheme.colors.mainBackground)
            .background(KotlinConfTheme.colors.tileBackground)
            .border(
                width = if (clash) 2.dp else 1.dp,
                color = if (clash) KotlinConfTheme.colors.orangeText else KotlinConfTheme.colors.strokeHalf,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = BlockVerticalPadding)
    ) {
        Text(block.session.title, style = KotlinConfTheme.typography.text2, maxLines = titleLines)
        if (showTime) {
            Text(
                "${block.session.shortTimeline} · ${block.session.locationLine}",
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.noteText,
                maxLines = 1,
            )
        }
        if (showClashLabel) {
            Text(
                stringResource(Res.string.my_schedule_clash),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.orangeText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FreeSlot(free: FreeBlock, lines: Int, onClick: () -> Unit, modifier: Modifier) {
    val shape = KotlinConfTheme.shapes.roundedCornerSm
    Column(
        modifier
            .padding(2.dp)
            .clip(shape)
            .border(1.dp, KotlinConfTheme.colors.strokePale, shape)
            .clickable(enabled = free.available.isNotEmpty(), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = BlockVerticalPadding)
    ) {
        Text(
            stringResource(Res.string.my_schedule_free, DateTimeFormatting.timeToTime(free.startsAt, free.endsAt)),
            style = KotlinConfTheme.typography.text2,
            color = KotlinConfTheme.colors.noteText,
            maxLines = 1,
        )
        if (free.available.isNotEmpty() && lines >= 2) {
            Text(
                stringResource(Res.string.my_schedule_free_fits, free.available.size),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.accentText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoxScope.FreeTimePanel(
    day: MyScheduleDay,
    window: Pair<LocalDateTime, LocalDateTime>,
    onClose: () -> Unit,
    onSession: (SessionId) -> Unit,
    onBookmark: (SessionId, Boolean) -> Unit,
) {
    val (start, end) = window
    // Taken from the live day, so bookmarking here updates the list and the timeline behind it
    val fits = day.sessions.filter { it.startsAt >= start && it.startsAt < end }

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .background(KotlinConfTheme.colors.mainBackground)
    ) {
        HorizontalDivider(thickness = 1.dp, color = KotlinConfTheme.colors.strokeHalf)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
        ) {
            Text(
                stringResource(Res.string.my_schedule_free, DateTimeFormatting.timeToTime(start, end)),
                style = KotlinConfTheme.typography.h3,
                modifier = Modifier.weight(1f),
            )
            TopMenuButton(
                icon = UiRes.drawable.close_24,
                contentDescription = stringResource(Res.string.my_schedule_close),
                onClick = onClose,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(fits, key = { it.id.id }) { session ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSession(session.id) }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(session.title, maxLines = 2)
                        Text(
                            "${session.shortTimeline} · ${session.locationLine}",
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
        }
    }
}

/** Lines of [KotlinConfTheme.typography.text2] that fit in a block of [height], at the user's font size. */
@Composable
private fun linesIn(height: Dp): Int {
    val lineHeight = with(LocalDensity.current) { KotlinConfTheme.typography.text2.lineHeight.toDp() }
    // Block padding (2.dp) plus the inner vertical padding on both sides
    val usable = height - 4.dp - BlockVerticalPadding * 2
    return (usable / lineHeight).toInt().coerceAtLeast(1)
}

