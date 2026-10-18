package com.lovelyreader.audiobook

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookSnapshotCodecTest {
    @Test
    fun `json null and legacy null text do not become a download error`() {
        val jsonNull = JSONObject().put("downloadErrorMessage", JSONObject.NULL)
        val legacyNull = JSONObject().put("downloadErrorMessage", "null")
        val error = JSONObject().put("downloadErrorMessage", "网络中断")

        assertEquals(null, jsonNull.optNullableAudiobookString("downloadErrorMessage"))
        assertEquals(null, legacyNull.optNullableAudiobookString("downloadErrorMessage"))
        assertEquals("网络中断", error.optNullableAudiobookString("downloadErrorMessage"))
    }
}
