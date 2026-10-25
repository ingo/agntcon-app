package org.jetbrains.kotlinconf.export

import org.jetbrains.kotlinconf.ShareHandler
import org.jetbrains.kotlinconf.SessionCardView
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.Speaker
import org.jetbrains.kotlinconf.SpeakerId
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating
import kotlin.time.Clock

/** Everything the exports are built from, as it is right now. */
class ExportSnapshot(
    val sessions: List<SessionCardView>,
    val speakers: Map<SpeakerId, Speaker>,
    val notes: PersonalNotes,
    val ratings: Map<String, SessionRating>,
    val met: Set<SpeakerId>,
    /** Photos and videos by session id. */
    val attachments: Map<String, List<Attachment>> = emptyMap(),
)

/**
 * The share and calendar actions of the app: builds the text or file from the current [snapshot]
 * and hands it to the platform's share sheet.
 */
class Exporter(
    private val snapshot: () -> ExportSnapshot,
    private val share: ShareHandler,
    private val event: ExportEvent,
    private val labels: ExportLabels,
) {
    private val bookmarked get() = snapshot().sessions.filter { it.isFavorite }

    /** One session as a calendar file, which the share sheet offers to add to the calendar. */
    fun addSessionToCalendar(id: SessionId) {
        val snap = snapshot()
        val session = snap.sessions.find { it.id == id } ?: return
        share.shareFile(
            fileName = icsFileName(event, "session-${id.id}"),
            mimeType = ICS_MIME_TYPE,
            content = buildIcs(listOf(session), snap.speakers, event, Clock.System.now()),
        )
    }

    fun shareSession(id: SessionId) {
        val snap = snapshot()
        val session = snap.sessions.find { it.id == id } ?: return
        share.shareText(session.title, sessionShareText(session, snap.speakers, event, labels))
    }

    /** All bookmarked sessions as one calendar file. */
    fun addScheduleToCalendar() {
        val snap = snapshot()
        share.shareFile(
            fileName = icsFileName(event, "schedule"),
            mimeType = ICS_MIME_TYPE,
            content = buildIcs(snap.sessions.filter { it.isFavorite }, snap.speakers, event, Clock.System.now()),
        )
    }

    fun shareSchedule() {
        share.shareText(labels.scheduleHeading, scheduleShareText(bookmarked, event, labels))
    }

    fun shareTripReport() {
        val snap = snapshot()
        share.shareText(
            "${labels.tripReportHeading}: ${event.name}",
            tripReportMarkdown(
                bookmarked = sessionsForTripReport(snap.sessions, snap.notes, snap.ratings, snap.attachments),
                speakersById = snap.speakers,
                notes = snap.notes,
                ratings = snap.ratings,
                met = snap.met,
                event = event,
                labels = labels,
            ),
        )
    }

    val hasBookmarks: Boolean get() = bookmarked.isNotEmpty()

    private companion object {
        const val ICS_MIME_TYPE = "text/calendar"
    }
}
