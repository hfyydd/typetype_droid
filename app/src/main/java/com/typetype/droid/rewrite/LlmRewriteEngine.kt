package com.typetype.droid.rewrite

import com.typetype.droid.settings.Android031Settings
import com.typetype.droid.settings.LlmRewriteConfig
import com.typetype.droid.settings.RewriteScenario
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class LlmRewriteEngine {
    fun isConfigured(settings: Android031Settings): Boolean {
        return settings.llmRewrite.enabled &&
            settings.llmRewrite.apiKey.isNotBlank() &&
            settings.llmRewrite.baseUrl.isNotBlank() &&
            settings.llmRewrite.model.isNotBlank()
    }

    fun rewrite(
        rawText: String,
        settings: Android031Settings,
        preserveTerms: List<String> = emptyList(),
    ): String {
        val normalized = rawText.trim()
        if (normalized.isEmpty() || !isConfigured(settings)) {
            return normalized
        }
        val config = settings.llmRewrite
        val systemPrompt = buildSystemPrompt(settings.rewriteScenario, preserveTerms)
        val userPrompt = "请润写以下语音转写内容，只输出润写后的正文：\n\n$normalized"
        return requestChatCompletion(config, systemPrompt, userPrompt).ifBlank { normalized }
    }

    fun testConnection(config: LlmRewriteConfig): LlmTestResult {
        if (config.apiKey.isBlank()) {
            return LlmTestResult(false, "请先填写 API Key")
        }
        return try {
            val started = System.currentTimeMillis()
            val text = requestChatCompletion(
                config = config,
                systemPrompt = "你是 typetype 的连接测试助手。",
                userPrompt = "请只回复：连接成功",
                maxTokensOverride = 16,
            )
            val elapsed = System.currentTimeMillis() - started
            LlmTestResult(text.contains("连接成功") || text.isNotBlank(), "连接成功 (${elapsed}ms)")
        } catch (error: Throwable) {
            LlmTestResult(false, error.message ?: "连接失败")
        }
    }

    private fun buildSystemPrompt(
        scenario: RewriteScenario,
        preserveTerms: List<String>,
    ): String {
        val scenarioText = "${scenario.group} / ${scenario.label}"
        return buildString {
            append("你是 typetype 的中文语音转写润写助手。")
            append("任务场景：").append(scenarioText).append("。")
            append("请按照该文稿类型生成对应格式，补标点、修正常见口误、整理层级，但不要编造信息，不要遗漏数字、姓名、品牌、时间和结论。")
            append("短句只做轻微清理，长段可整理为段落或清单。")
            if (preserveTerms.isNotEmpty()) {
                append("以下术语必须尽量原样保留：")
                append(preserveTerms.take(80).joinToString("、"))
                append("。")
            }
        }
    }

    private fun requestChatCompletion(
        config: LlmRewriteConfig,
        systemPrompt: String,
        userPrompt: String,
        maxTokensOverride: Int? = null,
    ): String {
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", config.temperature)
            .put("max_tokens", maxTokensOverride ?: config.maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
            .toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val responseText = if (status in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val detail = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                throw IOException(apiErrorMessage(status, detail))
            }
            val rawContent = JSONObject(responseText)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?: ""
            return sanitizeModelOutput(rawContent)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiErrorMessage(status: Int, detail: String): String {
        val cleanDetail = detail.take(240)
        return when (status) {
            401 -> "LLM API 认证失败，请检查 API Key、平台/地域和额度权限。$cleanDetail"
            403 -> "LLM API 权限不足，账号可能未开通该模型或额度不足。$cleanDetail"
            404 -> "LLM API 地址或模型不存在，请检查 Base URL 和模型名。$cleanDetail"
            429 -> "LLM API 请求过快或额度不足。$cleanDetail"
            in 400..499 -> "LLM API 请求参数不被接受，请检查模型名和平台。$cleanDetail"
            in 500..599 -> "LLM API 平台服务异常，请稍后重试。$cleanDetail"
            else -> "LLM API 失败 ($status)。$cleanDetail"
        }
    }
}

data class LlmTestResult(
    val ok: Boolean,
    val message: String,
)

internal fun sanitizeModelOutput(raw: String): String {
    var text = raw
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .replace(Regex("(?is)<thinking>.*?</thinking>"), "")
        .replace(Regex("(?is)<analysis>.*?</analysis>"), "")
        .trim()

    val finalMarkers = listOf("最终答案：", "最终答案:", "最终输出：", "最终输出:", "润写结果：", "润写结果:", "正文：", "正文:")
    val marker = finalMarkers
        .mapNotNull { marker -> text.lastIndexOf(marker).takeIf { it >= 0 }?.let { marker to it } }
        .maxByOrNull { it.second }
    if (marker != null) {
        text = text.substring(marker.second + marker.first.length).trim()
    }

    text = text
        .replace(Regex("(?is)^```[a-zA-Z0-9_-]*\\s*"), "")
        .replace(Regex("(?is)```$"), "")
        .lines()
        .filterNot { line ->
            val normalized = line.trim()
            normalized.matches(Regex("(?i)^(思考过程|推理过程|analysis|thinking|think)[:：]?.*"))
        }
        .joinToString("\n")
        .trim()

    return text
        .removePrefix("最终答案：")
        .removePrefix("最终答案:")
        .removePrefix("润写结果：")
        .removePrefix("润写结果:")
        .trim()
}
