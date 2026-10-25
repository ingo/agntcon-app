package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.camera_24
import org.jetbrains.kotlinconf.generated.resources.photo_library_24
import org.jetbrains.kotlinconf.generated.resources.play_video
import org.jetbrains.kotlinconf.generated.resources.session_media_actions
import org.jetbrains.kotlinconf.generated.resources.session_media_adding
import org.jetbrains.kotlinconf.generated.resources.session_media_cancel
import org.jetbrains.kotlinconf.generated.resources.session_media_from_library
import org.jetbrains.kotlinconf.generated.resources.session_media_hint
import org.jetbrains.kotlinconf.generated.resources.session_media_photo
import org.jetbrains.kotlinconf.generated.resources.session_media_record_video
import org.jetbrains.kotlinconf.generated.resources.session_media_remove
import org.jetbrains.kotlinconf.generated.resources.session_media_take_photo
import org.jetbrains.kotlinconf.generated.resources.session_media_title
import org.jetbrains.kotlinconf.generated.resources.session_media_video
import org.jetbrains.kotlinconf.generated.resources.videocam_24
import org.jetbrains.kotlinconf.media.MediaPicker
import org.jetbrains.kotlinconf.media.PickedMedia
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.ui.components.Button
import org.jetbrains.kotlinconf.ui.components.PageMenuItem
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme

/**
 * The attendee's photos, screenshots and videos on a session: a grid of thumbnails (tap to view, touch
 * and hold to remove) and the camera and library buttons. Not shown where the app keeps no files.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MediaBlock(
    picker: MediaPicker,
    attachments: List<Attachment>,
    adding: Boolean,
    pathOf: (String) -> String?,
    onLoading: () -> Unit,
    onPicked: (List<PickedMedia>) -> Unit,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = attachments.find { it.id == selectedId }

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
                        onClick = {
                            if (selectedId != null) selectedId = null
                            else pathOf(attachment.file)?.let { picker.open(it, attachment.kind) }
                        },
                        onLongClick = { selectedId = attachment.id },
                    )
                }
            }
            if (selected != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        label = stringResource(Res.string.session_media_remove),
                        onClick = {
                            selectedId = null
                            onRemove(selected)
                        },
                        primary = true,
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

        if (adding) {
            Text(
                stringResource(Res.string.session_media_adding),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.secondaryText,
            )
        }

        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (picker.hasCamera) {
                PageMenuItem(
                    label = stringResource(Res.string.session_media_take_photo),
                    onClick = { picker.takePhoto(onPicked) },
                    drawableStart = Res.drawable.camera_24,
                )
                PageMenuItem(
                    label = stringResource(Res.string.session_media_record_video),
                    onClick = { picker.recordVideo(onPicked) },
                    drawableStart = Res.drawable.videocam_24,
                )
            }
            PageMenuItem(
                label = stringResource(Res.string.session_media_from_library),
                onClick = { picker.pickFromLibrary(onLoading, onPicked) },
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
                onLongClickLabel = stringResource(Res.string.session_media_remove),
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
        }
    }
}
