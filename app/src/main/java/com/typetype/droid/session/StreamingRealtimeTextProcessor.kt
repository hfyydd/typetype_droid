package com.typetype.droid.session

import com.typetype.droid.rewrite.StructuredTextFormatter

data class StreamingTailCorrection(
    val replacementText: String,
    val charsToReplace: Int,
    val correctedRealtimeText: String,
)

data class StreamingRealtimeProcessResult(
    val rawText: String,
    val realtimeText: String,
    val stableText: String,
    val displayDelta: String,
    val cursorText: String,
    val stablePunctuationCandidate: String,
    val tailCorrection: StreamingTailCorrection?,
    val rawDeltaLength: Int,
    val tailCharsProcessed: Int,
)

class StreamingRealtimeTextProcessor(
    private val tailWindowChars: Int = DEFAULT_TAIL_WINDOW_CHARS,
) {
    private var rawText = ""
    private var realtimeText = ""

    fun reset() {
        rawText = ""
        realtimeText = ""
    }

    fun getRawText(): String = rawText

    fun getRealtimeText(): String = realtimeText

    fun acceptAppliedText(text: String) {
        realtimeText = StructuredTextFormatter.removeAsrArtifacts(text)
    }

    fun processPartial(
        rawCumulativeText: String,
        stablePause: Boolean = false,
        final: Boolean = false,
    ): StreamingRealtimeProcessResult {
        val cleanedRawText = StructuredTextFormatter.removeAsrArtifacts(rawCumulativeText)
        val rawDelta = appendDelta(rawText, cleanedRawText)
        val displayDelta = cleanRealtimeDelta(rawDelta)
        val nextRealtimeText = if (displayDelta.isNotEmpty()) {
            mergeTranscriptText(realtimeText, displayDelta)
        } else {
            realtimeText
        }
        val stableText = processTailWindow(nextRealtimeText, stablePause = stablePause, final = final)
        val tailCorrection = buildTailCorrection(nextRealtimeText, stableText)

        rawText = cleanedRawText
        realtimeText = nextRealtimeText

        return StreamingRealtimeProcessResult(
            rawText = cleanedRawText,
            realtimeText = nextRealtimeText,
            stableText = stableText,
            displayDelta = displayDelta,
            cursorText = nextRealtimeText,
            stablePunctuationCandidate = stableText,
            tailCorrection = tailCorrection,
            rawDeltaLength = rawDelta.length,
            tailCharsProcessed = minOf(nextRealtimeText.length, tailWindowChars),
        )
    }

    fun processStableSegment(
        text: String,
        stablePause: Boolean = false,
        final: Boolean = false,
    ): String {
        val cleaned = StructuredTextFormatter.removeAsrArtifacts(text)
        val normalized = StructuredTextFormatter.normalizeNumbers(
            cleaned,
            streamingPartial = !final,
        )
        return StructuredTextFormatter.applyStableStreamingPunctuation(
            normalized,
            final = final,
            stablePause = stablePause,
        )
    }

    private fun cleanRealtimeDelta(delta: String): String {
        if (delta.isBlank()) return ""
        return StructuredTextFormatter.applyExplicitPunctuationCommands(
            StructuredTextFormatter.removeAsrArtifacts(delta),
        ).trim()
    }

    private fun processTailWindow(text: String, stablePause: Boolean, final: Boolean): String {
        val (prefix, tail) = splitTail(text, tailWindowChars)
        if (tail.isEmpty()) return text
        val stableTail = processStableSegment(tail, stablePause = stablePause, final = final)
        return prefix + stableTail
    }

    private fun buildTailCorrection(
        realtimeText: String,
        stableText: String,
    ): StreamingTailCorrection? {
        if (stableText.isBlank() || stableText == realtimeText) return null
        val commonPrefixLength = commonPrefixLength(realtimeText, stableText)
        val charsToReplace = realtimeText.length - commonPrefixLength
        val replacementText = stableText.substring(commonPrefixLength)
        if (charsToReplace < MIN_TAIL_REPLACE_CHARS ||
            charsToReplace > MAX_TAIL_REPLACE_CHARS ||
            replacementText.isBlank()
        ) {
            return null
        }
        return StreamingTailCorrection(
            replacementText = replacementText,
            charsToReplace = charsToReplace,
            correctedRealtimeText = stableText,
        )
    }

    private fun appendDelta(previous: String, current: String): String {
        if (previous.isEmpty()) return current
        if (current.startsWith(previous)) return current.substring(previous.length)

        var index = 0
        val max = minOf(previous.length, current.length)
        while (index < max && previous[index] == current[index]) {
            index += 1
        }
        if (index >= previous.length - DEFAULT_TAIL_WINDOW_CHARS) {
            return current.substring(previous.length.coerceAtMost(current.length))
        }
        return current
    }

    private fun mergeTranscriptText(previous: String, delta: String): String {
        if (previous.isEmpty()) return delta
        if (delta.isEmpty()) return previous
        val needsSpace = previous.last().isAsciiWordChar() && delta.first().isAsciiWordChar()
        return previous + if (needsSpace) " $delta" else delta
    }

    private fun splitTail(text: String, tailChars: Int): Pair<String, String> {
        if (text.length <= tailChars) return "" to text
        val splitAt = text.length - tailChars
        return text.substring(0, splitAt) to text.substring(splitAt)
    }

    private fun commonPrefixLength(left: String, right: String): Int {
        var index = 0
        val max = minOf(left.length, right.length)
        while (index < max && left[index] == right[index]) {
            index += 1
        }
        return index
    }

    private fun Char.isAsciiWordChar(): Boolean {
        return this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'
    }

    private companion object {
        const val DEFAULT_TAIL_WINDOW_CHARS = 120
        const val MIN_TAIL_REPLACE_CHARS = 4
        const val MAX_TAIL_REPLACE_CHARS = 80
    }
}
