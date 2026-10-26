# Hand-off: photos, trip report and screen clean-up

State as of 25 October 2026, for whoever picks this up next. Features are described in the README; this note is
about where things stand and how to work on them.

## Repositories and branches

- **ingo/agntcon-app** is where TestFlight and web builds are made. Work happens here first, on
  `feat/conference-report` (PR #9, open).
- **ingo/sessionize-app** gets the same changes by cherry-pick, on `chore/sync-from-agntcon` (PR #22, open, stacked on
  PR #8). It also carries one extra commit that changes the app's identity: bundle `com.muschenetz.sessionize`, name
  "Sessionize", and its own `scripts/testflight.sh`. Leave that commit out when moving changes back.
- PR #7 on sessionize-app (a web start-up fix) is open on its own.

To move a commit across: in the sessionize-app checkout, run `git fetch <agntcon checkout> feat/conference-report`,
then `git cherry-pick <sha>`, run the tests and push.

## Where things stand

- The latest TestFlight build of AGNTCon is **94**: screen clean-up version 2 (finds more screens, removes colour
  casts, no washed-out results). It answers the build-93 feedback: "a few photos were missed in conversion", "more
  photos could be colour corrected and white balance adjusted", "some are a little washed out".
- **Still to check on a real phone:** whether the slide-outline detector (`VNDetectDocumentSegmentationRequest`) crops
  slides that fill the frame. It worked in the Mac prototype, but in the simulator it gives the same useless strip
  for every photo. The app rejects outlines that are not screen-shaped, so the worst case is no crop.
- **Known weak spot:** a photo of a screen that was already very pale still comes out pale.
- **Blocked:** the first Sessionize TestFlight build. The bundle ID `com.muschenetz.sessionize` and the App Store
  Connect record "Sessionize Schedule" (SKU `sessionize-app`) must be created by someone with the Team Admin role. The
  API key we have cannot register bundle IDs (it gets a 403).
- Ideas for later are GitHub issues #9 to #21 on ingo/sessionize-app.

## Map of the feature

All paths are under `app/shared/src/`.

| What | Where |
|---|---|
| Attachment model (`cleaned`, `cleanVersion`, `sourceId`, `contentHash`) | `commonMain/.../personal/Attachments.kt` |
| Adding, de-duplicating, importing, background clean-up loop | `commonMain/.../ConferenceService.kt` (`addAttachments`, `startImport`, `cleanUpScreenPhotos`) |
| Matching photos to sessions by time; flagging photos that are probably mismatched | `commonMain/.../media/MediaMatching.kt` (`MediaMatcher`, `groupMatches`, `findOutliers`) |
| Matching by slide text (OCR) | `commonMain/.../media/SlideText.kt` + `iosMain/.../SlideText.ios.kt` |
| Review screen for a batch of photos | `commonMain/.../screens/MediaImportScreen.kt`, `MediaImportViewModel.kt` |
| Trip report page, preview, zip | `screens/TripReportScreen.kt`, `ReportPreviewScreen.kt`, `nonWebMain/.../export/ReportBundler.kt`, `export/TripReportHtml.kt` |
| Picking photos and scanning the library (iOS) | `iosMain/.../media/MediaPicker.ios.kt` (loads 2 at a time, retries), `LibraryScan.ios.kt` |
| Screen clean-up (iOS only) | `iosMain/.../media/ScreenCleanUp.ios.kt`; version number in `commonMain/.../media/ScreenCleanUp.kt` |

Raising `SCREEN_CLEAN_UP_VERSION` makes every phone redo all its photos in the background the next time the trip
report opens. The new result replaces the old cleaned copy, and originals are never touched.

## How to

**Tests.** The Gradle toolchain needs JDK 21 passed in explicitly:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew -Porg.gradle.java.installations.paths=$JAVA_HOME --continue :core:jvmTest :app:shared:jvmTest :app:shared:compileKotlinIosArm64
```

**TestFlight upload.** Load the App Store Connect API key variables (`ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_PATH`;
Ingo keeps them in a local `.env`, never commit or print them), then run:

```bash
set -a; source <path to .env>; set +a
scripts/testflight.sh --upload
```

The archive always says build 79. App Store Connect gives the build the next number when it is exported, so check
the real number in App Store Connect.

**Read TestFlight feedback.** The AGNTCon app id is `6825671401`. With the same variables loaded:

```bash
python3 -I scripts/asc.py GET '/v1/apps/6825671401/betaFeedbackScreenshotSubmissions?limit=5&sort=-createdDate'
python3 -I scripts/asc.py GET '/v1/apps/6825671401/betaFeedbackCrashSubmissions?limit=50'
python3 -I scripts/asc.py GET '/v1/builds?filter[app]=6825671401&sort=-uploadedDate&limit=3'
```

Each screenshot submission carries its comment, the build and download URLs for the screenshots.

**Run in the simulator.** A Debug build has the bundle id `com.kotlinconf.iosapp`, so it installs as a second app.
Override it to replace the AGNTCon app:

```bash
xcodebuild -project app/iosApp/KotlinConf.xcodeproj -scheme KotlinConfAppScheme -configuration Debug \
  -destination 'id=<simulator id>' -derivedDataPath /tmp/dd PRODUCT_BUNDLE_IDENTIFIER=com.muschenetz.agntcon build
xcrun simctl install <simulator id> /tmp/dd/Build/Products/Debug-iphonesimulator/KotlinConf.app
```

To test with real photos without the photo picker:
1. Terminate the app.
2. Copy the files into `<app data>/Library/Application Support/files/2026/media/`.
3. Add entries to the JSON string under the key `2026_attachments` in
   `<app data>/Library/Preferences/com.muschenetz.agntcon.plist`. The format is
   `{sessionId: [{id, kind, file, addedAt}]}`; edit it with Python's `plistlib`, not `defaults write`.
4. Open the trip report, which starts the clean-up.

Use `xcrun simctl get_app_container <id> com.muschenetz.agntcon data` to find `<app data>`.

**Look at a tester's real data.** With the phone connected, the TestFlight app's container can be read:

```bash
xcrun devicectl device copy from --device <udid> --domain-type appDataContainer \
  --domain-identifier com.muschenetz.agntcon --source "Library/Preferences/com.muschenetz.agntcon.plist" --destination prefs.plist
```

Use the same command with `--source "Library/Application Support/files/2026/media/<file>"` for photos.
`idevicesyslog -u <udid>` shows device logs, for example when the app is killed for using too much memory.

**Tune the clean-up on a Mac.** Use `scripts/tuning/screen-cleanup.swift` and `contact-sheet.py`; the header of the
Swift file explains how. Version 2 was tuned on 48 real photos (22 the old version skipped, 26 it cleaned).
To compare two builds on real photos, export the trip report from each and run `scripts/tuning/compare-exports.swift`
(cast, brightness, blown highlights and lost shadows per group, plus original | A | B sheets); it needs no copy of the
clean-up logic, because it only reads the pictures the app wrote.

## Things that cost time

- In the simulator, Vision's document outline returns the same strip along the bottom of every photo. Rectangle and
  text detection work.
- Pillow does not colour-manage. Display P3 photos look shifted in a contact sheet; convert them with `sips` first.
- `CIColorMatrix` with a bias gives an infinite extent; crop the result.
- With 200 photos the app used to die: iOS kills it above about 3.4 GB, and the system's Photos services die if all
  items are requested at once. The fix is to load 2 at a time with retries, use 480 px thumbnails, and run detection
  and OCR on small copies. Peak memory is now about 540 MB.
- Kotlin/Native names: `CIVector(x =, Y =, Z =, W =)` (the later arguments are upper case). The extension properties
  for `AVMetadataItem` must be imported one by one. When a name doesn't resolve, look it up with
  `klib dump-metadata` on the platform klib.
- zsh does not split words on `$var`, which breaks `set -- $out`-style shell loops.
