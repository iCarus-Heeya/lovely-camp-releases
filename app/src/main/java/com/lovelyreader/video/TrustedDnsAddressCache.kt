package com.lovelyreader.video

import java.net.InetAddress

internal class TrustedDnsAddressCache(
    private val ttlMillis: Long = 30_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val addresses: List<InetAddress>, val expiresAtMillis: Long)
    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun get(host: String): List<InetAddress>? {
        val entry = entries[host.lowercase()] ?: return null
        if (nowMillis() >= entry.expiresAtMillis) {
            entries.remove(host.lowercase())
            return null
        }
        return entry.addresses
    }

    @Synchronized
    fun put(host: String, addresses: List<InetAddress>) {
        if (addresses.isEmpty()) return
        entries[host.lowercase()] = Entry(addresses.distinctBy { it.hostAddress }, nowMillis() + ttlMillis)
    }
}
