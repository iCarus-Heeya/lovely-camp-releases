package com.lovelyreader.video

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.LinkedHashSet

/**
 * Small UPnP/DLNA control point. It finds MediaRenderers over SSDP and sends only
 * public media accepted by [castMediaTarget] to their AVTransport service.
 */
class DlnaController(
    context: Context,
    private val mediaPreflight: CastMediaPreflight = CastMediaPreflight()
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun discover(onResult: (List<DlnaRenderer>, String) -> Unit): Job = scope.launch {
        val lock = (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("lovely-reader-dlna")
            ?.apply { setReferenceCounted(false); acquire() }
        try {
            val locations = findRendererDescriptionUrls()
            val renderers = locations.mapNotNull(::fetchRenderer).distinctBy(DlnaRenderer::id)
            val message = dlnaDiscoveryMessage(locations.size, renderers.size)
            withContext(Dispatchers.Main) { onResult(renderers, message) }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                onResult(emptyList(), "搜索电视失败，请检查同一 Wi-Fi 后重试。")
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    fun play(
        renderer: DlnaRenderer,
        target: CastMediaTarget,
        onMessage: (String) -> Unit
    ): Job = scope.launch {
        withContext(Dispatchers.Main) { onMessage(CastReadinessCopy.CheckingMedia) }
        if (mediaPreflight.check(target) != CastPreflightResult.Ready) {
            withContext(Dispatchers.Main) { onMessage(CastReadinessCopy.MediaUnavailable) }
            return@launch
        }
        withContext(Dispatchers.Main) { onMessage("正在向 ${renderer.friendlyName} 发送媒体地址…") }
        val result = runCatching {
            postSoap(renderer, "SetAVTransportURI", dlnaSetTransportUriRequest(target.url))
            withContext(Dispatchers.Main) { onMessage(CastReadinessCopy.ReceiverPreparing) }
            postSoap(renderer, "Play", dlnaPlayRequest())
        }
        withContext(Dispatchers.Main) {
            onMessage(
                if (result.isSuccess) "电视已接受播放请求，请稍候。"
                else "电视未能播放此媒体地址，请换一个片源或使用系统镜像投屏。"
            )
        }
    }

    fun close() = scope.cancel()

    private fun findRendererDescriptionUrls(): Set<String> {
        val found = LinkedHashSet<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 700
            dlnaDiscoverySearchTargets().forEach { target ->
                val request = ssdpSearchRequest(target).toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900))
            }
            val deadline = System.currentTimeMillis() + 5_000
            val buffer = ByteArray(8_192)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    dlnaDescriptionUrlFromSsdp(response)?.let(found::add)
                } catch (_: SocketTimeoutException) {
                    // Keep listening until the SSDP discovery window closes.
                }
            }
        }
        return found
    }

    private fun fetchRenderer(descriptionUrl: String): DlnaRenderer? = runCatching {
        val connection = (URL(descriptionUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            requestMethod = "GET"
        }
        connection.inputStream.bufferedReader().use { reader ->
            parseDlnaRendererDescription(descriptionUrl, reader.readText())
        }.also { connection.disconnect() }
    }.getOrNull()

    private fun postSoap(renderer: DlnaRenderer, action: String, body: String) {
        val connection = (URL(renderer.avTransportControlUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", dlnaSoapAction(renderer.avTransportServiceType, action))
        }
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("DLNA $action failed")
        }
        connection.inputStream.close()
        connection.disconnect()
    }
}

internal fun ssdpSearchRequest(searchTarget: String): String =
    "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: 239.255.255.250:1900\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 3\r\n" +
        "ST: $searchTarget\r\n\r\n"
