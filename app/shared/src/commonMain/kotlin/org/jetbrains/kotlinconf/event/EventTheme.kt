package org.jetbrains.kotlinconf.event

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.event_agntcon_mark
import org.jetbrains.kotlinconf.ui.theme.Colors
import org.jetbrains.kotlinconf.ui.theme.KotlinConfDarkColors
import org.jetbrains.kotlinconf.ui.theme.KotlinConfLightColors
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme

/**
 * A conference's own look: light and dark colors, and its logo mark (a single-color image with
 * transparency, tinted to suit the background).
 */
class EventTheme(
    val light: Colors,
    val dark: Colors,
    val mark: DrawableResource,
)

/** The conference's logo mark, in the accent color on light backgrounds and white on dark ones. */
@Composable
fun EventLogo(theme: EventTheme, size: Dp, modifier: Modifier = Modifier) {
    val colors = KotlinConfTheme.colors
    Image(
        painter = painterResource(theme.mark),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (colors.isDark) colors.primaryText else colors.primaryBackground),
        modifier = modifier.size(size),
    )
}

/** Colors of events.linuxfoundation.org/agntcon-mcpcon-north-america: deep indigo, rose and mint. */
internal object AgntconTheme {
    private val indigo = Color(0xFF1F213A)
    private val violet = Color(0xFF4B3F9E)
    private val rose = Color(0xFFEA6289)
    private val roseText = Color(0xFFB83A61) // rose dark enough for text on white
    private val roseButton = Color(0xFFD2486F) // rose dark enough for white text
    private val mint = Color(0xFF83FFDE)

    private val gradient = Brush.horizontalGradient(
        0f to indigo,
        0.55f to violet,
        1f to rose,
    )

    val theme = EventTheme(
        light = KotlinConfLightColors.copy(
            primaryBackground = indigo,
            strokeAccent = indigo,
            accentText = roseText,
            purpleText = violet,
            magentaText = roseText,
            pinkText = roseText,
            toggleOn = indigo,
            headerGradient = gradient,
        ),
        dark = KotlinConfDarkColors.copy(
            mainBackground = indigo,
            primaryBackground = roseButton,
            tooltipBackground = Color(0xFF34375C),
            strokeAccent = mint,
            accentText = mint,
            purpleText = Color(0xFFA9A2FF),
            magentaText = Color(0xFFFF8FB0),
            pinkText = rose,
            toggleOn = roseButton,
            headerGradient = gradient,
        ),
        mark = Res.drawable.event_agntcon_mark,
    )
}
