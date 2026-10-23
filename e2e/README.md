# End-to-end flows

[Maestro](https://maestro.mobile.dev) flows that drive the real app on an emulator or simulator and check what an
attendee sees: the tabs open with the live schedule, a bookmark and a rating survive a restart, and "Add to calendar"
opens the share sheet with the `.ics` file.

```bash
brew install mobile-dev-inc/tap/maestro                 # needs Java 17+
adb install -r app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
maestro test e2e/flows                                  # all flows; or one file
```

The flows use the app id `com.muschenetz.agntcon` and the session "OpenAI MCPCon Happy Hour", which is in the event's
feed. If the event removes it, pick another session title in `bookmark-persists.yaml`,
`rating-and-takeaway-persist.yaml` and `add-to-calendar.yaml`. Every flow starts from a fresh install (`shared/start.yaml`).

On GitHub they run from **Actions → End-to-end (Android) → Run workflow** (an emulator takes a few minutes, so it is
manual).
