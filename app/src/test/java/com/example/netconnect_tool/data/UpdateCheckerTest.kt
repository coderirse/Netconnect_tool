package com.example.netconnect_tool.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `版本号归一化去掉v前缀和预发布后缀`() {
        assertEquals("1.2.3", UpdateChecker().normalizeVersion("v1.2.3"))
        assertEquals("1.2.3", UpdateChecker().normalizeVersion("V1.2.3"))
        assertEquals("1.2.3", UpdateChecker().normalizeVersion(" 1.2.3 "))
        // 关键回归：预发布后缀不能让数字段解析成 0（曾导致 1.0.13-beta 被当成 1.0.0）
        assertEquals("1.0.13", UpdateChecker().normalizeVersion("1.0.13-beta"))
        assertEquals("0", UpdateChecker().normalizeVersion(""))
    }

    @Test
    fun `版本号比较`() {
        val checker = UpdateChecker()
        assertTrue(checker.compareVersions("1.0.13", "1.0.12") > 0)
        assertTrue(checker.compareVersions("1.0.12", "1.0.13") < 0)
        assertEquals(0, checker.compareVersions("1.0.12", "1.0.12"))
        // 逐段数值比较，不是字典序
        assertTrue(checker.compareVersions("1.0.10", "1.0.9") > 0)
        assertTrue(checker.compareVersions("2.0", "1.9.9") > 0)
        // 段数不同时缺省为 0
        assertEquals(0, checker.compareVersions("1.0", "1.0.0"))
    }
}
