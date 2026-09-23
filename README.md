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

## What is deliberately simulated

Hardware and platform integration is out of scope for this pass. Nothing below
touches real device capability yet:

- **Flashlight** — `torchOn` is plain state. No `CameraManager.setTorchMode`.
- **Shake detection** — no `SensorManager`. The "Shake to toggle" button stands
  in for a detected shake and runs the same wobble-then-toggle sequence the
  prototype's button runs.
- **Background service / reboot start / auto-off** — the switches persist their
  values and nothing more. `FOREGROUND_SERVICE_DATA_SYNC` is declared but unused.
- **Shizuku** — "Connect Shizuku" flips to "Connected" and disables itself.
- **Stats** — "Activations" is live and persisted. "Time on today" and
  "Avg session" are the prototype's placeholder values.

## Structure

```
app/src/main/kotlin/com/shakeit/
  MainActivity.kt              single-activity host, edge to edge
  state/ShakeItState.kt        settings + torch + nav state, SharedPreferences store
  ui/ShakeItApp.kt             theme resolution, animated background, nav host
  ui/ShakeItPreviews.kt        light/dark previews of both screens
  ui/navigation/               the two-screen cross-fade stack
  ui/home/                     HomeScreen + BlobCanvas (hero animation)
  ui/settings/                 SettingsScreen and its groups
  ui/components/               switch, slider, segmented control, buttons, pill,
                               and the shared `.screen` container
  ui/theme/                    palette, theme, typography
```

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
| `#blobPath` + the `frame()` loop | `BlobCanvas` + `rememberBlobShape` |
| `#blobPath.on` fill + `drop-shadow` | `onMix` driving a colour `lerp` and a `BlurMaskFilter` halo |
| `@keyframes wobble` | `sampleKeyframes` with per-segment CSS `ease` |
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

## Building

The project targets the toolchain already pinned in `gradle/libs.versions.toml`
— nothing was added or upgraded: AGP 8.13.0, Kotlin 2.1.0, Gradle 9.0.0,
compileSdk 36, minSdk 24, Compose BOM 2025.10.01 (Compose 1.9.4, Material 3
1.4.0).

```
./gradlew assembleDebug
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
Termux workflow keeps working.

## Two seams worth knowing about

**Manrope.** The prototype sets its display text (the OFF/ON word, the Settings
title, the stat numbers) in Manrope 700/800 and its body text in Roboto. Roboto
is the Android system font, so the body text already matches. Manrope is not
shipped with Android and no font binary is committed here, so
`DisplayFontFamily` in `ui/theme/Type.kt` currently resolves to the system font.
Drop `manrope_bold.ttf` and `manrope_extrabold.ttf` into
`app/src/main/res/font/` and point `DisplayFontFamily` at them — every display
style reads from that one value, so nothing else changes.

**Dynamic color.** The Appearance group's "Dynamic color" switch is wired and
persisted. On Android 12+ it selects the device's wallpaper-derived Material
colour scheme, but ShakeIT's own palette is deliberately *not* re-tinted:
every component reads the brand purple and amber from `ShakeItColors`, which is
what keeps the UI matching the design reference. The prototype's switch has no
visual effect either. Widening it to re-tint the brand palette is a one-line
change in `ui/theme/Theme.kt` if that is ever wanted.
