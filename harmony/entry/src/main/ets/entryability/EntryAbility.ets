import UIAbility from '@ohos.app.ability.UIAbility';
import Want from '@ohos.app.ability.Want';
import AbilityConstant from '@ohos.app.ability.AbilityConstant';
import window from '@ohos.window';
import hilog from '@ohos.hilog';

const DOMAIN = 0x54595045;
const TAG = 'TypeTypeEntryAbility';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, TAG, 'onCreate: %{public}s %{public}s', JSON.stringify(want), JSON.stringify(launchParam));
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/SettingsPage', (error) => {
      if (error.code !== 0) {
        hilog.error(DOMAIN, TAG, 'load settings failed: %{public}s', JSON.stringify(error));
      }
    });
  }
}
