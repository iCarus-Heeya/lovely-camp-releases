package com.lovelyreader.sync

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReadingEventType(val label: String) {
    OPEN_BOOK("打开书"),
    READ_PROGRESS("阅读进度"),
    SEARCH("搜索"),
    ADD_SHELF("加入书架"),
    DELETE_BOOK("删除书"),
    ADD_BOOKMARK("添加书签")
}

data class ReadingEvent(
    val type: ReadingEventType,
    val deviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val progressPercent: Int = 0,
    val keyword: String = "",
    val sourceId: String = "",
    val note: String = ""
) {
    fun toRow(): List<String> {
        return listOf(
            formatTime(timestamp),
            deviceName,
            type.label,
            bookTitle,
            bookAuthor,
            if (progressPercent > 0) "$progressPercent%" else "",
            keyword,
            sourceId,
            note
        )
    }

    fun toCsvRow(): String = toRow().joinToString(",") { it.toCsvCell() }

    companion object {
        fun header(): List<String> {
            return listOf("时间", "设备", "事件", "书名", "作者", "进度", "关键词/来源", "来源站点", "备注")
        }

        fun headerCsv(): String = header().joinToString(",") { it.toCsvCell() }

        private fun String.toCsvCell(): String {
            return if (contains(",") || contains("\"") || contains("\n") || contains("\r")) {
                "\"" + replace("\"", "\"\"") + "\""
            } else {
                this
            }
        }

        private fun formatTime(timestamp: Long): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(timestamp))
        }
    }
}
