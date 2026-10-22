[![JetBrains official project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) 

# KotlinConf App

This repository contains the source code of the official application for [KotlinConf](https://kotlinconf.com/).

![The KotlinConf official application](docs/header.png)

The application is written in Kotlin, sharing code between all of its platforms using [Kotlin Multiplatform](https://www.jetbrains.com/kotlin-multiplatform/).

* The client application for Android, iOS, desktop, and web is built with shared UI using [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).
* The backend application is powered by the [Ktor](https://ktor.io/) server-side framework and the [Exposed](https://www.jetbrains.com/help/exposed/home.html) database library.

The app is published for the following platforms:

* Android: [available on Google Play](https://play.google.com/store/apps/details?id=com.jetbrains.kotlinconf)
* iOS: [available from the App Store](https://apps.apple.com/us/app/kotlinconf/id1299196584)
* Web: [deployed to GitHub Pages](https://jetbrains.github.io/kotlinconf-app/)

## Building the project

To build the project locally:

1. [Set up your environment](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-setup.html) for Kotlin Multiplatform development.
2. Open the project in IntelliJ IDEA or Android Studio.
3. Run the desired platform
   * Android: use the `app.androidApp` run configuration
   * iOS: use `KotlinConfAppScheme`
   * Desktop: `./gradlew :app:desktopApp:hotRun -DmainClass=org.jetbrains.kotlinconf.MainKt`
   * Web: `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
   * Backend: `./gradlew :backend:run`

## Building for another Sessionize conference

This fork can be built for any conference that publishes its schedule on [Sessionize](https://sessionize.com).
Each build is for one conference, described by a profile in
[`EventProfiles.kt`](app/shared/src/commonMain/kotlin/org/jetbrains/kotlinconf/event/EventProfiles.kt).

1. Find the event's Sessionize API id. Event schedule pages that embed Sessionize contain URLs such as
   `https://sessionize.com/api/v2/{id}/view/GridSmart`; check that `https://sessionize.com/api/v2/{id}/view/All` returns JSON.
2. Add an `EventProfile` with that `sessionizeId`, the venue's time zone (e.g. `America/Los_Angeles`), name, about text and links.
3. Build with `-Pevent=<profile id>`, for example:
   * Web: `./gradlew -Pevent=agntcon-mcpcon-na-2026 :app:webApp:wasmJsBrowserDevelopmentRun`
   * Desktop: `./gradlew -Pevent=agntcon-mcpcon-na-2026 :app:desktopApp:run`
   * iOS (TestFlight): `scripts/ios-testflight.sh agntcon-mcpcon-na-2026 <bundle id> <display name> <team id> --upload`

A profile without an `apiUrl` needs no backend: the app reads the Sessionize feed directly, builds the schedule
filters from the feed's categories, and hides the features that need the backend (voting, feedback, Golden Kodee,
venue maps, partners, legal documents, push notifications). The default profile, `kotlinconf`, keeps the original app.

The backend (`:backend`) reads the same feed format; set `conference.timeZone` and the `sessionize.*` rules in
`application.yaml` for another conference.
