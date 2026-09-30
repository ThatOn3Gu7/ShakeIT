# Predictive back: why in-flight progress stays 0.0 on the Infinix X6730 (XOS 14 on Android 15)

Status: **investigation complete — no in-app fix exists within project constraints.**
Temporary diagnostic overlay (`ShakeItApp.kt`) is intentionally still in place; remove together with this file once the outcome is decided.

## 1. Observed behavior (on-device, `debug/` screenshots)

- `PredictiveBackHandler` goes ACTIVE, `swipeEdge = 1` (right edge) is correct.
- Exactly **one** `onBackProgressed` is ever received: `progress = 0.000` — the value is frozen for the entire drag.
- Commit (release past threshold) closes Settings → Home without replay; cancel restores Settings smoothly; the top-bar arrow and back-from-Home exit are unchanged.
- The window itself is never translated by the system (edge chevron only) — correct for callback-based predictive back.
- Other apps on the same device **do** animate a live in-app predictive-back preview.

## 2. How Android 15 in-app predictive back actually works

Verified against `aosp-mirror/platform_frameworks_base` @ `android15-release` (≈ device OS) and androidx `activity-compose` 1.11.0 source.

The platform does **not** stream progress to the app. Android 15 generates it *inside the app process* from the raw gesture touches:

1. WM `BackNavigationController.startBackNavigation`: app callback present → `TYPE_CALLBACK`, no system window animation; `appProgressAllowed = (window privateFlags & PRIVATE_FLAG_APP_PROGRESS_GENERATION_ALLOWED) != 0`; `touchableRegion = window.getFrame()`.
2. `ViewRootImpl.setView`: `if (mView instanceof DecorView) privateFlags |= PRIVATE_FLAG_APP_PROGRESS_GENERATION_ALLOWED` — set for every normal activity window; **no app-side opt-out exists**.
3. Shell `BackAnimationController` (android15): `isAppProgressGenerationAllowed() = appProgressAllowed && windowFrame.equals(currentWindowMetricsBounds)`. When true (our fullscreen edge-to-edge window): it dispatches `onBackStarted` and then **pilfers nothing and streams nothing** — `dispatchOnBackProgressed` early-returns for allowed windows.
4. Because the shell does not pilfer, the raw edge-gesture `MotionEvent`s keep being delivered to the app window, where:
   - `com/android/internal/policy/DecorView.java:499` (`onInterceptTouchEvent`) calls `viewRootImpl.getOnBackInvokedDispatcher().onMotionEvent(event)` for every touch event and intercepts them while the back gesture is in progress.
   - `WindowOnBackInvokedDispatcher.onMotionEvent` (line 115): while the app-side `BackTouchTracker` is ACTIVE (set by the binder `onBackStarted`) and the event is `ACTION_MOVE` → tracker `update(x, y)` → progress = start-X delta over the screen-width threshold → `BackProgressAnimator` spring → the app's `OnBackInvokedCallback.onBackProgressed`.
   - `BackProgressAnimator.onBackStarted` immediately dispatches **one** `onBackProgressed(progress = 0.0, swipeEdge)` — this is exactly the single value seen in the overlay.
5. `onBackInvoked` / `onBackCancelled` still arrive via binder from the shell (commit/cancel work — as observed).

For step 4 to fire on `MOVE` events, `ViewGroup.dispatchTouchEvent` must still call `onInterceptTouchEvent`. Stock android15 therefore added this back-gesture override (absent in `android14-release`, present in `android15-release`, `ViewGroup.dispatchTouchEvent`):

```java
final boolean isBackGestureInProgress = (viewRootImpl != null
        && viewRootImpl.getOnBackInvokedDispatcher().isBackGestureInProgress());
if (!disallowIntercept || isBackGestureInProgress) {
    // Allow back to intercept touch
    intercepted = onInterceptTouchEvent(ev);
```

## 3. Why this device freezes at 0.0

The test device is **Infinix X6730 (XOS 14 on Android 15)** — an OEM framework build.

Compose apps set the standard disallow-intercept flag while their content handles a gesture. androidx `AndroidComposeView.dispatchMotionEvent` (`androidx/androidx`, `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeView.android.kt`):

```kotlin
if (processResult.anyMovementConsumed) {
    parent.requestDisallowInterceptTouchEvent(true)
}
```

`requestDisallowInterceptTouchEvent(true)` propagates up the whole parent chain, so `DecorView` gets `FLAG_DISALLOW_INTERCEPT` for the duration of the gesture. On stock Android 15 the back-gesture override above defeats that flag, so `DecorView.onInterceptTouchEvent` (and the `onMotionEvent` feed) still runs. On this device it does not run: with `FLAG_DISALLOW_INTERCEPT` honored, `ViewGroup` skips `onInterceptTouchEvent` for `MOVE` events, so `WindowOnBackInvokedDispatcher.onMotionEvent` never receives a single MOVE, the `BackTouchTracker` never advances, and progress stays at the animator's start value of exactly 0.0 for the whole gesture — the observed signature, down to the single dispatched event.

Non-Compose (or non-consuming) apps on the same device still get fed via the no-touch-target `MOVE` path in `ViewGroup` (the group "continues to intercept" and dispatches to `DecorView.onTouchEvent`, which is `onInterceptTouchEvent`), which is why other apps' previews animate while ours does not.

The single device-dependent assumption in this chain — that the XOS 14-on-15 framework lacks the 15-cycle back-intercept bypass (and possibly the `DecorView` feed itself, both absent in android14 and added in android15) — is not directly verifiable from here because OEM frameworks are closed source, but it is the only remaining explanation consistent with all verified facts: app code + androidx delivery verified clean end-to-end, binder start/commit/cancel verified delivered, and zero touch-driven updates.

## 4. Why there is no in-scope app-side fix

- The app-progress mode is forced by the framework (private flag OR'd in by `ViewRootImpl` for every `DecorView` window); there is no API to opt out or to make the shell stream progress instead.
- The feed (`DecorView` → `WindowOnBackInvokedDispatcher.onMotionEvent`) is platform-internal; there is no app API to receive the forwarded gesture touches.
- The disallow-intercept request comes from the Compose runtime itself; suppressing it would require patching androidx (banned) and would break normal scroll/click interception app-wide.
- Forcing the shell into streaming mode would require altering the window frame (visible layout change — banned) and its interaction with an unknown OEM shell is unverified.
- All remaining alternatives (custom edge detector, pointer-derived/fake progress, Navigation Compose, new dependencies) are explicitly out of scope and would not use Android's real gesture.

## 5. What this means

- The shipped implementation (live `BackEventCompat.progress` rendering, commit from preview position without replay, smooth cancel, unchanged arrow/Home-exit) is **correct against the platform contract** and is the standard androidx usage.
- On stock Android 15/16 builds — and on any OEM build whose framework includes the 15-cycle back-intercept bypass — the same code is expected to show the live in-flight preview with no changes.
- This device's ROM is the limiting factor. Suggested follow-ups: verify on a stock Android 15/16 device, and/or report to Infinix with the file/line citations in this document.
- Nothing here should be treated as the feature being fixed: in-flight progress is not observed changing on this device.
