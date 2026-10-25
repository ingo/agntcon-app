package org.jetbrains.kotlinconf.export

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.jetbrains.kotlinconf.Session
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.asSessionCard
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.personal.PersonalNotes
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The downloadable report, built for real on disk and read back with the JDK's own zip reader. */
class ReportBundlerTest {
    private val root: File = Files.createTempDirectory("report-test").toFile()
    private val mediaDir = File(root, "media").apply { mkdirs() }
    private val event = ExportEvent("agntcon-mcpcon-na-2026", "AGNTCon + MCPCon North America 2026", TimeZone.of("America/Los_Angeles"))

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun card(id: String, title: String, hour: Int, slides: String? = null) = Session(
        id = SessionId(id),
        title = title,
        description = "",
        speakerIds = emptyList(),
        location = "230C",
        startsAt = LocalDateTime(2026, 10, 22, hour, 0),
        endsAt = LocalDateTime(2026, 10, 22, hour, 45),
        slidesUrl = slides,
    ).asSessionCard(emptyMap(), LocalDateTime(2026, 10, 1, 0, 0), setOf(SessionId(id)), null)

    private fun mediaFile(name: String, bytes: ByteArray): String {
        File(mediaDir, name).writeBytes(bytes)
        return name
    }

    @Test
    fun crc32MatchesTheJdk() {
        val bytes = Random(7).nextBytes(100_000)
        val ours = Crc32().apply { update(bytes, 0, bytes.size) }.value
        val jdk = CRC32().apply { update(bytes) }.value
        assertEquals(jdk, ours)
    }

    @Test
    fun bundleHasTheReportSlidesAndMedia() = runTest {
        val photo = Random(1).nextBytes(300_000)
        val video = Random(2).nextBytes(2_000_000)
        mediaFile("p1.jpg", photo)
        mediaFile("v1.mov", video)
        val slides = "%PDF-1.7 slides".encodeToByteArray()

        val keynote = card("1", "Opening Keynote", 9, slides = "https://sessionize.com/download/a~b.pdf~keynote.pdf")
        val broken = card("2", "Agents in production", 11, slides = "https://sessionize.com/download/c~d.pdf~gone.pdf")
        val snapshot = ExportSnapshot(
            sessions = listOf(keynote, broken),
            speakers = emptyMap(),
            notes = PersonalNotes().withSessionNote(SessionId("1"), "Use resource indicators"),
            ratings = emptyMap(),
            met = emptySet(),
            attachments = mapOf(
                "1" to listOf(
                    Attachment("p1", MediaKind.PHOTO, "p1.jpg", addedAt = 1),
                    Attachment("v1", MediaKind.VIDEO, "v1.mov", thumbnail = "v1s.jpg", addedAt = 2),
                    // Deleted from the device behind the app's back: left out, not a failure
                    Attachment("gone", MediaKind.PHOTO, "gone.jpg", addedAt = 3),
                ),
            ),
        )
        val downloads = mutableListOf<String>()
        val bundler = OkioReportBundler(
            outputDir = File(root, "out").toOkioPath(),
            download = { url, target: Path ->
                downloads += url
                if ("gone" in url) false else {
                    FileSystem.SYSTEM.write(target) { write(slides) }
                    true
                }
            },
        )
        val progress = mutableListOf<ReportProgress>()

        val result = bundler.build(ReportInput(snapshot, event, ExportLabels()) { File(mediaDir, it).path }) { progress += it }

        assertIs<ReportResult.Done>(result)
        assertEquals("agntcon-mcpcon-na-2026-trip-report.zip", result.fileName)
        assertEquals(2, result.sessions)
        assertEquals(1, result.slidesIncluded)
        assertEquals(1, result.slidesLinked)
        assertEquals(1, result.photos)
        assertEquals(1, result.videos)
        assertEquals(2, downloads.size)
        assertEquals(listOf(ReportProgress.Slides(0, 2), ReportProgress.Slides(1, 2), ReportProgress.Packing), progress)

        val zip = File(result.path)
        assertEquals(zip.length(), result.bytes)
        ZipFile(zip).use { file ->
            val names = file.entries().toList().map { it.name }
            assertEquals(
                listOf(
                    "report.md",
                    "report.html",
                    "slides/2026-10-22-0900-opening-keynote.pdf",
                    "media/2026-10-22-0900-opening-keynote/01.jpg",
                    "media/2026-10-22-0900-opening-keynote/02.mov",
                ),
                names,
            )
            val markdown = file.getInputStream(file.getEntry("report.md")).readBytes().decodeToString()
            assertTrue("- [Slides](slides/2026-10-22-0900-opening-keynote.pdf)" in markdown, markdown)
            // The slides that could not be fetched stay a link to Sessionize
            assertTrue("- [Slides](https://sessionize.com/download/c~d.pdf~gone.pdf)" in markdown)
            assertTrue("![Photo 1](media/2026-10-22-0900-opening-keynote/01.jpg)" in markdown)
            assertTrue("Use resource indicators" in markdown)
            val html = file.getInputStream(file.getEntry("report.html")).readBytes().decodeToString()
            assertTrue("<video src=\"media/2026-10-22-0900-opening-keynote/02.mov\"" in html)
        }
        // ZipInputStream checks every stored entry's size and checksum as it reads
        val contents = ZipInputStream(zip.inputStream()).use { stream ->
            generateSequence { stream.nextEntry }.associate { it.name to stream.readBytes() }
        }
        assertContentEquals(photo, contents.getValue("media/2026-10-22-0900-opening-keynote/01.jpg"))
        assertContentEquals(video, contents.getValue("media/2026-10-22-0900-opening-keynote/02.mov"))
        assertContentEquals(slides, contents.getValue("slides/2026-10-22-0900-opening-keynote.pdf"))
        // The work folder is cleaned up; only the zip is left
        assertEquals(listOf(zip.name), File(root, "out").list()!!.toList())
    }

    @Test
    fun aReportWithNothingInItIsStillAZipWithTheReport() = runTest {
        val bundler = OkioReportBundler(outputDir = File(root, "out").toOkioPath(), download = { _, _ -> false })
        val empty = ExportSnapshot(emptyList(), emptyMap(), PersonalNotes(), emptyMap(), emptySet())

        val result = bundler.build(ReportInput(empty, event, ExportLabels()) { it }) {}

        assertIs<ReportResult.Done>(result)
        ZipFile(File(result.path)).use { assertEquals(listOf("report.md", "report.html"), it.entries().toList().map { e -> e.name }) }
    }
}
