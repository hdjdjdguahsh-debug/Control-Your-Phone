# Control Your Phone

**Current build: `v1.0.0-rc1` — release candidate, not a final release**

Control Your Phone is a Java-based Android utility project focused on device information, diagnostics, and practical device-management tools. This release candidate adds an initial health check, a basic network connectivity/latency check, local diagnostic history, and diagnostic report export. It still needs on-device regression testing before the stable v1.0 release.

> **Project status:** experimental. Do not treat this version as a stable release. Back up important data before testing any feature that could affect your device.

## What works in the current project

The current codebase includes the following features, subject to Android version and device limitations:

- Device overview: manufacturer/model, Android/API level, CPU ABI, RAM, battery information, and app-data storage.
- Refreshable device diagnostics.
- Read-only shell diagnostic shortcuts for selected system properties, battery, thermal, and display information.
- Shizuku status and permission UI for features that use it.
- A shell command runner with output limits and a timeout.
- Shortcuts to relevant Android settings, where available.
- Installed-app listing based on packages visible to the app.
- One-Tap Health Check for basic memory, battery, and app-data-volume indicators; it is not a malware scan or full hardware diagnosis.
- Network Detective for Android-reported network capability and a single TCP connection-latency test.
- Local diagnostic history (last 10 entries) and export of the current health report to a user-selected text file.
- Crash-reporting UI and a GitHub Actions workflow for building a debug APK.

Some privileged features may not work on every device or build. The Shizuku provider is currently disabled while the earlier startup issue is investigated; Shizuku-backed shell and permission features must be treated as unavailable until the provider issue is fixed and tested. The planned move away from Shizuku is not complete.

## Remaining work for stable v1.0 — Phase 1

This section shows **only the current phase target**. The current build is a release candidate, not the stable v1.0 release. Items will be marked complete only after implementation and testing.

- [ ] Improve the main dashboard and overall UI.
- [x] Add an initial One-Tap Health Check for available basic indicators (limited scope; see above).
- [ ] Improve system monitoring for CPU, RAM, battery, temperature, and storage where Android exposes the data.
- [ ] Add battery-focused diagnostics and practical recommendations based on available data.
- [ ] Add storage analysis with safe, user-confirmed cleanup suggestions.
- [x] Add a basic Network Detective connectivity/latency check. Packet-loss diagnostics remain unimplemented.
- [x] Add basic diagnostic report export and local Diagnostic History.
- [ ] Add Storage Cleanup Preview and Performance Session Recorder.
- [ ] Add reviewed ADB Command Presets with clear explanations and safeguards.
- [ ] Design and validate a standalone Wireless ADB client/session manager to replace the current Shizuku-dependent approach where feasible.
- [ ] Fix known stability and functionality issues before stable v1.0: investigate the startup crash seen in an earlier build; resolve the Shizuku-provider/initialization issue without leaving dependent features broken; and restore or clearly handle Battery Diagnostics, App Diagnostics, and Shell Command functionality.
- [ ] Harden permission-denied, missing-data, timeout, empty-output, and dropped-ADB-connection handling.
- [ ] Run regression, stability, compatibility, and UI tests before calling the phase complete.

### Roadmap privacy

Only the upcoming phase is shown here. Later plans will be revealed gradually as the project version advances, so this README does not publish the full long-term roadmap.

## Versioning policy

The current code is a **`v1.0.0-rc1` release candidate**, not a stable release.

- `v0.9 → v1.0`: stable v1.0 is released only after Phase 1 is implemented and passes its required checks.
- `v1.0 → v1.1 → v1.2 → ...`: subsequent updates are released incrementally after their changes are tested.
- Larger milestone numbers are not assigned automatically just because a phase ends. Version numbers are updated deliberately, based on the actual release.

A successful APK build alone does not mean a release is stable. For `v0.9 → v1.0`, bug fixing is part of Phase 1—not a later optional task. The `v1.0.0-rc1` artifact is for testing and is not the final stable release. Known critical bugs, security issues, data-loss risks, broken existing features, and failed required tests must block a release. The earlier working build used as a baseline should be retained for comparison until its replacement is tested.

## Quality goals

Before a version is considered ready, the project aims to check:

- Clean builds and relevant automated tests.
- App startup, navigation, loading/error states, and common UI sizes.
- Behavior when permissions are denied, data is unavailable, or a connection drops.
- Regression of existing features.
- Backup and destructive-operation safeguards where applicable.
- Compatibility on supported Android versions and real devices.

No software can be honestly guaranteed to be completely bug-free. Compatibility and test results should be documented rather than assumed.

## Requirements for local builds

- Android device or emulator.
- Java 17 and Android SDK.
- Gradle 8.7 and Android Gradle Plugin 8.6.1, as configured by the project.
- Shizuku installed and running for current Shizuku-backed features.

## Build locally

Run from the repository root:

```bash
gradle --no-daemon assembleDebug
```

The debug APK should be generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Build with GitHub Actions

1. Open the repository's **Actions** tab.
2. Select **Build Control Your Phone APK**.
3. Run the workflow or push a commit to `main`.
4. Wait for the workflow to finish.
5. Download the debug APK artifact from the successful run.

A build is considered successful only when the workflow reports success. Test the resulting APK on a suitable device before relying on it.

## Compatibility and safety

- Shizuku shell access is **not root**. Android and device-manufacturer restrictions may deny commands or operations.
- Shell commands can affect device data and settings. Prefer built-in read-only diagnostics, and do not run commands unless you understand their effects.
- App visibility and diagnostic data vary by Android version, manufacturer, and permissions.
- Wireless debugging and ADB behavior depend on the device and Android version. ADB access does not grant root privileges.
- Bootloader unlocking and root are not universal capabilities and can cause data loss or device problems. Any future support must be device-specific, clearly explained, and tested.
- The app does not claim to modify CPU governors, GPU drivers, FPS caps, or thermal limits where Android or the manufacturer does not permit it.

## Contributing and bug reports

Bug reports are welcome. Include the app version, Android version, device model, steps to reproduce, expected behavior, actual behavior, and relevant redacted logs. **Do not post passwords, authentication tokens, personal files, or other sensitive data.**

---

This README describes the current repository and the next planned phase; planned features should not be interpreted as already available.
