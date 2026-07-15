const SENTENCE_ENDING = /[。！？!?…]$/;
const QUESTION_ENDING = /(吗|嘛|呢|么|是不是|有没有|能不能|可不可以|可以吗|好不好|行不行|对不对|要不要|会不会|需不需要|行吗|好吗|对吗)$/;
const QUESTION_CLAUSE = /(吗|嘛|是不是|有没有|能不能|可不可以|可以吗|好不好|行不行|对不对|要不要|会不会|需不需要|行吗|好吗|对吗)(?=(然后|再|我|你|他|她|它|我们|你们|他们|这个|那个|现在|今天|明天|后面|前面|一会|待会|先|就|帮|看|做|发|去|来|给|把|需要|可以|已经|继续))/g;

function normalizeQuestionMarks(value: string): string {
  return value.replace(/\?/g, '？').replace(/!/g, '！');
}

function hasSentenceEnding(value: string): boolean {
  return SENTENCE_ENDING.test(value);
}

function isQuestionLike(value: string): boolean {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return false;
  }
  return QUESTION_ENDING.test(trimmed);
}

export function applyFinalPunctuation(rawText: string): string {
  let text = normalizeQuestionMarks(rawText).trim();
  if (text.length === 0) {
    return '';
  }

  text = text.replace(QUESTION_CLAUSE, '$1？');

  if (hasSentenceEnding(text)) {
    return text;
  }

  return `${text}${isQuestionLike(text) ? '？' : '。'}`;
}

export function applyStreamingPunctuation(rawText: string): string {
  const text = normalizeQuestionMarks(rawText).trim();
  if (text.length === 0) {
    return '';
  }
  return text.replace(QUESTION_CLAUSE, '$1？');
}
