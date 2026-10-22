package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SessionState
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.directory.Company
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.arrow_left_24
import org.jetbrains.kotlinconf.generated.resources.arrow_up_right_24
import org.jetbrains.kotlinconf.generated.resources.company_error_not_found
import org.jetbrains.kotlinconf.generated.resources.company_sessions
import org.jetbrains.kotlinconf.generated.resources.company_showcase
import org.jetbrains.kotlinconf.generated.resources.company_speakers
import org.jetbrains.kotlinconf.generated.resources.company_title
import org.jetbrains.kotlinconf.generated.resources.company_website
import org.jetbrains.kotlinconf.generated.resources.navigate_back
import org.jetbrains.kotlinconf.generated.resources.schedule_in_x_minutes
import org.jetbrains.kotlinconf.ui.AdaptiveDetailLayout
import org.jetbrains.kotlinconf.ui.components.HorizontalDivider
import org.jetbrains.kotlinconf.ui.components.MainHeaderTitleBar
import org.jetbrains.kotlinconf.ui.components.PageMenuItem
import org.jetbrains.kotlinconf.ui.components.SpeakerCard
import org.jetbrains.kotlinconf.ui.components.TalkCard
import org.jetbrains.kotlinconf.ui.components.TalkStatus
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.components.TopMenuButton
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.ErrorLoadingContent
import org.jetbrains.kotlinconf.utils.topInsetPadding

/** A company: its website, its speakers and the sessions they give, exhibitor showcase talks first. */
@Composable
fun CompanyDetailScreen(
    name: String,
    onBack: () -> Unit,
    onSession: (SessionId) -> Unit,
    onSpeaker: (SpeakerId) -> Unit,
    viewModel: CompanyDetailViewModel =
        assistedMetroViewModel<CompanyDetailViewModel, CompanyDetailViewModel.Factory> { create(name) },
) {
    val state = viewModel.company.collectAsStateWithLifecycle().value
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxSize()
            .background(color = KotlinConfTheme.colors.mainBackground)
            .padding(topInsetPadding())
    ) {
        ErrorLoadingContent(
            state = state,
            errorMessage = stringResource(Res.string.company_error_not_found),
            modifier = Modifier.fillMaxSize().weight(1f),
        ) { company ->
            AdaptiveDetailLayout(
                compactHeader = {
                    MainHeaderTitleBar(
                        title = stringResource(Res.string.company_title),
                        startContent = {
                            TopMenuButton(
                                icon = Res.drawable.arrow_left_24,
                                contentDescription = stringResource(Res.string.navigate_back),
                                onClick = onBack,
                            )
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = KotlinConfTheme.colors.strokePale)
                },
                compactContentHeader = { CompanyName(company, Modifier.padding(vertical = 24.dp)) },
                largeContentHeader = { CompanyName(company, Modifier.padding(bottom = 24.dp, top = 6.dp)) },
                unifiedContent = {
                    Website(company, uriHandler::openUri)
                    Sessions(company, viewModel, onSession)
                    People(company, onSpeaker)
                },
                largeMainContent = { Sessions(company, viewModel, onSession) },
                largeSideContent = {
                    Website(company, uriHandler::openUri)
                    People(company, onSpeaker)
                },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CompanyName(company: Company, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = company.name,
            style = KotlinConfTheme.typography.h2,
            color = KotlinConfTheme.colors.primaryText,
            selectable = true,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun Website(company: Company, onOpen: (String) -> Unit) {
    val url = company.url ?: return
    PageMenuItem(
        label = stringResource(Res.string.company_website),
        onClick = { onOpen(url) },
        drawableEnd = Res.drawable.arrow_up_right_24,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun Sessions(company: Company, viewModel: CompanyDetailViewModel, onSession: (SessionId) -> Unit) {
    val showcase = company.showcaseSessions
    val others = company.sessions.filter { it !in showcase }
    if (showcase.isNotEmpty()) {
        SessionList(stringResource(Res.string.company_showcase), showcase, viewModel, onSession)
    }
    if (others.isNotEmpty()) {
        SessionList(stringResource(Res.string.company_sessions), others, viewModel, onSession)
    }
}

@Composable
private fun SessionList(
    title: String,
    sessions: List<SessionCardView>,
    viewModel: CompanyDetailViewModel,
    onSession: (SessionId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, style = KotlinConfTheme.typography.h3, modifier = Modifier.semantics { heading() })
        sessions.forEach { session ->
            TalkCard(
                title = session.title,
                titleHighlights = emptyList(),
                bookmarked = session.isFavorite,
                onBookmark = { viewModel.onBookmark(session.id, it) },
                tags = session.tags,
                tagHighlights = emptyList(),
                speakers = session.speakerLine,
                speakerHighlights = emptyList(),
                location = session.locationLine,
                lightning = session.isLightning,
                time = session.fullTimeline,
                timeNote = session.startsInMinutes?.let { stringResource(Res.string.schedule_in_x_minutes, it) },
                status = when (session.state) {
                    SessionState.Live -> TalkStatus.Live
                    SessionState.Past -> TalkStatus.Past
                    SessionState.Upcoming -> TalkStatus.Upcoming
                },
                feedbackContent = null,
                onClick = { onSession(session.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun People(company: Company, onSpeaker: (SpeakerId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.company_speakers),
            style = KotlinConfTheme.typography.h3,
            modifier = Modifier.semantics { heading() },
        )
        company.speakers.forEach { speaker ->
            SpeakerCard(
                name = speaker.name,
                title = speaker.position,
                photoUrl = speaker.photoUrl,
                compact = true,
                onClick = { onSpeaker(speaker.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}
