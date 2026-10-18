package com.lovelyreader.audiobook

import android.content.Context
import com.lovelyreader.download.DownloadConnection
import com.lovelyreader.download.DownloadAuthenticationException
import com.lovelyreader.download.DownloadContentRange
import com.lovelyreader.download.ResumableFileDownloader
import com.lovelyreader.download.parseContentRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

data class AudiobookApiConfig(
    val baseUrl: String,
    val sessionToken: String = "",
    val clientId: String = "",
)

data class AudiobookDownloadArtifact(
    val file: File,
    val partial: File
) {
    fun cleanup() {
        file.delete()
        partial.delete()
        File(partial.parentFile, "${partial.name}.meta").delete()
    }
}

data class AudiobookChapterRetryResult(
    val book: AudiobookBook,
    val chapter: AudiobookChapter,
)

internal fun interface AudiobookHttpConnectionFactory {
    fun open(
        config: AudiobookApiConfig,
        method: String,
        path: String,
        body: ByteArray?,
        readTimeoutMillis: Int,
    ): HttpURLConnection
}

internal fun interface AudiobookApiConfigProvider {
    fun get(): AudiobookApiConfig
}

class AudiobookConfigStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "lovely_reader_audiobook_api",
        Context.MODE_PRIVATE
    )

    init {
        // Do not carry the old deployment URL or relay token into the automatic session flow.
        preferences.edit()
            .remove(KEY_LEGACY_BASE_URL)
            .remove(KEY_LEGACY_TOKEN)
            .apply()
        if (preferences.getString(KEY_CLIENT_ID, "").orEmpty().isBlank()) {
            preferences.edit()
                .putString(KEY_CLIENT_ID, newClientId())
                .apply()
        }
    }

    fun config(): AudiobookApiConfig = AudiobookApiConfig(
        baseUrl = DEFAULT_BASE_URL,
        sessionToken = preferences.getString(KEY_SESSION_TOKEN, "").orEmpty().trim(),
        clientId = preferences.getString(KEY_CLIENT_ID, "").orEmpty().trim(),
    )

    fun saveSessionToken(token: String) {
        preferences.edit()
            .putString(KEY_SESSION_TOKEN, token.trim())
            .apply()
    }

    fun saveClientId(clientId: String) {
        if (isValidAudiobookClientId(clientId)) {
            preferences.edit()
                .putString(KEY_CLIENT_ID, clientId.trim())
                .apply()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_LEGACY_BASE_URL = "base_url"
        private const val KEY_LEGACY_TOKEN = "token"

        private fun newClientId(): String =
            (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")
    }
}

private fun isValidAudiobookClientId(value: String): Boolean =
    value.matches(Regex("^[A-Za-z0-9_-]{32,128}$"))

object AudiobookPipelineContract {
    const val POLICY_ID = "lovely-reader-team-v4"
    const val POLICY_SHA256 = "51337ffccbbc664444d07c0e6ce6563bcffc1a0d34af146ea2e5e2d61b3b7cac"
    const val CONTRACT_SCHEMA = "audiobook-pipeline-v1"
    const val PIPELINE_FINGERPRINT = "9105d1c7eeb5f11d8ac34ec9088a651da42db34bd008a307ced4cb46774e1b53"
    const val SOURCE_CHUNK_BYTES = 8 * 1024 * 1024
    const val DEFAULT_GENERATION_SCOPE = "representative"
    const val FULL_GENERATION_SCOPE = "full"
}

class AudiobookApi private constructor(
    private val configProvider: () -> AudiobookApiConfig,
    private val sessionTokenSaver: (String) -> Unit,
    private val connectionFactory: AudiobookHttpConnectionFactory,
    private val clientIdSaver: (String) -> Unit,
) {
    private val sessionMutex = Mutex()

    constructor(configStore: AudiobookConfigStore) : this(
        configProvider = { configStore.config() },
        sessionTokenSaver = configStore::saveSessionToken,
        connectionFactory = DEFAULT_CONNECTION_FACTORY,
        clientIdSaver = configStore::saveClientId,
    )

    internal constructor(
        config: AudiobookApiConfig,
        connectionFactory: AudiobookHttpConnectionFactory,
    ) : this({ config }, {}, connectionFactory, {})

    internal constructor(
        config: AudiobookApiConfig,
        sessionTokenSaver: (String) -> Unit = {},
        clientIdSaver: (String) -> Unit = {},
        connectionFactory: AudiobookHttpConnectionFactory,
    ) : this({ config }, sessionTokenSaver, connectionFactory, clientIdSaver)

    internal constructor(
        configProvider: AudiobookApiConfigProvider,
        sessionTokenSaver: (String) -> Unit = {},
        connectionFactory: AudiobookHttpConnectionFactory,
    ) : this({ configProvider.get() }, sessionTokenSaver, connectionFactory, {})

    suspend fun createBook(
        clientRequestId: String,
        bookId: String,
        title: String,
        author: String,
        sourceSha256: String,
        sourceBytes: Long,
        sourceChunkCount: Int,
        generationScope: String = AudiobookPipelineContract.DEFAULT_GENERATION_SCOPE
    ): AudiobookBook = withContext(Dispatchers.IO) {
        val response = requestJson(
            "POST",
            "/api/v1/books",
            JSONObject().apply {
                put("clientRequestId", clientRequestId)
                put("bookId", bookId)
                put("title", title)
                put("author", author)
                put("sourceSha256", sourceSha256)
                put("sourceBytes", sourceBytes)
                put("sourceChunkCount", sourceChunkCount)
                put("sourceChunkSize", AudiobookPipelineContract.SOURCE_CHUNK_BYTES)
                put("generationScope", generationScope)
                put("policyId", AudiobookPipelineContract.POLICY_ID)
                put("policySha256", AudiobookPipelineContract.POLICY_SHA256)
                put("contractSchema", AudiobookPipelineContract.CONTRACT_SCHEMA)
                put("pipelineFingerprint", AudiobookPipelineContract.PIPELINE_FINGERPRINT)
            },
            headers = mapOf("Idempotency-Key" to clientRequestId)
        )
        parseBook(response.getJSONObject("book"))
    }

    suspend fun uploadSourceChunk(bookId: String, index: Int, payload: ByteArray) {
        withContext(Dispatchers.IO) {
            val digest = sha256(payload)
            requestBytes(
                "PUT",
                "/api/v1/books/$bookId/source/chunks/$index",
                payload,
                "application/octet-stream",
                mapOf("X-Content-SHA256" to digest)
            )
        }
    }

    suspend fun completeSource(bookId: String): AudiobookBook = withContext(Dispatchers.IO) {
        val response = requestJson("POST", "/api/v1/books/$bookId/source/complete", JSONObject())
        parseBook(response.getJSONObject("book"))
    }

    suspend fun listBooks(): List<AudiobookBook> = withContext(Dispatchers.IO) {
        val response = requestJson(
            "GET",
            "/api/v1/books",
            readTimeoutMillis = METADATA_READ_TIMEOUT_MILLIS
        )
        response.optJSONArray("books").toObjects().map(::parseBook)
    }

    suspend fun getBook(bookId: String): Pair<AudiobookBook, List<AudiobookChapter>> = withContext(Dispatchers.IO) {
        val response = requestJson(
            "GET",
            "/api/v1/books/$bookId?limit=$CHAPTER_PAGE_SIZE",
            readTimeoutMillis = METADATA_READ_TIMEOUT_MILLIS
        )
        val book = parseBook(response.getJSONObject("book"))
        validateBookPage(response, book, expectedCursor = 0L)
        val manifestRevision = book.manifestRevision
        val chapters = parsePageChapters(response, book).toMutableList()
        var nextCursor = response.nextManifestCursor()
        var requestedCursor = 0L
        var pageCount = 1
        val seenCursors = mutableSetOf<Long>()
        val seenChapterIds = chapters.mapTo(mutableSetOf()) { it.id }
        val seenOrdinals = chapters.mapTo(mutableSetOf()) { it.ordinal }
        while (nextCursor != null) {
            val cursor = nextCursor.toLongOrNull()
                ?.takeIf { it >= 0L }
                ?: throw IllegalStateException("章节分页游标格式无效，请刷新后重试")
            if (cursor <= requestedCursor || !seenCursors.add(cursor) || pageCount >= MAX_CHAPTER_PAGES) {
                throw IllegalStateException("章节分页游标未向前推进，请刷新后重试")
            }
            val page = requestJson(
                "GET",
                "/api/v1/books/$bookId?limit=$CHAPTER_PAGE_SIZE&cursor=$nextCursor&manifestRevision=$manifestRevision",
                readTimeoutMillis = METADATA_READ_TIMEOUT_MILLIS
            )
            val pageBook = parseBook(page.getJSONObject("book"))
            validateBookPage(page, pageBook, expectedCursor = cursor, expectedBook = book)
            val pageChapters = parsePageChapters(page, book)
            if (pageChapters.isEmpty() && page.nextManifestCursor() != null) {
                throw IllegalStateException("章节分页返回空页但仍有后续游标，请刷新后重试")
            }
            pageChapters.forEach { chapter ->
                if (!seenChapterIds.add(chapter.id) || !seenOrdinals.add(chapter.ordinal)) {
                    throw IllegalStateException("章节分页返回重复章节，请刷新后重试")
                }
            }
            chapters += pageChapters
            requestedCursor = cursor
            nextCursor = page.nextManifestCursor()
            pageCount += 1
        }
        val allChapters = chapters.toList()
        val chaptersComplete = if (response.has("chaptersComplete")) {
            response.optBoolean("chaptersComplete", false) &&
                audiobookChapterSnapshotIsComplete(book, allChapters)
        } else {
            audiobookChapterSnapshotIsComplete(book, allChapters)
        }
        book.copy(chaptersSnapshotComplete = chaptersComplete) to allChapters
    }

    /** Requeue a failed pre-manifest analysis through the user's anonymous app session. */
    suspend fun retryBookAnalysis(bookId: String): AudiobookBook = withContext(Dispatchers.IO) {
        val response = requestJson(
            "POST",
            "/api/v1/books/$bookId/retry",
            JSONObject()
        )
        response.optJSONObject("book")?.let(::parseBook)
            ?: throw IllegalStateException("重新提交整本书任务失败，请刷新后重试")
    }

    /** Requeue one failed chapter through the automatic app session. */
    suspend fun retryChapter(bookId: String, chapterId: String): AudiobookChapterRetryResult = withContext(Dispatchers.IO) {
        val response = requestJson(
            "POST",
            "/api/v1/books/$bookId/chapters/$chapterId/retry",
            JSONObject()
        )
        val book = response.optJSONObject("book")?.let(::parseBook)
            ?: throw IllegalStateException("重新生成章节失败，请刷新后重试")
        val chapter = response.optJSONObject("chapter")?.let { value ->
            parseChapter(value, book.manifestRevision)
        } ?: throw IllegalStateException("重新生成章节后状态读取失败，请刷新后重试")
        AudiobookChapterRetryResult(book, chapter)
    }

    suspend fun downloadChapter(
        book: AudiobookBook,
        chapter: AudiobookChapter,
        target: File,
        onProgress: suspend (Long, Long?) -> Unit = { _, _ -> }
    ): AudiobookDownloadArtifact = withContext(Dispatchers.IO) {
        val directory = target.parentFile ?: error("章节音频目录不存在")
        val isolatedTarget = File(directory, audiobookDownloadArtifactName(target.name, chapter))
        val partial = File(directory, "${isolatedTarget.name}.part")
        try {
            val downloader = ResumableFileDownloader(
                open = { _, startByte -> openAudioConnection(book.id, chapter.id, startByte) }
            )
            var artifact: AudiobookDownloadArtifact? = null
            var sessionRotationAttempted = false
            while (artifact == null) {
                ensureSession()
                try {
                    val result = downloader.download(
                        url = audioUrl(book.id, chapter.id),
                        partial = partial,
                        target = isolatedTarget,
                        onProgress = { progress -> onProgress(progress.downloadedBytes, progress.totalBytes) },
                        expectedBytes = chapter.audioBytes,
                        partialIdentity = audiobookChapterAudioIdentity(chapter)
                    )
                    val expectedBytes = chapter.audioBytes
                    if (expectedBytes != null && result.length() != expectedBytes) {
                        throw IllegalStateException("章节音频字节数校验失败")
                    }
                    val expectedSha = chapter.audioSha256.orEmpty().lowercase()
                    if (expectedSha.isNotBlank() && sha256(result) != expectedSha) {
                        throw IllegalStateException("章节音频 SHA-256 校验失败")
                    }
                    artifact = AudiobookDownloadArtifact(result, partial)
                } catch (error: AudiobookSessionExpiredException) {
                    if (sessionRotationAttempted) throw error
                    rotateSession(error.expiredToken)
                    sessionRotationAttempted = true
                }
            }
            checkNotNull(artifact)
        } catch (error: CancellationException) {
            if (!shouldRetainAudiobookPartial(error)) {
                AudiobookDownloadArtifact(isolatedTarget, partial).cleanup()
            }
            throw error
        } catch (error: Exception) {
            if (!shouldRetainAudiobookPartial(error)) {
                AudiobookDownloadArtifact(isolatedTarget, partial).cleanup()
            }
            throw error
        }
    }

    private fun openAudioConnection(bookId: String, chapterId: String, startByte: Long): DownloadConnection {
        val config = configProvider()
        require(config.sessionToken.isNotBlank()) { "听书服务正在初始化，请稍后再试" }
        val connection = openConnection(config, "GET", "/api/v1/books/$bookId/chapters/$chapterId/audio", null)
        connection.setRequestProperty("X-Reader-Session", config.sessionToken)
        config.clientId.takeIf(::isValidAudiobookClientId)?.let {
            connection.setRequestProperty("X-Reader-Client-Id", it)
        }
        connection.setRequestProperty("Accept", "audio/wav, audio/*")
        connection.setRequestProperty("User-Agent", "LovelyReader-Audiobook/1.0")
        if (startByte > 0L) connection.setRequestProperty("Range", "bytes=$startByte-")
        return object : DownloadConnection {
            private var observedResponseCode: Int? = null
            override val responseCode: Int
                get() {
                    val code = observedResponseCode ?: connection.responseCode.also { observedResponseCode = it }
                    if (code == 401) {
                        throw AudiobookSessionExpiredException(
                            expiredToken = config.sessionToken,
                            message = "听书服务会话已失效，请重新打开应用"
                        )
                    }
                    return code
                }
            override val contentLength: Long? get() = connection.contentLengthLong.takeIf { it >= 0L }
            private val parsedContentRange: DownloadContentRange?
                get() = parseContentRange(connection.getHeaderField("Content-Range"))
            override val contentRangeStart: Long? get() = parsedContentRange?.start
            override val contentRangeEnd: Long? get() = parsedContentRange?.end
            override val contentRangeTotal: Long? get() = parsedContentRange?.total
            override val input get() = if (responseCode >= 400) connection.errorStream ?: connection.inputStream else connection.inputStream
            override fun close() = connection.disconnect()
        }
    }

    private fun audioUrl(bookId: String, chapterId: String): String =
        "${configProvider().baseUrl}/api/v1/books/$bookId/chapters/$chapterId/audio"

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
        readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS
    ): JSONObject {
        val raw = requestBytes(
            method,
            path,
            body?.toString()?.toByteArray(Charsets.UTF_8),
            if (body == null) null else "application/json",
            headers,
            readTimeoutMillis
        )
        return if (raw.isEmpty()) JSONObject() else JSONObject(raw.toString(Charsets.UTF_8))
    }

    private suspend fun requestBytes(
        method: String,
        path: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String> = emptyMap(),
        readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS
        ): ByteArray {
        val sessionToken = ensureSession()
        return try {
            requestBytesInternal(method, path, body, contentType, headers, readTimeoutMillis, sessionToken)
        } catch (error: AudiobookSessionExpiredException) {
            val refreshed = rotateSession(error.expiredToken)
            requestBytesInternal(method, path, body, contentType, headers, readTimeoutMillis, refreshed)
        }
    }

    private suspend fun rotateSession(expiredToken: String): String =
        ensureSession(forceRefresh = true, expiredToken = expiredToken)

    private suspend fun ensureSession(
        forceRefresh: Boolean = false,
        expiredToken: String? = null,
    ): String = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            val currentConfig = configProvider()
            val current = currentConfig.sessionToken
            if (!forceRefresh && current.isNotBlank()) return@withContext current
            if (forceRefresh && !expiredToken.isNullOrBlank() && current.isNotBlank() && current != expiredToken) {
                return@withContext current
            }
            val response = requestBytesInternal(
                method = "POST",
                path = "/api/v1/session",
                body = null,
                contentType = null,
                headers = mapOf("X-Reader-Client" to "android"),
                readTimeoutMillis = METADATA_READ_TIMEOUT_MILLIS,
                // Send the expired token only during a forced rotation so an
                // upgraded install without client_id can retain its owner row.
                sessionToken = if (forceRefresh) current.takeIf { it.isNotBlank() } else null,
            )
            val sessionResponse = JSONObject(response.toString(Charsets.UTF_8))
            val token = sessionResponse.optString("sessionToken").trim()
            if (!Regex("^[A-Za-z0-9_-]{64}$").matches(token)) {
                throw IllegalStateException("听书服务初始化失败，请稍后重试")
            }
            sessionTokenSaver(token)
            sessionResponse.optString("clientId").trim()
                .takeIf(::isValidAudiobookClientId)
                ?.let(clientIdSaver)
            token
        }
    }

    private fun requestBytesInternal(
        method: String,
        path: String,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>,
        readTimeoutMillis: Int,
        sessionToken: String?,
    ): ByteArray {
        val config = configProvider().copy(sessionToken = sessionToken.orEmpty())
        require(config.baseUrl.startsWith("https://")) { "听书服务暂时不可用" }
        val connection = openConnection(config, method, path, body, readTimeoutMillis)
        sessionToken?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("X-Reader-Session", it)
        }
        config.clientId.takeIf(::isValidAudiobookClientId)?.let {
            connection.setRequestProperty("X-Reader-Client-Id", it)
        }
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "LovelyReader-Audiobook/1.0")
        contentType?.let { connection.setRequestProperty("Content-Type", it) }
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val input = if (code in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val response = input.use { it.readBytes() }
            if (code !in 200..299) {
                val message = friendlyAudiobookHttpError(code, response.toString(Charsets.UTF_8))
                if (code == 401 && sessionToken != null) {
                    throw AudiobookSessionExpiredException(sessionToken, message)
                }
                throw IllegalStateException(message)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        config: AudiobookApiConfig,
        method: String,
        path: String,
        body: ByteArray?,
        readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS
    ): HttpURLConnection = connectionFactory.open(
        config,
        method,
        path,
        body,
        readTimeoutMillis,
    )

    private class AudiobookSessionExpiredException(
        val expiredToken: String,
        message: String,
    ) : DownloadAuthenticationException(message)

    private fun validateBookPage(
        response: JSONObject,
        pageBook: AudiobookBook,
        expectedCursor: Long,
        expectedBook: AudiobookBook? = null,
    ) {
        val book = expectedBook ?: pageBook
        if (pageBook.id != book.id ||
            !sameAudiobookSourceIdentity(book.sourceSha256, pageBook.sourceSha256) ||
            !sameAudiobookManifestRevision(book.manifestRevision, pageBook.manifestRevision)
        ) {
            throw IllegalStateException("章节清单在分页查询期间发生变化，请刷新后重试")
        }
        if (response.has("manifestRevision") && !response.isNull("manifestRevision") &&
            response.optLong("manifestRevision") != book.manifestRevision
        ) {
            throw IllegalStateException("章节清单在分页查询期间发生变化，请刷新后重试")
        }
        val pageCursor = response.manifestPageCursor()
        if (pageCursor != null && pageCursor != expectedCursor) {
            throw IllegalStateException("章节分页游标与请求不一致，请刷新后重试")
        }
    }

    private fun parsePageChapters(response: JSONObject, book: AudiobookBook): List<AudiobookChapter> =
        response.optJSONArray("chapters").toObjects().map { value ->
            val chapterBookId = value.optString("bookId").trim()
            if (chapterBookId != book.id) {
                throw IllegalStateException("章节清单包含了其他书籍的章节，请刷新后重试")
            }
            val chapterRevision = if (value.has("manifestRevision") && !value.isNull("manifestRevision")) {
                value.optLong("manifestRevision")
            } else {
                0L
            }
            if (!sameAudiobookManifestRevision(book.manifestRevision, chapterRevision)) {
                throw IllegalStateException("章节清单代际不一致，请刷新后重试")
            }
            parseChapter(value, book.manifestRevision)
        }

    private fun parseBook(value: JSONObject) = AudiobookBook(
        id = value.optString("id"),
        clientBookId = value.optString("bookId"),
        title = value.optString("title"),
        author = value.optString("author"),
        status = AudiobookBookStatus.fromWire(value.optString("status")),
        phase = value.optString("phase"),
        usable = value.optBoolean("usable"),
        progress = value.optInt("progress"),
        sourceSha256 = value.optString("sourceSha256"),
        sourceBytes = value.optLong("sourceBytes"),
        sourceChunkCount = value.optInt("sourceChunkCount"),
        sourceStatus = value.optString("sourceStatus"),
        manifestRevision = value.optLong("manifestRevision"),
        chaptersTotal = value.optInt("chaptersTotal"),
        chaptersReady = value.optInt("chaptersReady"),
        chaptersFailed = value.optInt("chaptersFailed"),
        generationScope = value.optString("generationScope").trim().ifBlank {
            AudiobookPipelineContract.FULL_GENERATION_SCOPE
        },
        generationTargetCount = value.optInt("generationTargetCount"),
        chaptersSnapshotComplete = false,
        policyId = value.optString("policyId"),
        policySha256 = value.optString("policySha256"),
        contractSchema = value.optString("contractSchema"),
        pipelineFingerprint = value.optString("pipelineFingerprint"),
        errorCode = value.optNullableAudiobookString("errorCode"),
        errorMessage = value.optNullableAudiobookString("errorMessage"),
        createdAt = value.optString("createdAt"),
        updatedAt = value.optString("updatedAt")
    )

    private fun parseChapter(value: JSONObject, manifestRevision: Long = value.optLong("manifestRevision")) = AudiobookChapter(
        id = value.optString("id"),
        bookId = value.optString("bookId"),
        ordinal = value.optInt("ordinal"),
        title = value.optString("title"),
        sourceStart = value.optInt("sourceStart"),
        sourceEnd = value.optInt("sourceEnd"),
        sourceSpanSha256 = value.optString("sourceSpanSha256"),
        manifestRevision = manifestRevision,
        status = AudiobookChapterStatus.fromWire(value.optString("status")),
        usable = value.optBoolean("usable"),
        progress = value.optInt("progress"),
        partCount = value.optInt("partCount", 1),
        audioArtifactId = value.optNullableAudiobookString("audioArtifactId"),
        audioSha256 = value.optNullableAudiobookString("audioSha256"),
        audioBytes = value.optLong("audioBytes").takeIf { value.has("audioBytes") && !value.isNull("audioBytes") },
        audioContentType = value.optNullableAudiobookString("audioContentType"),
        durationMs = value.optLong("durationMs").takeIf { value.has("durationMs") && !value.isNull("durationMs") },
        errorCode = value.optNullableAudiobookString("errorCode"),
        errorMessage = value.optNullableAudiobookString("errorMessage"),
        attempt = value.optInt("attempt"),
        updatedAt = value.optString("updatedAt")
    )

    private fun JSONArray?.toObjects(): List<JSONObject> = buildList {
        if (this@toObjects == null) return@buildList
        for (index in 0 until this@toObjects.length()) this@toObjects.optJSONObject(index)?.let(::add)
    }

    private fun JSONObject.nextManifestCursor(): String? =
        if (!has("nextCursor") || isNull("nextCursor")) null
        else optString("nextCursor").trim().let { raw ->
            if (raw.isEmpty() || raw == "null") null
            else raw.toLongOrNull()?.takeIf { it >= 0L }?.toString()
                ?: throw IllegalStateException("章节分页游标格式无效，请刷新后重试")
        }

    private fun JSONObject.manifestPageCursor(): Long? =
        if (!has("pageCursor") || isNull("pageCursor")) null
        else optString("pageCursor").trim().toLongOrNull()?.takeIf { it >= 0L }
            ?: throw IllegalStateException("章节页游标格式无效，请刷新后重试")

    companion object {
        private const val METADATA_READ_TIMEOUT_MILLIS = 20_000
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 120_000
        private const val CHAPTER_PAGE_SIZE = 100
        private const val MAX_CHAPTER_PAGES = 1_000

        private val DEFAULT_CONNECTION_FACTORY = AudiobookHttpConnectionFactory { config, method, path, body, readTimeoutMillis ->
            val connection = URL("${config.baseUrl}$path").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMillis
            connection.doInput = true
            if (body != null) {
                connection.doOutput = true
                // Sites' public edge rejects chunked request bodies with an HTML 403.
                // Fix the byte length so JSON creation and binary chunk uploads use
                // a normal Content-Length request on the phone path.
                connection.setFixedLengthStreamingMode(body.size)
            }
            connection
        }

        private fun sameAudiobookSourceIdentity(first: String, second: String): Boolean =
            first.isNotBlank() && second.isNotBlank() && first.equals(second, ignoreCase = true)

        private fun sameAudiobookManifestRevision(first: Long, second: Long): Boolean =
            first == second || (first == 0L && second == 0L)

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

}
