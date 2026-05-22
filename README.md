# Hutch Data Stall Watchdog

An Android app that watches for a specific kind of mobile data failure: the phone still shows cellular signal and mobile data, but websites stop loading until something "wakes up" the carrier data session.

This project was built for testing that behavior on Hutch mobile data in Sri Lanka, but the same idea can be useful on any network where the cellular data path sometimes stalls without the radio disconnecting.

## What it does

- Runs a foreground Android service with a persistent notification.
- Monitors the active default network using Android's `ConnectivityManager`.
- Only performs recovery logic when the active network is cellular internet.
- Runs lightweight DNS and HTTPS probes.
- Marks the network as:
  - `healthy` after successful probes.
  - `suspicious` after one failed probe cycle.
  - `stalled` after two consecutive failed probe cycles.
  - `recovering` while it is nudging Android to revalidate the mobile data path.
- Tries automatic non-root recovery by:
  - sending short probe bursts,
  - calling `ConnectivityManager.reportNetworkConnectivity(...)`,
  - briefly requesting a fresh cellular internet network with `requestNetwork(...)`,
  - recreating probe clients instead of reusing stale connections.

## What it cannot do

This app targets normal unrooted Android phones.

It cannot silently toggle airplane mode, toggle mobile data, reset the modem, or force a carrier-side session rebuild. Android reserves those controls for system apps, carrier apps, managed-device owners, or rooted devices.

So the app is best described as a watchdog and recovery nudge, not a guaranteed carrier-network repair tool.

## App UI

The app includes a simple native Android UI with:

- Watchdog on/off toggle.
- Current watchdog state.
- Last successful probe time.
- Recovery attempt count.
- Probe interval preset:
  - Balanced, default.
  - Aggressive.
  - Quiet.
- Optional custom probe URL.
- Battery optimization setup shortcut.

## Project structure

```text
app/src/main/java/com/dinuka/hutchwatchdog/
  MainActivity.kt                 App UI and setup actions
  StallWatchdogService.kt         Foreground watchdog service
  ProbeRunner.kt                  DNS and HTTPS probe logic
  NetworkRecoveryController.kt    Android connectivity recovery nudges
  StallStateMachine.kt            Health/stall/recovery state transitions
  WatchdogModels.kt               Shared models and enums
  WatchdogSettings.kt             SharedPreferences settings
  WatchdogStore.kt                In-memory app/service status store

app/src/test/java/com/dinuka/hutchwatchdog/
  StallStateMachineTest.kt        Unit tests for stall classification
```

## Requirements

- Android Studio
- Android SDK
- Kotlin/Android Gradle plugin support
- Android phone with mobile data
- USB debugging enabled for installing the debug build

Recommended device setup:

- Disable Wi-Fi during field tests.
- Use the SIM/network you want to test.
- Allow notification permission.
- Allow battery optimization exemption if Android prompts for it.

## Build and run

Open the repository folder in Android Studio, then:

1. Let Android Studio sync Gradle.
2. Connect the Android phone with USB debugging enabled.
3. Select the phone as the run target.
4. Run the `app` configuration.
5. On the phone, enable `Watchdog enabled`.
6. Confirm the persistent notification is visible.

## Command-line build

If your machine has Android SDK and Gradle available:

```powershell
gradle test assembleDebug
```

If you use Android Studio, you can also build from:

```text
Build > Make Project
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

## Field testing

For real-world testing:

1. Install and open the app.
2. Turn off Wi-Fi.
3. Enable the watchdog.
4. Allow notifications and battery optimization exemption.
5. Move around normally with mobile data active.
6. When websites stop loading, check whether the app moves from `healthy` to `suspicious`, `stalled`, or `recovering`.
7. Check whether it returns to `healthy` without manually opening the carrier app or toggling airplane mode.

Good signs:

- `Last success` updates regularly during normal mobile data use.
- `Recovery attempts` increases when the data path freezes.
- The state returns to `healthy` after a recovery attempt.

Bad signs:

- The persistent notification disappears.
- The app stays in `no_cellular` while mobile data is active.
- Recovery attempts increase forever but the network never comes back.

## Permissions

The app uses:

- `INTERNET` for DNS and HTTPS probes.
- `ACCESS_NETWORK_STATE` to inspect the active network.
- `CHANGE_NETWORK_STATE` for Android network revalidation/request APIs.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` for the watchdog service.
- `POST_NOTIFICATIONS` for the persistent service notification on newer Android versions.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to let the user exempt the app from aggressive background killing.

## Probe behavior

Default probe:

```text
https://connectivitycheck.gstatic.com/generate_204
```

The app also performs DNS resolution for:

```text
connectivitycheck.gstatic.com
```

You can add a custom probe URL in the app. Use a small endpoint that responds quickly, such as your own `/ping` or `/health` route.

## Known limitations

- No automatic startup after phone reboot yet.
- No long-term history/export UI yet.
- No root-based radio reset support.
- No VPN mode yet.
- Recovery behavior depends on Android version, OEM background restrictions, and carrier network behavior.

## Testing

Unit tests cover the core state machine:

- first failure becomes `suspicious`,
- second failure becomes `stalled`,
- success clears failure state,
- recovery needs two successful probes before returning to `healthy`,
- custom probe failure counts as a failure.

Run:

```powershell
gradle test
```

## Disclaimer

This is an experimental diagnostics and recovery helper. It may reduce or shorten mobile data stalls, but it cannot guarantee a fix for carrier-side congestion, routing failures, NAT/session issues, or radio firmware problems.
