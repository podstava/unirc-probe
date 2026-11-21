# UniRC Input Probe v0.1

Diagnostic Android Studio project for SIYI UniRC 7. Does **not** control a gimbal or connect to MAVLink/Bluetooth.

## Build
Open folder in Android Studio with Android SDK 35 installed, sync Gradle, Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`. JDK 17 is recommended. Alternatively run `gradle assembleDebug` with an installed Gradle and Android SDK.

## Test
1. Install APK on UniRC 7 and open it.
2. Press **Devices** to list Android input devices and axes.
3. Move LD and RD separately; press candidate buttons. Watch for AXIS/KEY entries.
4. Press **Export** and save the `.txt` log. Share the log for analysis.
5. If no AXIS/KEY changes appear, the RC controls are probably not exposed through standard Android Input API; investigate SIYI SDK next.

The app captures input only while foregrounded. Android system-reserved buttons may not be delivered. CH6/CH7 are SIYI RC channel numbers, not necessarily Android axis IDs. No network, storage, or Bluetooth permissions are requested.

## Build without Android Studio (GitHub Actions)
Upload this project's **contents** to the root of a GitHub repository, including `.github/workflows/build-apk.yml` (the `.github` folder is hidden on some systems). Open Actions > Build UniRC Input Probe APK > Run workflow. After successful completion, download the `UniRCInputProbe-debug-apk` artifact and extract `app-debug.apk`.

Requires network access to Maven repositories from GitHub runners. APK has not been compiled or device-tested in this environment.
