package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.ScreenWithTitle
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.about_app_description_sessionize
import org.jetbrains.kotlinconf.generated.resources.about_app_link_github
import org.jetbrains.kotlinconf.generated.resources.about_app_link_licenses
import org.jetbrains.kotlinconf.generated.resources.about_app_link_rate
import org.jetbrains.kotlinconf.generated.resources.about_app_privacy
import org.jetbrains.kotlinconf.generated.resources.about_app_title
import org.jetbrains.kotlinconf.generated.resources.about_app_unofficial
import org.jetbrains.kotlinconf.generated.resources.app_version
import org.jetbrains.kotlinconf.generated.resources.arrow_up_right_24
import org.jetbrains.kotlinconf.ui.components.PageMenuItem
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.getStoreUrl

@Composable
fun AboutAppScreen(
    onBack: () -> Unit,
    onGitHubRepo: () -> Unit,
    onRateApp: () -> Unit,
    onLicenses: () -> Unit,
) {
    ScreenWithTitle(
        title = stringResource(Res.string.about_app_title),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(Res.string.about_app_description_sessionize, ActiveEvent.name),
                    color = KotlinConfTheme.colors.longText,
                )
                Text(
                    stringResource(Res.string.about_app_unofficial),
                    color = KotlinConfTheme.colors.longText,
                )
                Text(
                    stringResource(Res.string.about_app_privacy),
                    color = KotlinConfTheme.colors.longText,
                )
            }

            PageMenuItem(
                stringResource(Res.string.about_app_link_github),
                drawableEnd = Res.drawable.arrow_up_right_24,
                onClick = onGitHubRepo,
            )

            val storeUrlAvailable = remember { getStoreUrl() != null }
            if (storeUrlAvailable) {
                PageMenuItem(
                    stringResource(Res.string.about_app_link_rate),
                    drawableEnd = Res.drawable.arrow_up_right_24,
                    onClick = onRateApp,
                )
            }

            PageMenuItem(stringResource(Res.string.about_app_link_licenses), onClick = onLicenses)

            Spacer(Modifier.height(8.dp))

            val clipboardManager = LocalClipboardManager.current
            val appVersion = stringResource(resource = Res.string.app_version)
            Text(
                text = appVersion,
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.primaryText,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(KotlinConfTheme.shapes.roundedCornerMd)
                    .clickable { clipboardManager.setText(AnnotatedString(appVersion)) }
                    .padding(16.dp)
            )
        }
    }
}
