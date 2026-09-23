# ShakeIT

Turn your phone into a shake-activated torch.

This repository contains a **native Android prototype** built with Kotlin and
Jetpack Compose. The visual and interaction reference is the HTML prototype in
[`debug/mockup/shakeit-prototype.html`](debug/mockup/shakeit-prototype.html) —
the Compose UI reproduces it rather than substituting a generic Material design.

## What is implemented

| Area | Status |
| --- | --- |
| Home screen (status pill, hero, shake button, stats) | Done |
| Settings screen (4 groups, scrolling, dividers) | Done |
| OFF/ON hero blob — morph, colour and glow | Done |
| `@keyframes wobble` shake animation | Done |
| Navigation between the two screens (cross-fade + 16dp slide) | Done |
| Light / dark / system theme, with an animated background | Done |
| Settings controls: slider, segmented controls, switches, outline button | Done |
| Settings persistence (`SharedPreferences`) | Done |
| System back handling on Settings | Done |
| Previews for both screens at 390x844, light and dark | Done |
| Real flashlight — tap the hero, the flash actually moves | Done |
| Real shake detection — accelerometer, debounced, one shake one toggle | Done |
| Pocket guard — proximity sensor suppresses shake while covered | Done |
| Background detection — foreground service, works screen off and locked | Done |
| Screen-off sensor delivery — wake-up sensor, else a scoped partial wake lock | Done |
| Detection health — `ACTIVE` / `STALLED` / `INACTIVE` in the notification and on Home | Done |
| Battery-optimisation check + standard settings intent + vendor auto-start guidance | Done |
| Haptic tick on every real torch change | Done |
| UI mirrors hardware: torch state comes from `CameraManager.TorchCallback` | Done |

## What is still simulated

The torch, the shake and the background service are real — see
[How a shake becomes light](#how-a-shake-becomes-light). These are not:

- **Settings switches** — every control persists and animates, but none of them
  change behaviour yet. Sensitivity does not retune the detector, "Detection
  active" does not stop it, "Auto-off" and "Start after reboot" have no
  implementation behind them. The one exception in the Advanced group is
  **Background reliability**, which reports a real platform answer and opens the
  real system screen — see [Background reliability](#background-reliability).
- **The on-screen "Shake to toggle" button** — it stands in for a shake: the same
  wobble, then a real toggle 380ms in. Useful for testing the torch without
  shaking the phone.
- **Shizuku** — "Connect Shizuku" flips to "Connected" and disables itself.
- **Stats** — "Activations" counts real torch-ons and is persisted. "Time on
  today" and "Avg session" are the prototype's placeholder values.

## Structure

```
app/src/main/kotlin/com/shakeit/
  ShakeItApplication.kt        creates the engine, so it outlives every screen
  MainActivity.kt              single-activity host, starts the service, edge to edge
  engine/ShakeItEngine.kt      process-wide hardware owner + detection watchdog
  background/BatteryRestrictions.kt  is the app exempt from Doze, and how to fix it
  hardware/TorchController.kt  CameraManager torch + callback -> StateFlow
  hardware/ShakeDetector.kt    sensor thread, wake lock, accelerometer & proximity
  hardware/ShakeAlgorithm.kt   the shake rule: pure maths, no Android imports
  hardware/DetectionHealth.kt  pure rules: when a wake lock is needed, when it stalled
  hardware/Haptics.kt          one short tick per real torch change
  service/ShakeItService.kt    foreground service, keeps detection alive
  service/ShakeItNotification.kt  channel + status notification
  service/StopDetectionReceiver.kt the notification's Stop action
  state/ShakeItState.kt        settings + torch + nav state, SharedPreferences store
  ui/ShakeItApp.kt             theme resolution, animated background, nav host
  ui/ShakeItPreviews.kt        light/dark previews of both screens
  ui/navigation/               the two-screen cross-fade stack
  ui/home/BlobMath.kt          the blob's numbers: easing, radius, spline (no Compose)
  ui/home/BlobCanvas.kt        turns those numbers into a Path and paints the glow
  ui/home/Wobble.kt            the @keyframes wobble curve (no Compose UI)
  ui/home/HomeScreen.kt        Home layout: top bar, hero, shake button, stats
  ui/settings/                 SettingsScreen and its groups
  ui/components/               switch, slider, segmented control, buttons, pill,
                               and the shared `.screen` container
  ui/theme/                    palette, theme, typography

app/src/test/kotlin/com/shakeit/
  Assertions.kt                float-tolerant assertEquals (JUnit 4 has no Float overload)
  ui/home/BlobMathTest.kt      the port checked against a Double transcription of the JS
  ui/home/WobbleTest.kt        keyframes + CSS cubic-bezier easing
  state/ShakeItStateTest.kt    torch counting, clamping, snapshots, navigation
  hardware/ShakeAlgorithmTest.kt  synthetic motion: shakes, running, pockets, knocks
  hardware/DetectionHealthTest.kt wake-lock rule and the stall thresholds

.github/
  workflows/ci.yml             unit tests, lint, assembleDebug, PR failure report
  actions/prepare-build/       JDK + SDK licences + runner-tuned gradle.properties
  scripts/report_failures.py   turns a red build into one pull-request comment
```

The maths behind the blob, the wobble and the shake rule lives in files with no
Compose or Android import at all, which is what makes them testable on a plain
JVM — see [Continuous integration](#continuous-integration).

## How the prototype maps onto the code

The palette is a 1:1 port of the prototype's CSS custom properties — each field
of `ShakeItColors` names the variable it came from, so the two can be diffed
against each other.

| Prototype | Native |
| --- | --- |
| `:root` / `[data-theme="dark"]` custom properties | `LightShakeItColors` / `DarkShakeItColors` |
| `.screen-clip { transition: background .35s }` | `animateFloatAsState` + colour `lerp` on the root background |
| `.screen` / `.screen.hidden` transform + opacity | `ShakeItNavHost` — two `graphicsLayer` progress values, 300ms, CSS `ease` |
| `.screen.hidden { pointer-events: none }` | an input-consuming layer between the two screens |
| `.screen { padding: 18px 20px ... }` + safe-area insets | `ShakeItScreen` — `windowInsetsPadding(systemBars)` then 18/20/20 |
| `#blobPath` + the `frame()` loop | `BlobMath` (the numbers) + `rememberBlobShape` / `BlobCanvas` (drawing them) |
| `#blobPath.on` fill + `drop-shadow` | `onMix` driving a colour `lerp` and a `BlurMaskFilter` halo |
| `@keyframes wobble` | `Wobble` — keyframe tables sampled with per-segment CSS `ease` |
| `.switch` / `.segmented` / `input[type=range]` | `ShakeItSwitch` / `SegmentedControl` / `ShakeItSlider` |
| `:active { background: ... }` | press state via `collectIsPressedAsState`, ripple disabled |

### The blob

`rememberBlobShape` ports the prototype's animation loop exactly: the same
`OFF_CFG` / `ON_CFG` targets, the same `0.1` per-frame easing, the same
`wobble += 0.018` phase, the same 40-sample radius function and the same
Catmull-Rom to cubic Bezier `smoothPath`. The per-frame factors are normalised
against a 16.67ms reference frame so a 90Hz or 120Hz panel morphs at the same
speed as the 60fps original.

The path, points and native paint are allocated once and reused; the animated
values are read inside the draw block so the loop invalidates drawing only and
never triggers recomposition. The loop parks while Settings is on top.

### Controls

Material 3's `Switch` and `Slider` are intentionally not used. The prototype's
44x26 switch with an 18dp-travelling knob and its `accent-color` range input do
not correspond to any Material component, and hand-drawing them keeps the
metrics exact while insulating the code from Material 3 signature churn.

## How a shake becomes light

Three layers, one job each:

| Layer | Files | Responsibility |
| --- | --- | --- |
| Hardware | `hardware/TorchController.kt`, `hardware/ShakeDetector.kt`, `hardware/ShakeAlgorithm.kt`, `hardware/Haptics.kt` | Drive the flash, read the sensors, decide what counts as a shake |
| Engine | `engine/ShakeItEngine.kt`, `ShakeItApplication.kt` | One process-wide owner of that hardware, publishing `StateFlow`s |
| Hosts | `MainActivity.kt`, `service/ShakeItService.kt`, `ui/ShakeItApp.kt` | Ask the engine for changes, and render what it reports |

The rule that keeps them honest runs one way: **hardware state flows into the UI,
never back**. `ShakeItState.torchOn` has no toggle method — the only way it
changes is `onTorchStateChanged`, called by a collector on
`TorchController.torchOn`, which is fed by a `CameraManager.TorchCallback`. A tap,
the on-screen button, a shake with the screen locked and a camera app stealing
the flash all arrive through that same path, so the screen cannot disagree with
the light.

### Torch

`CameraManager.setTorchMode()` against the back camera's flash unit. It does not
open the camera and needs no `CAMERA` permission, which is why the manifest does
not declare one. `TorchCallback` mirrors the real state back, including
`onTorchModeUnavailable` — that is what tells the truth when another app takes
the flash. Every request is wrapped: a device with no flash logs and refuses, and
the UI stays put rather than showing a light that is not on. A successful change
also fires one short haptic tick, from the engine, so a shake with the phone in
your hand feels the same as a tap.

### Shake recognition

`ShakeAlgorithm` is pure Kotlin — no Android imports and no clock of its own, the
caller passes in the sensor timestamp — so the whole rule is asserted on the JVM
in `ShakeAlgorithmTest` against synthetic streams shaped like real motion: hand
shakes at several rates and amplitudes, running, walking, a knock, a car
accelerating, a pocket.

Gravity is removed with a low-pass filter whose coefficient comes from the actual
sample interval, so the cutoff is identical at 20 Hz and at 200 Hz. What is left
is grouped into *impulses* — a peak above the threshold, closed by hysteresis, by
turning around along the axis it pushed on, or by timing out — and a shake needs
all of this at once:

| Guard | Default | What it rejects |
| --- | --- | --- |
| Intensity: impulse peak | 14 m/s² (~1.4g) | walking, typing, setting the phone down |
| Rate: 3 impulses within 450 ms | ≈6 per second | a 2 Hz walking cadence, a single knock |
| Reversal: alternating sign on one axis | 2 reversals | a car accelerating, an escalator, running impacts |
| Separation: 300 ms of calm after firing | | the tail of the same shake toggling again |
| Cooldown: floor between toggles | 500 ms | short repeated bursts machine-gunning the flash |

The separation rule is what makes "one shake, one toggle" hold however long the
shake goes on: shaking for two seconds is one toggle, shaking again after a beat
is two. A deliberate 4 Hz shake is recognised roughly 360ms after it starts.

### Pocket guard

The proximity sensor decides whether the phone is covered — anything under
`min(maximumRange, 4 cm)`, a form that works for both the binary sensors most
phones have and real distance sensors. While covered the algorithm is not merely
quiet, it *forgets*: impulses are discarded as they arrive, so the jostling of a
pocket cannot be banked up and released the instant the phone is pulled out. A
device with no proximity sensor simply never reports covered.

### Background

`ShakeItService` is a foreground service, started by the activity and outliving
it, so detection continues with the screen off, the device locked and the task
swiped away. It owns no logic: it promotes itself to the foreground, hands the
sensors to the engine, and keeps its notification in step with the hardware —
"Listening for shakes — torch off" / "— torch on" / "Paused — phone looks
covered". `START_STICKY` brings it back after a low-memory kill.

#### Why it used to stop when the screen went off

A foreground service keeps the *process* alive. It does not keep the
*application processor* awake, and a non-wake-up sensor delivers nothing while
that processor is suspended — which is exactly what happens when the screen turns
off. `SensorManager` documents the requirement outright: to keep receiving events
with the screen off, hold a partial wake lock. So the earlier version had a live
service, a registered listener and a visible notification saying "Listening",
over an accelerometer that had stopped reporting. Nothing was wrong with the
service; the samples simply never arrived.

Four things now stand between that and working detection:

1. **A wake-up accelerometer is preferred** — `getDefaultSensor(TYPE_
   ACCELEROMETER, true)` on API 26+. A wake-up sensor wakes the processor itself
   for every event, so it needs no help. Most devices do not expose one, which is
   why this is a preference rather than the fix.
2. **A `PARTIAL_WAKE_LOCK`, held only while armed *and* the screen is off** — the
   documented requirement above. It is scoped to screen-off because with the
   screen on the processor is awake already, and scoped to armed because an idle
   app holding a lock is how ShakeIT would end up on a battery-usage report. A
   non-reference-counted lock makes acquire and release idempotent, so a stray
   screen broadcast cannot double-hold or early-release it.
3. **Sensor callbacks run on their own `HandlerThread`** — so a blocked, busy or
   OEM-frozen main thread cannot stall recognition, and a shake with the screen
   off never queues behind a composition that no longer exists.
4. **A watchdog measures the gap between samples** and turns it into a
   `DetectionStatus` of `ACTIVE`, `STALLED` or `INACTIVE`, so the failure can no
   longer be silent — see [Background reliability](#background-reliability).

The lock costs battery — a partial wake lock held through a night is measurable,
and that is the price of a detector that has to hear a shake with the screen off.
What keeps it honest is the scoping: nothing is held while the screen is on or
while detection is stopped, and the moment `stop()` runs the lock goes with it.

Doze is the limit of all this. Doze *ignores* partial wake locks, so an app that
has not been exempted from battery optimisation can be armed, alive and still
starved — which is what `STALLED` reports. Being set to "Unrestricted" in the
system UI is what makes the lock count.

The notification's Stop action goes to a `BroadcastReceiver` rather than a service
`PendingIntent`, because stopping a service is allowed from any app state while
starting one from the background is not. The service type is `specialUse` with
the reason spelled out in the manifest's `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
property, since none of the platform's types describe "read the accelerometer
while the screen is off". `POST_NOTIFICATIONS` is requested once on Android 13+;
refusing it hides the notification but does not stop detection.

### Background reliability

`DetectionStatus` is the honest answer to "is it working?", and it is derived
from facts rather than intentions: armed, an accelerometer was found, and how
long it has been since a sample arrived. `hardware/DetectionHealth.kt` holds the
two pure rules — when a wake lock is needed, and when the detector has gone
stale — and `app/src/test/.../DetectionHealthTest.kt` pins them down, because
the failure they describe only reproduces on a real device with the screen off.

The engine's watchdog asks the detector for the age of its newest sample every
two seconds and publishes the result. It appears in two places:

- the **notification**, so the truth is visible with the screen off —
  "Detection stalled — battery settings may be blocking it";
- the **Home status row**, "Shake Detection", which now reports the hardware
  instead of the Settings switch's stored value.

`background/BatteryRestrictions.kt` reads
`PowerManager.isIgnoringBatteryOptimizations` and, when the answer is no, the
Advanced settings card offers **Open battery settings** plus a named set of manual
steps for this device's power manager. Only standard AOSP intents are used:
`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and
`ACTION_APPLICATION_DETAILS_SETTINGS`. `ACTION_REQUEST_IGNORE_BATTERY_
OPTIMIZATIONS` — the yes/no dialog — is deliberately avoided: it needs a
permission Play restricts, and it decides for the user before they have read
anything.

When the app *is* already exempt, the row says so and offers nothing to tap. No
user is sent into system settings to fix a problem they do not have.

**Vendor auto-start managers cannot be opened programmatically.** XOS and MIUI
have no public, stable intent for their auto-start screens; anything that reaches
one does it by guessing a component name that an OTA can move. So ShakeIT detects
the vendor from `Build.MANUFACTURER` and describes the steps in words instead.
On Infinix, Tecno and itel (all Transsion, all XOS/HiOS):

> Settings → Battery & power saving → App management → ShakeIT → allow background
> activity, then enable ShakeIT under Auto-start / Startup manager.

XOS is among the most aggressive OEM power managers known — dontkillmyapp lists
Infinix as one of the worst offenders — and can freeze a foreground service
regardless of what the app does correctly. `onTaskRemoved` re-arms detection
rather than stopping the service, because on stock Android a removed task takes
the activity with it and leaves the foreground service running, which is the
behaviour this app is designed around.

## Testing background detection

The sequence below is what the fix has to survive. `adb logcat -s ShakeDetector
ShakeItEngine` prints the arming line, every wake-lock transition and every
status change, which is how a failure identifies itself.

1. Open the app; the service starts and the notification reads **Listening for
   shakes — torch off**.
2. Confirm the Home row says **Shake detection active**.
3. `adb logcat` should show `detection armed: wakeUpSensor=…, proximity=…,
   wakeLock=…`.
4. Check Settings → Advanced → **Background reliability**. If it says battery
   optimisation may stop detection, tap **Open battery settings** and set ShakeIT
   to *Unrestricted*; on Infinix also follow the auto-start steps printed under
   the button.
5. Shake the phone → the torch flips, haptic ticks, notification reads **torch
   on**.
6. Lock the screen (`logcat`: `screen off: holding a partial wake lock…` unless
   the device has a wake-up sensor).
7. Shake → the torch flips. This is the step that failed before the fix.
8. Unlock: the Home row and the notification agree with the torch's real state.
9. Press Home; wait 2 minutes; shake → the torch flips.
10. Open Recents and swipe ShakeIT away. The notification must remain.
11. Wait 2 minutes; shake → the torch flips. The activity is gone; the service is
    not.
12. Check the notification still reads **Listening** rather than **stalled** —
    that is the no-silent-stop guarantee.
13. Lock the screen, wait 10 minutes, shake → the torch flips.
14. If any step fails, the notification and the Home row should say **Detection
    stalled**; that means the process was frozen or starved by the OEM power
    manager, not that the listener was unregistered.
15. Re-check step 4's answer after the failure: on XOS the exemption can be
    revoked by the system, and it is the usual cause.

## Building

The project targets the toolchain already pinned in `gradle/libs.versions.toml`
— nothing was added or upgraded: AGP 8.13.0, Kotlin 2.1.0, Gradle 9.0.0,
compileSdk 36, minSdk 24, Compose BOM 2025.10.01 (Compose 1.9.4, Material 3
1.4.0).

```
./gradlew assembleDebug      # the app
./gradlew testDebugUnitTest  # JVM unit tests
./gradlew lintDebug          # Android Lint
```

### Note on `gradle.properties`

`gradle.properties` contains

```
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

which points at a **Termux** install of `aapt2` and is required there, because
the `aapt2` binary published to Maven does not run in that environment. On a
normal JDK/Android SDK machine that path does not exist and the build will fail
before compiling anything. Override it for a single build with

```
./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2
```

or comment the line out locally. It has been left in place on purpose so the
Termux workflow keeps working — CI rewrites its own copy of the file in the
runner checkout instead, and never commits the result.

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request against `master` and on
pushes to `master`, filtered by path: a commit that only touches the README or
the design mockup gets no runner time.

| Job | Task | Fails on |
| --- | --- | --- |
| Unit tests (JVM) | `:app:testDebugUnitTest` | a failing assertion |
| Android Lint | `:app:lintDebug` | a lint error (`ExpiredTargetSdkVersion` is disabled) |
| Compile debug APK | `:app:assembleDebug` | any compile, resource or packaging error |

The three jobs are independent on purpose — the APK is compiled even when the
tests or lint fail, because "does it still build" is the question that decides
what to fix next.

`gradle.properties` is tuned for a phone under Termux (one worker, no daemon,
2GB heap, Termux's `aapt2`), so `.github/actions/prepare-build` rewrites those
settings in the runner's checkout before building. The committed file is never
modified.

### The failure report

Each job tees its Gradle output into an artifact, and the `report` job runs
`.github/scripts/report_failures.py` over them. It extracts the Kotlin compiler
errors, resource errors, lint errors and failing test names — not the 40MB of
surrounding log — and posts them as a single comment on the pull request,
replacing the previous one on the next push. When a run goes green the stale
comment is deleted.

From a terminal, the same information is reachable with:

```
gh pr view --comments          # the report comment
gh run view <run-id> --log-failed   # the raw Gradle output
```

## Seams worth knowing about

**Manrope.** The prototype sets its display text (the OFF/ON word, the Settings
title, the stat numbers) in Manrope 700/800 and its body text in Roboto. Roboto
is the Android system font, so the body text already matches. Manrope is not
shipped with Android and no font binary is committed here, so
`DisplayFontFamily` in `ui/theme/Type.kt` currently resolves to the system font.
Drop `manrope_bold.ttf` and `manrope_extrabold.ttf` into
`app/src/main/res/font/` and point `DisplayFontFamily` at them — every display
style reads from that one value, so nothing else changes.

**Detection switches.** The Settings group's "Detection active", "Run in
background" and "Start after reboot" switches persist their values and nothing
else. Detection is armed whenever the service runs, and there is no boot
receiver. Wiring them means driving `engine.stopDetection()` /
`engine.stopService()` from `ShakeItState.detectionActive` and adding a
`BOOT_COMPLETED` receiver — the engine already exposes both halves. The Home
status row no longer reads the switch: it reports `ShakeItEngine.detectionStatus`,
which is derived from the detector, so the screen can read *active* while that
switch is off. Making the switch authoritative is the missing half.

**OEM background limits.** The app now does everything standard Android allows:
foreground service, screen-off-scoped wake lock, wake-up sensor when available,
own sensor thread, and a watchdog that reports starvation instead of hiding it —
see [Background reliability](#background-reliability). It still cannot survive a
manufacturer that freezes foreground services regardless, and it will not try to:
no hidden activity, no keep-alive hacks, no guessed vendor component names. What
remains out of scope is Shizuku, which could lift restrictions the public API
cannot.

**Dynamic color.** The Appearance group's "Dynamic color" switch is wired and
persisted. On Android 12+ it selects the device's wallpaper-derived Material
colour scheme, but ShakeIT's own palette is deliberately *not* re-tinted:
every component reads the brand purple and amber from `ShakeItColors`, which is
what keeps the UI matching the design reference. The prototype's switch has no
visual effect either. Widening it to re-tint the brand palette is a one-line
change in `ui/theme/Theme.kt` if that is ever wanted.
