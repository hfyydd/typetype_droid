# TypeType HarmonyOS 6.1 Port

This directory is the native HarmonyOS/OpenHarmony port for devices that no
longer run Android APKs directly, such as HarmonyOS 6.1 / OpenHarmony 6.1
devices.

The Android code remains the source of truth for the regular Android and Xiaomi
floating-window builds. This Harmony branch keeps its own Stage-model project
under `harmony/`.

## Local Toolchain

- OpenHarmony SDK: `C:\OpenHarmony\6.1-sdk\openharmony-6.1.0.31`
- hvigor: `C:\OpenHarmony\hvigor\6.0.0.868`
- wrapper: `C:\OpenHarmony\bin\hvigorw.cmd`

## Build

```powershell
cd C:\typetype_droid\harmony
C:\OpenHarmony\bin\hvigorw.cmd assembleApp
```

The generated unsigned package appears under `build\outputs` and
`entry\build\default\outputs`. The 2026-06-01 full local ASR build also creates
signed distribution artifacts in the repository root:

- `C:\typetype_droid\typetype-harmonyos-6.1-full-asr-2026.06.01-signed.app`
- `C:\typetype_droid\typetype-harmonyos-6.1-full-asr-2026.06.01-signed.hap`

The public SDK installed locally is OpenHarmony 6.1 API 23
(`openharmony-6.1.0.31`). Huawei commercial HarmonyOS 6.1 devices can report API
24, so final customer-device installation still needs validation on an online
Harmony device and may require Huawei/DevEco-issued signing material.

## Port Scope

The first native milestone focuses on:

- registering TypeType as an `InputMethodExtensionAbility`;
- showing a fixed bottom soft-keyboard panel on phone, foldable, and tablet;
- keeping a small settings/switch button visible in the panel;
- opening the system input-method list through `InputMethodListDialog`, with the
  older switch API kept only as a fallback;
- committing recognized text through Harmony `InputClient.insertText`;
- clearing in-progress text after commit so sent text does not remain in the
  input box;
- preserving the punctuation helper used by the Android stream and translation
  flow.
- running local streaming ASR with `sherpa_onnx` on Harmony/OpenHarmony native
  libraries and bundled Chinese hotwords.

HY-MT2 translation still requires porting the Android llama.cpp JNI layer to a
Harmony NAPI / OHOS `.so` runtime before it can run natively in this branch.
