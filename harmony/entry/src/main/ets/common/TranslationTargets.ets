export interface TranslationTargetOption {
  code: string;
  label: string;
  promptLabel: string;
  punctuateLikeChinese: boolean;
}

export const TRANSLATION_TARGETS: TranslationTargetOption[] = [
  { code: 'en', label: '英语', promptLabel: '英语', punctuateLikeChinese: false },
  { code: 'ja', label: '日语', promptLabel: '日语', punctuateLikeChinese: false },
  { code: 'de', label: '德语', promptLabel: '德语', punctuateLikeChinese: false },
  { code: 'yue', label: '粤语', promptLabel: '粤语', punctuateLikeChinese: true },
  { code: 'zh', label: '中文', promptLabel: '中文', punctuateLikeChinese: true },
  { code: 'zh-Hant', label: '繁体中文', promptLabel: '繁体中文', punctuateLikeChinese: true },
  { code: 'fr', label: '法语', promptLabel: '法语', punctuateLikeChinese: false },
  { code: 'pt', label: '葡萄牙语', promptLabel: '葡萄牙语', punctuateLikeChinese: false },
  { code: 'es', label: '西班牙语', promptLabel: '西班牙语', punctuateLikeChinese: false },
  { code: 'tr', label: '土耳其语', promptLabel: '土耳其语', punctuateLikeChinese: false },
  { code: 'ru', label: '俄语', promptLabel: '俄语', punctuateLikeChinese: false },
  { code: 'ar', label: '阿拉伯语', promptLabel: '阿拉伯语', punctuateLikeChinese: false },
  { code: 'ko', label: '韩语', promptLabel: '韩语', punctuateLikeChinese: false },
  { code: 'th', label: '泰语', promptLabel: '泰语', punctuateLikeChinese: false },
  { code: 'it', label: '意大利语', promptLabel: '意大利语', punctuateLikeChinese: false },
  { code: 'vi', label: '越南语', promptLabel: '越南语', punctuateLikeChinese: false },
  { code: 'ms', label: '马来语', promptLabel: '马来语', punctuateLikeChinese: false },
  { code: 'id', label: '印尼语', promptLabel: '印尼语', punctuateLikeChinese: false },
  { code: 'fil', label: '菲律宾语', promptLabel: '菲律宾语', punctuateLikeChinese: false },
  { code: 'hi', label: '印地语', promptLabel: '印地语', punctuateLikeChinese: false },
  { code: 'pl', label: '波兰语', promptLabel: '波兰语', punctuateLikeChinese: false },
  { code: 'cs', label: '捷克语', promptLabel: '捷克语', punctuateLikeChinese: false },
  { code: 'nl', label: '荷兰语', promptLabel: '荷兰语', punctuateLikeChinese: false },
  { code: 'km', label: '高棉语', promptLabel: '高棉语', punctuateLikeChinese: false },
  { code: 'my', label: '缅甸语', promptLabel: '缅甸语', punctuateLikeChinese: false },
  { code: 'fa', label: '波斯语', promptLabel: '波斯语', punctuateLikeChinese: false },
  { code: 'gu', label: '古吉拉特语', promptLabel: '古吉拉特语', punctuateLikeChinese: false },
  { code: 'ur', label: '乌尔都语', promptLabel: '乌尔都语', punctuateLikeChinese: false },
  { code: 'te', label: '泰卢固语', promptLabel: '泰卢固语', punctuateLikeChinese: false },
  { code: 'mr', label: '马拉地语', promptLabel: '马拉地语', punctuateLikeChinese: false },
  { code: 'he', label: '希伯来语', promptLabel: '希伯来语', punctuateLikeChinese: false },
  { code: 'bn', label: '孟加拉语', promptLabel: '孟加拉语', punctuateLikeChinese: false },
  { code: 'ta', label: '泰米尔语', promptLabel: '泰米尔语', punctuateLikeChinese: false },
  { code: 'uk', label: '乌克兰语', promptLabel: '乌克兰语', punctuateLikeChinese: false },
  { code: 'bo', label: '藏语', promptLabel: '藏语', punctuateLikeChinese: false },
  { code: 'kk', label: '哈萨克语', promptLabel: '哈萨克语', punctuateLikeChinese: false },
  { code: 'mn', label: '蒙古语', promptLabel: '蒙古语', punctuateLikeChinese: false },
  { code: 'ug', label: '维吾尔语', promptLabel: '维吾尔语', punctuateLikeChinese: false }
];

export function getTranslationTarget(code: string): TranslationTargetOption {
  const found = TRANSLATION_TARGETS.find((target: TranslationTargetOption) => target.code === code);
  return found === undefined ? TRANSLATION_TARGETS[0] : found;
}
