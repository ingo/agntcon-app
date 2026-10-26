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
import org.jetbrains.kotlinconf.media.SlideCheck
import org.jetbrains.kotlinconf.media.SlideTextMatcher
import org.jetbrains.kotlinconf.media.recognizeText
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.media.findOutliers
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
    /**
     * The session the text on the photos names (see [SlideCheck]). When it is not the answer, the screen says "This
     * looks like ..." and offers to move the photos; nothing moves on its own.
     */
    val suggested: SessionId? = null,
    /** Which photos of the group [suggested] is for, by position; null for the whole group. */
    val suggestedItems: Set<Int>? = null,
    /** A photo the attendee took out of the group it was matched to, to add somewhere else or not at all. */
    val movedOut: Boolean = false,
    /** The session preselected because it is the likely one (a bookmarked session that ran long or started early). */
    val likely: SessionId? = null,
) {
    val kept: List<PickedMedia> get() = items

    val isQuestion: Boolean get() = match.needsAnswer
    val matchedSession: SessionCardView? get() = (answer as? ImportAnswer.To)?.let { a -> options.find { it.id == a.session } }
}

/** Photos of a group to offer moving to [session]: [bySlide] when their text says so, else by the photos around them. */
data class MoveFlag(val items: Set<Int>, val session: SessionId, val bySlide: Boolean)

data class ImportState(
    val groups: List<ImportGroup> = emptyList(),
    val attended: Set<SessionId> = emptySet(),
    val saving: Boolean = false,
    val done: Boolean = false,
    /** Picked photos and videos left out because they are already attached (or were picked twice). */
    val duplicates: Int = 0,
    /** True while the picked items are compared with the ones already attached. */
    val checking: Boolean = true,
    /** Chosen items that could not be read from the library. */
    val unreadable: Int = 0,
    /** The event's sessions, to name the one a misplaced photo probably belongs to. */
    val sessions: Map<SessionId, SessionCardView> = emptyMap(),
) {
    /**
     * Photos that are probably in the wrong session (see [findOutliers]), by group: which of its photos, and the session
     * the photos around them went to.
     */
    val outliers: Map<Int, Pair<Set<Int>, SessionId>> by lazy {
        val positions = groups.flatMapIndexed { g, group -> group.items.indices.map { g to it } }
        val items = positions.map { (g, i) ->
            val group = groups[g]
            group.items[i].takenAt?.inZone(ActiveEvent.timeZone) to (group.answer as? ImportAnswer.To)?.session
        }
        findOutliers(items).entries
            .groupBy({ positions[it.key].first }, { positions[it.key].second to it.value })
            .mapValues { (_, flagged) -> flagged.map { it.first }.toSet() to flagged.first().second }
    }

    /** Photos to offer moving, by group: what the slides say, else what the photos around them say. */
    val flags: Map<Int, MoveFlag> by lazy {
        val byPhotos = outliers.mapValues { (_, v) -> MoveFlag(v.first, v.second, bySlide = false) }
        val bySlide = groups.mapIndexedNotNull { i, g ->
            val target = g.suggested ?: return@mapIndexedNotNull null
            if (g.answer == ImportAnswer.To(target)) return@mapIndexedNotNull null
            i to MoveFlag(g.suggestedItems ?: g.items.indices.toSet(), target, bySlide = true)
        }.toMap()
        byPhotos + bySlide
    }

    /**
     * Groups holding photos taken at the very second of photos going to a different session, so the same photo may be
     * about to be in two: group to how many, and the other session. A warning only: photos of a burst share a second.
     */
    val sameMoment: Map<Int, Pair<Int, SessionId>> by lazy {
        fun moment(item: PickedMedia) = item.takenAt?.inZone(ActiveEvent.timeZone)
        val moments = groups.flatMapIndexed { g, group -> group.items.mapNotNull { item -> moment(item)?.let { it to g } } }
        groups.mapIndexedNotNull { g, group ->
            val mine = (group.answer as? ImportAnswer.To)?.session
            val others = group.items.mapNotNull { item ->
                val at = moment(item) ?: return@mapNotNull null
                moments.firstOrNull { (t, other) -> t == at && other != g }
                    ?.let { (_, other) -> (groups[other].answer as? ImportAnswer.To)?.session?.takeIf { it != mine } }
            }
            val target = others.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return@mapIndexedNotNull null
            g to (target.value to target.key)
        }.toMap()
    }

    val unanswered: Int get() = groups.count { it.answer == null }
    val toAdd: Int get() = groups.filter { it.answer is ImportAnswer.To }.sumOf { it.kept.size }
    /** What would be added if the open questions were skipped. */
    val answeredToAdd: Int get() = toAdd
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
    private val handedOver = service.takeImport()
    private var picked: List<PickedMedia> = handedOver.first
    private var finished = false

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    init {
        viewModelScope.launch {
            // Already attached, or picked twice: left out (and their copies deleted) before anything is asked
            val duplicates = service.findDuplicates(picked)
            service.discardPicked(picked.filterIndexed { i, _ -> i in duplicates })
            picked = picked.filterIndexed { i, _ -> i !in duplicates }
            _state.value = build().copy(
                duplicates = duplicates.size,
                checking = false,
                unreadable = handedOver.second,
                sessions = service.exportSnapshot().sessions.associateBy { it.id },
            )
            readSlides()
        }
    }

    /**
     * Reads the text on the photos of every group (see [SlideCheck]) and, when it clearly names another session than the
     * one the group is filed under, remembers it as [ImportGroup.suggested]; a group without an answer gets it
     * preselected. Runs after the screen is up, one photo at a time: reading text is heavy, and a big import has
     * many groups. The attendee's own answers are never changed.
     */
    private fun readSlides() {
        val snapshot = service.exportSnapshot()
        val byId = snapshot.sessions.associateBy { it.id }
        val zone = ActiveEvent.timeZone
        viewModelScope.launch {
            for (group in _state.value.groups) {
                if (group.movedOut || group.items.isEmpty()) continue
                val answered = group.answer
                val current = (answered as? ImportAnswer.To)?.session
                if (answered == ImportAnswer.Skip) continue
                val views = (SlideCheck.candidates(group.from, group.to, snapshot.sessions) + group.options + listOfNotNull(current?.let { byId[it] }))
                    .distinctBy { it.id }
                val currentView = current?.let { byId[it] }
                val order = group.items.indices.sortedWith(compareBy(nullsLast()) { group.items[it].takenAt?.inZone(zone) })
                val finding = SlideCheck.check(
                    order = order,
                    current = current,
                    candidates = views.map { it.id to SlideTextMatcher.candidateText(it) },
                    edgeStart = currentView != null && group.from != null && group.from - currentView.startsAt <= EDGE,
                    edgeEnd = currentView != null && group.to != null && currentView.endsAt - group.to <= EDGE,
                ) { position ->
                    val item = group.items[position]
                    val paths = listOfNotNull(if (item.kind == MediaKind.PHOTO) item.path else item.thumbnailPath)
                    paths.mapNotNull { recognizeText(it) }.joinToString("\n").ifBlank { null }
                } ?: continue
                val flagged = finding.items.map { group.items[it] }
                _state.update { state ->
                    // The group may have moved or changed while reading; find it again by its photos
                    val at = state.groups.indexOfFirst { g -> g.items.any { it === group.items.first() } }
                    val g = state.groups.getOrNull(at) ?: return@update state
                    if (g.answer != answered && g.answer != null) return@update state
                    val positions = g.items.indices.filter { g.items[it] in flagged }.toSet()
                    if (positions.isEmpty()) return@update state
                    val whole = positions.size == g.items.size
                    state.copy(groups = state.groups.mapIndexed { i, x ->
                        if (i != at) x else x.copy(
                            suggested = finding.session,
                            suggestedItems = if (whole) null else positions,
                            answer = x.answer ?: ImportAnswer.To(finding.session),
                        )
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
        fun byTime(list: List<SessionCardView>) = list.distinctBy { it.id }.sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title.lowercase() }))

        val groups = groupMatches(matches, times).map { group: MediaGroup ->
            val groupTimes = group.items.mapNotNull { times[it] }
            val from = groupTimes.minOrNull()
            val to = groupTimes.maxOrNull()
            val options = when (val match = group.match) {
                is MediaMatch.Matched -> around(from, sessions, attended, byId[match.session])
                is MediaMatch.Ask -> match.candidates.mapNotNull { byId[it] }
                is MediaMatch.Between -> byTime(listOfNotNull(match.before, match.after).mapNotNull { byId[it] })
                    .ifEmpty { attendedByTime }
                MediaMatch.NoTime -> attendedByTime
            }
            val likely = (group.match as? MediaMatch.Ask)?.likely
            ImportGroup(
                match = group.match,
                items = group.items.map { picked[it] },
                from = from,
                to = to,
                options = options,
                answer = (group.match as? MediaMatch.Matched)?.let { ImportAnswer.To(it.session) }
                    ?: likely?.let { ImportAnswer.To(it) },
                likely = likely,
            )
        }
        return ImportState(groups = groups, attended = attended)
    }

    /** The sessions to offer when changing a match: the ones running around then, then the attendee's of that day. */
    private fun around(at: LocalDateTime?, sessions: List<SessionCardView>, attended: Set<SessionId>, matched: SessionCardView?): List<SessionCardView> {
        val talks = sessions.filterNot { it.isServiceEvent }
        val nearby = if (at == null) emptyList() else talks.filter { it.startsAt - at <= 30.minutes && at - it.endsAt < 30.minutes }
        val sameDay = if (at == null) emptyList() else talks.filter { it.id in attended && it.startsAt.date == at.date }
        return (listOfNotNull(matched) + nearby + sameDay).distinctBy { it.id }
            .sortedWith(compareBy({ it.startsAt }, { it.endsAt }, { it.title.lowercase() }))
    }

    fun answer(index: Int, answer: ImportAnswer) = _state.update { state ->
        state.copy(groups = state.groups.mapIndexed { i, g -> if (i == index) g.copy(answer = answer, changing = false) else g })
    }

    /**
     * Takes one photo out of its group (it was grouped wrong): the rest keep their session, and the photo gets a card
     * of its own at the end, not added unless the attendee picks a session for it there.
     */
    fun takeOut(groupIndex: Int, itemIndex: Int) = _state.update { state ->
        val group = state.groups.getOrNull(groupIndex) ?: return@update state
        val item = group.items.getOrNull(itemIndex) ?: return@update state
        val at = item.takenAt?.inZone(ActiveEvent.timeZone)
        val snapshot = service.exportSnapshot()
        val byId = snapshot.sessions.associateBy { it.id }
        val previous = (group.answer as? ImportAnswer.To)?.session?.let { byId[it] }
        val options = (around(at, snapshot.sessions, state.attended, null) + listOfNotNull(previous)).distinctBy { it.id }
        val moved = ImportGroup(
            match = MediaMatch.Between(null, null),
            items = listOf(item),
            from = at,
            to = at,
            options = options,
            answer = ImportAnswer.Skip,
            movedOut = true,
        )
        val rest = group.items.filterIndexed { i, _ -> i != itemIndex }
        val groups = state.groups.mapIndexedNotNull { i, g ->
            if (i != groupIndex) g else if (rest.isEmpty()) null else g.copy(items = rest).let {
                // Positions in a partial suggestion no longer fit
                if (it.suggestedItems != null) it.copy(suggested = null, suggestedItems = null) else it
            }
        }
        state.copy(groups = (groups + moved).sortedWith(compareBy(nullsLast()) { it.from }))
    }

    /**
     * Moves the photos flagged as probably misplaced in a group to the session the photos around them went to. A group
     * flagged as a whole just gets that session; otherwise the flagged photos get a card of their own with it.
     */
    fun moveFlagged(groupIndex: Int) = _state.update { state ->
        val (flagged, target) = state.flags[groupIndex]?.let { it.items to it.session } ?: return@update state
        val group = state.groups[groupIndex]
        if (flagged.size == group.items.size) {
            return@update state.copy(groups = state.groups.mapIndexed { i, g ->
                if (i == groupIndex) g.copy(answer = ImportAnswer.To(target), changing = false, suggestedItems = null) else g
            })
        }
        val moved = group.items.filterIndexed { i, _ -> i in flagged }
        val times = moved.mapNotNull { it.takenAt?.inZone(ActiveEvent.timeZone) }
        val snapshot = service.exportSnapshot()
        val card = ImportGroup(
            match = MediaMatch.Matched(target),
            items = moved,
            from = times.minOrNull(),
            to = times.maxOrNull(),
            options = around(times.minOrNull(), snapshot.sessions, state.attended, state.sessions[target]),
            answer = ImportAnswer.To(target),
        )
        val rest = group.copy(items = group.items.filterIndexed { i, _ -> i !in flagged }, suggested = null, suggestedItems = null)
        state.copy(groups = (state.groups.mapIndexed { i, g -> if (i == groupIndex) rest else g } + card)
            .sortedWith(compareBy(nullsLast()) { it.from }))
    }

    fun change(index: Int) = _state.update { state ->
        state.copy(groups = state.groups.mapIndexed { i, g -> if (i == index) g.copy(changing = !g.changing) else g })
    }

    /** Leaves the questions not answered yet out and adds the rest: a big import need not be answered to the end. */
    fun skipUnansweredAndConfirm() {
        _state.update { state -> state.copy(groups = state.groups.map { if (it.answer == null) it.copy(answer = ImportAnswer.Skip) else it }) }
        confirm()
    }

    /**
     * Keeps the photos for the sessions chosen and deletes the rest. Groups are saved one after another, so even a
     * few hundred photos go in without holding everything in memory at once.
     */
    fun confirm() {
        val state = _state.value
        if (state.unanswered > 0 || state.saving || state.checking) return
        finished = true
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            // One photo goes into one session: an item that somehow sits in two groups is added once
            val added = mutableSetOf<PickedMedia>()
            state.groups.forEach { group ->
                when (val answer = group.answer) {
                    is ImportAnswer.To -> service.addAttachments(answer.session, group.items.filter { added.add(it) })
                    else -> service.discardPicked(group.items.filter { it !in added })
                }
            }
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    private companion object {
        /** How close to a session's start or end a group's photos must be for them to be another talk's slides. */
        val EDGE = 12.minutes
    }

    override fun onCleared() {
        // Leaving without adding: the temporary copies go
        if (!finished) service.discardPicked(picked)
    }
}
