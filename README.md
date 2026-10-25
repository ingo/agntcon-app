# AGNTCon + MCPCon North America 2026 app

An unofficial schedule app for [AGNTCon + MCPCon North America 2026](https://events.linuxfoundation.org/agntcon-mcpcon-north-america/)
(October 22–23, 2026, San Jose): iOS, Android, desktop and web from one Kotlin Multiplatform codebase. It reads the
event's public [Sessionize](https://sessionize.com) schedule directly; there is no server, no account and no tracking.

This repository is the AGNTCon build: it is where the TestFlight and web builds of this app are made. It was split off from
[ingo/sessionize-app](https://github.com/ingo/sessionize-app), which is becoming a generic Sessionize conference app.

**Project page:** https://muschenetz.com/agntcon-app/ (what the app does, with screenshots, and how to join the TestFlight beta).

## Credit

This is a fork of the [KotlinConf app](https://github.com/JetBrains/kotlinconf-app) by
[JetBrains](https://www.jetbrains.com/) and its [contributors](https://github.com/JetBrains/kotlinconf-app/graphs/contributors).
Its architecture, screens, design system and most of the code are theirs: the shared Compose Multiplatform UI,
navigation, schedule, speakers, search and filters, and session reminders. Thank you to the KotlinConf
team for building it so well and for publishing it under the Apache License 2.0.

This fork is not affiliated with or endorsed by JetBrains, KotlinConf, Sessionize or the Linux Foundation.
KotlinConf and Kodee are trademarks of JetBrains. The AGNTCon + MCPCon name and logo belong to the Linux Foundation and
are used here only to theme the AGNTCon build. The code is under the [Apache License 2.0](LICENSE), as the original;
the changes made in this fork are listed below and in the git history.

## What this fork adds

**Any Sessionize conference, and no backend**
- Conference profiles ([`EventProfiles.kt`](app/shared/src/commonMain/kotlin/org/jetbrains/kotlinconf/event/EventProfiles.kt)):
  name, time zone, links, theme and data source per conference, picked at build time with `-Pevent=<id>`.
- Reads a public Sessionize feed directly, so a conference needs no server of its own. Times work whether the feed sends
  UTC or event-local times; schedule filters (topic, level, format) come from the feed's categories.
- The KotlinConf backend and what depended on it (voting, feedback, Golden Kodee, partners, legal documents, push
  notifications, Firebase) were removed, so this app contains no JetBrains credentials and talks only to Sessionize.
- Per-conference look: light and dark colors, logo, app icon and launch screen.

**Planning your conference**
- **My schedule**, a tab with two views:
  - **Day**: your bookmarked sessions on a timeline. Overlapping sessions sit side by side and are marked as clashes;
    all-day items (registration, exhibitions) stay out of the way; free time is split at the conference's own breaks
    (breakout rounds, lunch) with a button to browse the sessions starting then and bookmark them in place.
  - **Now**: during the conference, what you are in and how long is left, what's next and how soon, its room and floor
    (with a hint when you need to change floors), free time before it, and the rest of the day.
- **Session pages** open with how long until the session starts (or how long is left), its room and floor, highlighted
  in the last 15 minutes, so you can tell at a glance whether you are in the right room for the right session.
- **Sync with the Sessionize web app**: link with the 6-digit code from the event's web app ("Sync favorites") and
  bookmarks stay in step both ways. This uses the same calls as Sessionize's own web app; they are not a documented
  API and may change, in which case the app keeps working from its own bookmarks.

**At the conference: notes, people and sharing** (all of it stays on your device; nothing needs a server)

<p>
  <img src="docs/screenshots/session-top.png" width="190" alt="Session page with add to calendar, share and find on map">
  <img src="docs/screenshots/session-rating-notes.png" width="190" alt="A thumbs-up rating, a one-line takeaway and notes on a session">
  <img src="docs/screenshots/speaker-links.png" width="190" alt="A speaker page with LinkedIn and other links">
  <img src="docs/screenshots/people-i-met.png" width="190" alt="The people I met, with notes and LinkedIn">
</p>
<p>
  <img src="docs/screenshots/companies-showcase.png" width="190" alt="Companies with a talk in the Solutions Showcase">
  <img src="docs/screenshots/search-and-map-pin.png" width="190" alt="Search results with a map pin on each room">
  <img src="docs/screenshots/calendar-export.png" width="190" alt="Calendar tab with add to calendar, share and trip report">
  <img src="docs/screenshots/share-sheet.png" width="190" alt="The iOS share sheet with the schedule as text">
</p>

- **Notes** on any session or speaker, **People I met** (mark a speaker as met; the Met tab lists them with your note and
  their LinkedIn link) and **private ratings** (thumbs up or down and a one-line takeaway; shown for events without
  their own voting).
- **Add to calendar** for one session or all your bookmarks (an `.ics` file), **share** a session or your schedule as
  text, and a **trip report** in Markdown: your sessions in time order with speakers, rating, takeaway, notes, slides and
  recordings, plus the people you met. It goes through the platform's share sheet.
- **Photos and videos** on a session: take a photo or record a video, or add photos, screenshots and videos from the
  library. They stay on the device. The **trip report** page (a big card on the Now tab) downloads everything as one
  zip: the report in Markdown and as a web page, each session's slides from Sessionize and your photos and videos, to
  mail, AirDrop or save. The web app shares the text report only.
- **Add photos from the event**: pick a batch from the library on the trip report page and each one is matched to the
  session it was taken in, by its date. Where it is not clear (two bookmarked sessions at once, parallel talks you did not
  mark, a break, no date) the app asks, once per group of photos; nothing is added before you confirm.
- **Companies**: speakers' companies from the feed, searchable, with a **Solutions Showcase** filter to browse exhibitor
  talks by company; a company filter in the schedule's search; speaker links (LinkedIn, blog, company site).
- **Better search**: the schedule search also matches session descriptions and speakers' companies, and **Find on map**
  on a session's room (and a pin on schedule rows) opens the venue map on that room.

**Fixes to the original**
- Sessions are grouped into their own time slots; a long session (such as all-day registration) no longer swallows the
  sessions inside it.
- Duplicate list keys that crashed the schedule's bookmarks view when time slots shared a start time.
- Short day labels when a conference has more days than fit on a phone.

**Testing**
- Snapshots of real Sessionize feeds ([`testdata/sessionize`](testdata/sessionize)) for one event-local and one UTC
  conference. Tests check parsing and time zones, schedule grouping, unique list keys and the personal calendar over many
  random sets of bookmarks; each was confirmed to fail with the bug it guards against.
- The Sessionize sync client is tested against a mock server with the requests and responses of the real web app.
- The GitHub Actions workflow runs the core and app tests and the iOS build for each conference profile.

## Publishing to TestFlight

The iOS beta is published from this repository: `scripts/testflight.sh --upload` archives the app (bundle id
`com.muschenetz.agntcon`, team `4M7MX9FB66`) and uploads it. It needs an App Store Connect API key
(`ASC_KEY_ID`, `ASC_ISSUER_ID` and the `.p8` key); see the header of [`scripts/ios-testflight.sh`](scripts/ios-testflight.sh).
Run it from an up-to-date `main`.

## Android

The Android app is `com.muschenetz.agntcon` ("AGNTCon", icon from the AGNTCon mark on indigo). Needs the Android SDK
(platform 37, build tools 37) and JDK 21; put `sdk.dir=<path>` in `local.properties`.

```bash
./gradlew :app:androidApp:assembleDebug     # app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.muschenetz.agntcon/org.jetbrains.kotlinconf.android.MainActivity
```

Checked on an Android 17 (arm64) emulator: the schedule loads, session pages open, bookmarks work, and "Add to calendar"
and "Share" open the system share sheet with the `.ics` file. A release build needs a signing key, which is not in this
repository.

## Building for a conference

1. [Set up your environment](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-setup.html) for
   Kotlin Multiplatform development (JDK 21, Xcode for iOS).
2. Find the event's Sessionize API id. Event schedule pages that embed Sessionize contain URLs such as
   `https://sessionize.com/api/v2/{id}/view/GridSmart`; check that `https://sessionize.com/api/v2/{id}/view/All`
   returns JSON. For bookmark sync, note the event's web app address (`https://<event>.sessionize.com`).
3. Add an `EventProfile` with that `sessionizeId`, the venue's time zone (e.g. `America/Los_Angeles`), name, about
   text and links, and optionally a theme ([`EventTheme.kt`](app/shared/src/commonMain/kotlin/org/jetbrains/kotlinconf/event/EventTheme.kt)).
   For iOS, add `AppIcon-<id>`, `<id> launch` and `<id> launch background` to the asset catalog to give the build its
   own icon and launch screen.
4. Build with `-Pevent=<profile id>`:
   * Web: `./gradlew -Pevent=agntcon-mcpcon-na-2026 :app:webApp:wasmJsBrowserDevelopmentRun`
   * iOS to TestFlight: `scripts/ios-testflight.sh agntcon-mcpcon-na-2026 <bundle id> <display name> <team id> --upload`
     (needs an App Store Connect app record and API key; see the script)
   * Tests: `./gradlew -Pevent=agntcon-mcpcon-na-2026 :core:jvmTest :app:shared:jvmTest`

   Android is described above; the desktop app (`./gradlew :app:desktopApp:hotRun -DmainClass=org.jetbrains.kotlinconf.MainKt`)
   shares the code but has not been tried for this event.

## Tests

* `./gradlew :core:jvmTest :app:shared:jvmTest`: unit tests and tests on snapshots of real feeds ([`testdata`](testdata)).
* `live-feed-check.yml` runs the real-feed tests on the live Sessionize feed every six hours.
* [`e2e`](e2e): Maestro flows that drive the Android app on an emulator.
* `scripts/web-smoke-test.mjs` opens the built web app in headless Chromium; the Pages deploy runs it before publishing.
