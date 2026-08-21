package com.lovelyreader.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

data class HttpTimeoutConfiguration(val connectMillis: Int, val readMillis: Int)

class HttpTextClient(
    private val userAgent: String = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 LovelyReader/0.1",
    private val minimumIntervalMillis: Long = 1_200,
    private val connectTimeoutMillis: Int = 12_000,
    private val readTimeoutMillis: Int = 60_000,
    private val connectionFactory: ((URL) -> HttpURLConnection)? = null
) {
    private var lastRequestAtMillis: Long = 0

    fun timeoutConfiguration(): HttpTimeoutConfiguration =
        HttpTimeoutConfiguration(connectTimeoutMillis, readTimeoutMillis)

    suspend fun get(url: String, safety: SourceSafety? = null, referer: String? = null): String =
        request(url, method = "GET", body = null, safety = safety, referer = referer)

    suspend fun getWithProgress(
        url: String,
        safety: SourceSafety? = null,
        referer: String? = null,
        onProgress: suspend (readBytes: Long, totalBytes: Long?) -> Unit
    ): String = request(
        url,
        method = "GET",
        body = null,
        safety = safety,
        referer = referer,
        onProgress = onProgress
    )

    suspend fun postForm(url: String, fields: Map<String, String>, charsetName: String = "UTF-8"): String {
        val charset = Charset.forName(charsetName)
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, charsetName)}=${URLEncoder.encode(value, charsetName)}"
        }.toByteArray(charset)
        return request(
            url = url,
            method = "POST",
            body = body,
            contentType = "application/x-www-form-urlencoded; charset=$charsetName",
            safety = null
        )
    }

    private suspend fun request(
        url: String,
        method: String,
        body: ByteArray?,
        contentType: String? = null,
        safety: SourceSafety? = null,
        challengeDepth: Int = 0,
        referer: String? = null,
        onProgress: suspend (readBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        waitForPoliteInterval()
        val connection = openConnection(url, method, body, contentType, referer)

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body) }
            }
            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val nextUrl = RedirectPolicy.follow(
                    currentUrl = url,
                    location = connection.getHeaderField("Location"),
                    safety = safety ?: SourceSafety(url)
                ).getOrThrow()
                connection.disconnect()
                return@withContext request(
                    nextUrl,
                    method = "GET",
                    body = null,
                    contentType = null,
                    safety = safety,
                    challengeDepth = challengeDepth,
                    referer = referer,
                    onProgress = onProgress
                )
            }
            val stream = if (responseCode >= 400) {
                connection.errorStream ?: connection.inputStream
            } else {
                connection.inputStream
            }
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var readBytes = 0L
                val totalBytes = connection.contentLengthLong.takeIf { it >= 0L }
                onProgress(0L, totalBytes)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    readBytes += count
                    onProgress(readBytes, totalBytes)
                }
                output.toByteArray()
            }
            val text = decodeText(connection.contentType, bytes)
            if (challengeDepth < 2) {
            followJsChallengeIfNeeded(url, text, safety, challengeDepth + 1, onProgress) ?: text
            } else {
                text
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun followJsChallengeIfNeeded(
        url: String,
        text: String,
        safety: SourceSafety?,
        nextDepth: Int,
        onProgress: suspend (readBytes: Long, totalBytes: Long?) -> Unit
    ): String? {
        if (!looksLikeJsChallenge(text)) return null
        val token = Regex("let token = \"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: return null
        val nextUrl = runCatching {
            RedirectPolicy.follow(
                currentUrl = url,
                location = url.substringBefore("?") + "?challenge=" + java.net.URLEncoder.encode(token, "UTF-8"),
                safety = safety ?: SourceSafety(url)
            ).getOrThrow()
        }.getOrNull() ?: return null
        return request(
            nextUrl,
            method = "GET",
            body = null,
            contentType = null,
            safety = safety,
            challengeDepth = nextDepth,
            onProgress = onProgress
        )
    }

    private fun looksLikeJsChallenge(text: String): Boolean {
        return text.contains("let token = \"") &&
            (
                text.contains("正在验证浏览器") ||
                    text.contains("正在进行安全驗證") ||
                    text.contains("正在進行安全驗證") ||
                    text.contains("安全驗證")
                )
    }

    private fun openConnection(
        url: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
        referer: String? = null
    ): HttpURLConnection {
        val requestUrl = URL(encodeUrl(url))
        return (connectionFactory?.invoke(requestUrl) ?: requestUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            if (!referer.isNullOrBlank()) {
                setRequestProperty("Referer", referer)
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
            }
        }
    }

    private fun encodeUrl(url: String): String {
        return url.map { char ->
            when {
                char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~" || char in ":/?#[]@!\$&'()*+,;=" -> char.toString()
                char == ' ' -> "%20"
                else -> URLEncoder.encode(char.toString(), "UTF-8")
            }
        }.joinToString("")
    }

    @Synchronized
    private fun waitForPoliteInterval() {
        val now = System.currentTimeMillis()
        val nextAllowedAt = lastRequestAtMillis + minimumIntervalMillis
        if (now < nextAllowedAt) {
            Thread.sleep(nextAllowedAt - now)
        }
        lastRequestAtMillis = System.currentTimeMillis()
    }

    companion object {
        init {
            if (CookieHandler.getDefault() == null) {
                CookieHandler.setDefault(CookieManager())
            }
        }

        fun decodeText(contentType: String?, bytes: ByteArray): String {
            val declared = detectCharsetName(contentType, bytes)
            val declaredText = declared
                ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
                ?.let { charset -> bytes.toString(charset) }
            if (declaredText != null && !hasDecodingDamage(declaredText)) {
                return declaredText
            }
            val candidates = listOfNotNull(declared, "UTF-8", "GB18030", "GBK", "Big5")
                .distinctBy { it.lowercase() }
                .mapNotNull { name -> runCatching { Charset.forName(name) }.getOrNull() }

            return candidates
                .map { charset -> bytes.toString(charset) }
                .maxByOrNull(::readabilityScore)
                ?: bytes.toString(Charsets.UTF_8)
        }

        private fun readabilityScore(text: String): Int {
            val replacementPenalty = text.count { it == '\uFFFD' } * 80
            val mojibakePenalty = listOf("锟斤拷", "ï¿½").sumOf { token ->
                Regex.escape(token).toRegex().findAll(text).count()
            } * 100
            val privateUsePenalty = text.count { it in '\uE000'..'\uF8FF' } * 25
            val suspiciousSymbolPenalty = text.count { it == '�' || it == '□' } * 80
            val controlPenalty = text.count { it.code in 0..8 || it.code in 14..31 } * 8
            val cjkScore = text.count { it in '\u4E00'..'\u9FFF' } * 3
            val commonPunctuation = text.count { it in "，。！？；：“”‘’（）《》、\n\r\t " }
            val commonWordScore = listOf(
                "作者", "简介", "小说", "章节", "下载", "完结", "排行", "年度", "中国", "网络", "文学",
                "出版", "销售", "正文", "全文", "书名", "最新", "老婆", "老公"
            ).sumOf { token -> Regex.escape(token).toRegex().findAll(text).count() } * 10
            return cjkScore + commonPunctuation + commonWordScore -
                replacementPenalty - mojibakePenalty - privateUsePenalty - suspiciousSymbolPenalty - controlPenalty
        }

        private fun hasDecodingDamage(text: String): Boolean {
            if (text.count { it == '\uFFFD' } >= 2) return true
            if ("锟斤拷" in text || "ï¿½" in text) return true
            if (text.count { it in '\uE000'..'\uF8FF' } >= 2) return true
            return false
        }

        private fun detectCharsetName(contentType: String?, bytes: ByteArray): String? {
            val headerCharset = contentType
                ?.split(";")
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter("=")
                ?.trim()
                ?.trim('"')

            if (!headerCharset.isNullOrBlank()) {
                return headerCharset
            }

            val prefixBytes = bytes.copyOfRange(0, minOf(bytes.size, 2048))
            val prefix = prefixBytes.toString(Charsets.ISO_8859_1)
            return Regex("charset=[\"']?([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
                .find(prefix)
                ?.groupValues
                ?.getOrNull(1)
        }
    }
}
