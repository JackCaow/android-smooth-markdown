package com.jackcaow.smoothmarkdown.demo

internal enum class AIProvider(val displayName: String) {
    DEEPSEEK("DeepSeek"),
    QWEN("Qwen"),
}

internal val defaultAIProvider = AIProvider.DEEPSEEK

/** Keep the synced Flutter fixture intact while matching its copy to the selected API provider. */
internal fun aiChatProviderCopy(source: String, provider: AIProvider): String =
    source.replace("DeepSeek", provider.displayName).replace("Qwen", provider.displayName)
