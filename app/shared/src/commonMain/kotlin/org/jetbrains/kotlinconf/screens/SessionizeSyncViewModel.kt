package org.jetbrains.kotlinconf.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.sync.CodeStatus
import org.jetbrains.kotlinconf.sync.SessionizeSyncState
import kotlin.time.Duration.Companion.seconds

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object Working : SyncUiState
    data class ShowingCode(val code: String) : SyncUiState
    data class Failed(val reason: SyncFailure) : SyncUiState
}

enum class SyncFailure { BadCode, Network, CodeExpired }

@ContributesIntoMap(AppScope::class)
@ViewModelKey
class SessionizeSyncViewModel(
    private val service: ConferenceService,
) : ViewModel() {
    val syncState: StateFlow<SessionizeSyncState?> = service.sessionizeSyncState

    val uiState: StateFlow<SyncUiState>
        field = MutableStateFlow<SyncUiState>(SyncUiState.Idle)

    private var job: Job? = null

    /** Links with a code shown by the web app. */
    fun useCode(code: String) = run {
        val linked = service.linkSessionize(code)
        uiState.value = if (linked) SyncUiState.Idle else SyncUiState.Failed(SyncFailure.BadCode)
    }

    /** Shows a code to enter in the web app, and waits until it has been used. */
    fun showCode() = run {
        val code = service.startSessionizeCode()
        if (code == null) {
            uiState.value = SyncUiState.Failed(SyncFailure.Network)
            return@run
        }
        uiState.value = SyncUiState.ShowingCode(code)
        while (true) {
            delay(2.seconds)
            when (service.sessionizeCodeStatus(code)) {
                CodeStatus.Used -> {
                    service.completeSessionizeLink()
                    uiState.value = SyncUiState.Idle
                    return@run
                }
                CodeStatus.Expired, CodeStatus.Invalid -> {
                    uiState.value = SyncUiState.Failed(SyncFailure.CodeExpired)
                    return@run
                }
                CodeStatus.Active, null -> Unit
            }
        }
    }

    fun syncNow() = run {
        uiState.value = if (service.syncFavorites()) SyncUiState.Idle else SyncUiState.Failed(SyncFailure.Network)
    }

    fun stopSyncing() = run {
        service.unlinkSessionize()
        uiState.value = SyncUiState.Idle
    }

    private fun run(block: suspend () -> Unit) {
        job?.cancel()
        uiState.value = SyncUiState.Working
        job = viewModelScope.launch { block() }
    }
}
