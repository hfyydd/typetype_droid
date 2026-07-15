import worker, { ErrorEvent, MessageEvents, ThreadWorkerGlobalScope } from '@ohos.worker';
import hymt from 'libtypetype_hymt.so';
import { getTranslationTarget, TranslationTargetOption } from '../common/TranslationTargets';

const workerPort: ThreadWorkerGlobalScope = worker.workerPort;
const MODEL_RAW_PATH = 'translation-models/Hy-MT2-1.8B-Q4_K_M.gguf';
const MODEL_FILE_NAME = 'Hy-MT2-1.8B-Q4_K_M.gguf';
const MODEL_EXPECTED_SIZE = 1133080448;
const DEFAULT_PREDICT_LENGTH = 160;

let modelReady = false;

function buildPrompt(text: string, target: TranslationTargetOption): string {
  return `将以下文本翻译为${target.promptLabel}，注意只需要输出翻译后的结果，不要额外解释：\n\n${text}`;
}

function normalizeOutput(value: string): string {
  return value
    .replace(/<\/?s>/g, '')
    .replace(/<\|endoftext\|>/g, '')
    .trim();
}

function initModel(context: Context): void {
  if (modelReady) {
    return;
  }

  const modelPath = `${context.filesDir}/translation-models/${MODEL_FILE_NAME}`;
  hymt.copyRawFileToSandbox(
    context.resourceManager,
    MODEL_RAW_PATH,
    modelPath,
    MODEL_EXPECTED_SIZE
  );
  hymt.loadModel(modelPath);
  modelReady = true;
}

workerPort.onmessage = (e: MessageEvents) => {
  const msgType = e.data['msgType'] as string;

  if (msgType === 'typetype-translation-init') {
    try {
      const context = e.data['context'] as Context;
      initModel(context);
      workerPort.postMessage({
        msgType: 'typetype-translation-init-done',
        systemInfo: hymt.systemInfo()
      });
    } catch (error) {
      workerPort.postMessage({
        msgType: 'typetype-translation-error',
        message: JSON.stringify(error)
      });
    }
    return;
  }

  if (msgType === 'typetype-translation-run') {
    try {
      if (!modelReady) {
        throw new Error('HY-MT2 model is not ready');
      }
      const requestId = e.data['requestId'] as number;
      const rawText = e.data['text'] as string;
      const targetCode = e.data['targetCode'] as string;
      const target = getTranslationTarget(targetCode);
      const prompt = buildPrompt(rawText.trim(), target);
      const result = normalizeOutput(hymt.translate(prompt, DEFAULT_PREDICT_LENGTH));
      workerPort.postMessage({
        msgType: 'typetype-translation-result',
        requestId,
        text: result,
        targetCode
      });
    } catch (error) {
      workerPort.postMessage({
        msgType: 'typetype-translation-error',
        message: JSON.stringify(error)
      });
    }
  }
}

workerPort.onmessageerror = (_e: MessageEvents) => {
}

workerPort.onerror = (e: ErrorEvent) => {
  workerPort.postMessage({
    msgType: 'typetype-translation-error',
    message: e.message
  });
}
