package org.jetbrains.kotlinconf

import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock
import org.jetbrains.kotlinconf.sync.withChange
import org.jetbrains.kotlinconf.sync.mergeAfterSync
import org.jetbrains.kotlinconf.sync.SessionizeSyncState
import org.jetbrains.kotlinconf.sync.SessionizeSyncApi
import org.jetbrains.kotlinconf.sync.CodeStatus
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.kotlinconf.LocalNotificationId.Type
import org.jetbrains.kotlinconf.di.YearGraph
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.event.BUNDLED_ASSET_PREFIX
import org.jetbrains.kotlinconf.event.bundledMapSvg
import org.jetbrains.kotlinconf.export.ExportSnapshot
import org.jetbrains.kotlinconf.flags.FlagsManager
import org.jetbrains.kotlinconf.media.Fingerprint
import org.jetbrains.kotlinconf.media.MediaDuplicates
import org.jetbrains.kotlinconf.media.MediaFiles
import org.jetbrains.kotlinconf.media.MoveTargets
import org.jetbrains.kotlinconf.media.PhotoCleanUp
import org.jetbrains.kotlinconf.media.cleanUpProgress
import org.jetbrains.kotlinconf.media.cleanUpScreenPhoto
import org.jetbrains.kotlinconf.media.makeThumbnail
import org.jetbrains.kotlinconf.media.recognizeText
import org.jetbrains.kotlinconf.media.removeStoredDuplicates
import org.jetbrains.kotlinconf.media.screenCleanUpAvailable
import org.jetbrains.kotlinconf.media.SCREEN_CLEAN_UP_VERSION
import org.jetbrains.kotlinconf.personal.CleanUpMode
import org.jetbrains.kotlinconf.personal.MediaKind
import org.jetbrains.kotlinconf.media.PickedMedia
import org.jetbrains.kotlinconf.personal.Attachment
import org.jetbrains.kotlinconf.personal.PersonalNotes
import org.jetbrains.kotlinconf.personal.SessionRating
import org.jetbrains.kotlinconf.personal.withAdded
import org.jetbrains.kotlinconf.personal.withMet
import org.jetbrains.kotlinconf.personal.withCleanUpMode
import org.jetbrains.kotlinconf.personal.withHighlighted
import org.jetbrains.kotlinconf.personal.withStarsFrom
import org.jetbrains.kotlinconf.personal.withMoved
import org.jetbrains.kotlinconf.personal.withRemoved
import org.jetbrains.kotlinconf.personal.withUpdated
import org.jetbrains.kotlinconf.personal.withRating
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.notification_session_already_started
import org.jetbrains.kotlinconf.generated.resources.notification_session_finished
import org.jetbrains.kotlinconf.generated.resources.notification_session_how_was_it
import org.jetbrains.kotlinconf.generated.resources.notification_session_starting_soon
import org.jetbrains.kotlinconf.generated.resources.notification_session_starts_in_5_min
import org.jetbrains.kotlinconf.storage.ApplicationStorage
import org.jetbrains.kotlinconf.storage.YearlyStorage
import org.jetbrains.kotlinconf.utils.Logger
import org.jetbrains.kotlinconf.utils.tagged
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
@SingleIn(AppScope::class)
class ConferenceService(
    private val applicationStorage: ApplicationStorage,
    private val timeProvider: TimeProvider,
    private val yearGraphFactory: YearGraph.Factory,
    private val localNotificationService: LocalNotificationService,
    private val flagsManager: FlagsManager,
    private val syncApi: SessionizeSyncApi,
    private val scope: CoroutineScope,
    logger: Logger,
) {
    companion object {
        private const val LOG_TAG = "ConferenceService"
        private val SYNC_INTERVAL = 60.seconds
    }

    private val taggedLogger = logger.tagged(LOG_TAG)

    private val currentYearGraph: MutableStateFlow<YearGraph?> = MutableStateFlow(null)

    // Declared before init: the sync loop started there uses them
    private val sessionizeAppUrl: String? = ActiveEvent.sessionizeAppUrl
    private val syncMutex = Mutex()
    private val syncStateMutex = Mutex()

    private val currentYearlyStorage: Flow<YearlyStorage> =
        currentYearGraph.map { it?.storage }.filterNotNull()

    init {
        applicationStorage.initialize()

        currentYearGraph.update { yearGraphFactory.create(ActiveEvent.year) }
        scope.launch { loadConferenceData() }

        scope.launch {
            timeProvider.run()
        }

        // Keep bookmarks in step with the event's Sessionize web app while linked
        if (sessionizeAppUrl != null) scope.launch {
            currentYearGraph.filterNotNull().first()
            while (true) {
                if (currentSyncState()?.linked == true) syncFavorites()
                delay(SYNC_INTERVAL)
            }
        }
    }

    val agenda: StateFlow<List<Day>> =
        combine(
            currentYearlyStorage.flatMapLatest { it.getConferenceCache() },
            currentYearlyStorage.flatMapLatest { it.getFavorites() },
            timeProvider.time,
        ) { conference, favorites, time ->
            conference?.buildAgenda(favorites, time) ?: emptyList()
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val sessionCards: StateFlow<List<SessionCardView>> =
        agenda.map {
            it.flatMap { it.timeSlots }.flatMap { it.sessions }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val speakers: StateFlow<List<Speaker>> =
        currentYearlyStorage.flatMapLatest { it.getConferenceCache() }
            .map { it?.speakers ?: emptyList() }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val speakersById: StateFlow<Map<SpeakerId, Speaker>> = speakers
        .map { speakers ->
            speakers.associateBy { it.id }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    // Private notes, who the attendee met and their ratings: stored on the device only

    val notes: StateFlow<PersonalNotes> =
        currentYearlyStorage.flatMapLatest { it.getNotes() }
            .stateIn(scope, SharingStarted.Eagerly, PersonalNotes())

    val metSpeakers: StateFlow<Set<SpeakerId>> =
        currentYearlyStorage.flatMapLatest { it.getMetSpeakers() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val ratings: StateFlow<Map<String, SessionRating>> =
        currentYearlyStorage.flatMapLatest { it.getRatings() }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val attachments: StateFlow<Map<String, List<Attachment>>> =
        currentYearlyStorage.flatMapLatest { it.getAttachments() }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The current event's folder of photos and videos; null until the event has loaded. */
    val mediaFiles: MediaFiles? get() = currentYearGraph.value?.media

    private val personalMutex = Mutex()

    /** Saves even when the screen that asked has gone, so a note typed just before leaving is not lost. */
    private suspend fun savePersonal(block: suspend (YearlyStorage) -> Unit) {
        val storage = currentYearGraph.value?.storage ?: return
        withContext(Dispatchers.Default + NonCancellable) { personalMutex.withLock { block(storage) } }
    }

    suspend fun setSessionNote(sessionId: SessionId, text: String) = updateNotes { it.withSessionNote(sessionId, text) }

    suspend fun setSpeakerNote(speakerId: SpeakerId, text: String) = updateNotes { it.withSpeakerNote(speakerId, text) }

    private suspend fun updateNotes(transform: (PersonalNotes) -> PersonalNotes) =
        savePersonal { it.setNotes(transform(it.getNotes().first())) }

    suspend fun setMet(speakerId: SpeakerId, met: Boolean) =
        savePersonal { it.setMetSpeakers(it.getMetSpeakers().first().withMet(speakerId, met)) }

    /** Changes a session's private rating; the thumb and the takeaway are set separately. */
    suspend fun updateRating(sessionId: SessionId, transform: (SessionRating) -> SessionRating) =
        savePersonal { storage ->
            val ratings = storage.getRatings().first()
            storage.setRatings(ratings.withRating(sessionId, transform(ratings[sessionId.id] ?: SessionRating())))
        }

    /**
     * Moves picked photos and videos into the event's folder and attaches them to a session. Ones already attached
     * (to any session) or picked twice are left out and their copies deleted. Finishes even when the screen that
     * asked has gone, so nothing picked is lost halfway. Returns how many were left out as duplicates.
     */
    suspend fun addAttachments(sessionId: SessionId, picked: List<PickedMedia>): Int = withContext(NonCancellable) {
        val media = mediaFiles ?: return@withContext 0
        val now = nowMillis()
        var duplicates = 0
        savePersonal { storage ->
            val all = storage.getAttachments().first()
            val seen = MediaDuplicates(withContentHashes(media, all.values.flatten()))
            val added = picked.mapIndexedNotNull { index, item ->
                val hash = media.hashOf(item.path)
                if (seen.isDuplicate(Fingerprint(item.sourceId, hash))) {
                    duplicates++
                    discardNow(media, item)
                    return@mapIndexedNotNull null
                }
                val file = media.adopt(item.path, item.extension) ?: return@mapIndexedNotNull null
                Attachment(
                    id = file.substringBefore('.'),
                    kind = item.kind,
                    file = file,
                    thumbnail = item.thumbnailPath?.let { media.adopt(it, "jpg") },
                    addedAt = now + index,
                    sourceId = item.sourceId,
                    contentHash = hash,
                    takenAt = item.takenAt?.epochMillis(ActiveEvent.timeZone),
                    highlighted = item.favorite && item.kind == MediaKind.PHOTO,
                )
            }
            storage.setAttachments(all.withAdded(sessionId, added))
        }
        cleanUpScreenPhotos()
        duplicates
    }

    /**
     * Which of [picked] are already attached (or appear twice in the list), so the review screen can leave them out
     * before asking anything. Returns their positions in [picked].
     */
    suspend fun findDuplicates(picked: List<PickedMedia>): Set<Int> {
        val media = mediaFiles ?: return emptySet()
        val seen = MediaDuplicates(withContentHashes(media, attachments.value.values.flatten()))
        return picked.indices.filterTo(mutableSetOf()) { i ->
            seen.isDuplicate(Fingerprint(picked[i].sourceId, media.hashOf(picked[i].path)))
        }
    }

    /** Files added before fingerprints were kept: hashed now, for the comparison, so a copy of one is recognised. */
    private suspend fun withContentHashes(media: MediaFiles, list: List<Attachment>): List<Attachment> = list.map {
        if (it.contentHash != null) it else it.copy(contentHash = media.hashOf(media.path(it.file)))
    }

    // The media folder whose stored duplicates were already removed in this run of the app
    private var duplicatesRemovedFor: MediaFiles? = null

    /**
     * Removes photos and videos that older versions of the app stored more than once, and their files (the cleaned-up
     * copy and preview too; the originals stay in the photo library). Gives every attachment without a content hash one
     * on the way, so the next check is quick. Once per run of the app and event.
     */
    private suspend fun removeStoredDuplicatesOnce() {
        val media = mediaFiles?.takeIf { it.isSupported } ?: return
        if (duplicatesRemovedFor === media) return
        val hashes = attachments.value.values.flatten()
            .filter { it.contentHash == null }
            .mapNotNull { attachment -> media.hashOf(media.path(attachment.file))?.let { attachment.id to it } }
            .toMap()
        var unused = emptyList<String>()
        savePersonal { storage ->
            val all = storage.getAttachments().first()
            val result = removeStoredDuplicates(all, hashes)
            if (result.kept != all) storage.setAttachments(result.kept)
            val stillUsed = result.kept.values.flatten().flatMapTo(mutableSetOf()) { it.files }
            unused = result.removed.flatMap { it.files }.filterNot { it in stillUsed }
        }
        unused.forEach { media.delete(it) }
        duplicatesRemovedFor = media
    }

    private suspend fun discardNow(media: MediaFiles, item: PickedMedia) {
        media.discard(item.path)
        item.thumbnailPath?.let { media.discard(it) }
    }

    private val cleaning = Mutex()

    /**
     * Straightens and cleans up photos of screens and whiteboards that have not been looked at yet, or were looked at by
     * an older clean-up, one at a time in the background; the originals are kept. Removes copies stored twice first.
     * Only that is done where the platform has no clean-up.
     */
    fun cleanUpScreenPhotos() {
        scope.launch(Dispatchers.Default) {
            if (!cleaning.tryLock()) return@launch
            try {
                removeStoredDuplicatesOnce()
                if (!screenCleanUpAvailable) return@launch
                while (true) {
                    val media = mediaFiles ?: return@launch
                    val next = attachments.value.values.flatten().firstOrNull { it.needsCleanUp() } ?: return@launch
                    // Photos looked at by the current clean-up before previews existed only need the preview
                    val redo = next.needsNewCleanUp()
                    val name = media.newName("jpg")
                    val cleaned = name.takeIf {
                        redo && next.cleanMode != CleanUpMode.ORIGINAL && cleanUpScreenPhoto(media.path(next.file), media.path(name), next.cleanMode)
                    }
                    // A preview of what lists should show: the cleaned-up photo if there is one, else the original
                    val shown = cleaned ?: next.cleaned.takeUnless { redo } ?: next.file
                    val previewName = media.newName("jpg")
                    val preview = if (redo || next.thumbnail == null) {
                        previewName.takeIf { makeThumbnail(media.path(shown), media.path(previewName)) }
                    } else {
                        null
                    }
                    // The words on the photo, read once, from the cleaned-up copy where there is one: straighter and
                    // clearer than the original. Kept with the photo, so nothing has to read it again.
                    val text = if (next.textChecked) null else recognizeText(media.path(shown))
                    savePersonal { storage ->
                        val all = storage.getAttachments().first()
                        val current = all.values.flatten().firstOrNull { it.id == next.id }
                        if (current == null) {
                            cleaned?.let { media.delete(it) }
                            preview?.let { media.delete(it) }
                        } else {
                            if (preview != null) current.thumbnail?.let { media.delete(it) }
                            // A newer clean-up replaces the older copy, also when it finds no screen in the photo
                            if (redo) current.cleaned?.let { media.delete(it) }
                            storage.setAttachments(all.withUpdated(next.id) {
                                it.copy(
                                    cleaned = if (redo) cleaned else it.cleaned,
                                    // Done, unless the attendee chose another clean-up while this one was running
                                    cleanChecked = it.cleanMode == next.cleanMode,
                                    cleanVersion = if (redo) SCREEN_CLEAN_UP_VERSION else it.cleanVersion,
                                    previewChecked = true,
                                    thumbnail = preview ?: it.thumbnail,
                                    text = if (next.textChecked) it.text else text,
                                    textChecked = true,
                                )
                            })
                        }
                    }
                    // Wait for the stored list to show the change before looking for the next one
                    attachments.first { list -> list.values.flatten().none { it.id == next.id && it.cleanMode == next.cleanMode && it.needsCleanUp() } }
                }
            } finally {
                cleaning.unlock()
            }
        }
    }

    /**
     * Chooses how a photo is cleaned up (null: the app decides). The new copy is made in the background and replaces
     * the old one; with [CleanUpMode.ORIGINAL] the photo is shown as it was taken.
     */
    suspend fun setCleanUpMode(attachmentId: String, mode: CleanUpMode?) {
        savePersonal { storage ->
            storage.setAttachments(storage.getAttachments().first().withUpdated(attachmentId) { it.withCleanUpMode(mode) })
        }
        cleanUpScreenPhotos()
    }

    /** Stars a photo (or takes the star off), so the trip report features it. */
    suspend fun setHighlighted(attachmentId: String, highlighted: Boolean) {
        savePersonal { storage ->
            storage.setAttachments(storage.getAttachments().first().withUpdated(attachmentId) { it.withHighlighted(highlighted) })
        }
    }

    /** Stars the photos hearted in the photo library (by their library ids); never takes a star off. Returns how many. */
    suspend fun starFavorites(favoriteIds: Set<String>): Int {
        var starred = 0
        savePersonal { storage ->
            val (next, count) = storage.getAttachments().first().withStarsFrom(favoriteIds)
            starred = count
            if (count > 0) storage.setAttachments(next)
        }
        return starred
    }

    /**
     * How far the background clean-up of the photos has got, so the trip report can show it; null where this platform
     * has no clean-up or there is no photo.
     */
    val photoCleanUp: Flow<PhotoCleanUp?>
        get() = attachments.map { all ->
            if (!screenCleanUpAvailable) null else cleanUpProgress(all.values.flatten()) { it.needsCleanUp() }
        }.distinctUntilChanged()

    private fun Attachment.needsNewCleanUp() = !cleanChecked || cleanVersion < SCREEN_CLEAN_UP_VERSION

    private fun Attachment.needsCleanUp() = kind == MediaKind.PHOTO && (needsNewCleanUp() || !previewChecked || !textChecked)

    // Photos and videos picked on the trip report page, waiting to be matched to sessions on the review screen
    private val pendingImport = MutableStateFlow<List<PickedMedia>>(emptyList())
    private var pendingUnreadable = 0

    /**
     * Hands photos and videos from the library to the review screen, which matches them to sessions. [unreadable] is
     * how many of the chosen ones could not be read, to tell the attendee.
     */
    fun startImport(picked: List<PickedMedia>, unreadable: Int = 0) {
        pendingUnreadable = unreadable
        discardPicked(pendingImport.getAndUpdate { picked })
    }

    /** The photos and videos waiting for the review screen, and how many could not be read; handed over once. */
    fun takeImport(): Pair<List<PickedMedia>, Int> =
        (pendingImport.getAndUpdate { emptyList() } to pendingUnreadable).also { pendingUnreadable = 0 }

    /** Deletes the temporary copies of picked photos and videos that were not kept. */
    fun discardPicked(picked: List<PickedMedia>) {
        if (picked.isEmpty()) return
        scope.launch {
            val media = mediaFiles ?: return@launch
            picked.forEach { item ->
                media.discard(item.path)
                item.thumbnailPath?.let { media.discard(it) }
            }
        }
    }

    /** Removes a photo or video from a session and deletes its files from the device. */
    suspend fun removeAttachment(sessionId: SessionId, attachment: Attachment) {
        savePersonal { it.setAttachments(it.getAttachments().first().withRemoved(sessionId, attachment.id)) }
        mediaFiles?.let { media ->
            media.delete(attachment.file)
            attachment.thumbnail?.let { media.delete(it) }
            attachment.cleaned?.let { media.delete(it) }
        }
    }

    /** Moves a photo or video to another session, e.g. one that landed on the wrong talk; its files stay as they are. */
    suspend fun moveAttachment(from: SessionId, to: SessionId, attachment: Attachment) {
        savePersonal { it.setAttachments(it.getAttachments().first().withMoved(from, to, attachment.id)) }
    }

    /** The sessions a photo or video on [sessionId] can be moved to. */
    fun moveTargetsFlow(sessionId: SessionId): Flow<MoveTargets> =
        sessionCards.map { MoveTargets.of(sessionId, it) }

    val conferenceInfo: StateFlow<ConferenceInfo?> =
        currentYearlyStorage.flatMapLatest { it.getConferenceInfoCache() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val mapData: StateFlow<MapData?> = conferenceInfo
        .map { it?.mapData }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun getTheme(): Flow<Theme> = applicationStorage.getTheme()

    fun setTheme(theme: Theme) {
        scope.launch { applicationStorage.setTheme(theme) }
    }

    suspend fun loadConferenceData() {
        val currentYearGraph = currentYearGraph.value ?: return
        val storage = currentYearGraph.storage
        val client = currentYearGraph.api

        // Load conference info (tags, map data)
        client.downloadConferenceInfo()?.let { storage.setConferenceInfoCache(it) }

        // Load conference schedule data
        val newData = client.downloadConferenceData() ?: return
        val oldData = storage.getConferenceCache().first()

        storage.setConferenceCache(newData)

        if (oldData != null) {
            val oldSessions = oldData.sessions.associateBy { it.id }
            val newSessions = newData.sessions.associateBy { it.id }

            // Cancel notifications for removed sessions
            val removedIds = oldSessions.keys - newSessions.keys
            removedIds.forEach { sessionId ->
                cancelNotifications(sessionId)
            }

            // Remove removed sessions from favorites
            val favorites = storage.getFavorites().first().toMutableSet()
            favorites.removeAll { it in removedIds }
            storage.setFavorites(favorites)

            // Check if any favorite sessions were rescheduled
            val remindersEnabled = getNotificationSettings().first().sessionReminders
            favorites.forEach { sessionId ->
                val newSession = newSessions[sessionId] ?: return@forEach
                val oldSession = oldSessions[sessionId] ?: return@forEach

                if (oldSession.startsAt != newSession.startsAt ||
                    oldSession.endsAt != newSession.endsAt ||
                    oldSession.location != newSession.location) {
                    cancelNotifications(sessionId)
                    if (remindersEnabled) {
                        scheduleNotification(
                            start = newSession.startsAt,
                            end = newSession.endsAt,
                            sessionId = newSession.id,
                            title = newSession.title,
                            room = newSession.location,
                        )
                    }
                }
            }
        }
    }

    fun isOnboardingComplete(): Flow<Boolean> {
        return applicationStorage.isOnboardingComplete()
    }

    suspend fun completeOnboarding() {
        applicationStorage.setOnboardingComplete(true)
    }

    /**
     * Request permissions to send notifications.
     * @return true if permission was granted, false otherwise
     */
    suspend fun requestNotificationPermissions(): Boolean =
        localNotificationService.requestPermission()
            .also { taggedLogger.log { "Notification permissions granted: $it" } }

    fun getNotificationSettings(): Flow<NotificationSettings> =
        currentYearlyStorage.flatMapLatest { it.getNotificationSettings() }
            .map {
                // No stored value yet: reminders are on by default
                it ?: NotificationSettings(sessionReminders = true)
            }

    suspend fun setNotificationSettings(settings: NotificationSettings) {
        val currentYearGraph = currentYearGraph.value ?: return
        val storage = currentYearGraph.storage

        val previousSettings = getNotificationSettings().first()
        storage.setNotificationSettings(settings)

        if (previousSettings.sessionReminders != settings.sessionReminders) {
            val favorites = storage.getFavorites().first()

            if (settings.sessionReminders) {
                // Re-schedule notifications for all favorite sessions
                favorites
                    .mapNotNull { sessionId -> sessionByIdFlow(sessionId).first() }
                    .forEach { session ->
                        scheduleNotification(
                            start = session.startsAt,
                            end = session.endsAt,
                            sessionId = session.id,
                            title = session.title,
                            room = session.locationLine,
                        )
                    }
            } else {
                // Cancel all session reminder notifications
                favorites.forEach { sessionId ->
                    cancelNotifications(sessionId)
                }
            }
        }
    }

    /** The schedule, speakers and the attendee's own data as they are now, for exporting. */
    fun exportSnapshot() = ExportSnapshot(
        sessions = sessionCards.value,
        speakers = speakersById.value,
        notes = notes.value,
        ratings = ratings.value,
        met = metSpeakers.value,
        attachments = attachments.value,
    )

    /** [exportSnapshot] as it changes, for the trip report page. */
    val exportSnapshots: Flow<ExportSnapshot> =
        combine(sessionCards, speakersById, notes, ratings, metSpeakers) { _, _, _, _, _ -> }
            .combine(attachments) { _, _ -> exportSnapshot() }

    fun speakerById(speakerId: SpeakerId): Speaker? = speakersById.value[speakerId]

    fun speakerByIdFlow(speakerId: SpeakerId): Flow<Speaker?> =
        speakersById.map { it[speakerId] }

    fun sessionByIdFlow(sessionId: SessionId): Flow<SessionCardView?> =
        sessionCards.map { sessions -> sessions.find { it.id == sessionId } }

    fun speakersBySessionId(sessionId: SessionId): Flow<List<Speaker>> =
        sessionByIdFlow(sessionId).map { session ->
            session?.speakerIds?.mapNotNull { speakerId -> speakerById(speakerId) } ?: emptyList()
        }

    fun sessionsForSpeakerFlow(id: SpeakerId): Flow<List<SessionCardView>> =
        sessionCards.map { sessions -> sessions.filter { id in it.speakerIds } }


    suspend fun setFavorite(sessionId: SessionId, favorite: Boolean) {
        withContext(Dispatchers.Default + NonCancellable) {
            val currentYearGraph = currentYearGraph.value ?: return@withContext
            val storage = currentYearGraph.storage

            val favorites = storage.getFavorites().first().toMutableSet()
            if (favorite) favorites.add(sessionId) else favorites.remove(sessionId)
            storage.setFavorites(favorites)

            if (recordSyncChange(sessionId, favorite)) {
                scope.launch { syncFavorites() }
            }

            if (favorite) {
                val remindersEnabled = getNotificationSettings().first().sessionReminders
                if (remindersEnabled) {
                    val session = sessionByIdFlow(sessionId).first()
                    if (session != null) {
                        scheduleNotification(
                            start = session.startsAt,
                            end = session.endsAt,
                            sessionId = session.id,
                            title = session.title,
                            room = session.locationLine,
                        )
                    }
                }
            } else {
                cancelNotifications(sessionId)
            }
        }
    }

    private suspend fun scheduleNotification(
        start: LocalDateTime,
        end: LocalDateTime,
        sessionId: SessionId,
        title: String,
        room: String,
    ) {
        val now = timeProvider.now()

        val reminderTime = start - 5.minutes

        // Notifications for session start
        val startsLater = now < reminderTime
        val startsSoon = now in reminderTime..<start
        val isLive = now in start..<end
        when {
            startsLater -> localNotificationService.post(
                time = reminderTime,
                localNotificationId = LocalNotificationId(Type.SessionStart, sessionId.id),
                title = title,
                message = getString(Res.string.notification_session_starts_in_5_min, room),
            )

            startsSoon -> localNotificationService.post(
                localNotificationId = LocalNotificationId(Type.SessionStart, sessionId.id),
                title = title,
                message = getString(Res.string.notification_session_starting_soon, room),
            )

            isLive -> localNotificationService.post(
                localNotificationId = LocalNotificationId(Type.SessionStart, sessionId.id),
                title = title,
                message = getString(Res.string.notification_session_already_started, room),
            )
        }
    }

    private fun cancelNotifications(sessionId: SessionId) {
        localNotificationService.cancel(LocalNotificationId(Type.SessionStart, sessionId.id))
        localNotificationService.cancel(LocalNotificationId(Type.SessionEnd, sessionId.id))
    }


    /** The contents of a bundled map image, wrapped as SVG; null for anything else. */
    suspend fun getAsset(path: String): String? {
        if (!path.startsWith(BUNDLED_ASSET_PREFIX)) return null
        return runCatching { bundledMapSvg(path) }
            .onFailure { taggedLogger.log { "Failed to read bundled asset: $path" } }
            .getOrNull()
    }

    // Bookmark sync with the event's Sessionize web app, see SessionizeSync.kt


    val supportsSessionizeSync: Boolean get() = sessionizeAppUrl != null

    val sessionizeSyncState: StateFlow<SessionizeSyncState?> =
        currentYearlyStorage.flatMapLatest { it.getSessionizeSync() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private suspend fun currentSyncState(): SessionizeSyncState? =
        currentYearGraph.value?.storage?.getSessionizeSync()?.first()

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    @OptIn(ExperimentalUuidApi::class)
    private fun newSyncState() = SessionizeSyncState(deviceId = Uuid.random().toString())

    private suspend fun updateSyncState(transform: (SessionizeSyncState?) -> SessionizeSyncState?) =
        syncStateMutex.withLock {
            val storage = currentYearGraph.value?.storage ?: return@withLock
            storage.setSessionizeSync(transform(storage.getSessionizeSync().first()))
        }

    /** Records a bookmark change for the next sync; false when not linked. */
    private suspend fun recordSyncChange(sessionId: SessionId, bookmarked: Boolean): Boolean {
        if (sessionizeAppUrl == null) return false
        var recorded = false
        updateSyncState { state ->
            if (state?.linked == true) {
                recorded = true
                state.withChange(sessionId, bookmarked, nowMillis())
            } else state
        }
        return recorded
    }

    /**
     * Sends pending bookmark changes and takes the server's list, keeping changes made in the
     * meantime. Returns false when there is nothing to sync with or the server can't be reached.
     */
    suspend fun syncFavorites(first: Boolean = false): Boolean = syncMutex.withLock {
        val baseUrl = sessionizeAppUrl ?: return false
        val sent = currentSyncState() ?: return false
        val items = try {
            syncApi.favorites(baseUrl, sent.deviceId, sent.changedAt, first, sent.toAdd, sent.toRemove)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            taggedLogger.log { "Sessionize sync failed: ${e.message}" }
            return false
        }

        var pending: SessionizeSyncState? = null
        updateSyncState { latest ->
            (latest ?: return@updateSyncState null).copy(
                toAdd = latest.toAdd - sent.toAdd,
                toRemove = latest.toRemove - sent.toRemove,
                lastSyncedAt = nowMillis(),
            ).also { pending = it }
        }
        pending?.let { applyFavorites(mergeAfterSync(items, it)) }
        taggedLogger.log { "Sessionize sync: ${items.size} bookmarks" }
        true
    }

    /** Links to the web app that shows [code]; this app's bookmarks are added to the web app's. */
    suspend fun linkSessionize(code: String): Boolean {
        val baseUrl = sessionizeAppUrl ?: return false
        val state = currentSyncState()?.takeUnless { it.linked } ?: newSyncState()
        val ok = try {
            // Register the device first, as the web app does on start; unregistered devices fail later
            syncApi.favorites(baseUrl, state.deviceId, 0, first = true, toAdd = emptySet(), toRemove = emptySet())
            syncApi.useCode(baseUrl, state.deviceId, code.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            taggedLogger.log { "Sessionize code failed: ${e.message}" }
            false
        }
        if (!ok) return false

        startLinkedSync(state)
        return true
    }

    /**
     * Shares this app's bookmarks and returns a code to enter in the web app. Poll
     * [sessionizeCodeStatus] and call [completeSessionizeLink] once the code was used.
     */
    suspend fun startSessionizeCode(): String? {
        val baseUrl = sessionizeAppUrl ?: return null
        val local = currentYearGraph.value?.storage?.getFavorites()?.first()?.map { it.id }?.toSet() ?: return null
        val state = (currentSyncState()?.takeUnless { it.linked } ?: newSyncState())
            .copy(toAdd = local, toRemove = emptySet(), changedAt = nowMillis())
        updateSyncState { state }
        if (!syncFavorites(first = true)) return null
        return try {
            syncApi.startCode(baseUrl, state.deviceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            taggedLogger.log { "Sessionize code failed: ${e.message}" }
            null
        }
    }

    suspend fun sessionizeCodeStatus(code: String): CodeStatus? {
        val baseUrl = sessionizeAppUrl ?: return null
        return try {
            syncApi.codeStatus(baseUrl, code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun completeSessionizeLink() {
        val state = currentSyncState() ?: return
        startLinkedSync(state)
    }

    private suspend fun startLinkedSync(state: SessionizeSyncState) {
        // Keep the bookmarks made in this app: send them with the first sync
        val local = currentYearGraph.value?.storage?.getFavorites()?.first()?.map { it.id }?.toSet() ?: emptySet()
        updateSyncState { state.copy(linked = true, toAdd = state.toAdd + local, toRemove = emptySet(), changedAt = nowMillis()) }
        syncFavorites(first = true)
    }

    /** Stops syncing; bookmarks stay as they are. */
    suspend fun unlinkSessionize() {
        updateSyncState { null }
    }

    /** Applies a bookmark list from the server, updating session reminders for what changed. */
    private suspend fun applyFavorites(favorites: Set<SessionId>) {
        val storage = currentYearGraph.value?.storage ?: return
        val old = storage.getFavorites().first()
        if (old == favorites) return
        storage.setFavorites(favorites)

        (old - favorites).forEach { cancelNotifications(it) }
        if (getNotificationSettings().first().sessionReminders) {
            (favorites - old).forEach { sessionId ->
                val session = sessionByIdFlow(sessionId).first() ?: return@forEach
                scheduleNotification(
                    start = session.startsAt,
                    end = session.endsAt,
                    sessionId = session.id,
                    title = session.title,
                    room = session.locationLine,
                )
            }
        }
    }
}
