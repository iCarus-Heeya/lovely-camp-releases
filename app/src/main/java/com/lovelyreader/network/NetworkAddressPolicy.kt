package com.lovelyreader.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Returns true for addresses that must never be used as a public-origin fallback target.
 *
 * This is deliberately stricter than [InetAddress.isSiteLocalAddress]. Android's helper does
 * not cover IPv6 ULA, documentation ranges, CGNAT, TEST-NET, benchmarking, or other special-use
 * blocks. Keeping the policy in one place prevents the updater and the drama source client from
 * drifting into different SSRF/DNS-rebinding rules.
 */
internal fun isUnsafeNetworkAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) return true

    return when (address) {
        is Inet4Address -> isUnsafeIpv4(address.address)
        is Inet6Address -> isUnsafeIpv6(address.address)
        else -> true
    }
}

private fun isUnsafeIpv4(bytes: ByteArray): Boolean {
    if (bytes.size != 4) return true
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    val third = bytes[2].toInt() and 0xff
    return first == 0 || // 0.0.0.0/8
        first == 10 || // RFC1918
        first == 127 || // loopback (also covered above)
        (first == 100 && second in 64..127) || // CGNAT
        (first == 169 && second == 254) || // link-local (also covered above)
        (first == 172 && second in 16..31) || // RFC1918
        (first == 192 && second == 0 && third == 0) || // IETF protocol assignments
        (first == 192 && second == 0 && third == 2) || // TEST-NET-1
        (first == 192 && second == 88 && third == 99) || // 6to4 anycast
        (first == 192 && second == 168) || // RFC1918
        (first == 198 && second in 18..19) || // benchmarking
        (first == 198 && second == 51 && third == 100) || // TEST-NET-2
        (first == 203 && second == 0 && third == 113) || // TEST-NET-3
        first in 224..239 || // multicast
        first >= 240 // reserved/future use
}

private fun isUnsafeIpv6(bytes: ByteArray): Boolean {
    if (bytes.size != 16) return true
    val mappedIpv4 = bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
        ((bytes[10].toInt() and 0xff) == 0 && (bytes[11].toInt() and 0xff) == 0 ||
            (bytes[10].toInt() and 0xff) == 0xff && (bytes[11].toInt() and 0xff) == 0xff)
    if (mappedIpv4) return isUnsafeIpv4(bytes.copyOfRange(12, 16))
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    // fc00::/7 (ULA) and 2001:db8::/32 (documentation) are never public targets.
    val uniqueLocal = first and 0xfe == 0xfc
    val documentation = first == 0x20 && second == 0x01 &&
        (bytes[2].toInt() and 0xff) == 0x0d && (bytes[3].toInt() and 0xff) == 0xb8
    return uniqueLocal || documentation
}
