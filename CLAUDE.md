# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

The AGNTCon + MCPCon North America 2026 schedule app, an unofficial app built on JetBrains' open-source
[KotlinConf app](https://github.com/JetBrains/kotlinconf-app) (Apache-2.0): Kotlin Multiplatform with Compose
Multiplatform for iOS, Android, JVM desktop and web. It reads the event's public Sessionize `view/All` feed; there is
no backend, and everything the attendee adds (bookmarks, notes, ratings, people met) stays on the device. TestFlight
builds and the GitHub Pages web app are published from this repository.

## Hand-off

Read `docs/handoff.md` first: where the photo, trip report and screen clean-up work stands, what is blocked, and how
to test, upload to TestFlight and read tester feedback.

## Commands

```bash
./gradlew :core:jvmTest :app:shared:jvmTest              # tests (snapshots of real feeds in testdata/)
./gradlew :app:webApp:wasmJsBrowserDevelopmentRun         # web app
./gradlew :app:androidApp:assembleDebug                   # Android (needs sdk.dir in local.properties)
scripts/testflight.sh --upload                            # build and upload the iOS app to TestFlight
maestro test e2e/flows                                    # end-to-end flows on a running Android emulator
```

The event profile is `-Pevent=<id>` (default `agntcon-mcpcon-na-2026`); iOS reads it from `ORG_GRADLE_PROJECT_event`.

## Layout

```
:core                 shared data model and the Sessionize feed parser. No UI.
:app:ui-components    reusable Compose components and theme; own resource class UiRes.
:app:shared           screens, ViewModels, navigation, ConferenceService, DI graph (Metro).
:app:androidApp / :app:desktopApp / :app:webApp / app/iosApp   platform entry points
scripts/              TestFlight upload, web branding, web smoke test
e2e/                  Maestro flows
```

`event/EventProfiles.kt` holds everything specific to the conference (Sessionize id, time zone, links, colours,
venue map). `ConferenceService` is the central singleton. Routes are in `navigation/Routes.kt`.

## Conventions

- **No network except Sessionize.** No analytics, trackers, crash reporters or push services. Anything that makes a
  request must be explained to users.
- **Storage:** keep `ApplicationStorageImpl.LATEST_STORAGE_VERSION` unchanged unless you add a migration; a different
  value wipes installed users' data.
- **Strings:** every user-facing string needs an English and a German entry.
- **Versions:** keep Android `build.gradle.kts`, iOS `project.pbxproj`/`Info.plist` and `values/version.xml` in step.
- **Credit:** keep the JetBrains credit, the Apache-2.0 licence and the "unofficial, not affiliated" wording.
