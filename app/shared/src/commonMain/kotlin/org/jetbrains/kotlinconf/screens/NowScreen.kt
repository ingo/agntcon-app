package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.nav_destination_now
import org.jetbrains.kotlinconf.ui.components.HorizontalDivider
import org.jetbrains.kotlinconf.ui.components.MainHeaderTitleBar
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.topInsetPadding

/** "What's up now": your bookmarked sessions from the current time. The app's home tab. */
@Composable
fun NowScreen(
    onSession: (SessionId) -> Unit,
    onTripReport: (() -> Unit)? = null,
    viewModel: MyScheduleViewModel = metroViewModel(),
) {
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    var freeWindow by remember { mutableStateOf<Pair<LocalDateTime, LocalDateTime>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(KotlinConfTheme.colors.mainBackground)
            .padding(topInsetPadding())
    ) {
        MainHeaderTitleBar(title = stringResource(Res.string.nav_destination_now))
        HorizontalDivider(thickness = 1.dp, color = KotlinConfTheme.colors.strokePale)

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            UpNextContent(
                upNext = upNext,
                now = now,
                onSession = onSession,
                onFree = { freeWindow = it.startsAt to it.endsAt },
                onBookmark = viewModel::setBookmark,
                onTripReport = onTripReport,
            )
            val window = freeWindow
            val day = upNext?.day
            if (window != null && day != null) {
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
