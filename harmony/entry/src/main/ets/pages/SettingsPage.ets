import inputMethod from '@ohos.inputMethod';
import { InputMethodListDialog } from '@ohos.inputMethodList';
import promptAction from '@ohos.promptAction';

@Entry
@Component
struct SettingsPage {
  @State private imeState: string = '未检测';

  private inputMethodListController: CustomDialogController = new CustomDialogController({
    builder: InputMethodListDialog(),
    autoCancel: true,
    alignment: DialogAlignment.Bottom
  });

  build() {
    Column() {
      Text('TypeType')
        .fontSize(32)
        .fontWeight(FontWeight.Bold)
        .fontColor('#15211E')
        .width('100%')
        .padding({ top: 36, left: 24, right: 24 })

      Column() {
        Row() {
          Text('输入法状态')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor('#15211E')
          Blank()
          Text(this.imeState)
            .fontSize(16)
            .fontColor('#5E6A66')
        }
        .width('100%')

        Button('打开输入法切换')
          .width('100%')
          .height(48)
          .margin({ top: 18 })
          .backgroundColor('#17201D')
          .fontColor(Color.White)
          .borderRadius(14)
          .onClick(() => {
            this.showInputMethodSwitcher();
          })

        Button('刷新状态')
          .width('100%')
          .height(48)
          .margin({ top: 12 })
          .backgroundColor('#E9F6F1')
          .fontColor('#173B33')
          .borderRadius(14)
          .onClick(() => {
            this.refreshState();
          })
      }
      .width('100%')
      .padding(20)
      .margin({ left: 18, right: 18, top: 24 })
      .backgroundColor(Color.White)
      .borderRadius(16)

      Blank()
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F4F8F6')
    .onAppear(() => {
      this.refreshState();
    })
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

  private async refreshState(): Promise<void> {
    try {
      const state = await inputMethod.getSetting().getInputMethodState();
      this.imeState = JSON.stringify(state);
    } catch (error) {
      this.imeState = '未启用';
    }
  }
}
