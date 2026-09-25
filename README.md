# ShakeIT

ShakeIT is a small Android flashlight app for people who want a physical, immediate way to control the torch. Give the phone a deliberate shake, and the flashlight toggles without hunting for a button.

## Features

- **Shake to toggle** — use one deliberate shake to switch the flashlight on or off.
- **Double Shake** — use a natural shake-shake gesture. Two separate shake bursts close together are required before the torch changes state.
- **Adjustable sensitivity** — choose from five sensitivity levels to match how you hold and move your phone.
- **Manual flashlight control** — the Home screen also provides a direct tap control.
- **Subtle haptics** — feel a small confirmation when the flashlight changes state and when moving between sensitivity levels.
- **Optional five-minute auto-off** — prevent the flashlight from staying on indefinitely.
- **Background detection** — when enabled, ShakeIT keeps detection available outside the app through a foreground service and a dedicated sensor process.
- **Start after reboot** — optionally resume detection after the device restarts.
- **Material 3 interface** — adaptive layouts, light/dark/system themes, and a focused settings experience.
- **Diagnostics** — inspect sensor registration, sample delivery, wake-up capability, wake-lock state, recovery attempts, power restrictions, and recent process information.
- **Optional Shizuku support** — on supported setups, Shizuku can help apply the system's Doze allowlist and background-operation repairs. It is not required for ordinary use.

## Background and device limitations

Background operation depends on Android and the phone manufacturer's power-management policy. ShakeIT uses the standard tools available to an Android app: a foreground service, a dedicated sensor process, a sensor thread, wake-up sensor selection, wake-lock handling where required, recovery checks, and diagnostics.

Some devices may still stop delivering accelerometer samples while the screen is completely off, or may terminate background processes aggressively. ShakeIT reports these conditions, but cannot guarantee screen-off detection on every Android device. If detection is important on a particular phone, check the device's battery and background-activity settings and use the Diagnostics screen to see what the sensor stack is doing.

## Project architecture

```text
ShakeIT/
├── app/
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/shakeit/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ShakeItApplication.kt
│       │   │   ├── background/       # Power, process-exit, and Shizuku support
│       │   │   ├── engine/            # Hardware ownership and diagnostics
│       │   │   ├── hardware/          # Torch, sensors, haptics, and detection
│       │   │   ├── service/           # Foreground service and process bridge
│       │   │   ├── state/             # Settings and persisted app state
│       │   │   └── ui/                # Home, Settings, navigation, and theme
│       │   └── res/                   # Strings, themes, icons, and resources
│       └── test/                      # JVM tests for detection and app logic
├── .github/
│   ├── workflows/
│   │   ├── android-ci.yml             # Tests, lint, and debug build
│   │   └── release.yml                # Authorized tagged release workflow
│   ├── actions/prepare-build/         # Shared CI Android setup
│   └── scripts/                       # CI failure reporting
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

The Activity and UI observe application state. The foreground service runs in the private `:sensor` process, where it owns sensor registration, detection health, recovery, and background gesture recognition. The two processes exchange explicit state and preference messages rather than sharing in-memory objects.

## Development

### Requirements

- Android Studio with an Android SDK
- JDK 17
- Android SDK Platform 36 and a compatible build-tools installation

### Build a debug APK

```sh
./gradlew :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Run tests and lint

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

### Build the release variant locally

The release build expects the release keystore to be supplied outside the repository through the signing configuration used by CI. Never commit a keystore or passwords. The tagged release workflow reconstructs the keystore temporarily, builds `assembleRelease`, verifies the APK certificate, and publishes the resulting APK only after signing succeeds.

## Releases

Pushing a `v*` tag starts the release workflow. Release actions are restricted to the repository owner and the verified Arena automation actor. The workflow builds the release variant, checks that the APK certificate matches the configured release keystore, writes polished release notes, and attaches the APK to the GitHub Release.

The first official release is **v1.0**.

## License

This project is distributed as configured by its repository owner. See the repository for the applicable licensing information.
