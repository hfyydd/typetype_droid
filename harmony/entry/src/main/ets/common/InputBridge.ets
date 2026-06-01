import inputMethodEngine from '@ohos.inputMethodEngine';
import hilog from '@ohos.hilog';
import { applyFinalPunctuation, applyStreamingPunctuation } from './PunctuationRules';

const DOMAIN = 0x54595045;
const TAG = 'TypeTypeInputBridge';

type DraftListener = (draft: string) => void;

export class InputBridge {
  private static currentClient?: inputMethodEngine.InputClient;
  private static draft: string = '';
  private static listeners: Array<DraftListener> = [];

  static bindClient(inputClient: inputMethodEngine.InputClient): void {
    InputBridge.currentClient = inputClient;
  }

  static clearClient(): void {
    InputBridge.currentClient = undefined;
    InputBridge.clearDraft();
  }

  static onDraftChanged(listener: DraftListener): void {
    InputBridge.listeners.push(listener);
    listener(InputBridge.draft);
  }

  static offDraftChanged(listener: DraftListener): void {
    InputBridge.listeners = InputBridge.listeners.filter((item: DraftListener) => item !== listener);
  }

  static updateDraft(rawText: string): void {
    InputBridge.draft = applyStreamingPunctuation(rawText);
    InputBridge.emitDraft();
  }

  static clearDraft(): void {
    InputBridge.draft = '';
    InputBridge.emitDraft();
  }

  static getDraft(): string {
    return InputBridge.draft;
  }

  static async commitFinalText(rawText: string): Promise<boolean> {
    const text = applyFinalPunctuation(rawText.length > 0 ? rawText : InputBridge.draft);
    if (text.length === 0 || InputBridge.currentClient === undefined) {
      return false;
    }

    try {
      const ok = await InputBridge.currentClient.insertText(text);
      InputBridge.clearDraft();
      return ok;
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'insertText failed: %{public}s', JSON.stringify(error));
      return false;
    }
  }

  static async deleteBackward(length: number = 1): Promise<boolean> {
    if (InputBridge.currentClient === undefined) {
      return false;
    }
    try {
      return await InputBridge.currentClient.deleteBackward(length);
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'deleteBackward failed: %{public}s', JSON.stringify(error));
      return false;
    }
  }

  static async sendEnter(): Promise<boolean> {
    if (InputBridge.currentClient === undefined) {
      return false;
    }
    try {
      return await InputBridge.currentClient.sendKeyFunction(inputMethodEngine.ENTER_KEY_TYPE_SEND);
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'send enter failed: %{public}s', JSON.stringify(error));
      return false;
    }
  }

  private static emitDraft(): void {
    InputBridge.listeners.forEach((listener: DraftListener) => {
      listener(InputBridge.draft);
    });
  }
}
