package com.lovelyreader.data

import java.security.MessageDigest
import org.json.JSONObject

object LibraryBackupCodec {
    private const val FORMAT_VERSION = 1

    fun encode(snapshot: LibrarySnapshot): String {
        val encoded = LibrarySnapshotCodec().encode(snapshot)
        val payload = JSONObject()
            .put("books", encoded.books)
            .put("progress", encoded.progress)
            .put("bookmarks", encoded.bookmarks)
            .put("notes", encoded.notes)
            .put("seenTitles", encoded.seenTitles)
            .put("seenBookIdentities", encoded.seenBookIdentities)
            .put("offlineChapters", encoded.offlineChapters)
            .put("partialChapters", encoded.partialChapters)
            .put("readerFontSize", encoded.readerFontSize)
            .put("readerLineSpacing", encoded.readerLineSpacing)
            .put("readerNightMode", encoded.readerNightMode)
            .put("appTheme", encoded.appTheme)
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("sha256", sha256(payload.toString()))
            .put("payload", payload)
            .toString()
    }

    fun decode(raw: String): Result<LibrarySnapshot> = runCatching {
        val root = JSONObject(raw)
        require(root.optInt("version") == FORMAT_VERSION) { "备份格式版本不支持" }
        val payload = root.getJSONObject("payload")
        require(root.optString("sha256") == sha256(payload.toString())) { "备份校验失败" }
        LibrarySnapshotCodec().decode(
            EncodedLibrarySnapshot(
                books = payload.optString("books"),
                progress = payload.optString("progress"),
                bookmarks = payload.optString("bookmarks"),
                notes = payload.optString("notes"),
                seenTitles = payload.optString("seenTitles"),
                seenBookIdentities = payload.optString("seenBookIdentities"),
                offlineChapters = payload.optString("offlineChapters"),
                partialChapters = payload.optString("partialChapters"),
                readerFontSize = payload.optString("readerFontSize"),
                readerLineSpacing = payload.optString("readerLineSpacing"),
                readerNightMode = payload.optString("readerNightMode"),
                appTheme = payload.optString("appTheme")
            )
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
