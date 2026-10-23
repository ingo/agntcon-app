package org.jetbrains.kotlinconf.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinconf.ui.generated.resources.UiRes
import org.jetbrains.kotlinconf.ui.generated.resources.action_error_reload
import org.jetbrains.kotlinconf.ui.generated.resources.info_28
import org.jetbrains.kotlinconf.ui.generated.resources.loading
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.ui.theme.PreviewHelper
import org.jetbrains.kotlinconf.ui.theme.UI
import androidx.compose.ui.tooling.preview.PreviewLightDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MinorError(
    message: String,
    modifier: Modifier = Modifier,
) {
    ErrorText(
        message,
        modifier
            .fillMaxWidth()
            .padding(32.dp)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
            }
    )
}

@Composable
fun MajorError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(UiRes.drawable.info_28),
            contentDescription = null,
            tint = KotlinConfTheme.colors.secondaryText,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        ErrorText(
            message = message,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Assertive
            }
        )
    }
}

@Composable
fun NormalErrorWithLoading(
    message: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    loadingText: String = stringResource(UiRes.string.loading),
    retryText: String = stringResource(UiRes.string.action_error_reload),
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Loading(enabled = isLoading)

        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            isLoading,
            transitionSpec = {
                val animIn = fadeIn(
                    animationSpec = tween(220, delayMillis = 100)
                ) + expandVertically(
                    expandFrom = Alignment.CenterVertically,
                    animationSpec = tween(100, easing = EaseInCubic)
                )
                val animOut = fadeOut(animationSpec = tween(90))
                animIn togetherWith animOut
            },
            modifier = Modifier.fillMaxWidth(),
        ) { loadingState ->
            if (loadingState) {
                ErrorText(loadingText)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorText(message)
                    if (onRetry != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(retryText, onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        message,
        color = KotlinConfTheme.colors.secondaryText,
        modifier = modifier.widthIn(max = 220.dp),
        style = KotlinConfTheme.typography.text1.copy(
            textAlign = TextAlign.Center,
        )
    )
}

/** A ring of dots with a brighter one travelling around it; faded and still when [enabled] is false. */
@Composable
fun Loading(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dots = 8
    val progress = if (enabled) {
        val transition = rememberInfiniteTransition(label = "loading")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = dots.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "loading-progress",
        ).value
    } else {
        0f
    }
    val color = KotlinConfTheme.colors.primaryText

    Canvas(modifier = modifier.size(48.dp)) {
        val ringRadius = size.minDimension / 2 - 5.dp.toPx()
        val dotRadius = 4.dp.toPx()
        for (i in 0 until dots) {
            val angle = i * (2 * PI / dots) - PI / 2
            // Distance behind the bright dot, 0 (bright) to 1 (faint)
            val behind = ((progress - i) % dots + dots) % dots / dots
            val alpha = if (enabled) 1f - 0.8f * behind else 0.2f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(
                    center.x + ringRadius * cos(angle).toFloat(),
                    center.y + ringRadius * sin(angle).toFloat(),
                ),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun LoadingEnabledPreview() = PreviewHelper {
    Loading(enabled = true)
}

@PreviewLightDark
@Composable
private fun LoadingDisabledPreview() = PreviewHelper {
    Loading(enabled = false)
}

@PreviewLightDark
@Composable
private fun NormalErrorLoadingPreview() = PreviewHelper {
    NormalErrorWithLoading(
        message = "Error message",
        isLoading = true,
        loadingText = "Loading...",
        retryText = "Retry",
        onRetry = {}
    )
}

@PreviewLightDark
@Composable
private fun NormalErrorInteractivePreview() = PreviewHelper {
    var scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    NormalErrorWithLoading(
        message = "Error message",
        isLoading = loading,
        loadingText = "Loading...",
        retryText = "Retry",
        onRetry = {
            scope.launch {
                loading = true
                delay(1000.milliseconds)
                loading = false
            }
        }
    )
}

@PreviewLightDark
@Composable
private fun NormalErrorIdlePreview() = PreviewHelper {
    NormalErrorWithLoading(
        message = "Error message",
        isLoading = false,
        loadingText = "Loading...",
        retryText = "Retry"
    )
}

@PreviewLightDark
@Composable
private fun MinorErrorPreview() = PreviewHelper {
    MinorError(
        message = "Minor error message"
    )
}

@PreviewLightDark
@Composable
private fun MajorErrorPreview() = PreviewHelper {
    MajorError(
        message = "Major error message"
    )
}
