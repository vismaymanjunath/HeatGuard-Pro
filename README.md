# HeatGuard Pro

A local-first Android app that **monitors, predicts and helps prevent smartphone overheating**.
Native Kotlin, Jetpack Compose and Material 3. No backend and no account. Everything runs on the device;
the only thing that uses the network is the **optional, opt-in AI assistant** (see below).

> Everything shown comes from the phone's real battery and thermal APIs, or from a clearly
> labelled simulation (Demo Mode). Nothing is invented, and the app never pretends to control
> charging, close other apps, or identify which app is producing heat.

## What it does

| Area | What you get | Where it lives |
|---|---|---|
| Live monitoring | Real battery temperature in °C/°F, green/orange/red indicator, configurable threshold (default 40 °C) | `data/battery`, `domain/thermal`, `ui/dashboard` |
| Alerts | Exact warning "Your phone is hot: stop gaming or charging", notification, vibration, hysteresis so it does not repeat | `domain/alert`, `platform/AlertNotifier` |
| Background monitoring | Foreground service with a persistent notification and a Stop action | `platform/MonitoringService` |
| Incidents | Room database: start, initial and peak temperature, duration, resolution, battery %, charging type, timeline | `domain/incident`, `data/db`, `ui/history` |
| Prediction | Linear trend over the last 5 minutes, estimated time to threshold with a confidence level | `domain/thermal/TemperatureTrend.kt` |
| Android thermal APIs | `PowerManager` thermal status and thermal headroom where supported, "not reported" where not | `data/thermal` |
| Thermal Risk Index | 0-100 score from six weighted, inspectable factors with safety floors | `domain/thermal/ThermalRiskIndex.kt` |
| Pre-call readiness | Ready / Caution / Cool down first from the live temperature, trend, Android thermal status and your own cooling history. Never promises a call duration | `domain/readiness`, `ui/dashboard/PreCallReadinessCard.kt` |
| Personal cooling profile | Your average cooling rate, average and fastest recovery and an estimated recovery time, from stored sessions and incidents; "Insufficient data" until there are 3 recoveries | `domain/cooling/PersonalCoolingProfile.kt`, `ui/coach` |
| AI assistant | Chat about your phone with Groq `openai/gpt-oss-120b`, grounded in live phone data, from every screen | `domain/ai`, `data/ai`, `ui/assistant` |
| Cooling Coach | Context-aware advice, interactive cooling sessions with a recovery timer, chart and statistics | `domain/cooling`, `ui/coach` |
| Profiles | Work, Navigation, Gaming, Charging, each with its own alert settings | `domain/profile` |
| Analytics | Live 30-minute graph, incident charts, daily/weekly insights, charging analysis, recommendations from local data | `domain/analytics`, `ui/analytics`, `ui/insights` |
| Export | CSV (incidents, readings, cooling sessions) and a text report through the share sheet | `domain/analytics/CsvExporter.kt`, `platform/ExportManager.kt` |
| Integrations | Home-screen widget, Quick Settings tile | `platform/TemperatureWidgetProvider.kt`, `platform/HeatTileService.kt` |
| Demo Mode | Simulated temperature through the *same* pipeline, labelled and kept apart from real data | `domain/demo`, `data/demo`, `ui/demo` |

## Architecture

```
 Android battery broadcast ─┐                         ┌─> Dashboard / Live / Insights (Compose)
 PowerManager thermal APIs ─┼─> ThermalRepository ────┤
 Demo simulator (DEMO tag) ─┘   (single pipeline)     └─> MonitoringCoordinator ─> IncidentTracker ─> Room
                                      │                          │
                                      │                          └─> AlertNotifier (notification + vibration)
                                      └─> prediction, risk index, history window
```

* **MVVM + repository.** Screens are Compose; each has a `ViewModel` exposing a `StateFlow`.
* **Pure domain layer.** Temperature rules, hysteresis, prediction, risk index, insights, CSV and the demo
  simulator are plain Kotlin with no Android types, so they are unit tested on the JVM.
* **One pipeline.** `ThermalRepository` is the only place readings are processed. Real and demo readings
  are the same `BatterySnapshot` type, distinguished only by a `source` tag.
* **Separation of demo data.** Demo incidents and sessions are stored with a `DEMO` tag, shown only while Demo Mode
  is on, and deleted when it ends. Exports contain real data only.
* **Manual dependency injection** (`AppContainer`) keeps the app small and the wiring readable.

## Build, test, run

Requirements: Android Studio (or a JDK 21+ and the Android SDK 37). The Gradle wrapper downloads Gradle 9.6 and a
JDK 25 toolchain on first use.

```powershell
.\gradlew.bat assembleDebug          # build the debug APK
.\gradlew.bat testDebugUnitTest      # JVM unit tests
.\gradlew.bat lintDebug              # Android lint
.\gradlew.bat assembleRelease        # R8-optimised release (unsigned)
.\gradlew.bat connectedDebugAndroidTest   # UI smoke tests; needs a device or emulator
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Install with
`adb install -r app\build\outputs\apk\debug\app-debug.apk`.

If a first build stalls while downloading the JDK or a large dependency on a slow connection, run it again; the
downloads resume.

## Permissions and privacy

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` (Android 13+) | Overheating alerts and the monitoring notification. Requested when you start monitoring. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep monitoring while the app is closed. Always started by you, stoppable from the notification. |
| `VIBRATE` | Vibrate on an overheating alert (can be turned off). |
| `INTERNET` | Used **only** by the AI assistant, after you opt in, to send questions to Groq over HTTPS. Nothing else in the app uses the network. |

Apart from the AI assistant, nothing leaves the device unless you export or share it. Incident history and the
encrypted API key are excluded from cloud backup.

## AI assistant

An opt-in chat (the sparkle button on every screen) that answers questions about your phone using
**Groq's `openai/gpt-oss-120b`** model.

* **Bring your own key.** There is no key in the code or the APK. You paste your Groq API key once; it is encrypted
  with a non-exportable Android Keystore key and excluded from backups. Remove it any time in Settings.
* **Explicit consent.** Nothing is sent until you tick the consent box. A switch lets you ask general questions
  without sharing any phone data.
* **What is sent:** your question and recent chat, plus a text summary of battery temperature, trend, forecast,
  risk index, Android thermal status, readiness, battery/charging, memory, storage, power saver, brightness,
  uptime, device model and Android version, your incident history and cooling profile, the screen you are on and the
  current time. **Never sent:** name, email, contacts, location, installed apps or files. The **eye icon** in the
  chat shows the exact text that would be sent with your next question.
* **Demo Mode:** the summary is marked as simulated and real device details are left out.
* **Grounded and honest:** the model is told to use only the supplied data, never to invent readings, never to
  claim to know which app causes heat, never to promise call duration or safety, and to say when a reading is
  stale. Every reading is described by its age.
* **Reliable timing:** the clock is read once per question so every age is consistent; requests have connect and
  read timeouts, transient failures (rate limits, 5xx, dropped connections) are retried with back-off, a hard
  two-minute deadline guarantees an answer or a clear error, Stop cancels the connection immediately, and a
  cancelled answer can never overwrite the next one.
* The chat lives in memory only and is cleared when the app process ends.

Answers are AI-generated and can be wrong; the app says so.

## Honest limits

* Battery temperature is the only temperature Android exposes to apps. It is not the CPU or skin temperature.
* The forecast is a straight-line estimate from recent readings, not a physical model.
* Thermal headroom is not reported by many phones; the app says so instead of guessing.
* The Thermal Risk Index and the charging weights are a transparent heuristic, not a manufacturer measurement.
* Monitoring does not restart after a reboot (Android restricts starting this service at boot); open the app and tap Start.
* The charging analysis shows when incidents *begin*, not what caused them.
* The live 30-minute graph is held in memory; incident readings are what is saved.

## Demo script (about 5 minutes)

Use **Demo Mode** so the story is repeatable. Settings, then Demo Mode, then Open demo controls.

1. **Dashboard, real data (30 s).** Show the live reading, status colour and charging state. Point out the
   "monitoring" card and that the temperature is the real battery sensor.
2. **Turn on Demo Mode (15 s).** The blue "DEMO MODE · simulated data" banner appears everywhere.
3. **Full cycle (about 5 min; talk over it).** Pick *Full cycle* and go back to the Dashboard. The simulation starts
   from 28 °C and warms at about 5 °C per minute.
   * After roughly a minute the forecast changes from "collecting" to "Reaches threshold in about N min"
     and counts down.
   * The Thermal Risk Index climbs; tap "How this is calculated" to show the factors.
   * The ring turns orange, then red, with the warning "Your phone is hot: stop gaming or charging"
     and a labelled demo notification with vibration.
4. **History (30 s).** The incident was recorded with peak, duration and a timeline chart, marked as simulated.
5. **Cooling Coach (60 s).** While it cools, open Coach: charging-aware advice, then start a cooling session and
   watch the recovery timer, chart and progress. Finish to see the recovery statistics.
6. **Insights (30 s).** Weekly chart, time-of-day pattern, charging analysis and recommendations from the demo data.
7. **Switch scenario (30 s).** *Charging heat* shows charging-aware advice; Manual lets you drag the temperature.
8. **Exit Demo Mode.** All demo data disappears and History is back to real incidents only. Mention the widget and
   the Quick Settings tile (Settings, Widget and Quick Settings) and that CSV export is on History and Insights.

## Testing

* **Unit tests (JVM):** temperature conversion, classification and hysteresis; battery parsing (including missing
  and invalid values); incident tracking; prediction; risk index; cooling sessions and recovery statistics; advice;
  insights; CSV; settings and profiles (against a real DataStore); the demo simulator; and an end-to-end
  Demo Mode pipeline test that proves demo readings produce demo-tagged incidents and alerts while real data is untouched.
  The AI client is tested against a local HTTP server (streaming, 401, rate-limit retry with `Retry-After`,
  5xx back-off, the reasoning-parameter fallback, timeouts, dropped connections), and the chat controller with a
  fake client (consent and key gating, privacy switch, retry, stop, late fragments ignored, hard deadline).
* **Instrumented smoke tests** (`app/src/androidTest`) launch the app and visit all six sections. They need a
  device or emulator to run.
* **Lint** is kept at zero errors.
