package com.example.arcomtechapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceLinksTest {
    @Test
    fun normalizesBareDomainsToHttps() {
        assertEquals("https://ops.example.com", WorkspaceLinks.normalizeUrl("ops.example.com").toString())
    }

    @Test
    fun rejectsUnsafeSchemes() {
        assertNull(WorkspaceLinks.normalizeUrl("javascript:alert(1)"))
        assertNull(WorkspaceLinks.normalizeUrl("ftp://files.example.com"))
    }

    @Test
    fun countsConfiguredWorkspaceUrls() {
        assertEquals(2, WorkspaceLinks.configuredCount("ops.example.com", "route.example.com", "javascript:alert(1)"))
    }
}
