package com.typetype.droid.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingRealtimeTextProcessorTest {
    @Test
    fun partialWorkStaysInsideTailWindow() {
        val processor = StreamingRealtimeTextProcessor(tailWindowChars = 120)
        val raw = "前".repeat(180) + "我的手机号是一三八一二三四五六七八"

        val result = processor.processPartial(raw)

        assertTrue(result.displayDelta.contains("我的手机号是一三八"))
        assertTrue(result.stableText.contains("我的手机号是13812345678"))
        assertTrue(result.tailCorrection?.replacementText?.contains("13812345678") == true)
        assertEquals(120, result.tailCharsProcessed)
    }

    @Test
    fun monotonicDisplayDeltasKeepCodeSwitchTextReadable() {
        val processor = StreamingRealtimeTextProcessor()

        assertEquals("今天开", processor.processPartial("今天开").displayDelta)
        assertEquals("meeting", processor.processPartial("今天开 meeting").displayDelta)
        assertEquals("今天开meeting", processor.getRealtimeText())
    }

    @Test
    fun stableStreamingPunctuationUsesSoftBoundaries() {
        val processor = StreamingRealtimeTextProcessor()

        val soft = processor.processStableSegment("阴天了那就是快下了", stablePause = true)
        val final = processor.processStableSegment(
            "下一个重点是讨论一下要怎么让更新通过线上更新来处理也就是说就算有报错也可以通过线上下载",
            final = true,
        )

        assertEquals("阴天了，那就是快下了", soft)
        assertTrue(final.contains("。也就是说，"))
        assertTrue(final.endsWith("。"))
    }

    @Test
    fun casualChainedQuestionsGetQuestionMarks() {
        val processor = StreamingRealtimeTextProcessor()

        val result = processor.processStableSegment(
            "阴天了那就是快下了你带没带伞啊耳机都找没找着",
            final = true,
        )

        assertEquals("阴天了，那就是快下了。你带没带伞啊？耳机都找没找着？", result)
    }

    @Test
    fun shortPartialPhoneNumberWaitsForMoreDigits() {
        val processor = StreamingRealtimeTextProcessor()

        val first = processor.processPartial("我的手机号是一三八")
        val second = processor.processPartial("我的手机号是一三八一二三四五六七八")

        assertEquals("我的手机号是一三八", first.stableText)
        assertEquals("我的手机号是13812345678", second.stableText)
        assertNotNull(second.tailCorrection)
    }

    @Test
    fun percentMarkersAreNormalizedInStreamingTailAndFinalText() {
        val processor = StreamingRealtimeTextProcessor()

        val partial = processor.processPartial("占比百分之七十六。三。百分之百", stablePause = true)
        val final = processor.processStableSegment("占比76。100。", final = true)
        val list = processor.processStableSegment("七十六。3。59.", final = true)
        val decimal = processor.processStableSegment("76。3。", final = true)

        assertTrue(partial.stableText.contains("76.3%"))
        assertTrue(partial.stableText.contains("100%"))
        assertEquals("占比76%、100%。", final)
        assertTrue(list.contains("76%、3%、59%"))
        assertTrue(decimal.contains("76.3%"))
    }
}
