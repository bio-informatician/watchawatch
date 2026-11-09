# WatchaWatch

WatchaWatch is a native Android time tracker built with Kotlin and Jetpack Compose.

## Features

- Start, pause, resume, and finish a persistent focus timer
- Organize work into color-coded projects
- Add time entries manually, including a task and optional note
- Review daily totals, a weekly chart, project breakdowns, and recent sessions
- Set a daily focus goal and switch between light and dark themes
- Export time entries as CSV
- Import WatchaWatch CSV backups, merge projects, and skip duplicate sessions
- Optional Force Timer, required task/note fields, and a rolling import lock
- Run a local logic audit for missing descriptions, invalid durations, missing projects, and sessions over 12 hours
- Track PTO allowance and usage
- Configure per-project currencies, billable rates, labor costs, retainers, time budgets, threshold alerts, and expenses
- Attach receipt images selected through Android's document picker
- Add one configurable custom field to timer sessions and manual entries
- Forecast project budget usage from the previous seven days of local entries
- Delete all locally stored settings, projects, entries, and active timer data
- Minimize an active timer into a movable floating clock, with an ongoing Android notification
- Keep projects and time entries locally on the device

## Open and run

Open this folder in Android Studio, let Gradle sync, and run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator. The project uses the Android Gradle Plugin 8.13.2, Kotlin 2.3.0, and targets Android 16 (API 36).

## Release build

The Play release bundle is signed with an upload key supplied locally through `WATCHAWATCH_UPLOAD_STORE_FILE`, `WATCHAWATCH_UPLOAD_STORE_PASSWORD`, `WATCHAWATCH_UPLOAD_KEY_ALIAS`, and `WATCHAWATCH_UPLOAD_KEY_PASSWORD`. Keep the keystore and passwords outside source control and back them up securely. `bundleRelease` fails when signing values are not configured; a debug-signed or unsigned bundle is not suitable for Play upload.

## Data behavior

Timer state, entries, project settings, PTO totals, expenses, and receipt images are stored locally. Timer state is saved in app-private Android `SharedPreferences`, so an active timer can be restored after closing and reopening the app. Elapsed time is derived from saved timestamps rather than a continuously running background process. The optional floating timer uses a foreground notification while it is active.

The app manifest requests no `INTERNET` permission, and the app includes no accounts, analytics, advertising, or remote API. Its Android cloud/device backup is disabled. CSV import reads the file explicitly selected through Android's document picker, validates it locally, merges matching project names, and skips duplicate sessions. CSV export writes only to the destination selected by the user; a selected cloud-backed provider may store that file outside the device. Deleting app data cannot remove copies previously exported elsewhere.

Features that require a special permission are opt-in and are grouped under **Optional permissions**. The app does not show a permission prompt on launch. Users can manage overlay access in that section; the floating timer itself starts only after the user explicitly minimizes an active session. Android asks for “display over other apps” if access has not already been granted. While enabled, a foreground service displays a draggable timer bubble and an ongoing notification. Tapping the bubble returns to WatchaWatch; the notification can dismiss the bubble without stopping the timer. Finishing the session removes the bubble.

The local audit uses explicit checks. Project forecasts use the recent seven-day average and do not contact an external service. Historical billable and labor rates are copied onto each newly created entry so later rate changes do not rewrite past calculations. Project financial totals remain separated by currency; no exchange-rate conversion is performed. Receipt images are copied into app-private storage and are removed by **Delete all local data**. Tapping **View receipt** opens Android's app chooser and temporarily grants the selected image to the viewer the user chooses.

Force Timer and the rolling timesheet lock are local controls, not tamper-proof enforcement: they can be changed by the device owner, and they do not replace a server-backed payroll or approval workflow. A lock blocks importing entries older than its configured window; this app currently has no editor for saved historical entries.

WatchaWatch does not record keystrokes, screenshots, app/site activity, or location. Team approvals, shared kiosk, schedules, SSO, and SCIM are not available in this offline single-device app.

Calendar sync, idle detection, natural-language commands, team scheduling/approvals, employee kiosk/PIN capture, geofencing, rate-history scheduling, and payroll/invoice workflows are not implemented. The audit and forecast use local rules and historical entries only. The app has no server or account system to support team governance or enterprise provisioning.

Local app data uses Android's private app storage but is not separately encrypted by WatchaWatch. Use a device screen lock to protect access. Android backup can still behave differently on customized devices or through root/ADB access.

## Privacy and legal scope

These are technical data-flow details and privacy controls, **not a certification of GDPR, US state privacy laws, employment/timekeeping laws, tax rules, or other legal compliance**. Legal obligations depend on who operates or distributes the app, its users, purpose, jurisdiction, and any employment/business context. Before a commercial or workplace deployment, the operator must identify the data controller/business, provide contact details, explain purposes and lawful bases, define retention and recipients, handle applicable rights and notices, and obtain jurisdiction-specific legal review. This repository does not supply those operator-specific facts or legal advice.
