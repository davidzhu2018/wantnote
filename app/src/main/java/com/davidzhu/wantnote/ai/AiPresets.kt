package com.davidzhu.wantnote.ai

/**
 * Built-in OpenAI-compatible provider presets, adapted from miniMuse's
 * `assets/nanomuse/providers.json` / `OwnKeyPresets` / `ProviderCatalogue`
 * (endpoints and key consoles only; no miniMuse branding).
 *
 * 千问 has no separate catalogue entry in miniMuse; official Qwen API access
 * uses the same DashScope compatible-mode endpoint as 阿里云百炼. It is exposed
 * here as its own preset so users can find it by that name. Keys are still
 * issued in the Bailian / Model Studio console.
 */
data class AiPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val keyUrl: String,
    val keyHint: String,
    val note: String,
    val requiresBaseUrl: Boolean = false,
)

object AiPresets {
    const val CUSTOM = "custom"
    const val DEEPSEEK = "deepseek"
    const val ZHIPU = "zhipu"
    const val KIMI = "kimi"
    const val BAILIAN = "bailian"
    const val QWEN = "qwen"

    val ALL: List<AiPreset> = listOf(
        AiPreset(
            id = DEEPSEEK,
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            keyUrl = "https://platform.deepseek.com/api_keys",
            keyHint = "sk-…",
            note = "OpenAI 兼容接口。填入 key 后可一键获取可用模型。",
        ),
        AiPreset(
            id = ZHIPU,
            name = "智谱",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            keyUrl = "https://open.bigmodel.cn/usercenter/apikeys",
            keyHint = "xxxxxxxx.xxxxxxxx",
            note = "智谱 GLM，OpenAI 兼容接口（/api/paas/v4）。",
        ),
        AiPreset(
            id = KIMI,
            name = "Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            keyUrl = "https://platform.moonshot.cn/console/api-keys",
            keyHint = "sk-…",
            note = "默认使用国内节点 api.moonshot.cn。海外账号可在自定义里改成 https://api.moonshot.ai/v1。本包只支持粘贴 key，不含设备码登录。",
        ),
        AiPreset(
            id = BAILIAN,
            name = "阿里云百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            keyUrl = "https://bailian.console.aliyun.com/?apiKey=1",
            keyHint = "sk-…",
            note = "DashScope 兼容模式。一把 key 可拉取百炼上已开通的模型。",
        ),
        AiPreset(
            id = QWEN,
            name = "千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            keyUrl = "https://bailian.console.aliyun.com/?apiKey=1",
            keyHint = "sk-…",
            note = "千问官方调用走阿里云百炼 / DashScope 兼容模式，与百炼同一控制台申请 key。若已添加百炼，不必重复配置。",
        ),
        AiPreset(
            id = CUSTOM,
            name = "自定义服务提供商",
            baseUrl = "",
            keyUrl = "",
            keyHint = "API Key",
            note = "填写任意 OpenAI 兼容接口地址（一般以 /v1 结尾）和 key，然后获取模型。",
            requiresBaseUrl = true,
        ),
    )

    fun byId(id: String): AiPreset? = ALL.firstOrNull { it.id == id }
}
