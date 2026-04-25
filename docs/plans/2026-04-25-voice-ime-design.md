# TypeType Droid Voice IME Design

**Date:** 2026-04-25

**Status:** Approved for planning

## Goal

Build an Android native pure voice input method that works offline, is Chinese-first, and supports two user-selectable recognition modes:

- Streaming dictation: continuously writes recognition output into the focused text field
- Non-streaming dictation: recognizes per utterance and commits stable sentence-level results

This is a pure voice IME. It does not provide a normal keyboard layout.

## Product Scope

### Included in V1

- Android native `InputMethodService`
- Pure voice input panel with reduced height
- Offline ASR powered by `sherpa-onnx`
- Two modes:
  - Streaming
  - Non-streaming
- Continuous dictation without auto-switching away from the voice IME after each utterance
- Chinese-first model selection
- Direct writing into the active text field
- Session shutdown when the target field loses focus

### Explicitly Excluded from V1

- Full keyboard UI
- In-panel transcript preview
- Cloud ASR
- Multilingual-first models
- Exposed Android `RecognitionService`
- Contact dictionary, hotwords, punctuation post-edit UI

## User Experience

### Core Interaction

- When the user focuses a text field and activates this IME, a low-height voice panel is shown instead of a keyboard.
- The panel contains:
  - Primary microphone button
  - Recognition mode toggle: streaming / non-streaming
  - Minimal status indicator: idle / listening / decoding / error
- The panel does not display recognized text.
- Recognized text is written only into the active input field.

### Streaming Mode Behavior

- Audio is captured continuously while listening.
- Recognition output is written into the focused field continuously.
- The IME may revise a small recent tail of text as the decoder stabilizes.
- The IME does not wait for a final utterance result before writing.
- Endpoint detection is still used to segment sessions internally, but the IME remains active after each segment.

### Non-Streaming Mode Behavior

- Audio is buffered into speech segments using VAD.
- Each finalized segment is decoded by the offline recognizer.
- Completed segment text is committed to the focused field as stable text.

### Focus and Visibility Rules

- If there is no valid focused text field, the IME should stop audio capture and hide naturally with the system IME lifecycle.
- If the input connection becomes invalid mid-session, the current session must stop immediately.
- No pending ASR result should be applied after focus loss.

## UX Decisions

### Pure Voice IME

The product is not a conventional keyboard with a voice button. It is a dedicated voice input method. This keeps the surface small and aligns with the user's workflow: switch into voice mode when dictating, stay there for multiple utterances, then switch away manually if needed.

### Low-Height Panel

The panel should be substantially shorter than a normal keyboard. The design target is a compact control surface rather than a typing surface. A normal keyboard height would waste screen space because there are no key rows to show.

### No Transcript in the Panel

The panel intentionally avoids showing transcript text. This keeps the user's attention on the real source of truth: the target text field. It also avoids mismatch between panel text and field text when the streaming writer performs revisions.

## Reference Projects and Takeaways

### OpenBoard

Use as a mature reference for:

- Android IME manifest structure
- `InputMethodService` lifecycle boundaries
- Setup and settings entry points

Reference:
- https://github.com/openboard-team/openboard

### FlorisBoard

Use as a reference for:

- Modern Kotlin IME architecture
- Lifecycle-aware IME service patterns
- Window and service coordination

Reference:
- https://github.com/florisboard/florisboard

### FUTO Voice Input

Use as the closest product reference for:

- Pure voice IME behavior
- Staying inside IME instead of bouncing through an Activity
- Writing results directly into the focused field

Reference:
- https://github.com/futo-org/voice-input

### WhisperInput

Use as a reference for:

- Offline voice-first Android UX
- Separation of recognition engine and UI concerns
- Practical issues around model warmup and recording lifecycle

Reference:
- https://github.com/alex-vt/WhisperInput

### Sayboard

Use as a reference for:

- Voice keyboard packaging
- Optional longer-term path to expose recognition as a separate service

Reference:
- https://github.com/ElishaAz/Sayboard

## Technical Architecture

The implementation should stay within a single IME-hosted app for V1, but be internally modular.

### Modules

#### `app`

Android app module containing:

- IME service declaration
- Settings / onboarding entrypoints
- Voice panel UI
- Dependency assembly

#### `VoiceImeService`

Primary `InputMethodService` implementation responsible for:

- IME lifecycle
- Input view creation
- Reacting to start / finish input events
- Access to `currentInputConnection`
- Session startup and shutdown

#### `VoiceSessionController`

Owns the active dictation session and coordinates:

- Current mode
- Audio capture lifecycle
- ASR engine state
- Focus validity
- Error handling

Suggested states:

- `Idle`
- `Preparing`
- `Listening`
- `Decoding`
- `Stopping`
- `Error`

#### `AudioCaptureEngine`

Encapsulates `AudioRecord` and microphone handling:

- 16 kHz mono PCM capture
- Buffer sizing
- Start / stop / release
- Audio focus and interruption response

#### `StreamingAsrEngine`

Wrapper around `sherpa-onnx OnlineRecognizer`:

- Stream creation
- Incremental decode loop
- Endpoint checks
- Incremental text extraction

#### `OfflineAsrEngine`

Wrapper around:

- `Vad`
- `OfflineRecognizer`

Responsibilities:

- Feed captured samples into VAD
- Extract completed speech segments
- Run segment-level offline decode

#### `InputCommitController`

Single abstraction for writing text to the active field:

- Validate connection before write
- Distinguish streaming and offline write semantics
- Reset on focus loss

#### `StreamingDiffWriter`

Specialized writer for streaming mode:

- Track previously written session text
- Compare previous result with new result
- Replace only the recent changed suffix
- Limit rewrite scope to a configurable tail window

## Text Writing Strategy

### Why Diff-Based Writing Is Required

Streaming recognizers often revise recent text as more audio arrives. If the app only appends, errors accumulate and the recognized text quality degrades quickly. If the app rewrites too broadly, the field becomes unstable and risks damaging user-edited text.

### Recommended Strategy

Maintain three values per streaming session:

- `sessionCommittedText`
- `latestRecognizerText`
- `rewriteWindowChars`

For each new recognizer result:

1. Compare it to the previous recognizer text
2. Compute the longest stable prefix
3. Restrict rewrites to only the recent suffix window
4. Delete the old unstable suffix from the field
5. Insert the new unstable suffix
6. Update session tracking

### Safety Rules

- Never rewrite text outside the current session-owned tail
- Stop rewriting immediately if the cursor moves unexpectedly
- Stop the session if the input connection becomes invalid
- Do not continue applying results after focus loss

## ASR Strategy

### Streaming Mode

Use `sherpa-onnx OnlineRecognizer`.

Desired characteristics:

- Chinese-first streaming model
- Low-latency output
- Sufficient stability for small-tail revisions

### Non-Streaming Mode

Use `sherpa-onnx Vad + OfflineRecognizer`.

Desired characteristics:

- Better sentence stability
- Lower correction churn
- Stronger quality for users who prefer utterance-level commit

### Model Packaging

For V1:

- Prefer local packaged assets
- Prioritize `arm64-v8a`
- Keep model switching limited to the two predefined modes

Do not implement an in-app model marketplace or downloader in V1 unless packaging proves infeasible.

## Android Lifecycle Requirements

The IME must behave safely across lifecycle boundaries.

### Required Reactions

- `onCreateInputView`: build compact panel UI
- `onStartInput` / `onStartInputView`: validate target field, prepare session state
- `onFinishInputView` / `onFinishInput`: stop capture and clear session state
- On invalid `InputConnection`: abort session immediately

### Session Teardown Must

- Stop recording
- Release `AudioRecord`
- Release ASR streams where applicable
- Clear session-owned diff tracking
- Drop undelivered recognition results

## Error Handling

V1 should explicitly surface only a small set of errors:

- Microphone permission missing
- Model initialization failed
- Recording start failed
- Input field unavailable

The panel should communicate these with minimal status text and recoverable actions where possible.

## Testing Strategy

### Unit Tests

- Streaming diff algorithm
- Session state transitions
- Input commit policy behavior for focus loss and invalid connection

### Instrumentation / Device Tests

- IME activation on text fields
- Continuous streaming text updates
- Non-streaming utterance commit
- Session teardown on focus loss
- Compact panel visibility

### Manual Validation

- WeChat / browser / notes app / search box compatibility
- Cursor behavior during streaming rewrites
- Latency and stability on real arm64 device

## Risks

### Risk: Streaming Rewrite Instability

Continuous field writes may behave differently across host apps. The diff writer must be conservative and bounded.

### Risk: Model Size and Startup Time

On-device assets may increase APK size and cold-start time. Prewarming and asset strategy should be measured early.

### Risk: IME Lifecycle Edge Cases

`InputMethodService` can lose input connection or input view state abruptly. Session ownership must be strict.

### Risk: Permissions from IME Context

Permission handling is awkward from services. The app needs a clear settings or onboarding flow to secure microphone access before use.

## Recommended Execution Order

1. Scaffold Android IME shell
2. Build compact voice panel
3. Add audio capture and session controller
4. Integrate streaming ASR path
5. Implement diff-based field writer
6. Integrate non-streaming path
7. Add settings and mode persistence
8. Run device validation and iterate
