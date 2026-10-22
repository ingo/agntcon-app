package org.jetbrains.kotlinconf.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.directory.Company
import org.jetbrains.kotlinconf.directory.buildCompanies
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.utils.ErrorLoadingState

@AssistedInject
class CompanyDetailViewModel(
    private val service: ConferenceService,
    @Assisted private val name: String,
) : ViewModel() {
    val company: StateFlow<ErrorLoadingState<Company>> = combine(service.speakers, service.agenda) { speakers, agenda ->
        val sessions = agenda.flatMap { it.timeSlots }.flatMap { it.sessions }
        buildCompanies(speakers, sessions, ActiveEvent.showcaseRoom)
            .find { it.name.equals(name, ignoreCase = true) }
            ?.let { ErrorLoadingState.Content(it) } ?: ErrorLoadingState.Error
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ErrorLoadingState.Loading)

    fun onBookmark(sessionId: SessionId, bookmarked: Boolean) {
        viewModelScope.launch { service.setFavorite(sessionId, bookmarked) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(name: String): CompanyDetailViewModel
    }
}
