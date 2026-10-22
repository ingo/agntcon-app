package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.session_my_notes
import org.jetbrains.kotlinconf.generated.resources.session_my_notes_hint
import org.jetbrains.kotlinconf.generated.resources.session_my_rating
import org.jetbrains.kotlinconf.generated.resources.session_rating_thumb_down
import org.jetbrains.kotlinconf.generated.resources.session_rating_thumb_up
import org.jetbrains.kotlinconf.generated.resources.session_takeaway_hint
import org.jetbrains.kotlinconf.generated.resources.thumb_down_24
import org.jetbrains.kotlinconf.generated.resources.thumb_up_24
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.personal.Thumb
import org.jetbrains.kotlinconf.ui.components.Icon
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme

private val FieldShape = RoundedCornerShape(12.dp)

/** How long typing pauses before a note is saved. Leaving the screen saves at once. */
private const val SAVE_DELAY_MS = 500L

/**
 * A text field that saves what is typed: shortly after typing pauses and when the screen goes away.
 * [stored] is what is saved now; it fills the field until the user starts typing.
 */
@Composable
fun AutoSaveField(
    stored: String,
    onSave: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(stored) }
    var edited by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(stored) }
    val latestText by rememberUpdatedState(text)
    val latestSave by rememberUpdatedState(onSave)

    // The stored value can arrive after the screen is up (cold start); take it until the user types
    LaunchedEffect(stored) {
        if (!edited) {
            text = stored
            saved = stored
        }
    }
    LaunchedEffect(text) {
        if (edited && text != saved) {
            delay(SAVE_DELAY_MS)
            saved = text
            latestSave(text)
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (edited && latestText != saved) latestSave(latestText) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(KotlinConfTheme.colors.tileBackground)
            .heightIn(min = minHeight)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = if (singleLine) it.replace("\n", "") else it
                edited = true
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            singleLine = singleLine,
            textStyle = KotlinConfTheme.typography.text1.copy(color = KotlinConfTheme.colors.primaryText),
            cursorBrush = SolidColor(KotlinConfTheme.colors.primaryText),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
        )
        if (text.isEmpty()) {
            Text(
                text = hint,
                style = KotlinConfTheme.typography.text1,
                // placeholderText is 1.95:1 on this background; secondaryText is about 4.5:1
                color = KotlinConfTheme.colors.secondaryText,
            )
        }
    }
}

/** A private note: a heading and a multi-line field. */
@Composable
fun NoteBlock(
    stored: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.session_my_notes),
    hint: String = stringResource(Res.string.session_my_notes_hint),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = KotlinConfTheme.typography.h3)
        AutoSaveField(stored = stored, onSave = onSave, hint = hint, minHeight = 96.dp)
    }
}

/** A private rating: thumbs up or down, and a one-line takeaway. */
@Composable
fun RatingBlock(
    rating: SessionRating,
    onThumb: (Thumb?) -> Unit,
    onTakeaway: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.session_my_rating),
                style = KotlinConfTheme.typography.h3,
                modifier = Modifier.weight(1f),
            )
            ThumbButton(
                selected = rating.thumb == Thumb.UP,
                description = stringResource(Res.string.session_rating_thumb_up),
                icon = Res.drawable.thumb_up_24,
                onClick = { onThumb(if (rating.thumb == Thumb.UP) null else Thumb.UP) },
            )
            ThumbButton(
                selected = rating.thumb == Thumb.DOWN,
                description = stringResource(Res.string.session_rating_thumb_down),
                icon = Res.drawable.thumb_down_24,
                onClick = { onThumb(if (rating.thumb == Thumb.DOWN) null else Thumb.DOWN) },
            )
        }
        AutoSaveField(
            stored = rating.takeaway,
            onSave = onTakeaway,
            hint = stringResource(Res.string.session_takeaway_hint),
            singleLine = true,
        )
    }
}

@Composable
private fun ThumbButton(
    selected: Boolean,
    description: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(FieldShape)
            .background(if (selected) KotlinConfTheme.colors.primaryBackground else KotlinConfTheme.colors.tileBackground)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) KotlinConfTheme.colors.primaryTextWhiteFixed else KotlinConfTheme.colors.primaryText,
            modifier = Modifier.size(24.dp),
        )
    }
}
