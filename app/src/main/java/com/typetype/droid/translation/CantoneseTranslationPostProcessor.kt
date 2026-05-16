package com.typetype.droid.translation

object CantoneseTranslationPostProcessor {
    fun normalize(
        text: String,
        targetLanguage: TranslationTargetLanguage,
    ): String {
        val normalized = text.trim()
        if (normalized.isEmpty() || targetLanguage != TranslationTargetLanguage.CANTONESE) {
            return normalized
        }
        return addFinalPunctuation(punctuateQuestionClauses(normalized))
    }

    private fun punctuateQuestionClauses(text: String): String {
        val output = StringBuilder()
        val segment = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char in WEAK_CLAUSE_DELIMITERS) {
                val currentSegment = segment.toString()
                segment.clear()
                val shouldUseQuestionMark = currentSegment.isNotBlank() &&
                    !hasTerminalPunctuation(currentSegment) &&
                    looksLikeQuestion(currentSegment)
                if (shouldUseQuestionMark) {
                    appendWithPunctuation(output, currentSegment, "？")
                    if (char in LINE_BREAK_DELIMITERS) {
                        output.append(char)
                    } else {
                        while (index + 1 < text.length && text[index + 1].isWhitespace()) {
                            index += 1
                        }
                    }
                } else {
                    output.append(currentSegment)
                    output.append(char)
                }
            } else {
                segment.append(char)
            }
            index += 1
        }
        output.append(segment)
        return output.toString()
    }

    private fun addFinalPunctuation(text: String): String {
        if (hasTerminalPunctuation(text)) {
            return text
        }

        val insertionIndex = text.indexOfLast { it !in CLOSING_MARKS } + 1
        val core = text.substring(0, insertionIndex)
        val closingMarks = text.substring(insertionIndex)
        val punctuation = if (looksLikeQuestion(lastClause(core))) "？" else "。"
        return core + punctuation + closingMarks
    }

    private fun hasTerminalPunctuation(text: String): Boolean {
        val lastMeaningful = text.lastOrNull { it !in CLOSING_MARKS } ?: return false
        return lastMeaningful in TERMINAL_PUNCTUATION
    }

    private fun appendWithPunctuation(
        output: StringBuilder,
        text: String,
        punctuation: String,
    ) {
        val insertionIndex = text.indexOfLast { it !in CLOSING_MARKS } + 1
        output.append(text.substring(0, insertionIndex))
        output.append(punctuation)
        output.append(text.substring(insertionIndex))
    }

    private fun looksLikeQuestion(text: String): Boolean {
        val trimmed = text.trim()
        return QUESTION_SUFFIXES.any { trimmed.endsWith(it) } ||
            QUESTION_CUES.any { cue -> trimmed.contains(cue) && !containsIndirectCue(trimmed, cue) }
    }

    private fun containsIndirectCue(
        text: String,
        cue: String,
    ): Boolean {
        val cueIndex = text.indexOf(cue)
        if (cueIndex < 0) return false
        return INDIRECT_CUE_PREFIXES.any { prefix ->
            val prefixIndex = text.indexOf(prefix)
            prefixIndex >= 0 && prefixIndex < cueIndex
        }
    }

    private fun lastClause(text: String): String {
        val index = text.indexOfLast { it in STRONG_CLAUSE_DELIMITERS || it in WEAK_CLAUSE_DELIMITERS }
        return if (index >= 0) text.substring(index + 1) else text
    }

    private val TERMINAL_PUNCTUATION = setOf('。', '！', '？', '!', '?', '.', '…', '~', '～')
    private val CLOSING_MARKS = setOf(')', '）', ']', '】', '}', '》', '」', '』', '"', '\'', '”', '’')
    private val WEAK_CLAUSE_DELIMITERS = setOf('，', ',', '；', ';', '\n', '\r')
    private val LINE_BREAK_DELIMITERS = setOf('\n', '\r')
    private val STRONG_CLAUSE_DELIMITERS = setOf('。', '！', '？', '!', '?')

    private val QUESTION_SUFFIXES = listOf(
        "好不好",
        "好唔好",
        "可不可以",
        "可唔可以",
        "行不行",
        "得唔得",
        "是不是",
        "係咪",
        "要不要",
        "要唔要",
        "有没有",
        "有冇",
        "对不对",
        "啱唔啱",
        "可以吗",
        "可以嗎",
        "得吗",
        "得嗎",
        "好吗",
        "好嗎",
        "是吗",
        "是嗎",
        "吗",
        "嗎",
        "咩",
        "呢",
        "未",
    )

    private val QUESTION_CUES = listOf(
        "係咪",
        "是不是",
        "是否",
        "有冇",
        "有没有",
        "可唔可以",
        "可不可以",
        "能不能",
        "要不要",
        "要唔要",
        "点解",
        "點解",
        "几时",
        "幾時",
        "边个",
        "邊個",
        "边度",
        "邊度",
        "乜嘢",
        "咩事",
    )

    private val INDIRECT_CUE_PREFIXES = listOf(
        "不知道",
        "唔知道",
        "唔知",
        "不清楚",
        "不确定",
        "不確定",
        "未确定",
        "未確定",
    )
}
