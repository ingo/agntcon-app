package org.jetbrains.kotlinconf.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.SpeakerLink
import org.jetbrains.kotlinconf.contactLink
import org.jetbrains.kotlinconf.directory.Company
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.arrow_right_24
import org.jetbrains.kotlinconf.generated.resources.company_session_count
import org.jetbrains.kotlinconf.generated.resources.company_showcase_count
import org.jetbrains.kotlinconf.ui.components.Icon
import org.jetbrains.kotlinconf.ui.components.SpeakerCard
import org.jetbrains.kotlinconf.ui.components.Text
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme

/** A company in the directory: its name, how many speakers and sessions, and its showcase talks. */
@Composable
fun CompanyCard(
    company: Company,
    nameHighlights: List<IntRange>,
    showcaseOnly: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .clickable(onClick = onClick)
            .background(KotlinConfTheme.colors.tileBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = highlighted(company.name, nameHighlights),
                style = KotlinConfTheme.typography.h3,
            )
            val sessions = company.sessions.size
            val counts = buildList {
                add(company.speakers.joinToString(", ") { it.name })
                if (sessions > 0) add(pluralStringResource(Res.plurals.company_session_count, sessions, sessions))
                val showcase = company.showcaseSessions.size
                if (showcase > 0 && showcaseOnly) add(pluralStringResource(Res.plurals.company_showcase_count, showcase, showcase))
            }
            Text(
                text = counts.joinToString(" · "),
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.secondaryText,
                maxLines = 2,
            )
        }
        Icon(
            painter = painterResource(Res.drawable.arrow_right_24),
            contentDescription = null,
            tint = KotlinConfTheme.colors.primaryText,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A speaker the attendee met: the speaker, their note, and a link to reach them. */
@Composable
fun MetPersonCard(
    person: MetPerson,
    onClick: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speaker = person.speaker
    val link: SpeakerLink? = speaker.contactLink
    Column(
        modifier
            .clip(KotlinConfTheme.shapes.roundedCornerMd)
            .background(KotlinConfTheme.colors.tileBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpeakerCard(
            name = speaker.name,
            nameHighlights = person.nameHighlights,
            title = speaker.position,
            titleHighlights = person.titleHighlights,
            photoUrl = speaker.photoUrl,
            compact = true,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        if (person.note.isNotBlank()) {
            Text(
                text = person.note,
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.longText,
                maxLines = 4,
            )
        }
        if (link != null) {
            Text(
                text = link.title,
                style = KotlinConfTheme.typography.text2,
                color = KotlinConfTheme.colors.accentText,
                modifier = Modifier
                    .clip(KotlinConfTheme.shapes.roundedCornerMd)
                    .clickable { onOpenLink(link.url) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun highlighted(text: String, highlights: List<IntRange>): AnnotatedString =
    if (highlights.isEmpty()) AnnotatedString(text) else buildAnnotatedString {
        append(text)
        highlights.forEach { range ->
            if (!range.isEmpty() && range.last in text.indices) {
                addStyle(
                    SpanStyle(
                        color = KotlinConfTheme.colors.primaryTextWhiteFixed,
                        background = KotlinConfTheme.colors.primaryBackground,
                    ),
                    range.first,
                    range.last + 1,
                )
            }
        }
    }
