package org.jetbrains.kotlinconf.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.media.MediaGroup
import org.jetbrains.kotlinconf.media.MediaMatch
import org.jetbrains.kotlinconf.media.MediaMatcher
import org.jetbrains.kotlinconf.media.PickedMedia
import org.jetbrains.kotlinconf.media.SlideTextMatcher
import org.jetbrains.kotlinconf.media.recognizeText
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.media.groupMatches
import org.jetbrains.kotlinconf.minus
import kotlin.time.Duration.Companion.minutes

/** What the attendee decided for a group of photos: a session, or not to add them. */
sealed interface ImportAnswer {
    data class To(val session: SessionId) : ImportAnswer
    data object Skip : ImportAnswer
}

/** One group on the review screen: photos with the same match, what can be picked for them, and the answer. */
data class ImportGroup(
    val match: MediaMatch,
    val items: List<PickedMedia>,
    val from: LocalDateTime?,
    val to: LocalDateTime?,
    /** The sessions to offer: the candidates of a question, or the ones around the time to change a match. */
    val options: List<SessionCardView>,
    val answer: ImportAnswer?,
    /** A matched group whose session the attendee wants to change. */
    val changing: Boolean = false,
    /** The session whose title or speakers the text on the photos matches (see [SlideTextMatcher]). */
    val suggested: SessionId? = null,
) {
    val isQuestion: Boolean get() = match.needsAnswer
    val matchedSession: SessionCardView? get() = (answer as? ImportAnswer.To)?.let { a -> options.find { it.id == a.session } }
}

data class ImportState(
    val groups: List<ImportGroup> = emptyList(),
    val attended: Set<SessionId> = emptySet(),
    val saving: Boolean = false,
    val done: Boolean = false,
    /** Picked photos and videos left out because they are already attached (or were picked twice). */
    val duplicates: Int = 0,
    /** True while the picked items are compared with the ones already attached. */
    val checking: Boolean = true,
) {
    val unanswered: Int get() = groups.count { it.answer == null }
    val toAdd: Int get() = groups.filter { it.answer is ImportAnswer.To }.sumOf { it.items.size }
    val total: Int get() = groups.sumOf { it.items.size }
}

/**
 * The review screen for photos and videos picked from the library: each one is matched to a session by the time it
 * was taken (see [MediaMatcher]); the attendee confirms, changes or answers, and nothing is kept before they do.
 */
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class MediaImportViewModel(
    private val service: ConferenceService,
) : ViewModel() {
    private var picked: List<PickedMedia> = service.takeImport()
    private var finished = false

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    init {
        viewModelScope.launch {
            // Already attached, or picked twice: left out (and their copies deleted) before anything is asked
            val duplicates = service.findDuplicates(picked)
            service.discardPicked(picked.filterIndexed { i, _ -> i in duplicates })
            picked = picked.filterIndexed { i, _ -> i !in duplicates }
            _state.value = build().copy(duplicates = duplicates.size, checking = false)
            readSlides()
        }
    }

    /**
     * Reads the text on the photos of each question and, when it clearly names one of the candidates, preselects it.
     * Runs after the screen is up; a few photos per group are enough and keep it quick.
     */
    private fun readSlides() {
        val byId = service.exportSnapshot().sessions.associateBy { it.id }
        _state.value.groups.forEachIndexed { index, group ->
            if (group.match !is MediaMatch.Ask) return@forEachIndexed
            viewModelScope.launch {
                val paths = group.items.mapNotNull { if (it.kind == MediaKind.PHOTO) it.path else it.thumbnailPath }.take(MAX_PHOTOS_READ)
                val text = paths.mapNotNull { recognizeText(it) }.joinToString("\n")
                if (text.isBlank()) return@launch
                val candidates = group.options.mapNotNull { byId[it.id] }.map { it.id to "${it.title} ${it.speakerLine}" }
                val suggested = SlideTextMatcher.best(text, candidates) ?: return@launch
                _state.update { state ->
                    state.copy(groups = state.groups.mapIndexed { i, g ->
                        if (i != index) g
                        else g.copy(suggested = suggested, answer = g.answer ?: ImportAnswer.To(suggested))
                    })
                }
            }
        }
    }

    private fun build(): ImportState {
        val snapshot = service.exportSnapshot()
        val sessions = snapshot.sessions
        val byId = sessions.associateBy { it.id }
        val attended = sessions.filter { it.isFavorite }.map { it.id }.toSet() +
            snapshot.notes.sessions.keys.map(::SessionId) +
            snapshot.ratings.filterValues { !it.isEmpty }.keys.map(::SessionId) +
            snapshot.attachments.filterValues { it.isNotEmpty() }.keys.map(::SessionId)
        val times = picked.map { it.takenAt?.inZone(ActiveEvent.timeZone) }
        val matches = times.map { MediaMatcher.match(it, sessions, attended) }
        val attendedByTime = sessions.filter { it.id in attended && !it.isServiceEvent }.sortedBy { it.startsAt }

        val groups = groupMatches(matches, times).map { group: MediaGroup ->
            val groupTimes = group.items.mapNotNull { times[it] }
            val from = groupTimes.minOrNull()
            val to = groupTimes.maxOrNull()
            val options = when (val match = group.match) {
                is MediaMatch.Matched -> around(from, sessions, attended, byId[match.session])
                is MediaMatch.Ask -> match.candidates.mapNotNull { byId[it] }
                is MediaMatch.Between -> listOfNotNull(match.before, match.after).mapNotNull { byId[it] }
                    .ifEmpty { attendedByTime }
                MediaMatch.NoTime -> attendedByTime
            }
            ImportGroup(
                match = group.match,
                items = group.items.map { picked[it] },
                from = from,
                to = to,
                options = options,
                answer = (group.match as? MediaMatch.Matched)?.let { ImportAnswer.To(it.session) },
            )
        }
        return ImportState(groups = groups, attended = attended)
    }

    /** The sessions to offer when changing a match: the ones running around then, then the attendee's of that day. */
    private fun around(at: LocalDateTime?, sessions: List<SessionCardView>, attended: Set<SessionId>, matched: SessionCardView?): List<SessionCardView> {
        val talks = sessions.filterNot { it.isServiceEvent }
        val nearby = if (at == null) emptyList() else talks.filter { it.startsAt - at <= 30.minutes && at - it.endsAt < 30.minutes }
        val sameDay = if (at == null) emptyList() else talks.filter { it.id in attended && it.startsAt.date == at.date }
        return (listOfNotNull(matched) + nearby.sortedBy { it.id !in attended } + sameDay.sortedBy { it.startsAt })
            .distinctBy { it.id }
    }

    fun answer(index: Int, answer: ImportAnswer) = _state.update { state ->
        state.copy(groups = state.groups.mapIndexed { i, g -> if (i == index) g.copy(answer = answer, changing = false) else g })
    }

    fun change(index: Int) = _state.update { state ->
        state.copy(groups = state.groups.mapIndexed { i, g -> if (i == index) g.copy(changing = !g.changing) else g })
    }

    /** Keeps the photos for the sessions chosen and deletes the rest. */
    fun confirm() {
        val state = _state.value
        if (state.unanswered > 0 || state.saving || state.checking) return
        finished = true
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            state.groups.forEach { group ->
                when (val answer = group.answer) {
                    is ImportAnswer.To -> service.addAttachments(answer.session, group.items)
                    else -> service.discardPicked(group.items)
                }
            }
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    private companion object {
        const val MAX_PHOTOS_READ = 4
    }

    override fun onCleared() {
        // Leaving without adding: the temporary copies go
        if (!finished) service.discardPicked(picked)
    }
}
