package com.lovelyreader.source

import java.net.URI

object RedirectPolicy {
    fun follow(currentUrl: String, location: String?, safety: SourceSafety): Result<String> {
        if (location.isNullOrBlank()) return Result.failure(IllegalArgumentException("Redirect location is empty."))
        val nextUrl = URI(currentUrl).resolve(location).toString()
        return runCatching { safety.requireAllowed(nextUrl) }
    }
}
