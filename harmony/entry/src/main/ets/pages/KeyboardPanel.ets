import inputMethod from '@ohos.inputMethod';
import { InputMethodListDialog } from '@ohos.inputMethodList';
import promptAction from '@ohos.promptAction';
import { InputBridge } from '../common/InputBridge';

@Entry
@Component
struct KeyboardPanel {
  @State private draft: string = '';
  @State private status: string = '正在听';

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
  }

  aboutToDisappear(): void {
    InputBridge.offDraftChanged(this.draftListener);
  }

  build() {
    Column() {
      Row() {
        Circle()
          .width(8)
          .height(8)
          .fill('#35C493')

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

      Stack() {
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          Text('麦')
            .fontSize(26)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
        }
        .width(86)
        .height(86)
        .backgroundColor('#D64B43')
        .onClick(() => {
          InputBridge.commitFinalText(this.draft);
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
            InputBridge.commitFinalText(this.draft);
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
}
