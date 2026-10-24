# Control Your Phone 2.0

Android utility project using Java, Material 3 UI, and Shizuku for optional Android shell access.

## Features
- Live device overview: manufacturer/model, Android/API level, CPU ABI, RAM, battery percentage/status/temperature/health, and app-data volume storage.
- Refreshable diagnostics.
- Read-only shell diagnostic shortcuts (\x60getprop\x60, \x60dumpsys battery\x60, thermal service summary, and display service summary).
- Shizuku binder and permission status with permission request.
- Shell command runner with merged output, bounded output, and a 20-second timeout.
- Shortcuts to this app's Android settings, general display settings, battery settings, and wireless debugging settings where available.
- Installed-app list based on packages visible to the app.
- Material 3 interface.
- GitHub Actions workflow to build and upload a debug APK.

## Requirements
- Android device.
- Java 17 and Android SDK for local builds.
- Shizuku installed and running for Shizuku-backed features.

## Build locally
The project uses Gradle 8.7 and Android Gradle Plugin 8.6.1. If Gradle is installed, run:

\x60\x60\x60bash
gradle --no-daemon assembleDebug
\x60\x60\x60

The debug APK should be generated at \x60app/build/outputs/apk/debug/app-debug.apk\x60.

## Build APK with GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Build Control Your Phone APK**.
3. Choose **Run workflow**, or push a commit to \x60main\x60.
4. Wait for the run to finish successfully.
5. Download the \x60ControlYourPhone-debug-APK\x60 artifact from the run.

A successful build has not been confirmed until the workflow reports success.

## Compatibility and safety notes
- Shizuku shell is **not root**. Some commands or privileged operations will be denied by Android, OS versions, or device-manufacturer restrictions.
- The shell command box can run commands as Android's shell identity after Shizuku permission is granted. Review commands before running them; destructive commands can damage data or settings. Prefer the built-in read-only diagnostics.
- Package visibility and app-list completeness can vary by Android version and policy.
- Android 11+ usually supports starting Shizuku using Wireless debugging. See https://shizuku.rikka.app/ for official instructions.
- This app does not claim to change CPU governors, GPU drivers, FPS caps, or thermal limits where Android/vendor policy does not permit it.

## Build status
This project has not been compiled or tested on a physical device in this environment. Check GitHub Actions for the authoritative build result.
