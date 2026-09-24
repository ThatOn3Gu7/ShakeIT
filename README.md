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
| Detection health — five truthful states in the notification and on Home | Done |
| Self-healing watchdog — teardown, rebuild, re-register, verify, budgeted backoff | Done |
| Significant-motion trigger as a hardware wake/recovery path (not a shake) | Done |
| `onTaskRemoved` treated as a recovery point, not a no-op | Done |
| Battery-optimisation check + standard settings intent + vendor guidance | Done |
| Diagnostics page — sensors, wake lock, power policy, last exit, Shizuku, device | Done |
| `ApplicationExitInfo` — why the previous process died, on Android 11+ | Done |
| Low Power Standby and the background app-op reported as their own mechanisms | Done |
| Shizuku — real connection state, permission flow, Doze and app-op repairs | Done |
| Settings switches wired for real: detection, background, auto-off, reboot, sensitivity | Done |
| Boot receiver — "Start after reboot" restarts detection after a reboot or update | Done |
| Haptic tick on every real torch change | Done |
| UI mirrors hardware: torch state comes from `CameraManager.TorchCallback` | Done |

## What is still simulated

The torch, the shake, the background service, the settings switches and the
Shizuku integration are real — see
[How a shake becomes light](#how-a-shake-becomes-light) and
[Background reliability](#background-reliability). These are not:

- **Two of the three gesture options.** The recognition layer implements
  **Shake**. **Double-shake** and **Flip & shake** come from the prototype's
  segmented control and are not implemented; selecting either says so in the row
  itself and detection keeps using Shake. A control that looks like it changes
  behaviour without changing it is the same lie as a notification that claims to
  be listening.
- **Vendor auto-start.** Every diagnostics row that reports a real answer does
  so from an API. This one has no API: no manufacturer publishes a supported way
  to ask whether a package may auto-start, so the row says *unknown* and the
  Advanced card describes the screens to check by hand.
- **The on-screen "Shake to toggle" button** — it stands in for a shake: the same
  wobble, then a real toggle 380ms in. Useful for testing the torch without
  shaking the phone.
- **Stats** — "Activations" counts real torch-ons and is persisted. "Time on
  today" and "Avg session" are the prototype's placeholder values.

## Structure

```
app/src/main/kotlin/com/shakeit/
  ShakeItApplication.kt        creates the engine, so it outlives every screen
  MainActivity.kt              single-activity host; the only place a service start
                               is attempted on the user's behalf
  engine/ShakeItEngine.kt      process-wide hardware owner: watchdog, recovery
                               budget, preference listener, Shizuku repairs
  engine/Diagnostics.kt        DiagnosticsSnapshot + PrivilegedAction (plain data)
  background/PowerDiagnostics.kt   Doze, app-ops, auto-start, Low Power Standby —
                               each mechanism read and reported separately
  background/ProcessExitDiagnostics.kt  why the previous process died (API 30+)
  background/ShizukuController.kt  connection state, permission flow, privileged
                               commands, and the parsers for their output
  hardware/TorchController.kt  CameraManager torch + callback -> StateFlow
  hardware/ShakeDetector.kt    sensor thread, wake lock, accelerometer, proximity,
                               significant-motion trigger, teardown + rebuild
  hardware/ShakeAlgorithm.kt   the shake rule + the sensitivity ladder: pure maths
  hardware/DetectionHealth.kt  pure rules: when a wake lock is needed, which of the
                               five states applies, how long to wait before a retry
  hardware/SensorDiagnostics.kt the sensor stack's own facts, as plain data
  hardware/Haptics.kt          one short tick per real torch change
  service/ShakeItService.kt    foreground service: foreground first, then sensors
  service/ShakeItNotification.kt  channel + status notification + Diagnostics action
  service/StopDetectionReceiver.kt the notification's Stop action
  service/BootReceiver.kt      BOOT_COMPLETED / MY_PACKAGE_REPLACED, preference-gated
  state/ShakeItState.kt        settings + torch + nav state, SharedPreferences store
  ui/ShakeItApp.kt             theme resolution, animated background, nav host
  ui/ShakeItPreviews.kt        light/dark previews of both screens
  ui/navigation/               the two-screen cross-fade stack
  ui/home/BlobMath.kt          the blob's numbers: easing, radius, spline (no Compose)
  ui/home/BlobCanvas.kt        turns those numbers into a Path and paints the glow
  ui/home/Wobble.kt            the @keyframes wobble curve (no Compose UI)
  ui/home/HomeScreen.kt        Home layout: top bar, hero, shake button, stats
  ui/settings/SettingsScreen.kt  the four cards, plus the diagnostics report
  ui/settings/DiagnosticsText.kt facts into sentences: pure, no Context, testable
  ui/components/               switch, slider, segmented control, buttons, pill,
                               and the shared `.screen` container
  ui/theme/                    palette, theme, typography

app/src/test/kotlin/com/shakeit/
  Assertions.kt                float-tolerant assertEquals (JUnit 4 has no Float overload)
  ui/home/BlobMathTest.kt      the port checked against a Double transcription of the JS
  ui/home/WobbleTest.kt        keyframes + CSS cubic-bezier easing
  ui/settings/DiagnosticsTextTest.kt  the wording of every diagnostic line
  state/ShakeItStateTest.kt    torch counting, clamping, snapshots, navigation
  hardware/ShakeAlgorithmTest.kt  synthetic motion: shakes, running, pockets, knocks
  hardware/ShakeConfigTest.kt  the sensitivity ladder moves one thing and one thing only
  hardware/DetectionHealthTest.kt wake-lock rule, the five states, the retry backoff
  background/BackgroundParsingTest.kt allowlist and app-op output, exit codes

.github/
  workflows/android-ci.yml     unit tests, lint, assembleDebug, PR failure report
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
swiped away. It owns no logic, and the order of what it does in `onStartCommand`
is not incidental:

1. `startForeground` — a service started with `startForegroundService` has only a
   few seconds to make that call or the system kills it, so nothing else happens
   first;
2. arm the sensors — idempotent, and repeated on every start, so a restart by the
   system re-arms rather than leaving a notification over a dead listener;
3. keep the notification in step with the hardware.

`START_STICKY` brings it back after a low-memory kill, `stopWithTask="false"`
keeps it when the task is swiped away, and `onTaskRemoved` is treated as a
*recovery point* rather than a no-op: some OEM power managers read a removed task
as a reason to freeze the package, which stops sample delivery while leaving
everything looking started.

#### Why it used to stop when the screen went off

A foreground service keeps the *process* alive. It does not keep the
*application processor* awake, and a non-wake-up sensor delivers nothing while
that processor is suspended — which is exactly what happens when the screen turns
off. `SensorManager` documents the requirement outright: to keep receiving events
with the screen off, hold a partial wake lock. So the earlier version had a live
service, a registered listener and a visible notification saying "Listening",
over an accelerometer that had stopped reporting. Nothing was wrong with the
service; the samples simply never arrived.

Five things now stand between that and working detection:

1. **A wake-up accelerometer is preferred** — `getDefaultSensor(TYPE_
   ACCELEROMETER, true)` on API 26+. A wake-up sensor wakes the processor itself
   for every event, so it needs no help. Most devices do not expose one, which is
   why this is a preference rather than the fix, and why the diagnostics page
   reports which one was found.
2. **A `PARTIAL_WAKE_LOCK`, held only while armed *and* the screen is off** — the
   documented requirement above. It is scoped to screen-off because with the
   screen on the processor is awake already, and scoped to armed because an idle
   app holding a lock is how ShakeIT would end up on a battery-usage report. A
   non-reference-counted lock makes acquire and release idempotent, so a stray
   screen broadcast cannot double-hold or early-release it. A rebuild releases
   and re-evaluates it rather than inheriting whatever the previous listener
   needed.
3. **Sensor callbacks run on their own `HandlerThread`** — so a blocked, busy or
   OEM-frozen main thread cannot stall recognition, and a shake with the screen
   off never queues behind a composition that no longer exists.
4. **A watchdog measures the gap between samples** and repairs what it can —
   see [The watchdog](#the-watchdog).
5. **A `TYPE_SIGNIFICANT_MOTION` trigger** as a hardware wake path —
   see [Significant motion](#significant-motion).

The lock costs battery — a partial wake lock held through a night is measurable,
and that is the price of a detector that has to hear a shake with the screen off.
What keeps it honest is the scoping: nothing is held while the screen is on or
while detection is stopped, and the moment `stop()` runs the lock goes with it.

Doze is the limit of all this, and Android 14's Low Power Standby is a second
one: both *ignore* partial wake locks, so an app that has not been exempted can
be armed, alive, holding a lock and still starved — which is what `STALLED`
reports rather than hides. See
[Background reliability](#background-reliability).

The notification's Stop action goes to a `BroadcastReceiver` rather than a service
`PendingIntent`, because stopping a service is allowed from any app state while
starting one from the background is not. The service type is `specialUse` with
the reason spelled out in the manifest's `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
property, since none of the platform's types describe "read the accelerometer
while the screen is off". `POST_NOTIFICATIONS` is requested once on Android 13+;
refusing it hides the notification but does not stop detection.

#### The watchdog

Reporting a stall is only half of it. Every two seconds, on `Dispatchers.Default`
rather than the main thread — because a frozen main thread is one of the things
being survived — the engine asks the detector for the age of its newest sample
and derives a `DetectionStatus` from facts, not intentions. When the answer is
"armed, and nothing has arrived for three seconds", it rebuilds:

- tear down the listener and the sensor thread;
- release the wake lock and recompute whether one is needed;
- re-select the best accelerometer, preferring a wake-up one;
- re-register the accelerometer, the proximity sensor and the motion trigger;
- re-acquire the lock if the new sensor set needs it;
- reset the shake algorithm, so a stale burst cannot fire on the first new sample;
- and only call the result `ACTIVE` once a *real sample* has arrived.

`SensorManager.registerListener` returns a boolean and the detector reads it: a
registration the platform refused never becomes "running", which is how a device
that will not deliver ends up reported as `NO_SENSOR` instead of silently
claiming to listen.

Rebuilding is rate-limited, because a device that has stopped delivering for good
would otherwise be churned every two seconds all night. Five attempts per stall,
two seconds apart at first and doubling to a five-minute ceiling
(`recoveryDelayMillis`, pure and unit-tested). Real samples win at any point, and
thirty seconds of stable delivery restores the whole budget. Two events are
strong enough evidence that the device is awake to be worth an extra attempt
outside the backoff: the significant-motion trigger firing, and the task being
swiped out of Recents.

#### Significant motion

`Sensor.TYPE_SIGNIFICANT_MOTION` is registered while detection is armed, and it
is *not* a shake detector. It is far broader than a deliberate shake — walking
across a room fires it — and toggling the torch on it would undo everything the
shake rule is tuned for. What it is good for is waking the application processor
from a state in which nothing else arrives: when it fires, the device has just
proved it can still deliver, so the engine treats it as a recovery event, waits
750 ms for the accelerometer to resume on its own, and rebuilds if it has not.
Devices without the sensor simply never get the extra attempt; nothing else
changes.

#### Starting after a reboot

`BootReceiver` handles `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` (an app update
kills the process exactly like a reboot does, and from the user's point of view
"it stopped working after the update" is the same failure). It is gated on three
persisted switches — **Start after reboot**, **Detection active** and **Run in
background** — so a user who turned the first one off is not handed a foreground
service on every boot, and a user who turned detection off is not handed a
service keeping alive nothing.

`BOOT_COMPLETED` is one of the few broadcasts from which starting a foreground
service is still allowed with no visible UI. From Android 15 only a restricted
set of service types may be started that way and `specialUse` is not among them;
this app targets 34, where that rule does not apply, and the receiver catches a
refused start rather than crashing in a place no user can see. An app that has
never been launched sits in the stopped state and receives neither broadcast.

#### Why there is no separate service process

`android:process=":shake_service"` was evaluated and rejected. It is a real
mechanism — a second process can survive the death of the first — but here it
would cost more than it buys:

- the engine, the torch state and the haptics live in one process today, so the
  UI would need IPC (a bound service or a content provider) to learn what it
  currently reads from a `StateFlow`, and every failure mode of that channel
  would be a new way for the screen to disagree with the hardware;
- `Application.onCreate` runs again in the second process, so anything
  process-wide has to become process-aware or be started twice;
- the notification, the wake lock and the sensor listener would all move, while
  the torch callback stays where `CameraManager` registered it;
- OEM power managers that kill a package kill *its processes*, plural — the split
  does not buy immunity from the freezer, which is the actual threat on the
  devices this matters for.

The mechanism that does address process death is already in place and does not
need a second process: `START_STICKY`, `stopWithTask="false"`, the boot receiver,
and `ApplicationExitInfo` reporting what happened.

### Background reliability

`DetectionStatus` is the honest answer to "is it working?", and it has five
values because five different things can be true:

| Status | Meaning | Notification |
| --- | --- | --- |
| `ACTIVE` | armed and samples are arriving | Listening for shakes — torch off/on, or Paused when covered |
| `RECOVERING` | armed, no samples yet, a rebuild is due or in progress | Detection recovering — rebuilding the sensor listener |
| `STALLED` | armed, no samples, the rebuild budget is spent | Detection stalled — battery settings may be blocking it |
| `INACTIVE` | not armed: switched off, or nothing started it | Detection off |
| `NO_SENSOR` | no accelerometer, or the platform refused the listener | No accelerometer — tap still works, shake does not |

`hardware/DetectionHealth.kt` holds the pure rules — when a wake lock is needed,
which status applies, how long to wait before the next rebuild — and
`hardware/DetectionHealthTest.kt` pins them down, because the failures they
describe only reproduce on a real device with the screen off. The status appears
in the notification (with a **Diagnostics** action offered while something is
wrong), on the Home status row, and in the diagnostics page.

#### The diagnostics page

Settings → **Diagnostics** is a report, not a set of controls, and it is there to
be read *after* the fact — the useful moment is the morning after a night in
which detection stopped. It is re-read on every resume, which is the only chance
the app gets to look at what happened while it was away. Each line states the
mechanism that was actually asked:

- **Service** — running, and whether that agrees with the switch that wants it;
- **Detector** — the status above, plus how much of the rebuild budget is spent;
- **Last accelerometer sample** — its age, or "never", or "not armed";
- **Accelerometer** — present, and whether it can wake the processor;
- **Listener registration** — accepted, or refused by the platform;
- **Wake lock** — required, and held;
- **Proximity** and **significant motion** — capability, arming, and how many
  times the trigger actually woke the app;
- **Doze exemption**, **background app-op**, **vendor auto-start**, **Low Power
  Standby**, and the power mode right now — each separately, see below;
- **Previous process exit** — reason, signal and how long ago;
- **This process** — uptime, which turns "killed and restarted" into a number;
- **Shizuku**, and the **device** itself.

#### Mechanisms are not interchangeable

`isIgnoringBatteryOptimizations` answers one question: is this package on the
Doze/App-Standby power-exemption allowlist. It says nothing about an OEM's
"Battery → Unrestricted" menu, its auto-start manager, its app freezer, or
Android's own `RUN_ANY_IN_BACKGROUND` app-op — and on a Transsion device those are
separate switches in separate places. An earlier version labelled the allowlist
answer "Unrestricted", which read as a claim about all of them. Each mechanism now
reports its own state, in four values rather than two: `ALLOWED`, `RESTRICTED`,
`NOT_SUPPORTED`, and `UNKNOWN` for the ones that exist but will not answer a
third-party app. "Unknown" is a result; collapsing it into "allowed" is how an app
ends up claiming a setting is fixed when it only ever checked a different one.

Low Power Standby (Android 14+) is reported the same way, enabled and exempt as
two separate facts. It deserves its own line because it is the one platform
feature that silently invalidates the wake-lock strategy above: while it is on and
the device is non-interactive, wake locks held by apps that are not exempt are
*ignored* — foreground service or not. There is no permission an ordinary app can
hold to exempt itself; exemption comes from the system, so ShakeIT reports it and
points at the one setting that changes it rather than claiming a bypass.

#### Process death versus starvation

"Nothing happened when I shook it" has at least four causes that look identical
from the outside: the process was killed, the listener was refused, delivery was
starved, or the wake lock was ignored. They need different fixes, so
`background/ProcessExitDiagnostics.kt` reads `ApplicationExitInfo` (Android 11+)
and keeps the most recent meaningful exit — `USER_REQUESTED` is what a Recents
swipe, a force stop and a vendor's "close app" all report, which is why the
timestamp beside it matters — and pairs it with this process's uptime. An exit
recorded minutes ago with an uptime of seconds means the process died and came
back; no exit with an uptime of hours and a stale sample means it was alive and
being starved.

#### Shizuku

Shizuku runs a process with shell (or root) identity and hands authorised apps a
binder to it. `background/ShizukuController.kt` uses the real API
(`dev.rikka.shizuku:api` and `:provider` 13.1.5, with the provider declared in
the manifest) and reports five states rather than connected/not: **not
installed**, **installed but not running**, **running but not granted**,
**denied**, and **ready** — each with the action that actually helps, and each
read from Shizuku rather than remembered, because a stored "connected" would be a
lie the moment Shizuku stopped. A binder-death listener re-reads the state when
it does.

When it is ready, two repairs are offered, both explicit, both limited to this
app's own package, and both verified by reading the setting back:

- `cmd deviceidle whitelist +com.shakeit` — the Doze/power-exemption allowlist;
- `cmd appops set com.shakeit RUN_ANY_IN_BACKGROUND allow` — the restriction
  Android's own "Restrict background activity" writes.

The read-back is the point: a command exiting 0 says it ran, not that it took.
The result line shows the command, its exit code, and what the follow-up read
found, and "could not be parsed" counts as failure rather than success. Nothing
else is touched — no other package, no device-wide setting — and Shizuku is not
root: the page shows the identity it actually has (uid 2000 is shell, 0 is root)
and never claims otherwise. What Shizuku cannot do is override a vendor freezer;
a Transsion phone manager that freezes the package will freeze it whatever the
allowlist says.

#### Vendor layers, in words

**Vendor auto-start managers cannot be opened programmatically.** XOS and MIUI
have no public, stable intent for their auto-start screens; anything that reaches
one does it by guessing a component name that an OTA can move. So ShakeIT detects
the vendor from `Build.MANUFACTURER`, reports auto-start as *unknown*, and
describes the layers in words. On Infinix, Tecno and itel (all Transsion, all
XOS/HiOS) each of these is a separate switch, and only the first is readable by
an app:

> Battery & power saving → App management → ShakeIT → *Unrestricted* / allow
> background activity; Auto-start or Startup manager → allow ShakeIT; Phone
> Master → App freezer / background management → do not freeze ShakeIT; then lock
> ShakeIT's card in Recents so the cleaner skips it.

Those four layers are what the Advanced card prints on a Transsion device, in
those words; the equivalent list for MIUI/HyperOS is printed there too.

XOS is among the most aggressive OEM power managers known — dontkillmyapp lists
Infinix as one of the worst offenders — and can freeze a foreground service
regardless of what the app does correctly. Only standard AOSP intents are used to
open anything: `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and
`ACTION_APPLICATION_DETAILS_SETTINGS`. `ACTION_REQUEST_IGNORE_BATTERY_
OPTIMIZATIONS` — the yes/no dialog — is deliberately avoided: it needs a
permission Play restricts, and it decides for the user before they have read
anything. When the app *is* already exempt, the row says so and offers nothing to
tap: no user is sent into system settings to fix a problem they do not have.

#### What this does not guarantee

Detection survives the activity being destroyed, the task being removed, the
screen being off and the device being locked, for as long as Android and the OEM
allow a foreground service to read sensors. It does not survive a manufacturer
that freezes the package anyway, Doze or Low Power Standby ignoring a wake lock
the app is not exempt from, or a platform decision to kill the process — and the
app will not pretend otherwise. What it does promise is that none of those are
silent: the notification, the Home row and the diagnostics page say which one
happened.

## Testing background detection

Every step below is a case the detector has to survive, and each has a place to
look when it does not. `adb logcat -s ShakeDetector ShakeItEngine PowerDiagnostics
ProcessExitDiagnostics ShizukuController ShakeItBoot` prints the arming line,
every wake-lock transition, every status change, every rebuild and every
privileged command.

| # | Case | Expected | Where to look when it fails |
| --- | --- | --- | --- |
| A | App open, screen on | Notification reads **Listening**; Home says **Shake detection active** | `detection armed: wakeUpSensor=…, registration=…` |
| B | Home button, screen on, 2 min | Shake still toggles | status change lines; `STALLED` means delivery stopped |
| C | Screen locked/off | `screen off: holding a partial wake lock…` unless a wake-up sensor was found; shake toggles | Diagnostics → **Wake lock**; `needsWakeLock` is the rule |
| D | Swipe the task out of Recents | Notification remains; `onTaskRemoved` forces a rebuild | `the task was removed from Recents: forcing a sensor recovery` |
| E | Screen off, 5 min wait | Shake toggles; no `STALLED` | sample age in Diagnostics → **Last accelerometer sample** |
| F | Screen off, 15 min wait | Same; Doze may already be active | Diagnostics → **Power mode** (`Doze active right now`) |
| G | Reboot, then wait for boot to settle | Service and notification come back on their own | `ShakeItBoot`; **Start after reboot** must be on |
| H | Battery-restricted (optimised, background restricted) | `STALLED` after the budget is spent, notification says so | Diagnostics → **Doze exemption** and **background app-op** |
| I | Battery-unrestricted (allowlist + app-op allowed) | `ACTIVE` through the night | the same two rows should read *exempt* / *allowed* |
| J | Shizuku not installed | Diagnostics still complete; no repair buttons | **Shizuku** row reads *not installed* |
| K | Shizuku installed and granted | Both repairs report a command, an exit code and a read-back | `ShizukuController`; the result line under the buttons |
| L | Low Power Standby on (Android 14+, not exempt) | Wake locks ignored; expect `STALLED`, not a silent stop | Diagnostics → **Low Power Standby** |

### Classifying a failure

The point of the diagnostics page is that "it stopped working" becomes one of
these, and each has a different fix:

| Classification | How it shows | Fix |
| --- | --- | --- |
| **Process death** | a **Previous process exit** entry with an age that matches the failure, and a short process uptime | `START_STICKY` / boot receiver should have restarted it; if the reason is `USER_REQUESTED` or `SIGNALED (9)`, something killed it deliberately — check the vendor layer |
| **Registration failure** | status `NO_SENSOR` while **Accelerometer** says *present* | the platform refused `registerListener`; a rebuild is the only retry, and the budget shows how many were spent |
| **Sample starvation** | process uptime long, no exit recorded, sample age growing, rebuilds spent | Doze, Low Power Standby or a vendor freezer — read the power rows, not the sensor rows |
| **Wake-lock suppression** | **Wake lock** reads *required but NOT held*, or *required and held* while Doze/LPS is active | the first is a bug; the second is the platform ignoring a lock it is entitled to ignore |
| **OEM restriction** | Doze exemption *exempt* and app-op *allowed*, and it still stalls | vendor auto-start / freezer / cleaner; the Advanced card names the screens, and no app can read them |
| **Service restart failure** | **Service** reads *not running* while **Run in background** is on | a start was refused — `ForegroundServiceStartNotAllowedException` in logcat, or a boot-time refusal on Android 15+ |
| **Android policy** | everything above reads fine, and the process is simply gone after a long idle period | cached-process reclaim; the boot receiver and sticky restart are the only legitimate answers, and both are in place |

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

`.github/workflows/android-ci.yml` runs on every push to any branch, filtered by
path: a commit that only touches the README or the design mockup gets no runner
time.

Each run is named after the subject line of the commit that triggered it, via
`run-name`, so the Actions list reads as a history of what changed instead of
repeating one pull-request title. That is also why the trigger is `push` and not
`pull_request`: a `pull_request` payload carries no commit message at all, so
every run of a branch with one open pull request was named after that pull
request's title and none of them could be told apart. Nothing else was lost — the
checks still appear on the pull request, because a check run is attached to the
head commit and a push builds exactly that commit, and the failure report still
lands as a pull-request comment, because `report_failures.py` resolves the pull
request from the head branch when the event is not `pull_request`. What *is* lost
is CI for pull requests opened from a fork, whose pushes run in the fork; adding
`pull_request` back as a second trigger restores it at the cost of a duplicate
run per commit.

A commit message with a body carries that body into the run name too: GitHub
expressions have no way to split a string, and there is no API for renaming a run
once it has started. The subject comes first, which is what the list shows.

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

**Detection switches.** All of them do what their labels say now, and the engine
is the thing that acts on them: it registers a
`SharedPreferences.OnSharedPreferenceChangeListener` on the same file the UI
writes, so a switch flipped in a composition reaches the detector, the service
and the boot receiver without either side knowing about the other. **Detection
active** off stops the detector *and* the service, because there would be nothing
for the service to keep alive. **Run in background** off stops the service and
leaves detection running in-process, which is what the switch promises and what
Android allows — background apps stop receiving sensor events, so it works until
the app leaves the foreground. **Start after reboot** gates `BootReceiver`.
**Auto-off after 5 min** schedules a real five-minute job, rescheduled on every
torch change. **Sensitivity** retunes the live detector through
`shakeConfigFor`, which moves the impulse threshold and nothing else — the
timing rules that make one shake mean one toggle are the same at every level.

The Home status row still reads the hardware rather than the switch: it reports
`ShakeItEngine.detectionStatus`, derived from the detector, so the screen says
*paused* when the switch is off and *stalled* when the switch is on and the
device is not delivering. Those are different facts and the row does not
conflate them.

**Gestures.** Only **Shake** is implemented. The other two options in the
prototype's segmented control persist and are labelled as not implemented in the
row itself. Implementing them is a change to `ShakeAlgorithm` — a second gate over
its fire events for double-shake, a gravity-inversion check for flip & shake —
and it is deliberately not done halfway: a gesture that fires sometimes is worse
than one that is visibly unavailable.

**OEM background limits.** The app does everything standard Android allows —
foreground service, screen-off-scoped wake lock, wake-up sensor when available,
own sensor thread, significant-motion wake path, a watchdog that repairs and then
reports, a boot receiver, and diagnostics that name the mechanism that failed —
plus the two Shizuku repairs for the restrictions the public API cannot lift.
See [Background reliability](#background-reliability). It still cannot survive a
manufacturer that freezes foreground services regardless, and it will not try to:
no hidden activity, no invisible keep-alive, no guessed vendor component names,
no changes to any package but its own, and no claim of a guarantee the platform
does not give.

**Dynamic color.** The Appearance group's "Dynamic color" switch is wired and
persisted. On Android 12+ it selects the device's wallpaper-derived Material
colour scheme, but ShakeIT's own palette is deliberately *not* re-tinted:
every component reads the brand purple and amber from `ShakeItColors`, which is
what keeps the UI matching the design reference. The prototype's switch has no
visual effect either. Widening it to re-tint the brand palette is a one-line
change in `ui/theme/Theme.kt` if that is ever wanted.

## Development APK signing

Debug builds use the Android debug certificate unless the shared development
signing variables are supplied. To make a local APK replace an APK from CI,
configure the same out-of-band keystore and credentials on the workstation:

```sh
export SHAKEIT_DEV_KEYSTORE=/secure/path/shakeit-development.keystore
export SHAKEIT_DEV_STORE_PASSWORD='...'
export SHAKEIT_DEV_KEY_ALIAS='...'
export SHAKEIT_DEV_KEY_PASSWORD='...'
./gradlew :app:assembleDebug
```

CI reads the equivalent repository secrets
`SHAKEIT_DEV_KEYSTORE_BASE64`, `SHAKEIT_DEV_STORE_PASSWORD`,
`SHAKEIT_DEV_KEY_ALIAS`, and `SHAKEIT_DEV_KEY_PASSWORD`. The keystore itself is
never committed. Configure those secrets once from the same development
keystore used locally; every subsequent debug APK then has the same signing
certificate and can update the previous test install while preserving app data.

If the secrets are not configured, CI deliberately warns and uses the ordinary
runner-generated debug key. That fallback is suitable only for builds that are
not installed over a local or previous CI APK.
