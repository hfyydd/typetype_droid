package com.typetype.droid.settings

enum class StreamingModelPreference(val label: String) {
    MULTILINGUAL_REALTIME("多语言实时"),
    MULTILINGUAL_SEGMENTED("多语言分段"),
    ZH_HIGH_ACCURACY_REALTIME("中文高准确率实时"),
}

enum class VoicePackagePreference(val label: String) {
    FAST_OFFLINE("轻量实时优先"),
    PRO_HIGH_ACCURACY("高准确率优先"),
}

enum class StreamingEnhancementMode(val label: String) {
    OFFLINE_PRIVATE("离线隐私增强"),
    ONLINE_ENHANCED("AI 联网增强"),
}

enum class RewriteBackendPreference(val label: String) {
    LOCAL("离线结构化润写"),
    AI("AI 联网润写"),
}

enum class RewriteScenario(val label: String, val group: String = "常用润写") {
    GENERAL("通用整理"),
    MEETING_NOTES("会议纪要"),
    WORK_REPORT("工作汇报"),
    MESSAGE_REPLY("邮件/微信回复"),
    TODO_LIST("待办清单"),
    STUDY_NOTES("学习笔记"),
    CUSTOMER_SERVICE("客服记录"),
    OFFICIAL_RESOLUTION("决议", "党政机关公文"),
    OFFICIAL_DECISION("决定", "党政机关公文"),
    OFFICIAL_ORDER("命令（令）", "党政机关公文"),
    OFFICIAL_COMMUNIQUE("公报", "党政机关公文"),
    OFFICIAL_ANNOUNCEMENT("公告", "党政机关公文"),
    OFFICIAL_PUBLIC_NOTICE("通告", "党政机关公文"),
    OFFICIAL_OPINION("意见", "党政机关公文"),
    OFFICIAL_NOTICE("通知", "党政机关公文"),
    OFFICIAL_CIRCULAR("通报", "党政机关公文"),
    OFFICIAL_REPORT("报告", "党政机关公文"),
    OFFICIAL_REQUEST("请示", "党政机关公文"),
    OFFICIAL_REPLY("批复", "党政机关公文"),
    OFFICIAL_PROPOSAL("议案", "党政机关公文"),
    OFFICIAL_LETTER("函", "党政机关公文"),
    OFFICIAL_MINUTES("纪要", "党政机关公文"),
    BUSINESS_NOTICE("公司通知", "公司/白领常用"),
    BUSINESS_PLAN("工作计划", "公司/白领常用"),
    BUSINESS_SUMMARY("工作总结", "公司/白领常用"),
    BUSINESS_PROPOSAL("工作方案", "公司/白领常用"),
    BUSINESS_EMAIL("商务邮件/微信", "公司/白领常用"),
    BUSINESS_MEMO("备忘录", "公司/白领常用"),
    BUSINESS_APPLICATION("申请/审批说明", "公司/白领常用"),
    BUSINESS_MEETING_MINUTES("企业会议纪要", "公司/白领常用"),
    STUDENT_LEAVE_NOTE("请假条", "学生/校园常用"),
    STUDENT_REPORT("实习/实践报告", "学生/校园常用"),
    STUDENT_ACTIVITY_PLAN("活动策划", "学生/校园常用"),
    STUDENT_SPEECH("演讲稿", "学生/校园常用"),
    STUDENT_REVIEW("学习总结", "学生/校园常用"),
    ;

    val menuLabel: String
        get() = "$group · $label"
}

data class LlmRewriteConfig(
    val enabled: Boolean = false,
    val providerKey: String = "openai",
    val provider: String = "openai",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-5.1",
    val temperature: Double = 0.3,
    val maxTokens: Int = 4096,
)

data class Android031Settings(
    val streamingModel: StreamingModelPreference = StreamingModelPreference.MULTILINGUAL_REALTIME,
    val voicePackage: VoicePackagePreference = VoicePackagePreference.FAST_OFFLINE,
    val streamingEnhancementMode: StreamingEnhancementMode = StreamingEnhancementMode.OFFLINE_PRIVATE,
    val streamingAiPanelEnabled: Boolean = true,
    val rewriteBackend: RewriteBackendPreference = RewriteBackendPreference.LOCAL,
    val autoLearningEnabled: Boolean = true,
    val voiceFormattingEnabled: Boolean = true,
    val systemLexiconEnabled: Boolean = true,
    val rewriteScenario: RewriteScenario = RewriteScenario.GENERAL,
    val llmRewrite: LlmRewriteConfig = LlmRewriteConfig(),
)

data class LlmProviderPreset(
    val key: String,
    val label: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val apiKeyHelp: String,
)

object LlmProviderPresets {
    val all: List<LlmProviderPreset> = listOf(
        LlmProviderPreset("openai", "OpenAI GPT", "openai", "https://api.openai.com/v1", "gpt-5.1", 0.3, "填写 OpenAI Platform API Key"),
        LlmProviderPreset("minimax_cn", "MiniMax 国内版", "compatible", "https://api.minimaxi.com/v1", "MiniMax-M2.7", 0.3, "填写 MiniMax 国内平台 API Key"),
        LlmProviderPreset("minimax_intl", "MiniMax 国际版", "compatible", "https://api.minimax.io/v1", "MiniMax-M2.7", 0.3, "填写 MiniMax 国际平台 API Key"),
        LlmProviderPreset("deepseek", "DeepSeek", "compatible", "https://api.deepseek.com", "deepseek-v4-flash", 0.3, "填写 DeepSeek API Key"),
        LlmProviderPreset("qwen_cn", "通义千问 / 阿里云百炼（北京）", "compatible", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", 0.3, "填写阿里云百炼北京地域 API Key"),
        LlmProviderPreset("qwen_sg", "通义千问 / 阿里云百炼（新加坡）", "compatible", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "qwen-plus", 0.3, "填写阿里云百炼新加坡地域 API Key"),
        LlmProviderPreset("qwen_us", "通义千问 / 阿里云百炼（美国）", "compatible", "https://dashscope-us.aliyuncs.com/compatible-mode/v1", "qwen-plus", 0.3, "填写阿里云百炼美国地域 API Key"),
        LlmProviderPreset("zhipu", "智谱 GLM", "compatible", "https://open.bigmodel.cn/api/paas/v4", "glm-4.7-flash", 0.3, "填写智谱开放平台 API Key"),
        LlmProviderPreset("kimi_cn", "Kimi / 月之暗面国内版", "compatible", "https://api.moonshot.cn/v1", "moonshot-v1-8k", 1.0, "填写 Kimi 国内 API Key"),
        LlmProviderPreset("kimi_intl", "Kimi 国际版", "compatible", "https://api.moonshot.ai/v1", "moonshot-v1-8k", 1.0, "填写 Kimi 国际 API Key"),
        LlmProviderPreset("siliconflow", "硅基流动", "compatible", "https://api.siliconflow.cn/v1", "zai-org/GLM-4.7-Flash", 0.3, "填写硅基流动 API Key"),
        LlmProviderPreset("baidu_cn", "百度千帆国内版", "compatible", "https://qianfan.baidubce.com/v2", "ernie-speed-pro-128k", 0.3, "填写百度千帆国内 API Key"),
        LlmProviderPreset("baidu_intl", "百度千帆国际版", "compatible", "https://api.baiduqianfan.ai/v1", "ernie-speed-pro-128k", 0.3, "填写百度千帆国际 API Key"),
        LlmProviderPreset("gemini", "Google Gemini", "compatible", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", 0.3, "填写 Google AI Studio API Key"),
        LlmProviderPreset("baichuan", "百川智能", "compatible", "https://api.baichuan-ai.com/v1", "Baichuan4", 0.3, "填写百川智能 API Key"),
        LlmProviderPreset("doubao", "豆包 / 火山方舟", "compatible", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-6-250615", 0.3, "填写火山方舟 API Key"),
    )

    fun presetFor(key: String): LlmProviderPreset = all.firstOrNull { it.key == key } ?: all.first()
}
