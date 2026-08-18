package com.lovelyreader.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubGistApi {

    /**
     * 获取 Gist 中指定文件的当前内容。
     */
    suspend fun getFileContent(token: String, gistId: String, filename: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL("${SyncConfig.GITHUB_API_BASE_URL}/gists/$gistId").openConnection()
                    as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "token $token")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val response = readResponse(conn) ?: return@withContext null
                val files = response.optJSONObject("files") ?: return@withContext null
                val file = files.optJSONObject(filename) ?: return@withContext null
                file.optString("content")
            } catch (_: Exception) {
                null
            }
        }

    /**
     * 更新 Gist 中指定文件的完整内容。
     */
    suspend fun updateFileContent(
        token: String,
        gistId: String,
        filename: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put(
                    "files",
                    JSONObject().put(
                        filename,
                        JSONObject().put("content", content)
                    )
                )
                .toString()
            val conn = URL("${SyncConfig.GITHUB_API_BASE_URL}/gists/$gistId").openConnection()
                as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = readResponse(conn)
            response != null && conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun readResponse(conn: HttpURLConnection): JSONObject? {
        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }
}
