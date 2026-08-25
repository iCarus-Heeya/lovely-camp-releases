package com.lovelyreader.source

import com.lovelyreader.domain.SourceCapability

data class SourceCapabilityProfile(
    val sourceId: String,
    val verified: Boolean,
    val capabilities: Set<SourceCapability>,
    val note: String
) {
    fun can(capability: SourceCapability): Boolean = verified && capability in capabilities
}

object SourceCapabilityMatrix {
    private val profiles = mapOf(
        "ixdzs" to SourceCapabilityProfile("ixdzs", true, setOf(SourceCapability.SEARCH, SourceCapability.READ_CHAPTER), "已验证章节阅读"),
        "qisuwang" to SourceCapabilityProfile("qisuwang", true, setOf(SourceCapability.SEARCH, SourceCapability.READ_CHAPTER, SourceCapability.TXT_IMPORT, SourceCapability.CHAPTER_CACHE), "已验证 TXT 导入阅读"),
        "qinkan" to SourceCapabilityProfile("qinkan", true, setOf(SourceCapability.SEARCH, SourceCapability.READ_CHAPTER, SourceCapability.TXT_IMPORT, SourceCapability.EPUB_IMPORT, SourceCapability.CHAPTER_CACHE), "已验证多格式下载阅读"),
        "zxcs" to SourceCapabilityProfile("zxcs", true, setOf(SourceCapability.SEARCH, SourceCapability.READ_CHAPTER, SourceCapability.TXT_IMPORT, SourceCapability.CHAPTER_CACHE), "已验证 TXT 导入阅读"),
        "ijjxs" to SourceCapabilityProfile("ijjxs", false, setOf(SourceCapability.SEARCH), "网络访问不稳定，详情与下载继续验证"),
        "yqxz" to SourceCapabilityProfile("yqxz", false, emptySet(), "Cloudflare 拦截，暂不计入已支持")
    )

    fun forSource(sourceId: String): SourceCapabilityProfile = profiles[sourceId]
        ?: SourceCapabilityProfile(sourceId, false, emptySet(), "来源尚未验证")

    fun all(): List<SourceCapabilityProfile> = profiles.values.toList()
}
