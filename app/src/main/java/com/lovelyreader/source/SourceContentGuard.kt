package com.lovelyreader.source

object SourceContentGuard {
    private val interruptionSignals = listOf(
        "正在验证浏览器",
        "正在进行安全驗證",
        "正在进行安全验证",
        "正在進行安全驗證",
        "checking your browser",
        "just a moment",
        "cloudflare",
        "enable javascript",
        "access denied",
        "forbidden",
        "captcha"
    )

    fun isReadableNovelText(text: String): Boolean {
        val normalized = HtmlTools.stripTags(text)
            .replace('\u00A0', ' ')
            .trim()
        if (normalized.length < 20) return false
        if (looksLikeMojibake(normalized)) return false
        val lower = normalized.lowercase()
        if (interruptionSignals.any { lower.contains(it.lowercase()) }) return false
        return true
    }

    private fun looksLikeMojibake(text: String): Boolean {
        val replacementCount = text.count { it == '\uFFFD' }
        if (replacementCount >= 2) return true

        val suspiciousTokens = listOf("锟斤拷", "ï¿½", "�")
        val suspiciousHits = suspiciousTokens.sumOf { token ->
            Regex.escape(token).toRegex().findAll(text).count()
        }
        if (suspiciousHits >= 2) return true

        val sample = text.take(600)
        val cjkCount = sample.count { it in '\u4E00'..'\u9FFF' }
        val readableRatio = cjkCount.toFloat() / sample.length.coerceAtLeast(1)
        return replacementCount > 0 && readableRatio < 0.25f
    }
}
