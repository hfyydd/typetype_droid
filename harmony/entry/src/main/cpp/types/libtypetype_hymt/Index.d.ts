export const copyRawFileToSandbox: (
  mgr: object,
  rawPath: string,
  destPath: string,
  expectedSize: number
) => boolean;

export const loadModel: (modelPath: string) => boolean;
export const translate: (prompt: string, predictLength: number) => string;
export const systemInfo: () => string;
export const release: () => boolean;

declare const typetypeHymt: {
  copyRawFileToSandbox: (
    mgr: object,
    rawPath: string,
    destPath: string,
    expectedSize: number
  ) => boolean;
  loadModel: (modelPath: string) => boolean;
  translate: (prompt: string, predictLength: number) => string;
  systemInfo: () => string;
  release: () => boolean;
};

export default typetypeHymt;
