package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.ScreenWithTitle
import org.jetbrains.kotlinconf.ScrollToTopHandler
import org.jetbrains.kotlinconf.URLs
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.event.EventLogo
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.about_conference_title
import org.jetbrains.kotlinconf.generated.resources.arrow_up_right_24
import org.jetbrains.kotlinconf.ui.components.PageMenuItem
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme

@Composable
fun AboutConference(
    onWebsiteLink: (() -> Unit)?,
    onBack: () -> Unit,
    viewModel: AboutConferenceViewModel = metroViewModel(),
) {
    val conferenceInfo = viewModel.conferenceInfo.collectAsStateWithLifecycle().value
    val scrollState = rememberScrollState()
    ScrollToTopHandler(scrollState)
    ScreenWithTitle(
        title = stringResource(Res.string.about_conference_title),
        onBack = onBack,
        contentScrollState = scrollState,
    ) {
        if (conferenceInfo != null) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = conferenceInfo.aboutHeader,
                    style = KotlinConfTheme.typography.h1,
                    modifier = Modifier.semantics { heading() }
                )
                Text(text = conferenceInfo.aboutDescription)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val eventTheme = ActiveEvent.theme
        if (eventTheme != null) {
            EventLogo(eventTheme, size = 120.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(64.dp))
        }

        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val websiteLabel = URLs.CONFERENCE_HOMEPAGE?.let(::websiteLabel)
            if (onWebsiteLink != null && websiteLabel != null) {
                PageMenuItem(
                    label = websiteLabel,
                    drawableEnd = Res.drawable.arrow_up_right_24,
                    onClick = onWebsiteLink,
                )
            }
        }
    }
}

/** "https://www.example.com/" -> "example.com" */
private fun websiteLabel(url: String): String =
    url.substringAfter("://").substringBefore('/').removePrefix("www.")
