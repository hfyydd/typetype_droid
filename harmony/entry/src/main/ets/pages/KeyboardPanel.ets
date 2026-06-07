import { abilityAccessCtrl, bundleManager, common, Permissions } from '@kit.AbilityKit';
import { audio } from '@kit.AudioKit';
import inputMethod from '@ohos.inputMethod';
import { InputMethodListDialog } from '@ohos.inputMethodList';
import promptAction from '@ohos.promptAction';
import worker, { MessageEvents } from '@ohos.worker';
import { InputBridge } from '../common/InputBridge';
import { TRANSLATION_TARGETS, TranslationTargetOption } from '../common/TranslationTargets';

interface PendingTranslationRequest {
  requestId: number;
  text: string;
  targetCode: string;
}

function buildTranslationSelectOptions(): SelectOption[] {
  const options: SelectOption[] = [];
  TRANSLATION_TARGETS.forEach((target: TranslationTargetOption) => {
    const option: SelectOption = { value: target.label };
    options.push(option);
  });
  return options;
}

@Entry
@Component
struct KeyboardPanel {
  @State private draft: string = '';
  @State private status: string = '正在加载模型';
  @State private statusColor: string = '#A8B9B2';
  @State private asrReady: boolean = false;
  @State private micStarted: boolean = false;
  @State private translationMode: boolean = false;
  @State private translationReady: boolean = false;
  @State private translationBusy: boolean = false;
  @State private selectedTargetIndex: number = 0;

  private readonly scriptURL: string = 'entry/ets/workers/TypeTypeStreamingAsrWorker.ets';
  private readonly translationScriptURL: string = 'entry/ets/workers/TypeTypeTranslationWorker.ets';
  private readonly sampleRate: number = 48000;
  private readonly translationTargets: TranslationTargetOption[] = TRANSLATION_TARGETS;
  private readonly translationSelectOptions: SelectOption[] = buildTranslationSelectOptions();
  private workerInstance?: worker.ThreadWorker;
  private translationWorkerInstance?: worker.ThreadWorker;
  private mic?: audio.AudioCapturer;
  private asrSegments: string[] = [];
  private currentPartial: string = '';
  private lastRecognizedText: string = '';
  private draftShouldPunctuate: boolean = true;
  private translationRequestId: number = 0;
  private activeTranslationRequestId: number = 0;
  private queuedTranslationRequest?: PendingTranslationRequest;

  private inputMethodListController: CustomDialogController = new CustomDialogController({
    builder: InputMethodListDialog(),
    autoCancel: true,
    alignment: DialogAlignment.Bottom
  });

  private draftListener = (value: string): void => {
    this.draft = value;
  };

  aboutToAppear(): void {
    InputBridge.onDraftChanged(this.draftListener);
    this.startAsrWorker();
  }

  aboutToDisappear(): void {
    InputBridge.offDraftChanged(this.draftListener);
    if (this.micStarted) {
      this.stopRecording();
    }
    if (this.workerInstance !== undefined) {
      this.workerInstance.terminate();
      this.workerInstance = undefined;
    }
    if (this.translationWorkerInstance !== undefined) {
      this.translationWorkerInstance.terminate();
      this.translationWorkerInstance = undefined;
    }
  }

  build() {
    Column() {
      Row() {
        Circle()
          .width(8)
          .height(8)
          .fill(this.statusColor)

        Text(this.status)
          .fontSize(16)
          .fontColor('#40504C')
          .margin({ left: 8 })

        Blank()

        Button({ type: ButtonType.Normal, stateEffect: true }) {
          Text('退格')
            .fontSize(16)
            .fontColor('#40504C')
        }
        .height(40)
        .padding({ left: 16, right: 16 })
        .backgroundColor('#EEF3F7')
        .borderRadius(14)
        .onClick(() => {
          InputBridge.deleteBackward();
        })
      }
      .width('100%')
      .padding({ left: 18, right: 18, top: 14 })

      Text(this.draft.length > 0 ? this.draft : ' ')
        .width('100%')
        .height(48)
        .fontSize(18)
        .fontColor('#1E2A27')
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .padding({ left: 22, right: 22, top: 8 })

      Row() {
        Button({ type: ButtonType.Normal, stateEffect: true }) {
          Text('原文')
            .fontSize(15)
            .fontColor(this.translationMode ? '#40504C' : Color.White)
        }
        .height(34)
        .padding({ left: 18, right: 18 })
        .backgroundColor(this.translationMode ? '#FFFFFF' : '#1B2220')
        .borderRadius(13)
        .onClick(() => {
          this.setTranslationMode(false);
        })

        Button({ type: ButtonType.Normal, stateEffect: true }) {
          Text('翻译')
            .fontSize(15)
            .fontColor(this.translationMode ? Color.White : '#40504C')
        }
        .height(34)
        .padding({ left: 18, right: 18 })
        .backgroundColor(this.translationMode ? '#1B2220' : '#FFFFFF')
        .borderRadius(13)
        .margin({ left: 8 })
        .onClick(() => {
          this.setTranslationMode(true);
        })

        if (this.translationMode) {
          Select(this.translationSelectOptions)
            .selected(this.selectedTargetIndex)
            .value(this.currentTranslationTarget().label)
            .height(34)
            .margin({ left: 10 })
            .font({ size: 15 })
            .fontColor('#1E2A27')
            .selectedOptionFont({ size: 15, weight: FontWeight.Medium })
            .optionFont({ size: 15 })
            .backgroundColor('#FFFFFF')
            .onSelect((index: number) => {
              this.selectTranslationTarget(index);
            })
        }

        Blank()
      }
      .width('100%')
      .height(42)
      .padding({ left: 18, right: 18 })

      Stack() {
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          Text(this.micStarted ? '停' : '麦')
            .fontSize(25)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
        }
        .width(86)
        .height(86)
        .backgroundColor(this.micStarted ? '#C9433C' : '#D64B43')
        .onClick(() => {
          this.toggleRecording();
        })

        Row() {
          Button({ type: ButtonType.Circle, stateEffect: true }) {
            Text('≡')
              .fontSize(20)
              .fontColor('#40504C')
          }
          .width(40)
          .height(40)
          .backgroundColor('#FFFFFF')
          .borderWidth(1)
          .borderColor('#D6DEE6')
          .onClick(() => {
            this.showInputMethodSwitcher();
          })

          Blank()

          Button({ type: ButtonType.Circle, stateEffect: true }) {
            Text('发')
              .fontSize(20)
              .fontWeight(FontWeight.Medium)
              .fontColor('#1E2A27')
          }
          .width(48)
          .height(48)
          .backgroundColor('#F3D23E')
          .onClick(() => {
            this.commitDraft();
          })
        }
        .width('100%')
        .padding({ left: 16, right: 18 })
      }
      .width('100%')
      .layoutWeight(1)
      .alignContent(Alignment.Center)
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#EFF4FA')
    .borderRadius({ topLeft: 22, topRight: 22 })
  }

  private async showInputMethodSwitcher(): Promise<void> {
    try {
      this.inputMethodListController.open();
    } catch (error) {
      try {
        await inputMethod.getSetting().showOptionalInputMethods();
      } catch (fallbackError) {
        try {
          promptAction.showToast({ message: '无法打开输入法切换' });
        } catch (_) {
        }
      }
    }
  }

  private setTranslationMode(enabled: boolean): void {
    if (this.translationMode === enabled) {
      return;
    }

    this.translationMode = enabled;
    if (enabled) {
      this.startTranslationWorker();
      if (this.lastRecognizedText.length > 0) {
        this.requestTranslation(this.lastRecognizedText);
      }
      return;
    }

    this.translationBusy = false;
    this.queuedTranslationRequest = undefined;
    this.draftShouldPunctuate = true;
    if (this.lastRecognizedText.length > 0) {
      InputBridge.updateDraft(this.lastRecognizedText, true);
      this.status = '已切回原文';
      this.statusColor = '#35C493';
    }
  }

  private selectTranslationTarget(index: number): void {
    if (index < 0 || index >= this.translationTargets.length) {
      return;
    }

    this.selectedTargetIndex = index;
    if (this.translationMode && this.lastRecognizedText.length > 0) {
      this.requestTranslation(this.lastRecognizedText);
    }
  }

  private currentTranslationTarget(): TranslationTargetOption {
    return this.translationTargets[this.selectedTargetIndex];
  }

  private translationTargetByCode(code: string): TranslationTargetOption {
    const found = this.translationTargets.find((target: TranslationTargetOption) => target.code === code);
    return found === undefined ? this.currentTranslationTarget() : found;
  }

  private requestTranslation(text: string): void {
    const sourceText = text.trim();
    if (sourceText.length === 0) {
      return;
    }

    const target = this.currentTranslationTarget();
    this.translationBusy = true;
    this.queuedTranslationRequest = {
      requestId: ++this.translationRequestId,
      text: sourceText,
      targetCode: target.code
    };

    if (this.translationWorkerInstance === undefined) {
      this.startTranslationWorker();
    }

    if (this.translationReady) {
      this.sendQueuedTranslation();
    } else {
      this.status = '翻译模型加载中';
      this.statusColor = '#F3D23E';
    }
  }

  private sendQueuedTranslation(): void {
    if (!this.translationReady ||
      this.translationWorkerInstance === undefined ||
      this.queuedTranslationRequest === undefined) {
      return;
    }

    const request = this.queuedTranslationRequest;
    this.queuedTranslationRequest = undefined;
    this.activeTranslationRequestId = request.requestId;
    this.status = `正在翻译为${this.translationTargetByCode(request.targetCode).label}`;
    this.statusColor = '#F3D23E';
    this.translationWorkerInstance.postMessage({
      msgType: 'typetype-translation-run',
      requestId: request.requestId,
      text: request.text,
      targetCode: request.targetCode
    });
  }

  private startAsrWorker(): void {
    if (this.workerInstance !== undefined) {
      return;
    }

    try {
      this.workerInstance = new worker.ThreadWorker(this.scriptURL, {
        name: 'TypeType Streaming ASR worker'
      });
      this.workerInstance.onmessage = (e: MessageEvents) => {
        this.handleWorkerMessage(e);
      };
      this.workerInstance.postMessage({
        msgType: 'typetype-asr-init',
        context: getContext()
      });
    } catch (error) {
      this.status = '模型加载失败';
      this.statusColor = '#D64B43';
    }
  }

  private startTranslationWorker(): void {
    if (this.translationWorkerInstance !== undefined) {
      return;
    }

    try {
      this.translationWorkerInstance = new worker.ThreadWorker(this.translationScriptURL, {
        name: 'TypeType HY-MT2 worker'
      });
      this.translationWorkerInstance.onmessage = (e: MessageEvents) => {
        this.handleTranslationMessage(e);
      };
      this.translationWorkerInstance.postMessage({
        msgType: 'typetype-translation-init',
        context: getContext()
      });
      this.status = '翻译模型加载中';
      this.statusColor = '#F3D23E';
    } catch (error) {
      this.translationReady = false;
      this.status = '翻译启动失败';
      this.statusColor = '#D64B43';
    }
  }

  private handleTranslationMessage(e: MessageEvents): void {
    const msgType = e.data['msgType'] as string;

    if (msgType === 'typetype-translation-init-done') {
      this.translationReady = true;
      if (this.translationMode && this.queuedTranslationRequest !== undefined) {
        this.sendQueuedTranslation();
      } else if (this.translationMode && !this.translationBusy) {
        this.status = '翻译已就绪';
        this.statusColor = '#35C493';
      }
      return;
    }

    if (msgType === 'typetype-translation-result') {
      const requestId = e.data['requestId'] as number;
      if (requestId !== this.activeTranslationRequestId) {
        return;
      }

      const translated = e.data['text'] as string;
      const targetCode = e.data['targetCode'] as string;
      const target = this.translationTargetByCode(targetCode);
      this.translationBusy = false;
      this.draftShouldPunctuate = target.punctuateLikeChinese;
      InputBridge.updateDraft(translated, this.draftShouldPunctuate);
      this.status = translated.trim().length > 0 ? `已翻译为${target.label}` : '翻译无结果';
      this.statusColor = translated.trim().length > 0 ? '#35C493' : '#D64B43';
      return;
    }

    if (msgType === 'typetype-translation-error') {
      this.translationBusy = false;
      this.translationReady = false;
      this.status = '翻译异常';
      this.statusColor = '#D64B43';
    }
  }

  private handleWorkerMessage(e: MessageEvents): void {
    const msgType = e.data['msgType'] as string;

    if (msgType === 'typetype-asr-init-done') {
      this.asrReady = true;
      this.status = '点按麦克风开始';
      this.statusColor = '#35C493';
      return;
    }

    if (msgType === 'typetype-asr-started') {
      this.status = '正在听';
      this.statusColor = '#35C493';
      return;
    }

    if (msgType === 'typetype-asr-result') {
      const text = e.data['text'] as string;
      const isEndpoint = e.data['isEndpoint'] as boolean;
      const isFinal = e.data['isFinal'] as boolean;
      this.handleAsrResult(text, isEndpoint, isFinal);
      return;
    }

    if (msgType === 'typetype-asr-error') {
      this.status = '识别异常';
      this.statusColor = '#D64B43';
    }
  }

  private async toggleRecording(): Promise<void> {
    if (this.micStarted) {
      await this.stopRecording();
      return;
    }
    await this.startRecording();
  }

  private async startRecording(): Promise<void> {
    if (!this.asrReady) {
      this.status = '模型加载中';
      return;
    }

    const micReady = await this.ensureMicReady();
    if (!micReady || this.mic === undefined || this.workerInstance === undefined) {
      return;
    }

    this.resetRecognitionState();
    this.workerInstance.postMessage({ msgType: 'typetype-asr-start' });

    try {
      await this.mic.start();
      this.micStarted = true;
      this.status = '正在听';
      this.statusColor = '#35C493';
    } catch (error) {
      this.status = '麦克风启动失败';
      this.statusColor = '#D64B43';
    }
  }

  private async stopRecording(): Promise<void> {
    if (this.mic !== undefined) {
      try {
        await this.mic.stop();
      } catch (_) {
      }
    }

    this.micStarted = false;
    this.status = '正在整理';
    this.statusColor = '#F3D23E';

    if (this.workerInstance !== undefined) {
      this.workerInstance.postMessage({
        msgType: 'typetype-asr-stop',
        sampleRate: this.sampleRate
      });
    }
  }

  private async ensureMicReady(): Promise<boolean> {
    if (this.mic !== undefined) {
      return true;
    }

    const permitted = await this.ensureMicrophonePermission();
    if (!permitted) {
      this.status = '请允许麦克风权限';
      this.statusColor = '#D64B43';
      return false;
    }

    return new Promise<boolean>((resolve) => {
      const audioStreamInfo: audio.AudioStreamInfo = {
        samplingRate: this.sampleRate,
        channels: audio.AudioChannel.CHANNEL_1,
        sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
        encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW
      };

      const audioCapturerInfo: audio.AudioCapturerInfo = {
        source: audio.SourceType.SOURCE_TYPE_MIC,
        capturerFlags: 0
      };

      const audioCapturerOptions: audio.AudioCapturerOptions = {
        streamInfo: audioStreamInfo,
        capturerInfo: audioCapturerInfo
      };

      audio.createAudioCapturer(audioCapturerOptions, (err, data) => {
        if (err) {
          this.status = '麦克风初始化失败';
          this.statusColor = '#D64B43';
          resolve(false);
          return;
        }

        this.mic = data;
        this.mic.on('readData', this.micCallback);
        resolve(true);
      });
    });
  }

  private async ensureMicrophonePermission(): Promise<boolean> {
    const permissions: Permissions[] = ['ohos.permission.MICROPHONE'];
    const manager = abilityAccessCtrl.createAtManager();
    const bundleInfo = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
    const tokenID = bundleInfo.appInfo.accessTokenId;
    const alreadyGranted = permissions.every((permission: Permissions) =>
    manager.checkAccessTokenSync(tokenID, permission) === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED);

    if (alreadyGranted) {
      return true;
    }

    try {
      const result = await manager.requestPermissionsFromUser(getContext() as common.UIAbilityContext, permissions);
      return result.authResults.length > 0 && result.authResults.every((value: number) => value === 0);
    } catch (_) {
      return false;
    }
  }

  private handleAsrResult(rawText: string, isEndpoint: boolean, isFinal: boolean): void {
    const text = rawText.trim();
    if (text.length > 0) {
      this.currentPartial = text;
    }

    if (isEndpoint && !isFinal && this.currentPartial.length > 0) {
      this.appendSegment(this.currentPartial, false);
      this.currentPartial = '';
    }

    if (isFinal) {
      if (this.currentPartial.length > 0) {
        this.appendSegment(this.currentPartial, true);
        this.currentPartial = '';
      }
      this.lastRecognizedText = this.composeRecognitionText();
      this.status = this.asrSegments.length > 0 ? '识别完成' : '没有识别到声音';
      this.statusColor = this.asrSegments.length > 0 ? '#35C493' : '#A8B9B2';
    }

    this.publishDraft();

    if (isFinal && this.translationMode && this.lastRecognizedText.length > 0) {
      this.requestTranslation(this.lastRecognizedText);
    }
  }

  private appendSegment(text: string, suppressDuplicate: boolean): void {
    if (text.length === 0) {
      return;
    }
    const last = this.asrSegments.length > 0 ? this.asrSegments[this.asrSegments.length - 1] : '';
    if (suppressDuplicate && last === text) {
      return;
    }
    this.asrSegments.push(text);
  }

  private publishDraft(): void {
    const text = this.composeRecognitionText();
    this.draftShouldPunctuate = true;
    InputBridge.updateDraft(text, true);
  }

  private composeRecognitionText(): string {
    return `${this.asrSegments.join('')}${this.currentPartial}`;
  }

  private resetRecognitionState(): void {
    this.asrSegments = [];
    this.currentPartial = '';
    this.lastRecognizedText = '';
    this.draftShouldPunctuate = true;
    InputBridge.clearDraft();
  }

  private async commitDraft(): Promise<void> {
    const ok = await InputBridge.commitFinalText(this.draft, this.draftShouldPunctuate);
    if (ok) {
      this.resetRecognitionState();
      this.status = '已发送';
      this.statusColor = '#35C493';
    }
  }

  private micCallback = (buffer: ArrayBuffer): void => {
    if (!this.micStarted || this.workerInstance === undefined) {
      return;
    }

    const view = new Int16Array(buffer);
    const samplesFloat = new Float32Array(view.length);
    for (let i = 0; i < view.length; ++i) {
      samplesFloat[i] = view[i] / 32768.0;
    }

    this.workerInstance.postMessage({
      msgType: 'typetype-asr-samples',
      samples: samplesFloat,
      sampleRate: this.sampleRate
    });
  }
}
