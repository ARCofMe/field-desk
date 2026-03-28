package com.example.arcomtechapp.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackendStorageTest {

    private lateinit var storage: Storage

    @Before
    fun setUp() {
        storage = Storage(ApplicationProvider.getApplicationContext())
        storage.clearAll()
    }

    @Test
    fun `active credentials switch with backend mode`() {
        storage.saveBaseUrl("bluefolder.example.com")
        storage.saveApiKey("bf-key")
        storage.saveOpsHubBaseUrl("https://ops-hub.example.com")
        storage.saveOpsHubApiKey("ops-key")

        storage.setBackendMode(Storage.BackendMode.BLUEFOLDER_DIRECT)
        assertEquals("bluefolder.example.com", storage.getActiveBaseUrl())
        assertEquals("bf-key", storage.getActiveApiKey())

        storage.setBackendMode(Storage.BackendMode.OPS_HUB)
        assertEquals("https://ops-hub.example.com", storage.getActiveBaseUrl())
        assertEquals("ops-key", storage.getActiveApiKey())
    }
}
