# Voice IME Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Chinese-first offline pure voice Android IME with selectable streaming and non-streaming dictation modes, compact IME UI, direct field writing, and strict focus-safe session handling.

**Architecture:** Create a single Android app hosting an `InputMethodService`, then isolate audio capture, ASR engines, session control, and field-writing logic into separate modules/classes. Ship streaming and non-streaming modes through one shared IME surface, with the streaming path using a diff-based writer that can revise a bounded recent suffix.

**Tech Stack:** Android native IME, Kotlin, Gradle, `InputMethodService`, `AudioRecord`, `sherpa-onnx` Android AAR/JNI/assets, unit tests, Android instrumentation tests.

---

### Task 1: Bootstrap the Android IME project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/method.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/layout/`

**Step 1: Write the failing project bootstrap check**

Create a minimal CI/build target expectation by planning to run:

```bash
./gradlew :app:assembleDebug
```

Expected initial failure before files exist:

```text
Task 'app:assembleDebug' not found / no Gradle build present
```

**Step 2: Create the minimal Gradle Android application skeleton**

Include:

- Kotlin Android app module
- `minSdk` compatible with `sherpa-onnx` Android examples
- `compileSdk` aligned with current Android toolchain
- IME manifest service entry

**Step 3: Add IME metadata**

Declare:

- `android.permission.BIND_INPUT_METHOD`
- `android.view.InputMethod` intent filter
- `@xml/method` metadata

**Step 4: Run bootstrap build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

or a smaller follow-up list of toolchain errors to fix next.

**Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app
git commit -m "Establish Android IME app scaffold"
```

### Task 2: Add the compact voice IME shell

**Files:**
- Create: `app/src/main/java/.../VoiceImeService.kt`
- Create: `app/src/main/java/.../ui/VoiceInputView.kt`
- Create: `app/src/main/res/layout/view_voice_ime.xml` or Compose equivalents
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/androidTest/...`

**Step 1: Write the failing IME lifecycle test**

Target behavior:

- IME service can create its input view
- Input view height is compact
- View does not contain keyboard rows or transcript text area

**Step 2: Implement the service skeleton**

Create `VoiceImeService` with:

- `onCreateInputView()`
- `onStartInput()`
- `onStartInputView()`
- `onFinishInput()`
- `onFinishInputView()`

**Step 3: Build the compact panel**

Include only:

- Mic button
- Mode toggle
- Status label

Do not include:

- Normal keys
- Transcript preview

**Step 4: Run tests/build**

Run:

```bash
./gradlew :app:assembleDebug :app:connectedDebugAndroidTest
```

Expected:

```text
IME view is created and visible in test host
```

**Step 5: Commit**

```bash
git add app/src/main app/src/androidTest
git commit -m "Add compact pure voice IME shell"
```

### Task 3: Add microphone permission flow and setup entrypoint

**Files:**
- Create: `app/src/main/java/.../MainActivity.kt`
- Create: `app/src/main/java/.../permissions/...`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/...`

**Step 1: Write the failing permission/setup tests**

Cover:

- App can launch a setup screen
- Setup screen can guide the user to grant microphone permission
- Setup screen can guide the user to enable/select the IME

**Step 2: Add launcher activity**

This activity should:

- Explain that this is a voice IME
- Request `RECORD_AUDIO`
- Deep-link users to IME settings if needed

**Step 3: Verify the manifest**

Add:

- `RECORD_AUDIO`
- launcher activity

**Step 4: Run tests/build**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

**Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Add setup flow for mic permission and IME enablement"
```

### Task 4: Integrate `sherpa-onnx` Android dependency path

**Files:**
- Create: `third_party/` notes or scripts if needed
- Modify: `app/build.gradle.kts`
- Create: `app/libs/` or dependency declaration path
- Create: `docs/plans/` integration notes if needed

**Step 1: Write the failing dependency verification**

Plan to run:

```bash
./gradlew :app:assembleDebug
```

Expected initial failure:

```text
Unresolved reference to sherpa-onnx classes
```

**Step 2: Add local AAR or module-based integration**

Preferred V1 path:

- Build or import `sherpa-onnx` Android AAR
- Wire required JNI libs and assets strategy

**Step 3: Add a minimal compile-time smoke usage**

Instantiate configs for:

- `OnlineRecognizer`
- `OfflineRecognizer`
- `Vad`

**Step 4: Run compile verification**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 5: Commit**

```bash
git add app/build.gradle.kts app/libs third_party docs/plans
git commit -m "Integrate sherpa-onnx Android runtime"
```

### Task 5: Build the shared audio capture engine

**Files:**
- Create: `app/src/main/java/.../audio/AudioCaptureEngine.kt`
- Create: `app/src/test/.../audio/AudioCaptureEngineTest.kt`
- Modify: `app/src/main/java/.../VoiceImeService.kt`

**Step 1: Write the failing unit tests**

Cover:

- Start/stop state transitions
- Invalid double-start protection
- Proper release on stop

**Step 2: Implement `AudioCaptureEngine`**

Include:

- 16 kHz mono PCM
- background read loop
- listener/callback interface for buffers

**Step 3: Hook lifecycle teardown**

Ensure IME lifecycle stops and releases audio on:

- `onFinishInput`
- `onFinishInputView`
- focus invalidation

**Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

**Step 5: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "Add shared microphone capture engine"
```

### Task 6: Build the voice session state machine

**Files:**
- Create: `app/src/main/java/.../session/VoiceSessionController.kt`
- Create: `app/src/main/java/.../session/VoiceSessionState.kt`
- Create: `app/src/test/.../session/VoiceSessionControllerTest.kt`

**Step 1: Write the failing session tests**

Cover:

- `Idle -> Preparing -> Listening`
- graceful stop from listening
- focus loss aborts the session
- invalid connection drops results

**Step 2: Implement the state machine**

Define strict transitions and a single active session owner.

**Step 3: Wire service callbacks**

Connect IME lifecycle and UI actions into session events.

**Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

**Step 5: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "Add voice session lifecycle controller"
```

### Task 7: Implement the streaming ASR engine

**Files:**
- Create: `app/src/main/java/.../asr/streaming/StreamingAsrEngine.kt`
- Create: `app/src/test/.../asr/streaming/...`
- Modify: `app/src/main/java/.../session/VoiceSessionController.kt`

**Step 1: Write the failing streaming engine tests**

Cover:

- stream lifecycle
- incremental callback emission
- clean reset on stop

**Step 2: Implement `OnlineRecognizer` wrapper**

Responsibilities:

- create/release streams
- feed waveform chunks
- decode while ready
- expose latest recognized text

**Step 3: Add endpoint integration**

Endpointing may segment internal utterances, but must not close the IME.

**Step 4: Run tests/build**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

**Step 5: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "Add streaming sherpa-onnx recognition path"
```

### Task 8: Implement diff-based streaming field writing

**Files:**
- Create: `app/src/main/java/.../input/InputCommitController.kt`
- Create: `app/src/main/java/.../input/StreamingDiffWriter.kt`
- Create: `app/src/test/.../input/StreamingDiffWriterTest.kt`
- Modify: `app/src/main/java/.../session/VoiceSessionController.kt`

**Step 1: Write the failing diff-writer tests**

Cover:

- pure append case
- recent-tail rewrite case
- no rewrite outside owned suffix
- abort on invalid cursor/connection assumptions

Example target:

```kotlin
previous = "今天天气"
next = "今天天气不错"
expectedDelete = ""
expectedInsert = "不错"
```

and:

```kotlin
previous = "语音输入法"
next = "语音输入"
expectedDelete = "法"
expectedInsert = ""
```

**Step 2: Implement session-owned diff model**

Track:

- last recognizer text
- session-owned tail length
- bounded rewrite window

**Step 3: Write to `InputConnection` conservatively**

Use:

- delete surrounding text only within owned suffix
- insert replacement text

**Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected:

```text
All diff-writer tests pass
```

**Step 5: Manual verification**

Verify on device:

- text grows live in the focused field
- recent suffix can be corrected
- focus loss stops updates

**Step 6: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "Add live diff-based streaming text writer"
```

### Task 9: Implement the non-streaming ASR path

**Files:**
- Create: `app/src/main/java/.../asr/offline/OfflineAsrEngine.kt`
- Create: `app/src/test/.../asr/offline/...`
- Modify: `app/src/main/java/.../session/VoiceSessionController.kt`

**Step 1: Write the failing offline path tests**

Cover:

- VAD receives chunks
- completed segments trigger decode
- completed text commits once per segment

**Step 2: Implement VAD + offline decode**

Use:

- `Vad`
- `OfflineRecognizer`

**Step 3: Reuse the same commit controller**

Offline mode should commit stable sentence text without transcript preview.

**Step 4: Run tests/build**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

**Step 5: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "Add non-streaming sherpa-onnx dictation path"
```

### Task 10: Add mode selection and persistence

**Files:**
- Create: `app/src/main/java/.../settings/VoiceImePreferences.kt`
- Modify: `app/src/main/java/.../ui/VoiceInputView.kt`
- Modify: `app/src/main/java/.../session/VoiceSessionController.kt`
- Create: `app/src/test/.../settings/...`

**Step 1: Write the failing preference tests**

Cover:

- selected mode persists
- IME restores the last chosen mode

**Step 2: Implement mode toggle**

Support:

- streaming
- non-streaming

**Step 3: Persist selection**

Use app preferences / datastore / shared preferences consistent with project setup.

**Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

**Step 5: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "Persist user-selected dictation mode"
```

### Task 11: Add focus-loss and teardown hardening

**Files:**
- Modify: `app/src/main/java/.../VoiceImeService.kt`
- Modify: `app/src/main/java/.../session/VoiceSessionController.kt`
- Modify: `app/src/main/java/.../input/InputCommitController.kt`
- Create: `app/src/test/.../focus/...`

**Step 1: Write the failing teardown tests**

Cover:

- no writes after focus loss
- no writes after input finish
- session state clears correctly

**Step 2: Implement teardown guards**

Add checks for:

- null/invalid `currentInputConnection`
- input finish callbacks
- unexpected cursor movement

**Step 3: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

**Step 4: Manual verification**

Validate:

- switch apps while listening
- tap outside the field
- dismiss input field

**Step 5: Commit**

```bash
git add app/src/main/java app/src/test
git commit -m "Harden session teardown on focus and lifecycle loss"
```

### Task 12: Package assets and validate on device

**Files:**
- Create/Modify: asset directories for models
- Modify: packaging configuration
- Create: validation notes if useful

**Step 1: Add model assets**

Package the chosen:

- streaming Chinese-first model
- offline/VAD assets

**Step 2: Measure startup path**

Verify:

- model load time
- first dictation latency
- memory footprint

**Step 3: Run full verification**

Run:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Then install on device and verify:

- IME enablement
- streaming direct field writing
- bounded suffix corrections
- offline mode utterance commit
- compact panel height
- no transcript in panel
- session stops on focus loss

**Step 4: Commit**

```bash
git add app
git commit -m "Package models and validate end-to-end voice IME flow"
```

### Task 13: Final cleanup and verification

**Files:**
- Modify: only files needed after review

**Step 1: Run final verification suite**

Run:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Run Android lint if configured:

```bash
./gradlew :app:lintDebug
```

**Step 2: Review for scope discipline**

Confirm V1 still excludes:

- normal keyboard
- transcript panel
- cloud ASR
- recognition service export

**Step 3: Prepare integration summary**

Record:

- chosen models
- known host-app compatibility gaps
- remaining risks

**Step 4: Commit**

```bash
git add .
git commit -m "Stabilize offline pure voice IME for initial delivery"
```
