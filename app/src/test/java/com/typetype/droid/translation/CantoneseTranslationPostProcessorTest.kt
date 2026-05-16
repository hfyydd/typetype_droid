package com.typetype.droid.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class CantoneseTranslationPostProcessorTest {
    @Test
    fun cantoneseStatementGetsFullStopWhenMissing() {
        assertEquals(
            "今日天气唔错",
            CantoneseTranslationPostProcessor.normalize(
                "今日天气唔错",
                TranslationTargetLanguage.ENGLISH,
            ),
        )
        assertEquals(
            "今日天气唔错。",
            CantoneseTranslationPostProcessor.normalize(
                "今日天气唔错",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
    }

    @Test
    fun cantoneseQuestionParticlesGetQuestionMark() {
        assertEquals(
            "你今日有冇空？",
            CantoneseTranslationPostProcessor.normalize(
                "你今日有冇空",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
        assertEquals(
            "而家开始好唔好？",
            CantoneseTranslationPostProcessor.normalize(
                "而家开始好唔好",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
        assertEquals(
            "你明唔明白吗？",
            CantoneseTranslationPostProcessor.normalize(
                "你明唔明白吗",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
    }

    @Test
    fun existingPunctuationIsPreserved() {
        assertEquals(
            "听日继续测试。",
            CantoneseTranslationPostProcessor.normalize(
                "听日继续测试。",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
        assertEquals(
            "係咪而家开始？",
            CantoneseTranslationPostProcessor.normalize(
                "係咪而家开始？",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
    }

    @Test
    fun questionClauseBeforeStatementGetsInternalQuestionMark() {
        assertEquals(
            "你今日有冇空？听日继续测试。",
            CantoneseTranslationPostProcessor.normalize(
                "你今日有冇空，听日继续测试",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
        assertEquals(
            "你要唔要先保存？我哋再继续。",
            CantoneseTranslationPostProcessor.normalize(
                "你要唔要先保存, 我哋再继续",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
    }

    @Test
    fun indirectQuestionCueKeepsStatementPunctuation() {
        assertEquals(
            "我唔知你有冇空，听日再讲。",
            CantoneseTranslationPostProcessor.normalize(
                "我唔知你有冇空，听日再讲",
                TranslationTargetLanguage.CANTONESE,
            ),
        )
    }
}
