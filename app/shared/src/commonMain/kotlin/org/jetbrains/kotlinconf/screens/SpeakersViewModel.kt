package org.jetbrains.kotlinconf.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.directory.Company
import org.jetbrains.kotlinconf.directory.buildCompanies
import org.jetbrains.kotlinconf.directory.search
import org.jetbrains.kotlinconf.event.ActiveEvent
import kotlinx.coroutines.flow.map
import org.jetbrains.kotlinconf.utils.ErrorLoadingState
import org.jetbrains.kotlinconf.utils.containsDiacritics
import org.jetbrains.kotlinconf.utils.performSearch
import org.jetbrains.kotlinconf.utils.removeDiacritics

data class SpeakerWithHighlights(
    val speaker: Speaker,
    val nameHighlights: List<IntRange> = emptyList(),
    val titleHighlights: List<IntRange> = emptyList(),
)

/** A speaker the attendee marked as met, with their note. */
data class MetPerson(
    val speaker: Speaker,
    val note: String,
    val nameHighlights: List<IntRange> = emptyList(),
    val titleHighlights: List<IntRange> = emptyList(),
)

data class CompanyWithHighlights(
    val company: Company,
    val nameHighlights: List<IntRange> = emptyList(),
)

@ContributesIntoMap(AppScope::class)
@ViewModelKey
class SpeakersViewModel(
    private val service: ConferenceService,
) : ViewModel() {
    private var loading = MutableStateFlow(false)
    private var searchText = MutableStateFlow("")
    private val showcaseOnly = MutableStateFlow(false)

    fun setSearchText(searchText: String) {
        this.searchText.value = searchText
    }

    fun setShowcaseOnly(value: Boolean) {
        showcaseOnly.value = value
    }

    /** Companies that speak, matching the search and, when asked, with a talk in the exhibitor showcase. */
    val companies: StateFlow<List<CompanyWithHighlights>> = combine(
        service.speakers, service.agenda, searchText, showcaseOnly,
    ) { speakers, agenda, searchText, showcaseOnly ->
        val sessions = agenda.flatMap { it.timeSlots }.flatMap { it.sessions }
        buildCompanies(speakers, sessions, ActiveEvent.showcaseRoom)
            .filter { !showcaseOnly || it.showcaseSessions.isNotEmpty() }
            .search(searchText)
            .map { CompanyWithHighlights(it, nameHighlights = highlights(it.name, searchText)) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Whether the event's speakers name their company, which decides if the Companies tab is shown. */
    val hasCompanies: StateFlow<Boolean> = service.speakers
        .map { speakers -> speakers.any { it.company.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), service.speakers.value.any { it.company.isNotBlank() })

    /** The speakers marked as met, A to Z, matching the search in their name, line or note. */
    val metPeople: StateFlow<List<MetPerson>> = combine(
        service.speakers, service.metSpeakers, service.notes, searchText,
    ) { speakers, met, notes, searchText ->
        speakers.filter { it.id in met }
            .sortedBy { it.name.lowercase() }
            .map { MetPerson(it, notes.speakerNote(it.id)) }
            .let { people ->
                if (searchText.isBlank()) people else people.performSearch(
                    searchText = searchText,
                    produceResult = { person, (name, title, _) ->
                        MetPerson(person.speaker, person.note, name, title)
                    },
                    selectors = listOf({ it.speaker.name }, { it.speaker.position }, { it.note }),
                )
            }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            try {
                service.loadConferenceData()
            } finally {
                loading.value = false
            }
        }
    }

    val speakers: StateFlow<ErrorLoadingState<List<SpeakerWithHighlights>>> = combine(
        service.speakers, searchText, loading
    ) { speakers, searchText, loading ->
        when {
            loading -> ErrorLoadingState.Loading

            searchText.isBlank() -> {
                val allSpeakers = speakers.map { SpeakerWithHighlights(it) }
                if (allSpeakers.isNotEmpty()) {
                    ErrorLoadingState.Content(allSpeakers)
                } else {
                    ErrorLoadingState.Error
                }
            }

            else -> {
                val searchResults = speakers.performSearch(
                    searchText = searchText,
                    produceResult = { speaker, (nameMatches, titleMatches) ->
                        SpeakerWithHighlights(speaker, nameMatches, titleMatches)
                    },
                    selectors = listOf({ it.name }, { it.position }),
                )
                ErrorLoadingState.Content(searchResults)
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ErrorLoadingState.Loading)
}

/** Where the search text occurs in [text], accents ignored, for highlighting. */
internal fun highlights(text: String, search: String): List<IntRange> {
    if (search.isBlank()) return emptyList()
    val exact = search.containsDiacritics()
    val target = if (exact) text else text.removeDiacritics()
    return Regex.escape(search.trim()).toRegex(RegexOption.IGNORE_CASE).findAll(target).map { it.range }.toList()
}
