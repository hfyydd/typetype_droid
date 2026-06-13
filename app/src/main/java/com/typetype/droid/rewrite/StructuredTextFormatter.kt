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
    private val chineseNumberChars = "零〇○OＯ一二两三四五六七八九十百千万亿幺壹贰叁肆伍陆柒捌玖拾佰仟萬億"
    private val chineseDigitChars = "零〇○OＯ一二两三四五六七八九幺壹贰叁肆伍陆柒捌玖"
    private val weekdayPattern = Regex("""(?:周|星期|礼拜)[一二三四五六日天]""")
    private val enumerationMarkerPattern = Regex("""第(?:一|二|三|四|五|六|七|八|九|十|[1-9]|10)""")
    private val questionPhrasePattern = Regex("""(能不能|可不可以|有没有|要不要|好不好|行不行|对不对|需不需要|会不会|找没找着|带没带)[\p{IsHan}A-Za-z0-9]{0,8}$""")
    private val incompleteStablePausePattern = Regex("""(我感觉|我觉得|应该|因为|如果|比如|就是|然后|另外|但是|不过|所以|接下来|下一个|要|需要|可以|通过|先|再|把|让)$""")
    private val completeClauseEndPattern = Regex("""(了|着|过|完|好|对|是|可以|完成|结束|下了|没问题|差不多)$""")
    private val percentValueChars = "${chineseNumberChars}点。"
    private val percentContextPattern = Regex("""(?i)(占比|比例|比率|百分比|增长率|完成率|准确率|正确率|错误率|通过率|合格率|达标率|转化率|留存率|覆盖率|达成率|利用率|出勤率|满意度|ROI|同比|环比|利润率)([是为达到达到了约大概左右\s:：]*)(\d{1,3}(?:\.\d+)?)(?![%‰‱\d.年月日号点分元块人个])""")

    private val riskSeparators = listOf("另外风险是", "风险是", "风险：", "风险:")
    private val softBoundaryWords = listOf("然后", "同时", "而且", "并且", "还有", "比如", "就是", "那就是")
    private val strongBoundaryWords = listOf("另外", "但是", "不过", "所以", "因此", "接下来", "下一个", "也就是说", "换句话说", "最后")
    private val sentenceBoundaryWords = setOf("另外", "接下来", "下一个", "也就是说", "换句话说", "最后")
    private val allBoundaryWords = (strongBoundaryWords + softBoundaryWords).distinct().sortedByDescending { it.length }

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
        return normalizeNumbers(punctuateQuestionClauses(text), streamingPartial = true)
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

    fun normalizeNumbers(text: String, streamingPartial: Boolean = false): String {
        if (text.isBlank()) return text
        val protected = protectNumberSensitiveTerms(text)
        var working = protected.text
        working = normalizeVersionNumbers(working)
        working = normalizePhoneNumbers(working, streamingPartial)
        working = normalizeIdentifierNumbers(working, streamingPartial)
        working = normalizeDates(working)
        working = normalizePercentages(working)
        working = normalizeMoney(working)
        working = normalizeTimes(working)
        working = normalizeCountNumbers(working)
        return restoreProtectedTerms(working, protected.tokens)
    }

    fun applyExplicitPunctuationCommands(text: String): String {
        return text
            .replace(Regex("""(?:加)?逗号"""), "，")
            .replace(Regex("""(?:加)?句号"""), "。")
            .replace(Regex("""(?:加)?问号"""), "？")
            .replace(Regex("""(?:加)?感叹号"""), "！")
    }

    fun applyStableStreamingPunctuation(
        text: String,
        final: Boolean,
        stablePause: Boolean,
    ): String {
        var result = applyExplicitPunctuationCommands(text)
            .replace(Regex("""\s+([，。！？；：、,.!?;:])"""), "\$1")
            .replace(Regex("""([（【《])\s+"""), "\$1")
            .replace(Regex("""\s+([）】》])"""), "\$1")
            .trim()

        result = punctuateQuestionClauses(result)
        result = insertQuestionBoundaryPunctuation(result)
        result = insertSemanticBoundaryPunctuation(result, final)
        result = insertDiscourseMarkerComma(result)

        if ((questionEndingPattern.containsMatchIn(result) || questionPhrasePattern.containsMatchIn(result)) &&
            !Regex("""[？?]$""").containsMatchIn(result)
        ) {
            return result.replace(trailingClausePunctuationPattern, "") + "？"
        }
        if (final && result.isNotBlank() && !sentenceEndingPattern.containsMatchIn(result)) {
            return result.replace(trailingClausePunctuationPattern, "") + "。"
        }
        if (stablePause &&
            result.isNotBlank() &&
            !anyEndingPunctuationPattern.containsMatchIn(result) &&
            !incompleteStablePausePattern.containsMatchIn(result)
        ) {
            val lastClause = lastStableClause(result)
            val lastClauseLength = lastClause.length
            if (lastClauseLength >= 8) {
                return "$result，"
            }
        }
        return result
    }

    fun ensureFinalPunctuation(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val clausePunctuated = punctuateQuestionClauses(trimmed)
        if (sentenceEndingPattern.containsMatchIn(clausePunctuated)) return normalizeNumbers(clausePunctuated)

        val withoutTrailingClausePunctuation = clausePunctuated.replace(trailingClausePunctuationPattern, "")
        return normalizeNumbers(withoutTrailingClausePunctuation + chooseFinalPunctuation(withoutTrailingClausePunctuation))
    }

    fun rewrite(rawText: String): String {
        val cleaned = cleanup(rawText)
        if (cleaned.isEmpty()) return cleaned
        return normalizeNumbers(formatEnumeratedSpeech(cleaned) ?: ensureFinalPunctuation(cleaned))
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

    private fun protectNumberSensitiveTerms(text: String): ProtectedText {
        val values = buildList {
            addAll(numberIdiomsToPreserve)
            addAll(weekdayPattern.findAll(text).map { it.value })
        }.distinct().sortedByDescending { it.length }
        var working = text
        val tokens = mutableListOf<ProtectedToken>()
        values.forEach { value ->
            if (!working.contains(value)) return@forEach
            val token = "\uE000TN${tokens.size}\uE001"
            working = working.replace(value, token)
            tokens += ProtectedToken(token, value)
        }
        return ProtectedText(working, tokens)
    }

    private fun restoreProtectedTerms(text: String, tokens: List<ProtectedToken>): String {
        var restored = text
        tokens.forEach { token ->
            restored = restored.replace(token.token, token.value)
        }
        return restored
    }

    private fun normalizePhoneNumbers(text: String, streamingPartial: Boolean): String {
        val context = "(手机号|手机号码|电话号码|联系电话|客服电话|客服热线|服务热线|热线电话|座机号码|座机|电话|分机号|分机)"
        val pattern = Regex("""$context([是为叫:]|：)?\s*([$chineseDigitChars]{2,}(?:[\s-]*[$chineseDigitChars])*)""")
        return pattern.replace(text) { match ->
            val label = match.groupValues[1]
            val joiner = match.groupValues[2]
            val digits = chineseDigitsToArabic(match.groupValues[3]) ?: return@replace match.value
            val isExtension = label.contains("分机")
            val minDigits = if (streamingPartial) 7 else 5
            if (!isExtension && digits.length < minDigits) match.value else "$label$joiner$digits"
        }
    }

    private fun normalizeIdentifierNumbers(text: String, streamingPartial: Boolean): String {
        val context = "(订单号|订单编号|编号|单号|工号|验证码|取件码|房间号|门牌号|卡号|账号|帐号|单据号)"
        val pattern = Regex("""$context([是为叫:]|：)?\s*([$chineseDigitChars]{2,}(?:[\s-]*[$chineseDigitChars])*)""")
        return pattern.replace(text) { match ->
            val digits = chineseDigitsToArabic(match.groupValues[3]) ?: return@replace match.value
            if (streamingPartial && digits.length < 4) return@replace match.value
            "${match.groupValues[1]}${match.groupValues[2]}$digits"
        }
    }

    private fun normalizeDates(text: String): String {
        var result = Regex("""([$chineseDigitChars]{2,4})年""").replace(text) { match ->
            val value = chineseDigitsToArabic(match.groupValues[1]) ?: return@replace match.value
            val year = if (value.length == 3 && value.startsWith("0")) "2$value" else value
            "${year}年"
        }
        result = Regex("""([$chineseNumberChars]{1,3})月""").replace(result) { match ->
            val month = parseChineseNumber(match.groupValues[1])
            if (month != null && month in 1..12) "${month}月" else match.value
        }
        result = Regex("""([$chineseNumberChars]{1,3})(日|号)""").replace(result) { match ->
            val day = parseChineseNumber(match.groupValues[1])
            if (day != null && day in 1..31) "$day${match.groupValues[2]}" else match.value
        }
        return result
    }

    private fun normalizeTimes(text: String): String {
        val daypart = "(凌晨|清晨|早上|上午|中午|下午|傍晚|晚上|今晚|明早|明天上午|明天下午|明天晚上)?"
        val timePattern = Regex("""$daypart([$chineseNumberChars]{1,3})点(半|[$chineseNumberChars]{1,3}分|[$chineseNumberChars]{1,3})?""")
        var result = timePattern.replace(text) { match ->
            val prefix = match.groupValues[1]
            val hourText = match.groupValues[2]
            val suffix = match.groupValues[3]
            val hour = parseChineseNumber(hourText)
            if (hour == null || hour !in 0..24) return@replace match.value
            if (prefix.isEmpty() && suffix.isEmpty() && (hourText == "一" || hourText == "二" || hourText == "两")) {
                return@replace match.value
            }
            if (suffix.isEmpty() || suffix == "半") {
                return@replace "$prefix${hour}点$suffix"
            }
            if (suffix.endsWith("分")) {
                val minute = parseChineseNumber(suffix.dropLast(1))
                return@replace if (minute != null && minute in 0..59) "$prefix${hour}点${minute}分" else match.value
            }
            val minute = parseChineseNumber(suffix)
            if (minute != null && minute in 0..59) "$prefix${hour}点$minute" else match.value
        }
        result = Regex("""([$chineseNumberChars]{1,3})分(钟)?""").replace(result) { match ->
            val minute = parseChineseNumber(match.groupValues[1])
            if (minute != null && minute in 0..59) "${minute}分${match.groupValues[2]}" else match.value
        }
        return result
    }

    private fun normalizePercentages(text: String): String {
        var result = normalizeExplicitPercentMarkers(text)
        result = normalizeContextualPercentNumbers(result)
        result = normalizeStandalonePercentList(result)
        result = normalizeStandalonePercentDecimal(result)
        result = normalizePercentListSeparators(result)
        result = Regex("""([%‰‱])(?=\d)""").replace(result, "$1、")
        return result
    }

    private fun normalizeExplicitPercentMarkers(text: String): String {
        val output = StringBuilder()
        var index = 0
        while (index < text.length) {
            val marker = percentMarkers.firstOrNull { text.startsWith(it.marker, index) }
            if (marker == null) {
                output.append(text[index])
                index += 1
                continue
            }

            val valueStart = skipSpaces(text, index + marker.marker.length)
            val parsed = parsePercentValueAt(text, valueStart)
            if (parsed == null) {
                output.append(marker.marker)
                index += marker.marker.length
                continue
            }

            output.append(parsed.value).append(marker.suffix)
            index = parsed.end
        }
        return output.toString()
    }

    private fun normalizeContextualPercentNumbers(text: String): String {
        var result = percentContextPattern.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}%"
        }
        result = Regex("""([%‰‱])([。.]?\s*)(\d{1,3}(?:\.\d+)?)(?![%‰‱\d.年月日号点分元块人个])""")
            .replace(result) { match ->
                val separator = if (match.groupValues[2].contains(Regex("""[。.]"""))) "、" else match.groupValues[2]
                "${match.groupValues[1]}$separator${match.groupValues[3]}${match.groupValues[1]}"
            }
        return result
    }

    private fun normalizePercentListSeparators(text: String): String {
        return Regex("""([%‰‱])[。.]\s*(?=\d{1,3}(?:\.\d{1,2})?[%‰‱])""").replace(text, "$1、")
    }

    private fun normalizeStandalonePercentList(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || Regex("""[%‰‱百分千万]""").containsMatchIn(trimmed)) return text
        if (!Regex("""^[\d零〇○OＯ一二两三四五六七八九十百幺壹贰叁肆伍陆柒捌玖\s。.,，、]+$""").matches(trimmed)) return text

        val tokens = trimmed
            .split(Regex("""[\s。.,，、]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.size < 3) return text

        val values = tokens.map { parseStandalonePercentListValue(it) }
        if (values.any { it == null }) return text
        val numericValues = values.filterNotNull()
        if (numericValues.none { it >= 10.0 }) return text

        val finalPunctuation = if (trimmed.endsWith("。")) "。" else ""
        val normalized = numericValues.joinToString("、") { "${formatPercentNumber(it)}%" } + finalPunctuation
        return text.replace(trimmed, normalized)
    }

    private fun normalizeStandalonePercentDecimal(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || Regex("""[%‰‱百分千万]""").containsMatchIn(trimmed)) return text
        val finalPunctuation = if (trimmed.endsWith("。")) "。" else ""

        Regex("""^(\d{1,3})。(\d{1,2})[。.]?$""").matchEntire(trimmed)?.let { match ->
            val integer = match.groupValues[1].toIntOrNull()
            if (integer != null && integer in 0..100) {
                return text.replace(trimmed, "${integer}.${match.groupValues[2]}%$finalPunctuation")
            }
            return text
        }

        Regex("""^([$chineseNumberChars]+)。([$chineseDigitChars]{1,2})[。.]?$""").matchEntire(trimmed)?.let { match ->
            val value = parseChineseNumberLikePercent("${match.groupValues[1]}点${match.groupValues[2]}") ?: return text
            val numericValue = value.toDoubleOrNull() ?: return text
            return if (numericValue in 0.0..100.0) text.replace(trimmed, "$value%$finalPunctuation") else text
        }
        return text
    }

    private fun parseStandalonePercentListValue(token: String): Double? {
        if (Regex("""^\d{1,3}(?:\.\d{1,2})?$""").matches(token)) {
            val value = token.toDoubleOrNull() ?: return null
            return value.takeIf { it in 0.0..100.0 }
        }
        if (!Regex("""^[$chineseNumberChars]+$""").matches(token)) return null
        val value = parseChineseNumber(token)?.toDouble() ?: return null
        return value.takeIf { it in 0.0..100.0 }
    }

    private fun parsePercentValueAt(text: String, start: Int): PercentParseResult? {
        val arabic = Regex("""^(\d+)(?:[.。点](\d{1,2}))?""").find(text.substring(start))
        if (arabic != null) {
            val value = if (arabic.groupValues[2].isEmpty()) arabic.groupValues[1] else "${arabic.groupValues[1]}.${arabic.groupValues[2]}"
            return PercentParseResult(value, start + arabic.value.length)
        }

        val valueText = StringBuilder()
        var index = start
        var decimalSeen = false
        while (index < text.length) {
            if (percentMarkers.any { text.startsWith(it.marker, index) }) break
            val char = text[index]
            if (char == '点' || char == '。') {
                val nextChar = text.getOrNull(index + 1)
                if (decimalSeen || nextChar == null || chineseDigitValue(nextChar) == null) break
                valueText.append('点')
                decimalSeen = true
                index += 1
                continue
            }
            if (!percentValueChars.contains(char)) break
            valueText.append(char)
            index += 1
        }

        val value = parseChineseNumberLikePercent(valueText.toString()) ?: return null
        return PercentParseResult(value, index)
    }

    private fun parseChineseNumberLikePercent(value: String): String? {
        if (value.isBlank()) return null
        val parts = value.split("点")
        if (parts.size > 2) return null
        val integer = parseChineseNumber(parts.first().ifEmpty { "零" }) ?: return null
        if (parts.size == 1) return integer.toString()
        val decimals = parts[1].map { chineseDigitValue(it) ?: return null }
        if (decimals.isEmpty()) return null
        return "$integer${"."}${decimals.joinToString("")}"
    }

    private fun skipSpaces(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index += 1
        return index
    }

    private fun formatPercentNumber(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')
    }

    private fun normalizeMoney(text: String): String {
        return Regex("""([$chineseNumberChars]{2,10})(元|块钱|块|人民币|美元|万元|千元)""").replace(text) { match ->
            val value = parseChineseNumber(match.groupValues[1])
            if (value != null && value > 0) "$value${match.groupValues[2]}" else match.value
        }
    }

    private fun normalizeVersionNumbers(text: String): String {
        val pattern = Regex("""(版本号?|version|v|V)([是为:]|：)?\s*([$chineseNumberChars]+(?:点[$chineseNumberChars]+){1,4})""")
        return pattern.replace(text) { match ->
            val parts = match.groupValues[3].split("点").map { parseChineseNumber(it) }
            if (parts.any { it == null }) return@replace match.value
            "${match.groupValues[1]}${match.groupValues[2]}${parts.joinToString(".")}"
        }
    }

    private fun normalizeCountNumbers(text: String): String {
        val countUnitClass = countUnitChars.joinToString("")
        return Regex("""([$chineseNumberChars]{1,8})([$countUnitClass])""").replace(text) { match ->
            val value = parseChineseNumber(match.groupValues[1])
            if (value != null) "$value${match.groupValues[2]}" else match.value
        }
    }

    private fun chineseDigitsToArabic(value: String): String? {
        val digits = value.replace(Regex("""[\s-]"""), "").map { char ->
            chineseDigitValue(char)?.toString() ?: return null
        }
        return digits.joinToString("")
    }

    private fun parseChineseNumber(text: String): Long? {
        if (text.isBlank()) return null
        if (text.none { it in "十百千万亿拾佰仟萬億" }) {
            return text.mapNotNull(::chineseDigitValue)
                .takeIf { it.size == text.length }
                ?.joinToString("")
                ?.toLongOrNull()
        }

        var result = 0L
        var section = 0L
        var number = 0L
        text.forEach { char ->
            val digit = chineseDigitValue(char)
            if (digit != null) {
                number = digit.toLong()
                return@forEach
            }
            val unit = chineseUnitValue(char) ?: return null
            if (unit < 10_000) {
                val n = if (number == 0L) 1L else number
                section += n * unit
            } else {
                section += number
                result += section * unit
                section = 0L
            }
            number = 0L
        }
        return result + section + number
    }

    private fun chineseDigitValue(char: Char): Int? {
        return when (char) {
            '零', '〇', '○', 'O', 'Ｏ' -> 0
            '一', '幺', '壹' -> 1
            '二', '两', '贰' -> 2
            '三', '叁' -> 3
            '四', '肆' -> 4
            '五', '伍' -> 5
            '六', '陆' -> 6
            '七', '柒' -> 7
            '八', '捌' -> 8
            '九', '玖' -> 9
            else -> null
        }
    }

    private fun chineseUnitValue(char: Char): Long? {
        return when (char) {
            '十', '拾' -> 10L
            '百', '佰' -> 100L
            '千', '仟' -> 1_000L
            '万', '萬' -> 10_000L
            '亿', '億' -> 100_000_000L
            else -> null
        }
    }

    private fun formatEnumeratedSpeech(text: String): String? {
        val hits = enumerationMarkerPattern.findAll(text)
            .map { match -> MarkerHit(match.value, match.range.first) }
            .toList()

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

    private fun insertQuestionBoundaryPunctuation(text: String): String {
        return text
            .replace(
                Regex("""([了着过完好对是])((?:你|我|他|她|它|我们|他们)?(?:带没带|找没找着|有没有|能不能|可不可以|要不要|是不是|会不会|行不行))"""),
                "\$1。\$2",
            )
            .replace(
                Regex("""((?:你|我|他|她|它|我们|他们)?(?:带没带|找没找着|有没有|能不能|可不可以|要不要|是不是|会不会|行不行)[^，。！？!?]{0,8}(?:啊|呀|呢|吗)?)(?=(?:耳机|手机|钥匙|文件|东西|你|我|他|她|它|我们|他们|今天|明天|后天|现在|然后|另外|但是|不过|所以|接下来|下一个))"""),
                "\$1？",
            )
    }

    private fun insertSemanticBoundaryPunctuation(text: String, final: Boolean): String {
        if (text.isBlank() || allBoundaryWords.isEmpty()) return text
        val pattern = Regex("""([^。！？!?，,、；;：:\s])(${allBoundaryWords.joinToString("|") { Regex.escape(it) }})""")
        return pattern.replace(text) { match ->
            val previousChar = match.groupValues[1]
            val word = match.groupValues[2]
            if (word == "就是" && previousChar == "那") {
                return@replace previousChar + word
            }
            val previousClause = lastStableClause(text.substring(0, match.range.first + previousChar.length))
            val isStrongBoundary = strongBoundaryWords.contains(word)
            val isSentenceBoundary = sentenceBoundaryWords.contains(word)
            val punctuation = if (isStrongBoundary && isSentenceBoundary && (final || previousClause.length >= 16)) {
                "。"
            } else {
                "，"
            }
            previousChar + punctuation + word
        }
    }

    private fun insertDiscourseMarkerComma(text: String): String {
        return text.replace(Regex("""(也就是说|换句话说|比如)([^，,。！？!?；;：:\s])"""), "\$1，\$2")
    }

    private fun lastStableClause(text: String): String {
        return text.split(Regex("""[，,。！？!?；;：:\n]""")).lastOrNull()?.trim() ?: text.trim()
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

    private data class ProtectedText(
        val text: String,
        val tokens: List<ProtectedToken>,
    )

    private data class ProtectedToken(
        val token: String,
        val value: String,
    )

    private data class PercentMarker(
        val marker: String,
        val suffix: String,
    )

    private data class PercentParseResult(
        val value: String,
        val end: Int,
    )

    private val weakClauseDelimiters = setOf('，', ',', '；', ';', '\n', '\r')
    private val lineBreakDelimiters = setOf('\n', '\r')
    private val strongClauseDelimiters = setOf('。', '！', '？', '!', '?')
    private val countUnitChars = setOf(
        '个', '件', '次', '台', '条', '位', '名', '人', '只', '张', '份', '本',
        '天', '年', '月', '日', '号', '分', '秒',
        '块', '元', '角', '毛', '岁',
        '米', '斤', '克', '吨', '升',
        '楼', '层', '页', '行',
    )
    private val numberIdiomsToPreserve = listOf(
        "一心一意",
        "三三两两",
        "三心二意",
        "不三不四",
        "五花八门",
        "七上八下",
        "乱七八糟",
        "一五一十",
        "十全十美",
        "一干二净",
        "一清二楚",
        "一模一样",
        "一来二去",
    )
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
    private val percentMarkers = listOf(
        PercentMarker("百分之", "%"),
        PercentMarker("千分之", "‰"),
        PercentMarker("万分之", "‱"),
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
        "ROI",
        "SDK",
        "SEO",
        "SLA",
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
