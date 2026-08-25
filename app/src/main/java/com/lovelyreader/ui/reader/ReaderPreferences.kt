package com.lovelyreader.ui.reader

data class ReaderPreferences(
    val fontSize: Int = 20,
    val lineSpacing: Int = 16,
    val nightMode: Boolean = false
) {
    fun normalized(): ReaderPreferences = copy(
        fontSize = fontSize.coerceIn(14, 24),
        lineSpacing = lineSpacing.coerceIn(12, 32)
    )
}
