package com.lichong.calibrereader.data.sync

import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.model.ServerKind
import com.lichong.calibrereader.data.net.OpdsClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgressSyncTest {

    private val sync = ProgressSync(OpdsClient(File(System.getProperty("java.io.tmpdir"))) { null })

    private fun server(kind: ServerKind, token: String = "") = ServerConfig(
        id = "s1",
        baseUrl = "http://192.168.1.10:8083",
        kind = kind,
        koboToken = token
    )

    @Test
    fun `Calibre-Web 配了 Kobo 令牌才支持同步`() {
        assertTrue(sync.isSupported(server(ServerKind.CALIBRE_WEB, "0123456789abcdef0123456789abcdef")))
        assertFalse(sync.isSupported(server(ServerKind.CALIBRE_WEB)))
    }

    @Test
    fun `calibre-server 没有进度接口`() {
        assertFalse(sync.isSupported(server(ServerKind.CALIBRE_SERVER, "0123456789abcdef")))
        assertFalse(sync.isSupported(server(ServerKind.UNKNOWN, "0123456789abcdef")))
    }
}
