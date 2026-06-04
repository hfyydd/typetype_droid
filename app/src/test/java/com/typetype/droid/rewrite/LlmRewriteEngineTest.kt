package com.typetype.droid.rewrite

import com.typetype.droid.settings.Android031Settings
import com.typetype.droid.settings.LlmProviderPresets
import com.typetype.droid.settings.LlmRewriteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRewriteEngineTest {
    private val engine = LlmRewriteEngine()

    @Test
    fun configuredOnlyWhenEnabledAndComplete() {
        assertFalse(engine.isConfigured(Android031Settings()))

        val complete = Android031Settings(
            llmRewrite = LlmRewriteConfig(
                enabled = true,
                apiKey = "test-key",
                baseUrl = "https://example.com/v1",
                model = "test-model",
            ),
        )
        assertTrue(engine.isConfigured(complete))

        assertFalse(engine.isConfigured(complete.copy(llmRewrite = complete.llmRewrite.copy(apiKey = ""))))
        assertFalse(engine.isConfigured(complete.copy(llmRewrite = complete.llmRewrite.copy(model = ""))))
    }

    @Test
    fun unknownProviderFallsBackToOpenAiPreset() {
        val fallback = LlmProviderPresets.presetFor("missing-provider")

        assertEquals("openai", fallback.key)
        assertEquals("https://api.openai.com/v1", fallback.baseUrl)
    }

    @Test
    fun sanitizeRemovesThinkAndKeepsFinalText() {
        val raw = """
            <think>
            这里是模型思考过程，不应该进入输入框。
            </think>
            最终答案：今天下午三点开会，请提前准备材料。
        """.trimIndent()

        assertEquals("今天下午三点开会，请提前准备材料。", sanitizeModelOutput(raw))
    }

    @Test
    fun sanitizeUsesLastFinalMarker() {
        val raw = """
            思考过程：先判断上下文。
            草稿：不要输出这一段。
            润写结果：客户已经确认收货，后续跟进发票。
        """.trimIndent()

        assertEquals("客户已经确认收货，后续跟进发票。", sanitizeModelOutput(raw))
    }
}
