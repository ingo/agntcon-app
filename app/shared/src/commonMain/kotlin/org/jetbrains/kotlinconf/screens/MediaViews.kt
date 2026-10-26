package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.camera_24
import org.jetbrains.kotlinconf.generated.resources.photo_library_24
import org.jetbrains.kotlinconf.generated.resources.play_video
import org.jetbrains.kotlinconf.generated.resources.session_media_actions
import org.jetbrains.kotlinconf.generated.resources.import_duplicates
import org.jetbrains.kotlinconf.generated.resources.import_no_options
import org.jetbrains.kotlinconf.generated.resources.media_reading_progress
import org.jetbrains.kotlinconf.generated.resources.media_unreadable
import org.jetbrains.kotlinconf.generated.resources.session_media_adding
import org.jetbrains.kotlinconf.generated.resources.session_media_cleaned
import org.jetbrains.kotlinconf.generated.resources.cleanup_auto
import org.jetbrains.kotlinconf.generated.resources.cleanup_auto_detail
import org.jetbrains.kotlinconf.generated.resources.cleanup_colourful
import org.jetbrains.kotlinconf.generated.resources.cleanup_colourful_detail
import org.jetbrains.kotlinconf.generated.resources.cleanup_dark
import org.jetbrains.kotlinconf.generated.resources.cleanup_dark_detail
import org.jetbrains.kotlinconf.generated.resources.cleanup_light
import org.jetbrains.kotlinconf.generated.resources.cleanup_light_detail
import org.jetbrains.kotlinconf.generated.resources.cleanup_original
import org.jetbrains.kotlinconf.generated.resources.cleanup_original_detail
import org.jetbrains.kotlinconf.generated.resources.session_media_cancel
import org.jetbrains.kotlinconf.generated.resources.session_media_highlight
import org.jetbrains.kotlinconf.generated.resources.session_media_unhighlight
import org.jetbrains.kotlinconf.generated.resources.session_media_cleanup
import org.jetbrains.kotlinconf.generated.resources.session_media_cleanup_title
import org.jetbrains.kotlinconf.generated.resources.session_media_from_library
import org.jetbrains.kotlinconf.generated.resources.session_media_hint
import org.jetbrains.kotlinconf.generated.resources.session_media_move
import org.jetbrains.kotlinconf.generated.resources.session_media_move_or_remove
import org.jetbrains.kotlinconf.generated.resources.session_media_move_other_days
import org.jetbrains.kotlinconf.generated.resources.session_media_move_title
import org.jetbrains.kotlinconf.generated.resources.session_media_photo
import org.jetbrains.kotlinconf.generated.resources.session_media_record_video
import org.jetbrains.kotlinconf.generated.resources.session_media_remove
import org.jetbrains.kotlinconf.generated.resources.session_media_take_photo
import org.jetbrains.kotlinconf.generated.resources.session_media_title
import org.jetbrains.kotlinconf.generated.resources.session_media_video
import org.jetbrains.kotlinconf.generated.resources.videocam_24
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.media.MediaPicker
import org.jetbrains.kotlinconf.media.MoveTargets
import org.jetbrains.kotlinconf.media.PickedMedia
import org.jetbrains.kotlinconf.media.screenCleanUpAvailable
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.CleanUpMode
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.ui.components.Button
import org.jetbrains.kotlinconf.ui.components.PageMenuItem
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.DateTimeFormatting

/**
 * The attendee's photos, screenshots and videos on a session: a grid of thumbnails (tap to view, touch
 * and hold to move to another session, choose how a photo is cleaned up, or remove) and the camera and library buttons. Not shown where the app
 * keeps no files.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MediaBlock(
    picker: MediaPicker,
    attachments: List<Attachment>,
    adding: Boolean,
    skippedDuplicates: Int,
    progress: Pair<Int, Int>?,
    unreadable: Int,
    pathOf: (String) -> String?,
    onProgress: (done: Int, total: Int) -> Unit,
    onPicked: (List<PickedMedia>, failed: Int) -> Unit,
    onRemove: (Attachment) -> Unit,
    moveTargets: MoveTargets,
    onMove: (Attachment, SessionId) -> Unit,
    onCleanUp: (Attachment, CleanUpMode?) -> Unit,
    onHighlight: (Attachment, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = attachments.find { it.id == selectedId }
    // Choosing the session to move the selected one to, and whether the other days' talks are shown too
    var moving by rememberSaveable { mutableStateOf(false) }
    var allDays by rememberSaveable { mutableStateOf(false) }
    // Choosing how the selected photo is cleaned up
    var choosingCleanUp by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.session_media_title), style = KotlinConfTheme.typography.h3)
        Text(
            stringResource(Res.string.session_media_hint),
            style = KotlinConfTheme.typography.text2,
            color = KotlinConfTheme.colors.secondaryText,
        )

        if (attachments.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                var photos = 0
                var videos = 0
                attachments.forEach { attachment ->
                    val label = if (attachment.kind == MediaKind.PHOTO) {
                        stringResource(Res.string.session_media_photo, ++photos)
                    } else {
                        stringResource(Res.string.session_media_video, ++videos)
                    }
                    MediaTile(
                        attachment = attachment,
                        label = label,
                        previewPath = attachment.preview?.let(pathOf),
                        selected = attachment.id == selectedId,
                        onHighlight = { onHighlight(attachment, !attachment.highlighted) },
                        onClick = {
                            if (selectedId != null) {
                                selectedId = null
                                moving = false
                                choosingCleanUp = false
                            } else {
                                attachment.viewFiles.mapNotNull(pathOf).takeIf { it.isNotEmpty() }?.let { picker.open(it, attachment.kind) }
                            }
                        },
                        onLongClick = {
                            selectedId = attachment.id
                            moving = false
                            choosingCleanUp = false
                        },
                    )
                }
            }
            if (selected != null && moving) {
                // A photo on the wrong talk: pick the right one, the talks of the same day first
                Text(stringResource(Res.string.session_media_move_title), style = KotlinConfTheme.typography.h4)
                val shown = if (allDays) moveTargets.sameDay + moveTargets.otherDays else moveTargets.sameDay
                if (shown.isEmpty()) {
                    Text(stringResource(Res.string.import_no_options), style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.noteText)
                }
                shown.forEach { session ->
                    val day = if (session in moveTargets.otherDays) DateTimeFormatting.weekdayDate(session.startsAt.date) else null
                    OptionRow(
                        title = session.title,
                        bold = session.isFavorite,
                        detail = listOfNotNull(day, sessionDetail(session, session.isFavorite)).joinToString(" · "),
                        selected = false,
                        onClick = {
                            selectedId = null
                            moving = false
                            onMove(selected, session.id)
                        },
                    )
                }
                if (!allDays && moveTargets.otherDays.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.session_media_move_other_days),
                        style = KotlinConfTheme.typography.text2,
                        color = KotlinConfTheme.colors.accentText,
                        modifier = Modifier.clickable(role = Role.Button, onClick = { allDays = true }).padding(vertical = 12.dp),
                    )
                }
                Button(label = stringResource(Res.string.session_media_cancel), onClick = { moving = false })
            } else if (selected != null && choosingCleanUp) {
                // How a photo of a screen is cleaned up, when the app's own choice is not right
                Text(stringResource(Res.string.session_media_cleanup_title), style = KotlinConfTheme.typography.h4)
                val options = listOf(
                    Triple<CleanUpMode?, String, String>(null, stringResource(Res.string.cleanup_auto), stringResource(Res.string.cleanup_auto_detail)),
                    Triple(CleanUpMode.ORIGINAL, stringResource(Res.string.cleanup_original), stringResource(Res.string.cleanup_original_detail)),
                    Triple(CleanUpMode.DARK, stringResource(Res.string.cleanup_dark), stringResource(Res.string.cleanup_dark_detail)),
                    Triple(CleanUpMode.LIGHT, stringResource(Res.string.cleanup_light), stringResource(Res.string.cleanup_light_detail)),
                    Triple(CleanUpMode.COLOURFUL, stringResource(Res.string.cleanup_colourful), stringResource(Res.string.cleanup_colourful_detail)),
                )
                options.forEach { (mode, title, detail) ->
                    OptionRow(
                        title = title,
                        bold = false,
                        detail = detail,
                        selected = selected.cleanMode == mode,
                        onClick = {
                            selectedId = null
                            choosingCleanUp = false
                            if (selected.cleanMode != mode) onCleanUp(selected, mode)
                        },
                    )
                }
                Button(label = stringResource(Res.string.session_media_cancel), onClick = { choosingCleanUp = false })
            } else if (selected != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        label = stringResource(Res.string.session_media_move),
                        onClick = {
                            moving = true
                            allDays = false
                        },
                        primary = true,
                    )
                    if (selected.kind == MediaKind.PHOTO) {
                        Button(
                            label = stringResource(if (selected.highlighted) Res.string.session_media_unhighlight else Res.string.session_media_highlight),
                            onClick = {
                                selectedId = null
                                onHighlight(selected, !selected.highlighted)
                            },
                        )
                    }
                    if (screenCleanUpAvailable && selected.kind == MediaKind.PHOTO) {
                        Button(
                            label = stringResource(Res.string.session_media_cleanup),
                            onClick = { choosingCleanUp = true },
                        )
                    }
                    Button(
                        label = stringResource(Res.string.session_media_remove),
                        onClick = {
                            selectedId = null
                            onRemove(selected)
                        },
                    )
                    Button(label = stringResource(Res.string.session_media_cancel), onClick = { selectedId = null })
                }
            } else {
                Text(
                    stringResource(Res.string.session_media_actions),
                    style = KotlinConfTheme.typography.text2,
                    color = KotlinConfTheme.colors.secondaryText,
                )
            }
        }

        if (attachments.any { it.cleaned != null }) {
            Text(
                stringResource(Res.string.session_media_cleaned),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.secondaryText,
            )
        }
        if (skippedDuplicates > 0 && !adding) {
            Text(
                pluralStringResource(Res.plurals.import_duplicates, skippedDuplicates, skippedDuplicates),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.secondaryText,
            )
        }
        if (unreadable > 0 && !adding) {
            Text(
                pluralStringResource(Res.plurals.media_unreadable, unreadable, unreadable),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.orangeText,
            )
        }
        if (adding) {
            Text(
                progress?.let { (done, total) -> stringResource(Res.string.media_reading_progress, done, total) }
                    ?: stringResource(Res.string.session_media_adding),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.secondaryText,
            )
        }

        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (picker.hasCamera) {
                PageMenuItem(
                    label = stringResource(Res.string.session_media_take_photo),
                    onClick = { picker.takePhoto { onPicked(it, 0) } },
                    drawableStart = Res.drawable.camera_24,
                )
                PageMenuItem(
                    label = stringResource(Res.string.session_media_record_video),
                    onClick = { picker.recordVideo { onPicked(it, 0) } },
                    drawableStart = Res.drawable.videocam_24,
                )
            }
            PageMenuItem(
                label = stringResource(Res.string.session_media_from_library),
                onClick = { if (!adding) picker.pickFromLibrary(onProgress, onPicked) },
                drawableStart = Res.drawable.photo_library_24,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    attachment: Attachment,
    label: String,
    previewPath: String?,
    selected: Boolean,
    onHighlight: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = KotlinConfTheme.shapes.roundedCornerMd
    Box(
        Modifier
            .size(104.dp)
            .clip(shape)
            .background(KotlinConfTheme.colors.tileBackground)
            .then(if (selected) Modifier.border(3.dp, KotlinConfTheme.colors.primaryBackground, shape) else Modifier)
            .combinedClickable(
                role = Role.Image,
                onClickLabel = label,
                onLongClickLabel = stringResource(Res.string.session_media_move_or_remove),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (previewPath != null) {
            AsyncImage(
                model = previewPath,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(label, style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.secondaryText)
        }
        if (attachment.kind == MediaKind.VIDEO) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.play_video),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 3.dp).size(20.dp),
                )
            }
        } else {
            // The star: tap to feature the photo in the trip report. A separate click target on top of the tile
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(if (attachment.highlighted) Res.string.session_media_unhighlight else Res.string.session_media_highlight),
                        onClick = onHighlight,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (attachment.highlighted) "★" else "☆",
                    style = KotlinConfTheme.typography.h3,
                    color = if (attachment.highlighted) Color(0xFFFFC107) else Color.White,
                )
            }
        }
    }
}
