# HarmonyOS 4.2 Android Compatibility Build

This branch records the HarmonyOS 4.2.x delivery path for TypeType Android
0.3.1. HarmonyOS 4.2 devices install the Android APK compatibility build rather
than the native Harmony/OpenHarmony HAP package.

## Target System

- HarmonyOS 4.2.0.237
- Android APK compatibility runtime

## Build Output

Local delivery artifact generated on 2026-06-07:

- `typetype-HarmonyOS-4.2.0.237-0.3.1.apk`

## Version

- `versionName`: `0.3.1`
- `versionCode`: `5`
- `applicationId`: `com.typetype.droid`

## Verification

- `.\gradlew.bat :app:assembleRelease`
- APK metadata confirmed `versionName=0.3.1` and `versionCode=5`.
- APK contents include the bundled HY-MT2 translation model and sherpa-onnx ASR
  assets required by the Android 0.3.1 feature set.
