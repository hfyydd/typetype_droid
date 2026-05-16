# Model Assets

This repository intentionally does not track model files. Download the assets below before building or running the offline speech and translation flows.

## Required layout

Place files under `app/src/main/assets/` with this layout:

```text
app/src/main/assets/
  silero_vad.onnx
  sherpa-onnx-paraformer-zh-2023-09-14/
    model.int8.onnx
    tokens.txt
  sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/
    encoder-epoch-99-avg-1.int8.onnx
    decoder-epoch-99-avg-1.onnx
    joiner-epoch-99-avg-1.int8.onnx
    tokens.txt
  translation-models/
    HY-MT1.5-1.8B-Q4_K_M.gguf
```

## Download sources

- Hy-MT translation model: download `HY-MT1.5-1.8B-Q4_K_M.gguf` from [Tencent HY-MT1.5-1.8B-GGUF](https://huggingface.co/tencent/HY-MT1.5-1.8B-GGUF) and place it at `app/src/main/assets/translation-models/HY-MT1.5-1.8B-Q4_K_M.gguf`.
- Offline Chinese ASR model: download `sherpa-onnx-paraformer-zh-2023-09-14.tar.bz2` from the [sherpa-onnx offline Paraformer model page](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-paraformer/paraformer-models.html#csukuangfj-sherpa-onnx-paraformer-zh-2023-09-14-chinese), or directly from `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-2023-09-14.tar.bz2`, and place its files in `app/src/main/assets/sherpa-onnx-paraformer-zh-2023-09-14/`.
- Streaming Chinese ASR model: download `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2` from the [sherpa-onnx streaming Zipformer model page](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html#csukuangfj-sherpa-onnx-streaming-zipformer-zh-14m-2023-02-23-chinese), or directly from `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2`, and place its files in `app/src/main/assets/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/`.
- Voice activity detection: download `silero_vad.onnx` from the [sherpa-onnx VAD model page](https://k2-fsa.github.io/sherpa/onnx/vad/silero-vad.html), or directly from `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx`, and place it at `app/src/main/assets/silero_vad.onnx`.

## Notes

- Keep downloaded assets out of Git. The repository `.gitignore` excludes these model paths.
- The app code expects the exact directory and file names shown above. The older 2-bit HY-MT GGUF asset is not used by the Android build because it is not accepted by the bundled llama.cpp runtime.
- If a source publishes a compressed archive, extract it first and copy only the expected files into `app/src/main/assets/`.
