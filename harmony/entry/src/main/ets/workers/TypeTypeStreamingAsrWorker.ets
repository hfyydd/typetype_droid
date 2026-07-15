import worker, { ErrorEvent, MessageEvents, ThreadWorkerGlobalScope } from '@ohos.worker';
import {
  OnlineModelConfig,
  OnlineRecognizer,
  OnlineRecognizerConfig,
  OnlineStream
} from 'sherpa_onnx';

const workerPort: ThreadWorkerGlobalScope = worker.workerPort;
const MODEL_DIR = 'sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01';

let recognizer: OnlineRecognizer | undefined;
let micStream: OnlineStream | undefined;

function createModelConfig(): OnlineModelConfig {
  const modelConfig = new OnlineModelConfig();
  modelConfig.zipformer2Ctc.model = `${MODEL_DIR}/model.int8.onnx`;
  modelConfig.tokens = `${MODEL_DIR}/tokens.txt`;
  modelConfig.numThreads = 2;
  modelConfig.provider = 'cpu';
  modelConfig.debug = false;
  return modelConfig;
}

function createRecognizer(context: Context): OnlineRecognizer {
  const config = new OnlineRecognizerConfig();
  config.modelConfig = createModelConfig();
  config.enableEndpoint = true;
  config.hotwordsFile = 'hotwords_zh_cn.txt';
  config.hotwordsScore = 2.0;
  config.rule1MinTrailingSilence = 1.2;
  config.rule2MinTrailingSilence = 0.8;
  config.rule3MinUtteranceLength = 12;
  return new OnlineRecognizer(config, context.resourceManager);
}

function decodeStream(isFinal: boolean = false): void {
  if (!recognizer || !micStream) {
    return;
  }

  while (recognizer.isReady(micStream)) {
    recognizer.decode(micStream);
  }

  const result = recognizer.getResult(micStream);
  const text = result.text.trim();
  const endpoint = recognizer.isEndpoint(micStream);

  if (text.length > 0 || isFinal || endpoint) {
    workerPort.postMessage({
      msgType: 'typetype-asr-result',
      text,
      isEndpoint: endpoint,
      isFinal
    });
  }

  if (endpoint && !isFinal) {
    recognizer.reset(micStream);
  }
}

function resetMicStream(): void {
  if (!recognizer) {
    return;
  }
  micStream = recognizer.createStream();
}

workerPort.onmessage = (e: MessageEvents) => {
  const msgType = e.data['msgType'] as string;

  if (msgType === 'typetype-asr-init') {
    if (!recognizer) {
      const context = e.data['context'] as Context;
      recognizer = createRecognizer(context);
    }
    resetMicStream();
    workerPort.postMessage({ msgType: 'typetype-asr-init-done' });
    return;
  }

  if (msgType === 'typetype-asr-start') {
    resetMicStream();
    workerPort.postMessage({ msgType: 'typetype-asr-started' });
    return;
  }

  if (msgType === 'typetype-asr-samples') {
    if (!micStream) {
      resetMicStream();
    }
    if (!micStream) {
      return;
    }
    const samples = e.data['samples'] as Float32Array;
    const sampleRate = e.data['sampleRate'] as number;
    micStream.acceptWaveform({ samples, sampleRate });
    decodeStream(false);
    return;
  }

  if (msgType === 'typetype-asr-stop') {
    if (micStream) {
      const sampleRate = e.data['sampleRate'] as number;
      const tailPadding = new Float32Array(Math.floor(sampleRate * 0.25));
      tailPadding.fill(0);
      micStream.acceptWaveform({ samples: tailPadding, sampleRate });
      micStream.inputFinished();
      decodeStream(true);
    } else {
      workerPort.postMessage({ msgType: 'typetype-asr-result', text: '', isEndpoint: true, isFinal: true });
    }
    return;
  }
}

workerPort.onmessageerror = (_e: MessageEvents) => {
}

workerPort.onerror = (e: ErrorEvent) => {
  workerPort.postMessage({
    msgType: 'typetype-asr-error',
    message: e.message
  });
}
