package org.jetbrains.kotlinconf.screens

import kotlinx.datetime.LocalDateTime
import org.jetbrains.kotlinconf.TimeProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.media.PickedMedia
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.personal.Thumb
import org.jetbrains.kotlinconf.utils.ErrorLoadingState

@AssistedInject
class SessionViewModel(
    private val service: ConferenceService,
    timeProvider: TimeProvider,
    @Assisted private val sessionId: SessionId,
) : ViewModel() {
    /** The current time in the event's time zone, updated every minute. */
    val now: StateFlow<LocalDateTime> = timeProvider.time

    val session: StateFlow<ErrorLoadingState<SessionCardView>> = service.sessionByIdFlow(sessionId)
        .map { session ->
            if (session != null) ErrorLoadingState.Content(session)
            else ErrorLoadingState.Error
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ErrorLoadingState.Loading)

    val speakers: StateFlow<List<Speaker>> = service.speakersBySessionId(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val note: StateFlow<String> = service.notes
        .map { it.sessionNote(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), service.notes.value.sessionNote(sessionId))

    val rating: StateFlow<SessionRating> = service.ratings
        .map { it[sessionId.id] ?: SessionRating() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), service.ratings.value[sessionId.id] ?: SessionRating())

    /** The attendee's photos and videos on this session, in the order they were added. */
    val attachments: StateFlow<List<Attachment>> = service.attachments
        .map { it[sessionId.id].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), service.attachments.value[sessionId.id].orEmpty())

    private val _addingMedia = MutableStateFlow(false)

    /** True while picked photos or videos are being copied in. */
    val addingMedia: StateFlow<Boolean> = _addingMedia

    /** The absolute path of a stored photo or video, to show or open it. */
    fun mediaPath(file: String): String? = service.mediaFiles?.takeIf { it.isSupported }?.path(file)

    fun onMediaLoading() {
        _addingMedia.value = true
    }

    fun addMedia(picked: List<PickedMedia>) {
        if (picked.isEmpty()) {
            _addingMedia.value = false
            return
        }
        _addingMedia.value = true
        viewModelScope.launch {
            try {
                service.addAttachments(sessionId, picked)
            } finally {
                _addingMedia.value = false
            }
        }
    }

    fun removeMedia(attachment: Attachment) {
        viewModelScope.launch { service.removeAttachment(sessionId, attachment) }
    }

    /** The rooms with a pin on the venue map, to offer "Find on map". */
    val pinnedRooms: StateFlow<Set<String>> = service.mapData
        .map { it?.rooms?.keys ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), service.mapData.value?.rooms?.keys ?: emptySet())

    fun setNote(text: String) {
        viewModelScope.launch { service.setSessionNote(sessionId, text) }
    }

    fun setThumb(thumb: Thumb?) {
        viewModelScope.launch { service.updateRating(sessionId) { it.copy(thumb = thumb) } }
    }

    fun setTakeaway(text: String) {
        viewModelScope.launch { service.updateRating(sessionId) { it.copy(takeaway = text) } }
    }

    fun toggleFavorite(isBookmarked: Boolean) {
        viewModelScope.launch {
            service.setFavorite(sessionId, isBookmarked)
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(sessionId: SessionId): SessionViewModel
    }
}
