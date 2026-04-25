# sherpa-onnx Android Runtime

This app uses the official `sherpa-onnx` Android runtime from:

https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.40/sherpa-onnx-v1.12.40-android.tar.bz2

Current integration:

- Kotlin API source files are copied from `sherpa-onnx/sherpa-onnx/kotlin-api`.
- Native runtime libraries are packaged for `arm64-v8a` and `x86_64`.
- ASR models are expected under `app/src/main/assets` using the paths defined by the official model config helpers.

V1 model targets:

- Streaming: `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`
- Offline: `sherpa-onnx-paraformer-zh-2023-09-14`
- VAD: `silero_vad.onnx`
