package org.jetbrains.kotlinconf.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.TimeProvider
import kotlinx.coroutines.flow.combine
import org.jetbrains.kotlinconf.SessionId

@ContributesIntoMap(AppScope::class)
@ViewModelKey
class MyScheduleViewModel(
    private val service: ConferenceService,
    timeProvider: TimeProvider,
) : ViewModel() {
    val days: StateFlow<List<MyScheduleDay>> = service.agenda
        .map(::buildMySchedule)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildMySchedule(service.agenda.value))

    /** "What's up now", following the clock (null after the conference). */
    val upNext: StateFlow<UpNext?> = combine(days, timeProvider.time) { days, now -> upNext(days, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), upNext(days.value, timeProvider.now()))

    val syncLinked: StateFlow<Boolean> = service.sessionizeSyncState
        .map { it?.linked == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBookmark(sessionId: SessionId, bookmarked: Boolean) {
        viewModelScope.launch { service.setFavorite(sessionId, bookmarked) }
    }
}
