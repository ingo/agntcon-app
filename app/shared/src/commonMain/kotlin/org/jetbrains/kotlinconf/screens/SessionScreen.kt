package org.jetbrains.kotlinconf.screens

import org.jetbrains.kotlinconf.event.ActiveEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SessionState
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.export.Exporter
import org.jetbrains.kotlinconf.export.rememberExporter
import org.jetbrains.kotlinconf.media.rememberMediaPicker
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.calendar_add_24
import org.jetbrains.kotlinconf.generated.resources.session_add_to_calendar
import org.jetbrains.kotlinconf.generated.resources.session_find_on_map
import org.jetbrains.kotlinconf.generated.resources.session_share
import org.jetbrains.kotlinconf.generated.resources.share_24
import org.jetbrains.kotlinconf.generated.resources.arrow_left_24
import org.jetbrains.kotlinconf.generated.resources.arrow_up_right_24
import org.jetbrains.kotlinconf.generated.resources.down_24
import org.jetbrains.kotlinconf.generated.resources.navigate_back
import org.jetbrains.kotlinconf.generated.resources.play_video
import org.jetbrains.kotlinconf.generated.resources.schedule_in_x_minutes
import org.jetbrains.kotlinconf.generated.resources.session_room_state_description_collapsed
import org.jetbrains.kotlinconf.generated.resources.session_room_state_description_expanded
import org.jetbrains.kotlinconf.generated.resources.session_screen_error
import org.jetbrains.kotlinconf.generated.resources.session_slides
import org.jetbrains.kotlinconf.generated.resources.session_title
import org.jetbrains.kotlinconf.generated.resources.session_watch_video
import org.jetbrains.kotlinconf.generated.resources.slides
import org.jetbrains.kotlinconf.ui.AdaptiveDetailLayout
import org.jetbrains.kotlinconf.ui.components.Action
import org.jetbrains.kotlinconf.ui.components.ActionSize
import org.jetbrains.kotlinconf.ui.components.HorizontalDivider
import org.jetbrains.kotlinconf.ui.components.MainHeaderTitleBar
import org.jetbrains.kotlinconf.ui.components.PageMenuItem
import org.jetbrains.kotlinconf.ui.components.PageTitle
import org.jetbrains.kotlinconf.ui.components.SpeakerCard
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.components.TopMenuButton
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.ErrorLoadingContent
import org.jetbrains.kotlinconf.utils.ErrorLoadingState
import org.jetbrains.kotlinconf.utils.LocalWindowSize
import org.jetbrains.kotlinconf.utils.WindowSize
import org.jetbrains.kotlinconf.utils.topInsetPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    sessionId: SessionId,
    onBack: () -> Unit,
    onSpeaker: (SpeakerId) -> Unit,
    onNavigateToMap: (String) -> Unit,
    onWatchVideo: (String) -> Unit,
    viewModel: SessionViewModel =
        assistedMetroViewModel<SessionViewModel, SessionViewModel.Factory> { create(sessionId) }
) {
    val sessionState = viewModel.session.collectAsStateWithLifecycle().value
    val speakers = viewModel.speakers.collectAsStateWithLifecycle().value

    val now by viewModel.now.collectAsStateWithLifecycle()
    val exporter = rememberExporter()
    ErrorLoadingContent(
        state = sessionState,
        errorMessage = stringResource(Res.string.session_screen_error),
        modifier = Modifier.fillMaxSize()
            .background(color = KotlinConfTheme.colors.mainBackground)
            .padding(topInsetPadding()),
    ) { session ->

        AdaptiveDetailLayout(
            compactHeader = {
                MainHeaderTitleBar(
                    title = stringResource(Res.string.session_title),
                    startContent = {
                        TopMenuButton(
                            icon = Res.drawable.arrow_left_24,
                            contentDescription = stringResource(Res.string.navigate_back),
                            onClick = onBack,
                        )
                    },
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = KotlinConfTheme.colors.strokePale
                )
            },
            compactContentHeader = {
                // When it starts, so you can tell at a glance you're at the right session
                SessionTimingBanner(session, now, Modifier.padding(top = 16.dp))
                Title(session, viewModel, Modifier.padding(vertical = 24.dp))
            },
            largeContentHeader = {
                SessionTimingBanner(session, now, Modifier.padding(bottom = 16.dp))
                Title(session, viewModel, Modifier.padding(bottom = 24.dp))
            },
            unifiedContent = {
                SlidesLink(session)
                VideoLink(session, onWatchVideo)
                SessionActions(session, exporter)
                Speakers(speakers, onSpeaker)
                RoomSection(session.locationLine, onNavigateToMap)
                Description(session.description)
                PersonalSection(viewModel)
            },
            largeMainContent = {
                if (session.description.isNotBlank()) {
                    Description(session.description)
                } else {
                    Speakers(speakers, onSpeaker)
                }
            },
            largeSideContent = {
                SlidesLink(session)
                VideoLink(session, onWatchVideo)
                SessionActions(session, exporter)
                if (session.description.isNotBlank()) {
                    Speakers(speakers, onSpeaker)
                }
                RoomSection(session.locationLine, onNavigateToMap)
                PersonalSection(viewModel)
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun Description(description: String) {
    Text(
        text = description,
        style = KotlinConfTheme.typography.text1,
        selectable = true,
    )
}

@Composable
private fun Speakers(
    speakers: List<Speaker>,
    onSpeaker: (SpeakerId) -> Unit
) {
    speakers.forEach { speaker ->
        SpeakerCard(
            name = speaker.name,
            title = speaker.position,
            photoUrl = speaker.photoUrl,
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            onClick = { onSpeaker(speaker.id) }
        )
    }
}

@Composable
private fun VideoLink(
    session: SessionCardView,
    onWatchVideo: (String) -> Unit
) {
    if (session.videoUrl != null) {
        PageMenuItem(
            label = stringResource(Res.string.session_watch_video),
            onClick = { onWatchVideo(session.videoUrl) },
            drawableStart = Res.drawable.play_video,
            drawableEnd = Res.drawable.arrow_up_right_24,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun SlidesLink(session: SessionCardView) {
    val slidesUrl = session.slidesUrl ?: return
    val uriHandler = LocalUriHandler.current
    PageMenuItem(
        label = stringResource(Res.string.session_slides),
        onClick = { uriHandler.openUri(slidesUrl) },
        drawableStart = Res.drawable.slides,
        drawableEnd = Res.drawable.arrow_up_right_24,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/** Add the session to the calendar, or share it. */
@Composable
private fun SessionActions(session: SessionCardView, exporter: Exporter) {
    PageMenuItem(
        label = stringResource(Res.string.session_add_to_calendar),
        onClick = { exporter.addSessionToCalendar(session.id) },
        drawableStart = Res.drawable.calendar_add_24,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    PageMenuItem(
        label = stringResource(Res.string.session_share),
        onClick = { exporter.shareSession(session.id) },
        drawableStart = Res.drawable.share_24,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/**
 * The attendee's private rating (where the event has no voting of its own), notes, photos and videos,
 * kept on the device.
 */
@Composable
private fun PersonalSection(viewModel: SessionViewModel) {
    val note by viewModel.note.collectAsStateWithLifecycle()
    val rating by viewModel.rating.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val addingMedia by viewModel.addingMedia.collectAsStateWithLifecycle()
    val skippedDuplicates by viewModel.skippedDuplicates.collectAsStateWithLifecycle()
    val mediaProgress by viewModel.mediaProgress.collectAsStateWithLifecycle()
    val unreadable by viewModel.unreadable.collectAsStateWithLifecycle()
    val moveTargets by viewModel.moveTargets.collectAsStateWithLifecycle()
    val picker = rememberMediaPicker()
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        RatingBlock(rating = rating, onThumb = viewModel::setThumb, onTakeaway = viewModel::setTakeaway)
        NoteBlock(stored = note, onSave = viewModel::setNote)
        if (picker != null) {
            MediaBlock(
                picker = picker,
                attachments = attachments,
                adding = addingMedia,
                skippedDuplicates = skippedDuplicates,
                progress = mediaProgress,
                unreadable = unreadable,
                pathOf = viewModel::mediaPath,
                onProgress = viewModel::onMediaProgress,
                onPicked = viewModel::addMedia,
                onRemove = viewModel::removeMedia,
                moveTargets = moveTargets,
                onMove = viewModel::moveMedia,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Title(
    session: SessionCardView,
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier,
) {
    PageTitle(
        time = session.fullTimeline,
        title = session.title,
        lightning = session.isLightning,
        tags = session.tags,
        bookmarked = session.isFavorite,
        onBookmark = { viewModel.toggleFavorite(it) },
        modifier = modifier,
        timeNote = session.startsInMinutes?.let { count ->
            stringResource(Res.string.schedule_in_x_minutes, count)
        },
        isLive = session.state == SessionState.Live,
        large = LocalWindowSize.current != WindowSize.Compact,
    )
}

@Composable
private fun RoomSection(
    roomName: String,
    onNavigateToMap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val mapViewModel: MapViewModel = metroViewModel()
    val mapState = mapViewModel.state.collectAsStateWithLifecycle().value

    val mapContent = (mapState as? ErrorLoadingState.Content)?.data
    val mapData = mapContent?.mapData
    val room = mapData?.rooms?.get(roomName)

    val largeScreen = LocalWindowSize.current == WindowSize.Large

    Column(modifier = modifier.padding(vertical = 16.dp)) {
        if (room != null) {
            if (largeScreen) {
                Text(
                    text = roomName,
                    style = KotlinConfTheme.typography.h3,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                val iconRotation by animateFloatAsState(if (isExpanded) 180f else 0f)
                val stateDesc = stringResource(
                    if (isExpanded) Res.string.session_room_state_description_expanded
                    else Res.string.session_room_state_description_collapsed
                )
                Action(
                    label = roomName,
                    icon = Res.drawable.down_24,
                    size = ActionSize.Large,
                    enabled = true,
                    onClick = { isExpanded = !isExpanded },
                    iconRotation = iconRotation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = stateDesc
                        }
                )
            }

            AnimatedVisibility(
                visible = largeScreen || isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StaticMap(
                    mapData = mapData,
                    room = room,
                    svgsByPath = mapContent.svgsByPath,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(KotlinConfTheme.shapes.roundedCornerMd)
                        .background(KotlinConfTheme.colors.tileBackground)
                        .clickable {
                            onNavigateToMap(roomName)
                        }
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                )
            }

            PageMenuItem(
                label = stringResource(Res.string.session_find_on_map),
                onClick = { onNavigateToMap(roomName) },
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Text(
                text = roomName,
                style = KotlinConfTheme.typography.h3,
            )
        }
    }
}
