package com.lichong.calibrereader.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `新版本号大于旧版本号`() {
        assertTrue(UpdateChecker.compareVersions("0.3.0", "0.2.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "0.99.99") > 0)
    }

    @Test
    fun `v 前缀不影响比较`() {
        assertEquals(0, UpdateChecker.compareVersions("v0.2.0", "0.2.0"))
        assertEquals(0, UpdateChecker.compareVersions("V0.2.0", "v0.2.0"))
    }

    @Test
    fun `缺失段按 0 补齐`() {
        assertEquals(0, UpdateChecker.compareVersions("1.0", "1.0.0"))
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0") > 0)
    }

    @Test
    fun `按数值而非字典序比较`() {
        // 字典序下 "10" < "9"，这里必须是 10 > 9
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.9") > 0)
    }

    @Test
    fun `预发布后缀被剥离`() {
        assertEquals("1.2.3", UpdateChecker.normalizeVersion("v1.2.3-beta.1"))
        assertEquals("1.2.3", UpdateChecker.normalizeVersion("1.2.3+build42"))
        // 0.3.0-rc1 与 0.3.0 等价，不算升级
        assertEquals(0, UpdateChecker.compareVersions("0.3.0-rc1", "0.3.0"))
    }

    @Test
    fun `非数字段不抛异常`() {
        assertEquals(0, UpdateChecker.compareVersions("1.beta.3", "1.0.3"))
        assertTrue(UpdateChecker.compareVersions("2.x", "1.9") > 0)
    }
}
