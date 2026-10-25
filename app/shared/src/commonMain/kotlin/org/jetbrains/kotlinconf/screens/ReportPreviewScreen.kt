package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.ScreenWithTitle
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.export.ExportSnapshot
import org.jetbrains.kotlinconf.export.nameWithCompany
import org.jetbrains.kotlinconf.export.sessionsForTripReport
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.export_my_notes
import org.jetbrains.kotlinconf.generated.resources.export_recording
import org.jetbrains.kotlinconf.generated.resources.export_slides
import org.jetbrains.kotlinconf.generated.resources.export_speakers
import org.jetbrains.kotlinconf.generated.resources.play_video
import org.jetbrains.kotlinconf.generated.resources.trip_report_empty
import org.jetbrains.kotlinconf.generated.resources.trip_report_preview
import org.jetbrains.kotlinconf.generated.resources.trip_report_preview_hint
import org.jetbrains.kotlinconf.media.rememberMediaPicker
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.personal.Thumb
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.DateTimeFormatting

/** One session of the report as it will read, with its photos and videos. */
class PreviewSession(
    val session: SessionCardView,
    val speakers: String,
    val rating: String?,
    val note: String,
    val media: List<Pair<Attachment, PreviewMedia>>,
)

/** A photo or video in the preview: the small picture to show and the files to open (cleaned-up first). */
class PreviewMedia(val thumbnail: String?, val full: List<String>)

@ContributesIntoMap(AppScope::class)
@ViewModelKey
class ReportPreviewViewModel(private val service: ConferenceService) : ViewModel() {
    val days: StateFlow<List<Pair<LocalDate, List<PreviewSession>>>?> = service.exportSnapshots
        .map { build(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun build(snap: ExportSnapshot): List<Pair<LocalDate, List<PreviewSession>>> {
        val media = service.mediaFiles
        fun path(file: String?) = file?.let { media?.path(it) }
        return sessionsForTripReport(snap.sessions, snap.notes, snap.ratings, snap.attachments)
            .sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title }))
            .map { s ->
                val rating = snap.ratings[s.id.id]
                PreviewSession(
                    session = s,
                    speakers = s.speakerIds.mapNotNull { snap.speakers[it] }.joinToString(", ") { it.nameWithCompany() },
                    rating = rating?.takeIf { !it.isEmpty }?.let { r ->
                        listOfNotNull(
                            r.thumb?.let { if (it == Thumb.UP) "👍" else "👎" },
                            r.takeaway.takeIf { it.isNotBlank() }?.let { "“$it”" },
                        ).joinToString(" ")
                    },
                    note = snap.notes.sessionNote(s.id),
                    media = snap.attachments[s.id.id].orEmpty().sortedBy { it.addedAt }.map { a ->
                        a to PreviewMedia(thumbnail = path(a.preview), full = a.viewFiles.mapNotNull { path(it) })
                    },
                )
            }
            .groupBy { it.session.startsAt.date }
            .toList()
    }
}

/**
 * The trip report as it will read, inside the app: each session with its speakers, rating, notes, slides and a grid of
 * small thumbnails. Tapping a thumbnail opens it full screen; swiping goes on through that session's photos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportPreviewScreen(onBack: () -> Unit, viewModel: ReportPreviewViewModel = metroViewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val picker = rememberMediaPicker()
    val uriHandler = LocalUriHandler.current

    ScreenWithTitle(title = stringResource(Res.string.trip_report_preview), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 24.dp)) {
            val loaded = days ?: return@Column
            if (loaded.isEmpty()) {
                Text(stringResource(Res.string.trip_report_empty), color = KotlinConfTheme.colors.noteText)
                return@Column
            }
            Text(stringResource(Res.string.trip_report_preview_hint), color = KotlinConfTheme.colors.noteText)
            loaded.forEach { (date, sessions) ->
                Text(
                    DateTimeFormatting.weekdayDate(date),
                    style = KotlinConfTheme.typography.h2,
                    modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                )
                sessions.forEach { item -> PreviewCard(item, onOpen = { paths, kind -> picker?.open(paths, kind) }, onLink = uriHandler::openUri) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewCard(item: PreviewSession, onOpen: (List<String>, MediaKind) -> Unit, onLink: (String) -> Unit) {
    val s = item.session
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(KotlinConfTheme.colors.tileBackground)
            .padding(16.dp),
    ) {
        Text(s.title, style = KotlinConfTheme.typography.h3)
        Text(
            listOfNotNull(DateTimeFormatting.timeToTime(s.startsAt, s.endsAt), s.locationLine.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = KotlinConfTheme.typography.text2,
            color = KotlinConfTheme.colors.noteText,
        )
        if (item.speakers.isNotBlank()) {
            Text("${stringResource(Res.string.export_speakers)}: ${item.speakers}", style = KotlinConfTheme.typography.text2)
        }
        item.rating?.let { Text(it, style = KotlinConfTheme.typography.text2) }
        listOfNotNull(
            s.slidesUrl?.let { stringResource(Res.string.export_slides) to it },
            s.videoUrl?.let { stringResource(Res.string.export_recording) to it },
        ).forEach { (label, url) ->
            Text(
                label,
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.accentText,
                modifier = Modifier.clickable(role = Role.Button) { onLink(url) }.padding(vertical = 6.dp),
            )
        }
        if (item.note.isNotBlank()) {
            Text(stringResource(Res.string.export_my_notes), style = KotlinConfTheme.typography.text2, color = KotlinConfTheme.colors.noteText)
            Text(item.note, style = KotlinConfTheme.typography.text2)
        }
        if (item.media.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                val photos = item.media.filter { it.first.kind == MediaKind.PHOTO }
                item.media.forEach { (attachment, media) ->
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(KotlinConfTheme.shapes.roundedCornerSm)
                            .background(KotlinConfTheme.colors.mainBackground)
                            .clickable(role = Role.Image) {
                                if (attachment.kind == MediaKind.VIDEO) {
                                    onOpen(media.full.take(1), MediaKind.VIDEO)
                                } else {
                                    // This photo first, then the session's others, to swipe through
                                    val start = photos.indexOfFirst { it.first.id == attachment.id }.coerceAtLeast(0)
                                    onOpen((photos.drop(start) + photos.take(start)).mapNotNull { it.second.full.firstOrNull() }, MediaKind.PHOTO)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        media.thumbnail?.let {
                            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        if (attachment.kind == MediaKind.VIDEO) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(painterResource(Res.drawable.play_video), contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(13.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
