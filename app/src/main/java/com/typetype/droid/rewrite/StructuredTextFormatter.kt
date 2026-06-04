package com.typetype.droid.rewrite

import java.util.Locale

object StructuredTextFormatter {
    private val fillerPattern = Regex("""(?i)\b(um|uh|like|you know)\b|嗯+|呃+|那个|就是说""")
    private val sentenceEndingPattern = Regex("""[。！？!?]$""")
    private val anyEndingPunctuationPattern = Regex("""[。！？!?，,、；;：:]$""")
    private val leadingPunctuationPattern = Regex("""^[。！？!?，,、；;：:]""")
    private val trailingClausePunctuationPattern = Regex("""[，,、；;：:]+$""")
    private val questionEndingPattern = Regex(
        """(吗|嘛|么|呢|什么|为什么|怎么|怎样|咋|如何|哪里|哪儿|哪个|哪些|几|多少|谁|啥|是否|是不是|能不能|可不可以|有没有|要不要|好不好|行不行|对不对|需不需要|会不会)$""",
    )
    private val questionPrefixPattern = Regex("""^(请问|问一下|我想问|想问一下|麻烦问一下)""")
    private val questionCuePattern = Regex("""(是否|是不是|能不能|可不可以|有没有|要不要|好不好|行不行|对不对|需不需要|会不会|为什么|怎么|怎样|哪里|哪儿|哪个|哪些|多少|谁|啥)""")
    private val whitespacePattern = Regex("""\s+""")
    private val uppercaseEnglishTokenPattern = Regex("""\b[A-Z][A-Z0-9]*(?:[-/][A-Z0-9]+)*\b""")

    private val enumerationMarkers = listOf(
        "第一",
        "第二",
        "第三",
        "第四",
        "第五",
        "第六",
        "第七",
        "第八",
        "第九",
        "第十",
    )
    private val riskSeparators = listOf("另外风险是", "风险是", "风险：", "风险:")

    fun prefixStreamingBoundaryPunctuation(previousText: String, nextText: String): String {
        val previous = previousText.trim()
        val next = nextText.trimStart()
        if (previous.isEmpty() || next.isEmpty()) return nextText
        if (anyEndingPunctuationPattern.containsMatchIn(previous) || leadingPunctuationPattern.containsMatchIn(next)) {
            return nextText
        }
        if (looksLikeQuestion(previous)) {
            return "？$nextText"
        }
        return "，$nextText"
    }

    fun punctuateStreamingQuestions(text: String): String {
        return punctuateQuestionClauses(text)
    }

    fun removeAsrArtifacts(text: String): String {
        return text
            .replace(Regex("""(?i)<\s*unk\s*>"""), "")
            .replace(Regex("""(?i)\bunk\b"""), "")
            .replace(Regex("""([。！？!?])\s*[，,、；;：:]+"""), "\$1")
            .replace(Regex("""[，,、；;：:]+\s*([。！？!?])"""), "\$1")
            .replace(Regex("""([。！？!?])\s*([。！？!?])+"""), "\$1")
            .replace(Regex("""[，,、；;：:]{2,}"""), "，")
            .replace(Regex("""[^\S\r\n]+"""), " ")
            .replace(Regex("""[^\S\r\n]*(\r?\n)[^\S\r\n]*"""), "\$1")
            .let(::normalizeEnglishCasing)
            .trim()
            .trim('，', ',', '；', ';', '：', ':', ' ')
    }

    fun ensureFinalPunctuation(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val clausePunctuated = punctuateQuestionClauses(trimmed)
        if (sentenceEndingPattern.containsMatchIn(clausePunctuated)) return clausePunctuated

        val withoutTrailingClausePunctuation = clausePunctuated.replace(trailingClausePunctuationPattern, "")
        return withoutTrailingClausePunctuation + chooseFinalPunctuation(withoutTrailingClausePunctuation)
    }

    fun rewrite(rawText: String): String {
        val cleaned = cleanup(rawText)
        if (cleaned.isEmpty()) return cleaned
        return formatEnumeratedSpeech(cleaned) ?: ensureFinalPunctuation(cleaned)
    }

    private fun cleanup(text: String): String {
        return text
            .replace(fillerPattern, "")
            .replace(whitespacePattern, " ")
            .let(::normalizeEnglishCasing)
            .trim()
    }

    private fun normalizeEnglishCasing(text: String): String {
        return uppercaseEnglishTokenPattern.replace(text) { match ->
            normalizeUppercaseEnglishToken(match.value)
        }.let(::normalizeKnownEnglishPhrases)
    }

    private fun normalizeUppercaseEnglishToken(token: String): String {
        val canonical = canonicalEnglishTokens[token]
        if (canonical != null) return canonical
        if (token.length == 1) return token
        if (preservedUppercaseEnglishTokens.contains(token)) return token
        if (token.any(Char::isDigit) && token.any { it in 'A'..'Z' }) return token
        return token.lowercase(Locale.US)
    }

    private fun normalizeKnownEnglishPhrases(text: String): String {
        return text
            .replace(Regex("""\bML kit\b"""), "ML Kit")
            .replace(Regex("""\bOpenai\b"""), "OpenAI")
    }

    private fun formatEnumeratedSpeech(text: String): String? {
        val hits = enumerationMarkers
            .mapNotNull { marker ->
                val index = text.indexOf(marker)
                if (index >= 0) MarkerHit(marker, index) else null
            }
            .sortedBy { it.index }

        if (hits.size < 2) return null

        val intro = text.substring(0, hits.first().index)
            .trim()
            .trim('，', ',', '。', '；', ';', '：', ':')
        val numberedItems = mutableListOf<String>()
        var riskText: String? = null

        hits.forEachIndexed { index, hit ->
            val nextIndex = hits.getOrNull(index + 1)?.index ?: text.length
            val rawItem = text.substring(hit.index + hit.marker.length, nextIndex).trim()
            val split = splitRisk(rawItem)
            val item = split.item.trim('，', ',', '。', '；', ';', '：', ':', ' ')
            if (item.isNotEmpty()) {
                numberedItems += ensureFinalPunctuation(item)
            }
            if (!split.risk.isNullOrBlank()) {
                riskText = split.risk
            }
        }

        if (numberedItems.size < 2) return null

        return buildString {
            if (intro.isNotEmpty()) {
                append(intro)
                append("：\n")
            }
            numberedItems.forEachIndexed { index, item ->
                append(index + 1)
                append(". ")
                append(item)
                if (index != numberedItems.lastIndex || !riskText.isNullOrBlank()) {
                    append('\n')
                }
            }
            val risk = riskText?.trim('，', ',', '。', '；', ';', '：', ':', ' ')
            if (!risk.isNullOrEmpty()) {
                append("风险：")
                append(ensureFinalPunctuation(risk))
            }
        }.trim()
    }

    private fun splitRisk(text: String): RiskSplit {
        val separator = riskSeparators
            .mapNotNull { separator ->
                val index = text.indexOf(separator)
                if (index >= 0) separator to index else null
            }
            .minByOrNull { it.second }
            ?: return RiskSplit(item = text, risk = null)

        val item = text.substring(0, separator.second)
        val risk = text.substring(separator.second + separator.first.length)
        return RiskSplit(item = item, risk = risk)
    }

    private fun chooseFinalPunctuation(text: String): String {
        if (looksLikeQuestion(lastClause(text))) {
            return "？"
        }
        return "。"
    }

    private fun punctuateQuestionClauses(text: String): String {
        val output = StringBuilder()
        val segment = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char in weakClauseDelimiters) {
                val currentSegment = segment.toString()
                segment.clear()
                if (currentSegment.isNotBlank() &&
                    !sentenceEndingPattern.containsMatchIn(currentSegment.trim()) &&
                    looksLikeQuestion(currentSegment)
                ) {
                    output.append(currentSegment.trimEnd())
                    output.append("？")
                    if (char in lineBreakDelimiters) {
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

    private fun looksLikeQuestion(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (questionPrefixPattern.containsMatchIn(trimmed) || questionEndingPattern.containsMatchIn(trimmed)) {
            return true
        }
        return questionCuePattern.findAll(trimmed).any { match ->
            !hasIndirectPrefixBefore(trimmed, match.range.first)
        }
    }

    private fun hasIndirectPrefixBefore(text: String, cueIndex: Int): Boolean {
        return indirectQuestionPrefixes.any { prefix ->
            val prefixIndex = text.indexOf(prefix)
            prefixIndex >= 0 && prefixIndex < cueIndex
        }
    }

    private fun lastClause(text: String): String {
        val index = text.indexOfLast { it in strongClauseDelimiters || it in weakClauseDelimiters }
        return if (index >= 0) text.substring(index + 1) else text
    }

    private data class MarkerHit(
        val marker: String,
        val index: Int,
    )

    private data class RiskSplit(
        val item: String,
        val risk: String?,
    )

    private val weakClauseDelimiters = setOf('，', ',', '；', ';', '\n', '\r')
    private val lineBreakDelimiters = setOf('\n', '\r')
    private val strongClauseDelimiters = setOf('。', '！', '？', '!', '?')
    private val indirectQuestionPrefixes = listOf(
        "不知道",
        "不清楚",
        "不确定",
        "不確定",
        "未确定",
        "未確定",
        "没确定",
        "沒確定",
    )
    private val preservedUppercaseEnglishTokens = setOf(
        "AI",
        "API",
        "APK",
        "ASR",
        "CPU",
        "DNS",
        "GPU",
        "GPT",
        "HTTP",
        "HTTPS",
        "HY-MT",
        "HY-MT2",
        "IP",
        "JSON",
        "LLM",
        "ML",
        "NLLB",
        "OCR",
        "OK",
        "PCS",
        "PDF",
        "SDK",
        "TLS",
        "UI",
        "URL",
        "USB",
        "VPN",
    )
    private val canonicalEnglishTokens = mapOf(
        "ANDROID" to "Android",
        "OPENAI" to "OpenAI",
        "IPHONE" to "iPhone",
        "IOS" to "iOS",
        "TYPE" to "type",
        "TYPETYPE" to "TypeType",
    )
}
