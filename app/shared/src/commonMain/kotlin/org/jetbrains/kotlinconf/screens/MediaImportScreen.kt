package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.ScreenWithTitle
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.import_answer_first
import org.jetbrains.kotlinconf.generated.resources.import_add
import org.jetbrains.kotlinconf.generated.resources.import_add_matched
import org.jetbrains.kotlinconf.generated.resources.import_bookmarked
import org.jetbrains.kotlinconf.generated.resources.import_change
import org.jetbrains.kotlinconf.generated.resources.import_dont_add
import org.jetbrains.kotlinconf.generated.resources.import_dont_add_it
import org.jetbrains.kotlinconf.generated.resources.import_duplicates
import org.jetbrains.kotlinconf.generated.resources.media_unreadable
import org.jetbrains.kotlinconf.generated.resources.import_intro
import org.jetbrains.kotlinconf.generated.resources.import_items
import org.jetbrains.kotlinconf.generated.resources.import_leave_out
import org.jetbrains.kotlinconf.generated.resources.import_likely
import org.jetbrains.kotlinconf.generated.resources.import_no_options
import org.jetbrains.kotlinconf.generated.resources.import_outlier
import org.jetbrains.kotlinconf.generated.resources.import_outlier_move
import org.jetbrains.kotlinconf.generated.resources.import_not_added
import org.jetbrains.kotlinconf.generated.resources.import_nothing_to_add
import org.jetbrains.kotlinconf.generated.resources.import_question_ask
import org.jetbrains.kotlinconf.generated.resources.import_question_between
import org.jetbrains.kotlinconf.generated.resources.import_question_moved
import org.jetbrains.kotlinconf.generated.resources.import_question_no_time
import org.jetbrains.kotlinconf.generated.resources.import_slide_matches
import org.jetbrains.kotlinconf.generated.resources.import_tap_to_view
import org.jetbrains.kotlinconf.generated.resources.import_title
import org.jetbrains.kotlinconf.generated.resources.play_video
import org.jetbrains.kotlinconf.media.MediaMatch
import org.jetbrains.kotlinconf.media.PickedMedia
import org.jetbrains.kotlinconf.media.rememberMediaPicker
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.ui.components.Button
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.DateTimeFormatting

/**
 * Photos and videos from the library, matched to sessions by when they were taken: confirm, change or answer,
 * then add. Leaving without adding keeps nothing.
 */
@Composable
fun MediaImportScreen(
    onDone: () -> Unit,
    viewModel: MediaImportViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberMediaPicker()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    ScreenWithTitle(title = stringResource(Res.string.import_title), onBack = onDone) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 24.dp),
        ) {
            Text(stringResource(Res.string.import_intro), color = KotlinConfTheme.colors.longText)
            if (state.unreadable > 0) {
                Text(
                    pluralStringResource(Res.plurals.media_unreadable, state.unreadable, state.unreadable),
                    color = KotlinConfTheme.colors.orangeText,
                )
            }
            if (state.duplicates > 0) {
                Text(
                    pluralStringResource(Res.plurals.import_duplicates, state.duplicates, state.duplicates),
                    color = KotlinConfTheme.colors.noteText,
                )
            }
            state.groups.forEachIndexed { index, group ->
                GroupCard(
                    group = group,
                    attended = state.attended,
                    onView = { item -> picker?.open(listOf(item.path), item.kind) },
                    onTakeOut = { item -> viewModel.takeOut(index, item) },
                    outlier = state.outliers[index]?.let { (flagged, target) -> flagged.size to state.sessions[target] },
                    onMoveFlagged = { viewModel.moveFlagged(index) },
                    onAnswer = { viewModel.answer(index, it) },
                    onChange = { viewModel.change(index) },
                )
            }
            if (state.unanswered > 0) {
                Text(
                    pluralStringResource(Res.plurals.import_answer_first, state.unanswered, state.unanswered),
                    color = KotlinConfTheme.colors.noteText,
                )
            }
            Button(
                label = if (state.toAdd > 0) {
                    pluralStringResource(Res.plurals.import_add, state.toAdd, state.toAdd)
                } else {
                    stringResource(Res.string.import_nothing_to_add)
                },
                onClick = viewModel::confirm,
                primary = true,
                enabled = state.unanswered == 0 && !state.saving && !state.checking,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.unanswered > 0 && state.answeredToAdd > 0) {
                Button(
                    label = pluralStringResource(Res.plurals.import_add_matched, state.answeredToAdd, state.answeredToAdd),
                    onClick = viewModel::skipUnansweredAndConfirm,
                    enabled = !state.saving && !state.checking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: ImportGroup,
    attended: Set<SessionId>,
    onView: (PickedMedia) -> Unit,
    onTakeOut: (Int) -> Unit,
    /** How many of this group's photos are probably misplaced, and the session the photos around them went to. */
    outlier: Pair<Int, SessionCardView?>?,
    onMoveFlagged: () -> Unit,
    onAnswer: (ImportAnswer) -> Unit,
    onChange: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(KotlinConfTheme.colors.tileBackground)
            .padding(16.dp),
    ) {
        if (group.isQuestion) {
            Text(
                stringResource(
                    when {
                        group.movedOut -> Res.string.import_question_moved
                        else -> when (group.match) {
                        is MediaMatch.Ask -> Res.string.import_question_ask
                        is MediaMatch.Between -> Res.string.import_question_between
                        else -> Res.string.import_question_no_time
                        }
                    }
                ),
                style = KotlinConfTheme.typography.h3,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            listOfNotNull(
                pluralStringResource(Res.plurals.import_items, group.items.size, group.items.size),
                timeRange(group.from, group.to),
            ).joinToString(" · "),
            style = KotlinConfTheme.typography.text2,
            color = KotlinConfTheme.colors.noteText,
        )
        Thumbnails(group.items, canTakeOut = !group.movedOut && group.items.size > 1, onView, onTakeOut)
        val target = outlier?.second
        if (outlier != null && target != null) {
            // One or two photos going somewhere else than the photos taken just before and after: probably a slip
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(KotlinConfTheme.shapes.roundedCornerSm)
                    .border(1.dp, KotlinConfTheme.colors.orangeText, KotlinConfTheme.shapes.roundedCornerSm)
                    .padding(12.dp),
            ) {
                Text(
                    pluralStringResource(Res.plurals.import_outlier, outlier.first, outlier.first, target.title),
                    style = KotlinConfTheme.typography.text2,
                    color = KotlinConfTheme.colors.orangeText,
                )
                Button(
                    label = stringResource(Res.string.import_outlier_move),
                    onClick = onMoveFlagged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(stringResource(Res.string.import_tap_to_view), style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.noteText)

        if (group.isQuestion || group.changing) {
            if (group.options.isEmpty()) {
                Text(stringResource(Res.string.import_no_options), style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.noteText)
            }
            group.options.forEach { session ->
                OptionRow(
                    title = session.title,
                    bold = session.id in attended,
                    detail = listOfNotNull(
                        sessionDetail(session, session.id in attended),
                        if (session.id == group.suggested) stringResource(Res.string.import_slide_matches) else null,
                        if (session.id == group.likely && session.id != group.suggested) stringResource(Res.string.import_likely) else null,
                    ).joinToString(" · "),
                    selected = group.answer == ImportAnswer.To(session.id),
                    onClick = { onAnswer(ImportAnswer.To(session.id)) },
                )
            }
            OptionRow(
                title = stringResource(if (group.movedOut) Res.string.import_dont_add_it else Res.string.import_dont_add),
                bold = false,
                detail = null,
                selected = group.answer == ImportAnswer.Skip,
                onClick = { onAnswer(ImportAnswer.Skip) },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val session = group.matchedSession
                    if (session != null) {
                        Text(
                            session.title,
                            style = if (session.id in attended) KotlinConfTheme.typography.text1.copy(fontWeight = FontWeight.Bold) else KotlinConfTheme.typography.text1,
                        )
                        Text(sessionDetail(session, session.id in attended), style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.noteText)
                    } else {
                        Text(stringResource(Res.string.import_not_added), style = KotlinConfTheme.typography.text1)
                    }
                }
                Text(
                    stringResource(Res.string.import_change),
                    style = KotlinConfTheme.typography.text2,
                    color = KotlinConfTheme.colors.accentText,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onChange).padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** Large enough to make out a slide; tap one to see it full screen. */
@Composable
private fun Thumbnails(items: List<PickedMedia>, canTakeOut: Boolean, onView: (PickedMedia) -> Unit, onTakeOut: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        items.forEachIndexed { index, item ->
            Box(
                Modifier
                    .size(width = 176.dp, height = 132.dp)
                    .clip(KotlinConfTheme.shapes.roundedCornerSm)
                    .background(KotlinConfTheme.colors.mainBackground)
                    .clickable(role = Role.Image, onClick = { onView(item) }),
                contentAlignment = Alignment.Center,
            ) {
                // The small preview made when the photo was copied; the full photo only where there is none
                val preview = if (item.kind == MediaKind.VIDEO) item.thumbnailPath else item.thumbnailPath ?: item.path
                if (preview != null) {
                    AsyncImage(model = preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (item.kind == MediaKind.VIDEO) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(Res.drawable.play_video), contentDescription = null, modifier = Modifier.padding(start = 3.dp).size(18.dp))
                    }
                }
                // Take this one photo out of the group (it was grouped wrong); it gets a card of its own below
                if (canTakeOut) {
                val toggleLabel = stringResource(Res.string.import_leave_out)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp)
                        .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = { onTakeOut(index) })
                        .semantics { contentDescription = toggleLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = KotlinConfTheme.typography.text1, color = Color.White)
                    }
                }
                }
            }
        }
    }
}

@Composable
internal fun OptionRow(title: String, bold: Boolean, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerSm)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) KotlinConfTheme.colors.primaryBackground else KotlinConfTheme.colors.strokeHalf, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (selected) KotlinConfTheme.colors.primaryBackground else Color.Transparent),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // The attendee's own sessions stand out: most likely the right answer
            Text(
                title,
                style = if (bold) KotlinConfTheme.typography.text1.copy(fontWeight = FontWeight.Bold) else KotlinConfTheme.typography.text1,
            )
            if (detail != null) Text(detail, style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.noteText)
        }
    }
}

@Composable
internal fun sessionDetail(session: SessionCardView, attended: Boolean): String = listOfNotNull(
    DateTimeFormatting.timeToTime(session.startsAt, session.endsAt),
    session.locationLine.takeIf { it.isNotBlank() },
    if (attended && session.isFavorite) stringResource(Res.string.import_bookmarked) else null,
).joinToString(" · ")

private fun timeRange(from: LocalDateTime?, to: LocalDateTime?): String? {
    from ?: return null
    val day = DateTimeFormatting.weekdayDate(from.date)
    return if (to == null || to == from) "$day, ${DateTimeFormatting.time(from)}" else "$day, ${DateTimeFormatting.time(from)}–${DateTimeFormatting.time(to)}"
}
