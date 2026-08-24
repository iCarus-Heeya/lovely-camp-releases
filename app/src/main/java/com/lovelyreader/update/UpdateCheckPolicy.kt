package com.lovelyreader.update

/** Automatic Release checks are intentionally rate-limited to one attempt per day. */
const val UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L

/**
 * Runs a lightweight release discovery check on any validated network. The check is still
 * rate-limited so a normal app launch does not repeatedly poll the feed; downloading and
 * installing an APK remain explicit user actions. Manual checks intentionally bypass this policy.
 */
fun shouldRunAutomaticUpdateCheck(
    nowMillis: Long,
    lastAutomaticAttemptMillis: Long?,
    isValidatedNetwork: Boolean
): Boolean {
    if (!isValidatedNetwork) return false
    val last = lastAutomaticAttemptMillis ?: return true
    return nowMillis >= last && nowMillis - last >= UPDATE_AUTOMATIC_CHECK_INTERVAL_MILLIS
}
