package com.lovelyreader.source

data class SourceHealthSnapshot(
    val sourceId: String,
    val available: Boolean,
    val consecutiveFailures: Int,
    val cooldownUntilMillis: Long?,
    val message: String
)

/** Short-lived per-process circuit breaker for public sources. */
class SourceHealthLedger(
    private val failureThreshold: Int = 2,
    private val cooldownMillis: Long = 60_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Entry(
        var consecutiveFailures: Int = 0,
        var cooldownUntilMillis: Long? = null,
        var message: String = ""
    )

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun canRequest(sourceId: String): Boolean {
        val entry = entries[sourceId] ?: return true
        val cooldownUntil = entry.cooldownUntilMillis ?: return true
        if (nowMillis() >= cooldownUntil) {
            entry.cooldownUntilMillis = null
            entry.consecutiveFailures = 0
            entry.message = ""
            return true
        }
        return false
    }

    @Synchronized
    fun recordFailure(sourceId: String, message: String) {
        val entry = entries.getOrPut(sourceId) { Entry() }
        entry.consecutiveFailures++
        entry.message = message.trim().ifBlank { "网络请求失败" }
        if (entry.consecutiveFailures >= failureThreshold) {
            entry.cooldownUntilMillis = nowMillis() + cooldownMillis
        }
    }

    @Synchronized
    fun recordSuccess(sourceId: String) {
        entries.remove(sourceId)
    }

    @Synchronized
    fun snapshot(sourceId: String): SourceHealthSnapshot {
        val entry = entries[sourceId] ?: return SourceHealthSnapshot(
            sourceId = sourceId,
            available = true,
            consecutiveFailures = 0,
            cooldownUntilMillis = null,
            message = ""
        )
        val available = canRequest(sourceId)
        val text = if (available) {
            entry.message
        } else {
            "片源暂时不可用：${entry.message}"
        }
        return SourceHealthSnapshot(
            sourceId = sourceId,
            available = available,
            consecutiveFailures = entry.consecutiveFailures,
            cooldownUntilMillis = entry.cooldownUntilMillis,
            message = text
        )
    }
}
