package com.typetype.droid.rewrite

import org.junit.Assert.assertEquals
import org.junit.Test

class StructuredTextFormatterTest {
    @Test
    fun shortTextGetsPunctuationWithoutOverStructuring() {
        assertEquals("今天我们测试语音输入。", StructuredTextFormatter.rewrite("嗯今天我们测试语音输入"))
    }

    @Test
    fun enumeratedSpeechBecomesNumberedListWithRisk() {
        val raw = "今天开会主要有三件事第一产品这周要把流式输入修好第二翻译功能粤语要继续测试第三我下周一之前整理使用说明另外风险是开机自启动可能不稳定"

        assertEquals(
            """
            今天开会主要有三件事：
            1. 产品这周要把流式输入修好。
            2. 翻译功能粤语要继续测试。
            3. 我下周一之前整理使用说明。
            风险：开机自启动可能不稳定。
            """.trimIndent(),
            StructuredTextFormatter.rewrite(raw),
        )
    }

    @Test
    fun streamingBoundaryUsesCommaBetweenSegments() {
        assertEquals(
            "，继续测试",
            StructuredTextFormatter.prefixStreamingBoundaryPunctuation("第一段", "继续测试"),
        )
    }

    @Test
    fun questionClauseBeforeStatementGetsInternalQuestionMark() {
        assertEquals(
            "你今天有没有空？明天继续测试。",
            StructuredTextFormatter.rewrite("你今天有没有空，明天继续测试"),
        )
        assertEquals(
            "我们不知道有没有空，明天再说。",
            StructuredTextFormatter.rewrite("我们不知道有没有空，明天再说"),
        )
    }

    @Test
    fun streamingQuestionBoundaryUsesQuestionMark() {
        assertEquals(
            "？明天继续测试",
            StructuredTextFormatter.prefixStreamingBoundaryPunctuation("你今天有没有空", "明天继续测试"),
        )
        assertEquals(
            "你今天有没有空？明天继续测试",
            StructuredTextFormatter.punctuateStreamingQuestions("你今天有没有空，明天继续测试"),
        )
    }

    @Test
    fun asrUnknownArtifactsAreRemovedWithoutDroppingEnglish() {
        assertEquals(
            "有 enough，行了",
            StructuredTextFormatter.removeAsrArtifacts("有<unk> enough，<unk>行了"),
        )
        assertEquals(
            "有，够。行了",
            StructuredTextFormatter.removeAsrArtifacts("有<unk>，够。<unk>，<unk><unk>，行了"),
        )
    }

    @Test
    fun uppercaseAsrEnglishBecomesNaturalCasing() {
        assertEquals(
            "你好呀，小伙子，hello hello hello。",
            StructuredTextFormatter.removeAsrArtifacts("你好呀，小伙子，HELLO HELLO HELLO。"),
        )
        assertEquals(
            "hello，go to sleep。OK 了。",
            StructuredTextFormatter.removeAsrArtifacts("HELLO，GO TO SLEEP。OK 了。"),
        )
    }

    @Test
    fun technicalEnglishAcronymsKeepCanonicalCasing() {
        assertEquals(
            "AI API USB APK OK ML Kit OpenAI iOS Android HY-MT2",
            StructuredTextFormatter.removeAsrArtifacts("AI API USB APK OK ML KIT OPENAI IOS ANDROID HY-MT2"),
        )
    }
}
