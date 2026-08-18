package com.lovelyreader.video

import org.w3c.dom.Element
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/** A UPnP MediaRenderer that exposes the AVTransport service required for DLNA play control. */
data class DlnaRenderer(
    val id: String,
    val friendlyName: String,
    val avTransportControlUrl: String,
    val avTransportServiceType: String
)

/** Broad SSDP targets cover renderers that do not answer the narrow MediaRenderer query. */
internal fun dlnaDiscoverySearchTargets(): List<String> = listOf(
    "urn:schemas-upnp-org:device:MediaRenderer:1",
    "upnp:rootdevice",
    "ssdp:all"
)

internal fun dlnaDiscoveryMessage(locationCount: Int, rendererCount: Int): String = when {
    rendererCount > 0 -> "已找到 $rendererCount 台可投屏设备"
    locationCount == 0 -> "未收到电视的 SSDP 响应；请检查电视投屏开关、访客网络或路由器 AP 隔离。"
    else -> "收到 $locationCount 个设备响应，但没有发现可用的 DLNA 播放服务。"
}

internal fun dlnaDescriptionUrlFromSsdp(response: String): String? =
    response.lineSequence()
        .firstOrNull { it.substringBefore(':').equals("location", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { runCatching { URI(it).isAbsolute }.getOrDefault(false) }

internal fun parseDlnaRendererDescription(descriptionUrl: String, xml: String): DlnaRenderer? = runCatching {
    val document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
    }.newDocumentBuilder().parse(xml.byteInputStream())
    val root = document.documentElement
    val base = root.childText("URLBase")?.trim()?.takeIf(String::isNotBlank) ?: descriptionUrl
    root.getElementsByTagName("device").asElementSequence().toList().asReversed()
        .firstNotNullOfOrNull { device -> device.toDlnaRenderer(base) }
}.getOrNull()

private fun Element.childText(name: String): String? =
    getElementsByTagName(name).item(0)?.textContent

private fun Element.directChildText(name: String): String? =
    childNodes.asElementSequence().firstOrNull { it.tagName == name }?.textContent

private fun Element.toDlnaRenderer(base: String): DlnaRenderer? {
    val name = directChildText("friendlyName")?.trim().orEmpty().ifBlank { return null }
    val id = directChildText("UDN")?.trim().orEmpty().ifBlank { return null }
    val service = getElementsByTagName("service").asElementSequence().firstOrNull { candidate ->
        candidate.directChildText("serviceType")?.trim()?.contains(":AVTransport:") == true
    } ?: return null
    val type = service.directChildText("serviceType")?.trim() ?: return null
    val control = service.directChildText("controlURL")?.trim()?.takeIf(String::isNotBlank) ?: return null
    return DlnaRenderer(
        id = id,
        friendlyName = name,
        avTransportControlUrl = URI(base).resolve(control).toString(),
        avTransportServiceType = type
    )
}

private fun org.w3c.dom.NodeList.asElementSequence(): Sequence<Element> = sequence {
    for (index in 0 until length) {
        (item(index) as? Element)?.let { element -> yield(element) }
    }
}

internal fun dlnaSetTransportUriRequest(mediaUrl: String): String = """
    <?xml version="1.0" encoding="utf-8"?>
    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
      <s:Body>
        <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
          <InstanceID>0</InstanceID>
          <CurrentURI>${mediaUrl.xmlEscape()}</CurrentURI>
          <CurrentURIMetaData></CurrentURIMetaData>
        </u:SetAVTransportURI>
      </s:Body>
    </s:Envelope>
""".trimIndent()

internal fun dlnaPlayRequest(): String = """
    <?xml version="1.0" encoding="utf-8"?>
    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
      <s:Body><u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
        <InstanceID>0</InstanceID><Speed>1</Speed>
      </u:Play></s:Body>
    </s:Envelope>
""".trimIndent()

internal fun dlnaSoapAction(serviceType: String, action: String): String =
    "\"$serviceType#$action\""

private fun String.xmlEscape(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
