package com.lovelyreader.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ReadingLogSync(private val context: Context) {

    private val store = SyncTokenStore(context)
    private val api = GitHubGistApi()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queueFile: File
        get() = File(context.filesDir, "reading_log_queue.csv")

    val isEnabled: Boolean get() = store.syncEnabled
    val isConfigured: Boolean get() = store.isConfigured()

    var githubToken: String
        get() = store.githubToken ?: ""
        set(value) {
            store.githubToken = value
        }

    var gistId: String
        get() = store.gistId ?: ""
        set(value) {
            store.gistId = value
        }

    init {
        // 凭证只能由当前用户在设置页提供，绝不随 APK 或源码分发。
        if (!canEnableSync(store.githubToken, store.gistId)) store.syncEnabled = false

        // 启动定时上传任务，每 30 秒把本地队列批量同步到 GitHub Gist。
        scope.launch { batchUploadLoop() }
    }

    fun setEnabled(enabled: Boolean) {
        store.syncEnabled = enabled && canEnableSync(store.githubToken, store.gistId)
    }

    fun clearAuth() {
        store.clear()
        store.syncEnabled = false
    }

    /**
     * 记录一条阅读事件到本地队列，立即返回，不直接调用网络。
     */
    suspend fun syncEvent(event: ReadingEvent): Boolean = withContext(Dispatchers.IO) {
        if (!store.syncEnabled) return@withContext false
        mutex.withLock {
            runCatching {
                queueFile.appendText(event.toCsvRow() + "\n")
                true
            }.getOrDefault(false)
        }
    }

    private suspend fun batchUploadLoop() {
        while (true) {
            delay(30_000)
            runCatching { uploadPendingEvents() }
        }
    }

    /**
     * 把本地队列中的事件批量追加到 Gist，成功后清空队列。
     */
    private suspend fun uploadPendingEvents() {
        if (!store.syncEnabled) return
        val token = store.githubToken?.takeIf { it.isNotBlank() } ?: return
        val gistId = store.gistId?.takeIf { it.isNotBlank() } ?: return

        mutex.withLock {
            if (!queueFile.exists() || queueFile.length() == 0L) return
            val pendingRows = queueFile.readText(Charsets.UTF_8).trimEnd('\n')
            if (pendingRows.isBlank()) return

            val current = api.getFileContent(token, gistId, SyncConfig.GIST_FILENAME)
            val newContent = if (current.isNullOrBlank()) {
                buildString {
                    appendLine(ReadingEvent.headerCsv())
                    appendLine(pendingRows)
                }
            } else {
                // 最新记录放在最上面，旧记录往下移。
                val oldBody = current.lines().drop(1).joinToString("\n").trimEnd('\n')
                buildString {
                    appendLine(ReadingEvent.headerCsv())
                    appendLine(pendingRows)
                    if (oldBody.isNotBlank()) {
                        appendLine()
                        append(oldBody)
                    }
                }
            }

            if (api.updateFileContent(token, gistId, SyncConfig.GIST_FILENAME, newContent)) {
                queueFile.delete()
            }
        }
    }

    fun currentDeviceName(): String {
        val saved = store.deviceName
        if (!saved.isNullOrBlank()) return saved
        val name = Build.MODEL ?: Settings.Global.getString(
            context.contentResolver,
            Settings.Global.DEVICE_NAME
        )
        val fallback = name?.takeIf { it.isNotBlank() } ?: "未知设备"
        store.deviceName = fallback
        return fallback
    }
}
