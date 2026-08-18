package com.lovelyreader.update

/** Automatic Release checks are intentionally rate-limited to one attempt per day. */
const val UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L

/**
 * Keeps automatic update checks useful without turning normal app launches into a mobile-data poll.
 * Manual checks intentionally bypass this policy.
 */
fun shouldRunAutomaticUpdateCheck(
    nowMillis: Long,
    lastAutomaticAttemptMillis: Long?,
    isUnmetered: Boolean
): Boolean {
    if (!isUnmetered) return false
    val last = lastAutomaticAttemptMillis ?: return true
    return nowMillis >= last && nowMillis - last >= UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS
}
