# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

An unofficial, open-source conference app for events that publish their schedule on
[Sessionize](https://sessionize.com): Kotlin Multiplatform with Compose Multiplatform for Android, iOS,
JVM desktop and web. There is no backend: the app reads the event's public Sessionize `view/All` feed and keeps
everything the attendee adds (bookmarks, notes, ratings, people met) on the device.

It is a derivative of JetBrains' open-source [KotlinConf app](https://github.com/JetBrains/kotlinconf-app)
(Apache-2.0). Keep the credit, the licence and the "unofficial, not affiliated" wording intact.

## Commands

```bash
# Tests (JVM)
./gradlew :core:jvmTest :app:shared:jvmTest

# Web app (wasmJs, development mode)
./gradlew :app:webApp:wasmJsBrowserDevelopmentRun

# Desktop app
./gradlew :app:desktopApp:hotRun -DmainClass=org.jetbrains.kotlinconf.MainKt

# Bump the version across platforms + regenerate library definitions
./gradlew prepareRelease
```

Android uses the `app.androidApp` run configuration; iOS uses the `KotlinConfAppScheme` scheme. The conference
profile is chosen at build time with `-Pevent=<id>`.

## Modules

```
:core                 shared data model and the Sessionize feed parser (Conference, Session, Speaker, ...). No UI.
:app:ui-components    reusable Compose components and theme (KotlinConfTheme); own resource class UiRes.
:app:shared           the client: screens, ViewModels, navigation, ConferenceService, DI graph.
                      Targets: android, jvm, iosArm64, iosSimulatorArm64, wasmJs, js.
:app:androidApp       Android entry point.   :app:desktopApp  JVM entry point.   :app:webApp  web entry points.
```

## Client architecture

- **DI: Metro.** `AppGraph` (`AppScope`) is per process; `YearGraph` (`YearScope`) is a sub-graph per active
  conference holding `YearlyApi` and `YearlyStorage`. ViewModels register with `@ContributesIntoMap(AppScope::class)`
  and `@ViewModelKey`.
- **`ConferenceService`** is the central singleton: it loads the feed through `YearlyApi`, exposes `agenda`, `speakers`,
  `conferenceInfo`, notes, ratings and "people met", schedules local reminders, and runs optional bookmark sync with the
  event's Sessionize web app.
- **Event profiles** (`event/EventProfile.kt`, `EventProfiles.kt`): name, Sessionize id, time zone, links, theme and
  map per conference. Everything event-specific lives there; nothing else should mention a conference by name.
- **Navigation:** `androidx.navigation3`; routes are in `navigation/Routes.kt`, wiring in `NavHost.kt`.
- **Storage:** multiplatform-settings with a per-conference key prefix. Keep `LATEST_STORAGE_VERSION` unchanged unless
  you also write a migration; a different value wipes saved data.
- **Source sets:** `nonAndroidMain` (ios, jvm, web), `nonWebMain` (android, ios, jvm), `webMain` (wasmJs + js).

## Conventions

- **No network except Sessionize.** No analytics, trackers, crash reporters, remote images proxies or push services.
  Anything new that makes a request must be listed in the privacy statement (About the app).
- **Be kind to Sessionize's servers:** conditional requests where possible, no tight polling loops.
- **Branding:** Sessionize's name and logo are trademarks of Web-ideja doo. Use the colours, not the logo, and keep the
  "unofficial" notices.
- **Version:** keep Android `build.gradle.kts`, iOS `project.pbxproj`/`Info.plist` and
  `composeResources/values/version.xml` in step; use `./gradlew prepareRelease`.
- **Resources:** `app:shared` uses `org.jetbrains.kotlinconf.generated.resources`; `app:ui-components` uses `UiRes`.
  Don't mix them. Every user-facing string needs an English and a German entry.
