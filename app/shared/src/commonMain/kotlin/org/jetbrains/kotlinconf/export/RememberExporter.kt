package org.jetbrains.kotlinconf.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.LocalAppGraph
import org.jetbrains.kotlinconf.LocalShareHandler
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.export_my_notes
import org.jetbrains.kotlinconf.generated.resources.export_no_sessions
import org.jetbrains.kotlinconf.generated.resources.export_people_met
import org.jetbrains.kotlinconf.generated.resources.export_rating
import org.jetbrains.kotlinconf.generated.resources.export_recording
import org.jetbrains.kotlinconf.generated.resources.export_room
import org.jetbrains.kotlinconf.generated.resources.export_schedule_heading
import org.jetbrains.kotlinconf.generated.resources.export_slides
import org.jetbrains.kotlinconf.generated.resources.export_speakers
import org.jetbrains.kotlinconf.generated.resources.export_trip_report_heading

/** The share and calendar actions, worded in the app's language. */
@Composable
fun rememberExporter(): Exporter {
    val service = LocalAppGraph.current.conferenceService
    val share = LocalShareHandler.current
    val labels = ExportLabels(
        speakers = stringResource(Res.string.export_speakers),
        room = stringResource(Res.string.export_room),
        myNotes = stringResource(Res.string.export_my_notes),
        rating = stringResource(Res.string.export_rating),
        slides = stringResource(Res.string.export_slides),
        recording = stringResource(Res.string.export_recording),
        scheduleHeading = stringResource(Res.string.export_schedule_heading),
        tripReportHeading = stringResource(Res.string.export_trip_report_heading),
        peopleMet = stringResource(Res.string.export_people_met),
        noSessions = stringResource(Res.string.export_no_sessions),
    )
    return remember(service, share, labels) {
        Exporter(
            snapshot = service::exportSnapshot,
            share = share,
            event = ExportEvent(ActiveEvent.id, ActiveEvent.name, ActiveEvent.timeZone, ActiveEvent.venueAddress),
            labels = labels,
        )
    }
}
