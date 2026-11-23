# Creta Steering Diagnostic – Android source project (v0.1)

## What is implemented
- Native Android Kotlin application with a simple diagnostic interface.
- Lists **already paired** Bluetooth Classic devices (SPP / RFCOMM); connects with SPP UUID.
- ELM327 compatibility probe (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATSP0`, `ATI`, `0100`).
- **Read-only**, generic OBD-II live PIDs: engine RPM 010C, vehicle speed 010D and control module voltage 0142 (if supported).
- Displays raw communication replies and cautions about possible non-ELM CaRPM firmware.
- Displays steering pull inspection guidance and explicitly marks EPS/SAS calibration unavailable.

## What is NOT implemented
- Actual Steering Angle Sensor (SAS) calibration / EPS ASP calibration.
- EPS or ABS manufacturer-specific data, DTCs, coding, learning, resets or clear-DTC functions.
- BLE (Bluetooth Low Energy) or proprietary CaRPM/GaragePro protocols.
- An APK: the current environment cannot build/test this Android project; source is provided for Android Studio.

**Do not treat a successful Bluetooth connection as proof that EPS diagnostics or calibration are supported.**

## Build with Android Studio
1. Install a recent Android Studio with Android SDK Platform 35 and JDK 17.
2. Open the extracted `Creta_Android_Diagnostic` folder as a project.
3. Allow Gradle sync (network required to fetch Android Gradle plugin and Kotlin plugin).
4. Build > Build APK(s). The debug APK will be in `app/build/outputs/apk/debug/`.
5. On Android, pair the OBD scanner in **Settings > Bluetooth** first. Open app and grant Nearby devices permission.
6. Park safely and switch ignition ON before running a diagnostic check. Do not drive while using the app.

## Key compatibility limitation
The photographed device is CaRPM branded but the photos do not prove its firmware accepts generic ELM327 commands. The project attempts only ordinary SPP/ELM327. If it fails, capture the device name, connection error and application log; do not attempt undocumented steering ECU commands.

## Safety / diagnosis
Car pulling left can come from tyres, alignment, brake drag or suspension; SAS calibration is not automatically the cure. Calibrate using a verified vehicle-specific professional diagnostic tool following Hyundai procedure.

This is an independently written diagnostic prototype; not endorsed by Hyundai or CaRPM.

## Automated APK build (GitHub Actions)
The included `.github/workflows/build-apk.yml` builds a **debug APK** on GitHub's Android build runner.
1. Create a GitHub repository and upload *all* project files, including the hidden `.github` folder, to the default branch.
2. Open **Actions → Build Creta Diagnostic APK → Run workflow** (or wait for the push workflow).
3. Open the successful workflow run and download artifact **Creta-Diagnostic-Android-APK**.
4. Extract the artifact ZIP to obtain `app-debug.apk` and install it on the Android phone.

**Important:** No APK was compiled or tested in this delivery environment. The app is read-only diagnostics, **not** a steering calibration app. CaRPM interoperability is not yet verified.
