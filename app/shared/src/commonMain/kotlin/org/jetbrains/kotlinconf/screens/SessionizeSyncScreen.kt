package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.ScreenWithTitle
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.sync_code_hint
import org.jetbrains.kotlinconf.generated.resources.sync_error_code
import org.jetbrains.kotlinconf.generated.resources.sync_error_expired
import org.jetbrains.kotlinconf.generated.resources.sync_error_network
import org.jetbrains.kotlinconf.generated.resources.sync_have_code_steps
import org.jetbrains.kotlinconf.generated.resources.sync_have_code_title
import org.jetbrains.kotlinconf.generated.resources.sync_intro
import org.jetbrains.kotlinconf.generated.resources.sync_last_synced
import org.jetbrains.kotlinconf.generated.resources.sync_link
import org.jetbrains.kotlinconf.generated.resources.sync_linked_text
import org.jetbrains.kotlinconf.generated.resources.sync_linked_title
import org.jetbrains.kotlinconf.generated.resources.sync_no_code_steps
import org.jetbrains.kotlinconf.generated.resources.sync_no_code_title
import org.jetbrains.kotlinconf.generated.resources.sync_not_synced_yet
import org.jetbrains.kotlinconf.generated.resources.sync_now
import org.jetbrains.kotlinconf.generated.resources.sync_show_code
import org.jetbrains.kotlinconf.generated.resources.sync_stop
import org.jetbrains.kotlinconf.generated.resources.sync_title
import org.jetbrains.kotlinconf.generated.resources.sync_waiting
import org.jetbrains.kotlinconf.ui.components.Button
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.DateTimeFormatting
import kotlin.time.Instant

private const val CODE_LENGTH = 6

/** Links this app's bookmarks with the event's Sessionize web app ("Sync favorites" there). */
@Composable
fun SessionizeSyncScreen(
    onBack: () -> Unit,
    viewModel: SessionizeSyncViewModel = metroViewModel(),
) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val working = uiState == SyncUiState.Working

    ScreenWithTitle(title = stringResource(Res.string.sync_title), onBack = onBack) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 24.dp),
        ) {
            val failure = (uiState as? SyncUiState.Failed)?.reason

            if (syncState?.linked == true) {
                Heading(stringResource(Res.string.sync_linked_title))
                Text(stringResource(Res.string.sync_linked_text), color = KotlinConfTheme.colors.longText)
                val lastSynced = syncState?.lastSyncedAt
                Text(
                    if (lastSynced != null) {
                        stringResource(Res.string.sync_last_synced, formatTime(lastSynced))
                    } else {
                        stringResource(Res.string.sync_not_synced_yet)
                    },
                    color = KotlinConfTheme.colors.noteText,
                )
                FailureText(failure)
                Button(stringResource(Res.string.sync_now), onClick = viewModel::syncNow, primary = true, enabled = !working)
                Button(stringResource(Res.string.sync_stop), onClick = viewModel::stopSyncing, enabled = !working)
                return@Column
            }

            val host = ActiveEvent.sessionizeAppUrl?.substringAfter("://") ?: ""
            Text(stringResource(Res.string.sync_intro, host), color = KotlinConfTheme.colors.longText)

            Heading(stringResource(Res.string.sync_have_code_title))
            Text(stringResource(Res.string.sync_have_code_steps), color = KotlinConfTheme.colors.longText)
            var code by rememberSaveable { mutableStateOf("") }
            CodeField(code, onChange = { code = it.filter(Char::isDigit).take(CODE_LENGTH) })
            if (failure == SyncFailure.BadCode) FailureText(failure)
            Button(
                stringResource(Res.string.sync_link),
                onClick = { viewModel.useCode(code) },
                primary = true,
                enabled = code.length == CODE_LENGTH && !working,
            )

            Heading(stringResource(Res.string.sync_no_code_title))
            Text(stringResource(Res.string.sync_no_code_steps), color = KotlinConfTheme.colors.longText)
            val shown = uiState as? SyncUiState.ShowingCode
            if (shown != null) {
                Text(
                    shown.code.chunked(3).joinToString(" "),
                    style = KotlinConfTheme.typography.h1,
                    modifier = Modifier.semantics { contentDescription = shown.code.toList().joinToString(" ") },
                )
                Text(stringResource(Res.string.sync_waiting), color = KotlinConfTheme.colors.noteText)
            } else {
                if (failure == SyncFailure.Network || failure == SyncFailure.CodeExpired) FailureText(failure)
                Button(stringResource(Res.string.sync_show_code), onClick = viewModel::showCode, enabled = !working)
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = KotlinConfTheme.typography.h3,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun FailureText(failure: SyncFailure?) {
    val message = when (failure) {
        SyncFailure.BadCode -> Res.string.sync_error_code
        SyncFailure.Network -> Res.string.sync_error_network
        SyncFailure.CodeExpired -> Res.string.sync_error_expired
        null -> return
    }
    Text(stringResource(message), color = KotlinConfTheme.colors.orangeText)
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit) {
    val colors = KotlinConfTheme.colors
    val hint = stringResource(Res.string.sync_code_hint)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = KotlinConfTheme.typography.h2.copy(color = colors.primaryText),
        cursorBrush = SolidColor(colors.primaryText),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.strokeHalf, KotlinConfTheme.shapes.roundedCornerSm)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = hint },
        decorationBox = { field ->
            if (value.isEmpty()) {
                Text(hint, style = KotlinConfTheme.typography.h2, color = colors.placeholderText)
            }
            field()
        },
    )
}

private fun formatTime(epochMillis: Long): String =
    DateTimeFormatting.time(Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault()))
