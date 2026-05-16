package com.typetype.droid.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingDiffWriterTest {
    @Test
    fun appendsNewSuffix() {
        val writer = StreamingDiffWriter()

        assertEquals(StreamingEdit(0, "今天天气", ""), writer.nextEdit("今天天气"))
        assertEquals(StreamingEdit(0, "不错", ""), writer.nextEdit("今天天气不错"))
    }

    @Test
    fun rewritesRecentTail() {
        val writer = StreamingDiffWriter()

        writer.nextEdit("语音输入法")

        assertEquals(
            StreamingEdit(deleteChars = 1, insertText = "", expectedDeletedText = "法"),
            writer.nextEdit("语音输入"),
        )
    }

    @Test
    fun commitControllerUpdatesFieldWithStreamingDiffs() {
        val connection = FakeEditableInputConnection()
        val controller = InputCommitController()
        controller.attach(connection)

        assertTrue(controller.writeStreaming("今天天气"))
        assertEquals("今天天气", connection.text)

        assertTrue(controller.writeStreaming("今天天气不错"))
        assertEquals("今天天气不错", connection.text)

        assertTrue(controller.writeStreaming("今天天气很好"))
        assertEquals("今天天气很好", connection.text)
    }

    @Test
    fun commitControllerCanDeleteEntireStreamingTail() {
        val connection = FakeEditableInputConnection()
        val controller = InputCommitController()
        controller.attach(connection)

        assertTrue(controller.writeStreaming("错误"))
        assertEquals("错误", connection.text)

        assertTrue(controller.writeStreaming(""))
        assertEquals("", connection.text)
    }

    @Test
    fun resetSessionKeepsCommittedTextAndStartsNextSegmentAsAppend() {
        val connection = FakeEditableInputConnection()
        val controller = InputCommitController()
        controller.attach(connection)

        assertTrue(controller.writeStreaming("今天天气"))
        controller.resetSession()
        assertTrue(controller.hasWrittenOutput())
        assertTrue(controller.writeStreaming("不错"))

        assertEquals("今天天气不错", connection.text)
    }

    @Test
    fun streamingDoesNotRestoreTextAfterHostClearsField() {
        val connection = FakeEditableInputConnection()
        val controller = InputCommitController()
        controller.attach(connection)

        assertTrue(controller.writeStreaming("今天天气"))
        connection.clear()

        assertEquals(false, controller.writeStreaming("今天天气不错"))
        assertEquals("", connection.text)

        controller.resetSession()
        assertTrue(controller.writeStreaming("下一句"))
        assertEquals("下一句", connection.text)
    }

    @Test
    fun resetAfterExternalCommitStartsCleanNextDictationSegment() {
        val connection = FakeEditableInputConnection()
        val controller = InputCommitController()
        controller.attach(connection)

        assertTrue(controller.writeStreaming("今天天气"))
        connection.clear()
        controller.resetAfterExternalCommit()

        assertEquals(false, controller.hasWrittenOutput())
        assertTrue(controller.writeStreaming("下一句"))
        assertEquals("下一句", connection.text)
    }

    @Test
    fun replaceStreamingTextRewritesOwnedSessionText() {
        val connection = FakeEditableInputConnection()
        val controller = InputCommitController()
        controller.attach(connection)

        assertTrue(controller.writeStreaming("今天有两件事"))
        controller.finishStreamingSegment()
        assertTrue(controller.writeStreaming("第一测试流式第二测试粤语"))

        assertTrue(controller.replaceStreamingText("今天有两件事：\n1. 测试流式。\n2. 测试粤语。"))
        assertEquals("今天有两件事：\n1. 测试流式。\n2. 测试粤语。", connection.text)
    }
}
