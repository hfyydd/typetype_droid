import Want from '@ohos.app.ability.Want';
import InputMethodExtensionAbility from '@ohos.InputMethodExtensionAbility';
import inputMethodEngine from '@ohos.inputMethodEngine';
import hilog from '@ohos.hilog';
import { InputBridge } from '../common/InputBridge';

const DOMAIN = 0x54595045;
const TAG = 'TypeTypeImeService';

export default class TypeTypeInputMethodService extends InputMethodExtensionAbility {
  private imeAbility?: inputMethodEngine.InputMethodAbility;
  private keyboardController?: inputMethodEngine.KeyboardController;
  private panel?: inputMethodEngine.Panel;

  onCreate(want: Want): void {
    hilog.info(DOMAIN, TAG, 'onCreate: %{public}s', JSON.stringify(want));
    this.imeAbility = inputMethodEngine.getInputMethodAbility();
    this.registerInputCallbacks();
    this.createKeyboardPanel();
  }

  onDestroy(): void {
    hilog.info(DOMAIN, TAG, 'onDestroy');
    InputBridge.clearClient();
    if (this.imeAbility !== undefined && this.panel !== undefined) {
      this.imeAbility.destroyPanel(this.panel).catch((error: Error) => {
        hilog.warn(DOMAIN, TAG, 'destroyPanel failed: %{public}s', JSON.stringify(error));
      });
    }
    this.panel = undefined;
    this.keyboardController = undefined;
    this.imeAbility = undefined;
  }

  private registerInputCallbacks(): void {
    if (this.imeAbility === undefined) {
      return;
    }

    this.imeAbility.on('inputStart', (kbController: inputMethodEngine.KeyboardController,
      inputClient: inputMethodEngine.InputClient) => {
      this.keyboardController = kbController;
      InputBridge.bindClient(inputClient);
      this.showPanel();
    });

    this.imeAbility.on('inputStop', () => {
      InputBridge.clearClient();
      this.hidePanel();
    });

    this.imeAbility.on('keyboardShow', () => {
      this.showPanel();
    });

    this.imeAbility.on('keyboardHide', () => {
      this.hidePanel();
    });
  }

  private async createKeyboardPanel(): Promise<void> {
    if (this.imeAbility === undefined || this.panel !== undefined) {
      return;
    }

    try {
      this.panel = await this.imeAbility.createPanel(this.context, {
        type: inputMethodEngine.PanelType.SOFT_KEYBOARD,
        flag: inputMethodEngine.PanelFlag.FLG_FIXED
      });
      await this.panel.setUiContent('pages/KeyboardPanel');
      await this.panel.show();
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'create panel failed: %{public}s', JSON.stringify(error));
    }
  }

  private showPanel(): void {
    if (this.panel === undefined) {
      this.createKeyboardPanel();
      return;
    }
    this.panel.show().catch((error: Error) => {
      hilog.warn(DOMAIN, TAG, 'show panel failed: %{public}s', JSON.stringify(error));
    });
  }

  private hidePanel(): void {
    if (this.panel === undefined) {
      return;
    }
    this.panel.hide().catch((error: Error) => {
      hilog.warn(DOMAIN, TAG, 'hide panel failed: %{public}s', JSON.stringify(error));
    });
  }
}
