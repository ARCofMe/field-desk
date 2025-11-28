package com.example.arcomtechapp.storage

import androidx.test.core.app.ApplicationProvider
import androidx.appcompat.app.AppCompatDelegate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageTest {

    private lateinit var storage: Storage

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        storage = Storage(context)
        storage.clearAll()
    }

    @After
    fun tearDown() {
        storage.clearAll()
    }

    @Test
    fun `defaults are empty and safe`() {
        val snapshot = storage.getSnapshot()
        assertNull(snapshot.apiKey)
        assertNull(snapshot.baseUrl)
        assertFalse(snapshot.isAuthenticated)
        assertTrue(snapshot.autoCompressPhotos) // default should help users
        assertNull(snapshot.technicianName)
        assertNull(snapshot.technicianId)
        assertFalse(snapshot.debugMode)
        assertEquals(0L, snapshot.lastSyncEpochMillis)
        assertNull(snapshot.notesDraft)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, snapshot.themeMode)
    }

    @Test
    fun `saving core fields persists snapshot`() {
        val now = 123456789L
        storage.saveApiKey("key-123")
        storage.saveBaseUrl("myco.bluefolder.com")
        storage.setAuthenticated(true)
        storage.setAutoCompressPhotos(false)
        storage.saveTechnician("Sam Tech", "T-42")
        storage.setDebugMode(true)
        storage.markSyncNow(now)
        storage.setThemeMode(AppCompatDelegate.MODE_NIGHT_YES)

        val snapshot = storage.getSnapshot()
        assertEquals("key-123", snapshot.apiKey)
        assertEquals("myco.bluefolder.com", snapshot.baseUrl)
        assertTrue(snapshot.isAuthenticated)
        assertFalse(snapshot.autoCompressPhotos)
        assertEquals("Sam Tech", snapshot.technicianName)
        assertEquals("T-42", snapshot.technicianId)
        assertTrue(snapshot.debugMode)
        assertEquals(now, snapshot.lastSyncEpochMillis)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, snapshot.themeMode)
    }

    @Test
    fun `notes draft stores and clears`() {
        assertNull(storage.getNotesDraft())

        storage.setNotesDraft("draft text")
        assertEquals("draft text", storage.getNotesDraft())

        storage.clearNotesDraft()
        assertNull(storage.getNotesDraft())
    }
}
