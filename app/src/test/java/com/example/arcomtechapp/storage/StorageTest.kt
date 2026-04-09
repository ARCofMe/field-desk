package com.example.arcomtechapp.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageTest {
    private lateinit var storage: Storage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storage = Storage(context)
        storage.clearAll()
    }

    @Test
    fun savesAndClearsWorkspaceUrls() {
        storage.saveOpsHubUrl("ops.example.com")
        storage.saveRouteDeskUrl("route.example.com")
        storage.savePartsDeskUrl("parts.example.com")

        assertEquals("ops.example.com", storage.getOpsHubUrl())
        assertEquals("route.example.com", storage.getRouteDeskUrl())
        assertEquals("parts.example.com", storage.getPartsDeskUrl())

        storage.saveOpsHubUrl(null)
        storage.saveRouteDeskUrl(" ")
        storage.savePartsDeskUrl("")

        assertNull(storage.getOpsHubUrl())
        assertNull(storage.getRouteDeskUrl())
        assertNull(storage.getPartsDeskUrl())
    }
}
