package com.lovelyreader.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DlnaDeviceDescriptionParserTest {
    @Test
    fun `discovery asks both renderer and root device targets`() {
        assertEquals(
            listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "upnp:rootdevice",
                "ssdp:all"
            ),
            dlnaDiscoverySearchTargets()
        )
    }

    @Test
    fun `extracts a renderer description url from SSDP response`() {
        assertEquals(
            "http://192.168.1.22:49152/description.xml",
            dlnaDescriptionUrlFromSsdp(
                "HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
                    "LOCATION: http://192.168.1.22:49152/description.xml\r\n\r\n"
            )
        )
    }

    @Test
    fun `finds AVTransport endpoint and resolves relative control url`() {
        val renderer = parseDlnaRendererDescription(
            descriptionUrl = "http://192.168.1.22:49152/description.xml",
            xml = """
                <root><URLBase>http://192.168.1.22:49152/</URLBase><device>
                <friendlyName>客厅海信电视</friendlyName><UDN>uuid:hisense-1</UDN><serviceList><service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/upnp/control/AVTransport</controlURL>
                </service></serviceList></device></root>
            """.trimIndent()
        )

        assertEquals("客厅海信电视", renderer?.friendlyName)
        assertEquals("uuid:hisense-1", renderer?.id)
        assertEquals("http://192.168.1.22:49152/upnp/control/AVTransport", renderer?.avTransportControlUrl)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", renderer?.avTransportServiceType)
    }

    @Test
    fun `finds AVTransport inside an embedded renderer device`() {
        val renderer = parseDlnaRendererDescription(
            "http://192.168.1.23/desc.xml",
            """
                <root><device><friendlyName>电视主设备</friendlyName><UDN>uuid:root</UDN><deviceList><device>
                <friendlyName>卧室电视播放器</friendlyName><UDN>uuid:renderer</UDN><serviceList><service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>control</controlURL>
                </service></serviceList></device></deviceList></device></root>
            """.trimIndent()
        )

        assertEquals("uuid:renderer", renderer?.id)
        assertEquals("卧室电视播放器", renderer?.friendlyName)
        assertEquals("http://192.168.1.23/control", renderer?.avTransportControlUrl)
    }

    @Test
    fun `rejects a device description without AVTransport`() {
        assertNull(
            parseDlnaRendererDescription(
                "http://192.168.1.22/desc.xml",
                "<root><device><friendlyName>Not a player</friendlyName></device></root>"
            )
        )
    }

    @Test
    fun `AVTransport request carries the public media uri without escaping it as markup`() {
        val request = dlnaSetTransportUriRequest(
            "https://media.example.com/episode.m3u8?token=a&part=1"
        )

        assertEquals(true, request.contains("<CurrentURI>https://media.example.com/episode.m3u8?token=a&amp;part=1</CurrentURI>"))
        assertEquals(true, request.contains("<InstanceID>0</InstanceID>"))
    }

    @Test
    fun `SOAP action uses the renderer advertised AVTransport version`() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:2#Play\"",
            dlnaSoapAction("urn:schemas-upnp-org:service:AVTransport:2", "Play")
        )
    }

    @Test
    fun `zero responder diagnostic distinguishes blocked discovery from unsupported devices`() {
        assertEquals(
            "未收到电视的 SSDP 响应；请检查电视投屏开关、访客网络或路由器 AP 隔离。",
            dlnaDiscoveryMessage(locationCount = 0, rendererCount = 0)
        )
    }

    @Test
    fun `non renderer diagnostic reports that devices responded`() {
        assertEquals(
            "收到 2 个设备响应，但没有发现可用的 DLNA 播放服务。",
            dlnaDiscoveryMessage(locationCount = 2, rendererCount = 0)
        )
    }
}
