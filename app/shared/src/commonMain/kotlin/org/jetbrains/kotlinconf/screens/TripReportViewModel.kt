package org.jetbrains.kotlinconf.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.export.ExportEvent
import org.jetbrains.kotlinconf.export.ExportLabels
import org.jetbrains.kotlinconf.export.ExportSnapshot
import org.jetbrains.kotlinconf.export.ReportInput
import org.jetbrains.kotlinconf.export.ReportProgress
import org.jetbrains.kotlinconf.export.ReportResult
import org.jetbrains.kotlinconf.export.reportBundleBuilder
import org.jetbrains.kotlinconf.export.sessionsForTripReport
import kotlinx.datetime.toInstant
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.isServiceEvent
import org.jetbrains.kotlinconf.media.MediaMatcher
import org.jetbrains.kotlinconf.media.PhotoCleanUp
import org.jetbrains.kotlinconf.export.ReportMedia
import org.jetbrains.kotlinconf.export.tripReportHtml
import org.jetbrains.kotlinconf.media.PickedMedia
import org.jetbrains.kotlinconf.minus
import kotlin.time.Instant
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind

/** One session in the report, with what the attendee kept for it. */
data class TripReportItem(
    val session: SessionCardView,
    val photos: Int,
    val videos: Int,
    val hasNote: Boolean,
    val hasRating: Boolean,
)

data class TripReportSummary(
    val items: List<TripReportItem> = emptyList(),
    /** The size of the attendee's photos and videos in the report, in bytes. */
    val mediaBytes: Long = 0,
    /** False until the first summary is computed, so the page does not flash "nothing here yet". */
    val loaded: Boolean = false,
) {
    val withSlides: Int get() = items.count { it.session.slidesUrl != null }
    val photos: Int get() = items.sumOf { it.photos }
    val videos: Int get() = items.sumOf { it.videos }
}

sealed interface TripReportState {
    data object Idle : TripReportState
    data class Working(val progress: ReportProgress?) : TripReportState
    data class Finished(val result: ReportResult) : TripReportState
}

@ContributesIntoMap(AppScope::class)
@ViewModelKey
class TripReportViewModel(
    private val service: ConferenceService,
) : ViewModel() {
    /** Whether this platform can build the zip; the web build only shares the text. */
    val canDownload: Boolean = reportBundleBuilder != null

    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<TripReportSummary> = service.exportSnapshots
        .mapLatest { summarize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripReportSummary())

    /** How far the clean-up of the photos has got; null when there is nothing to show. */
    val photoCleanUp: StateFlow<PhotoCleanUp?> = service.photoCleanUp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _state = MutableStateFlow<TripReportState>(TripReportState.Idle)
    val state: StateFlow<TripReportState> = _state

    private var job: Job? = null

    init {
        service.cleanUpScreenPhotos()
    }

    /** Hands photos and videos picked from the library to the review screen. */
    fun startImport(picked: List<PickedMedia>, unreadable: Int = 0) = service.startImport(picked, unreadable)

    /**
     * When to look for photos in the library: every talk of the event, from a few minutes before it starts to a
     * quarter of an hour after it ends, as exact moments. Breaks and all-day items are left out.
     */
    fun scanWindows(): List<ClosedRange<Instant>> {
        val zone = ActiveEvent.timeZone
        return service.exportSnapshot().sessions
            .filterNot { it.isServiceEvent || it.endsAt - it.startsAt > MediaMatcher.ALL_DAY }
            .map { (it.startsAt.toInstant(zone) - MediaMatcher.EARLY)..(it.endsAt.toInstant(zone) + MediaMatcher.LATE) }
            .sortedBy { it.start }
            .fold(mutableListOf<ClosedRange<Instant>>()) { merged, range ->
                val last = merged.lastOrNull()
                if (last != null && range.start <= last.endInclusive) {
                    merged[merged.lastIndex] = last.start..maxOf(last.endInclusive, range.endInclusive)
                } else {
                    merged += range
                }
                merged
            }
    }

    /** Library ids of the photos and videos already attached, so a scan does not copy them again. */
    fun knownSourceIds(): Set<String> = service.attachments.value.values.flatten().mapNotNullTo(mutableSetOf()) { it.sourceId }

    /** Builds the zip and hands it to [share] when it is ready. */
    fun download(event: ExportEvent, labels: ExportLabels, share: (ReportResult.Done) -> Unit) {
        val builder = reportBundleBuilder ?: return
        if (job?.isActive == true) return
        val media = service.mediaFiles
        _state.value = TripReportState.Working(null)
        job = viewModelScope.launch {
            val input = ReportInput(service.exportSnapshot(), event, labels) { file -> media?.path(file) ?: file }
            val result = builder.build(input) { progress -> _state.value = TripReportState.Working(progress) }
            _state.value = TripReportState.Finished(result)
            if (result is ReportResult.Done) share(result)
        }
    }

    private suspend fun summarize(snapshot: ExportSnapshot): TripReportSummary {
        val sessions = sessionsForTripReport(snapshot.sessions, snapshot.notes, snapshot.ratings, snapshot.attachments)
            .sortedWith(compareBy({ it.startsAt }, { it.title }))
        val media = service.mediaFiles
        var bytes = 0L
        val items = sessions.map { session ->
            val attachments: List<Attachment> = snapshot.attachments[session.id.id].orEmpty()
            if (media != null) attachments.forEach { bytes += media.size(it.file) }
            TripReportItem(
                session = session,
                photos = attachments.count { it.kind == MediaKind.PHOTO },
                videos = attachments.count { it.kind == MediaKind.VIDEO },
                hasNote = snapshot.notes.sessionNote(session.id).isNotBlank(),
                hasRating = snapshot.ratings[session.id.id]?.isEmpty == false,
            )
        }
        return TripReportSummary(items, bytes, loaded = true)
    }
}

/** A file size for people: "840 KB", "3.2 MB", "120 MB", "1.4 GB". */
fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    fun oneDecimal(value: Double): String {
        val tenths = kotlin.math.round(value * 10).toLong()
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
    return when {
        bytes < mb -> "${kotlin.math.ceil(bytes / kb).toLong()} KB"
        bytes < 10 * mb -> "${oneDecimal(bytes / mb)} MB"
        bytes < gb -> "${kotlin.math.round(bytes / mb).toLong()} MB"
        else -> "${oneDecimal(bytes / gb)} GB"
    }
}
