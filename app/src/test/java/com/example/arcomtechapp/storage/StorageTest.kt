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
        storage = Storage(ApplicationProvider.getApplicationContext())
        storage.clearAll()
    }

    @Test
    fun `job note drafts are isolated per job`() {
        storage.setJobNotesDraft("1001", "first draft")
        storage.setJobNotesDraft("1002", "second draft")

        assertEquals("first draft", storage.getJobNotesDraft("1001"))
        assertEquals("second draft", storage.getJobNotesDraft("1002"))
        assertNull(storage.getJobNotesDraft("1003"))
    }

    @Test
    fun `photo captures accumulate per job`() {
        storage.recordJobPhotoCapture("1001", "Model / serial")
        storage.recordJobPhotoCapture("1001", "Overview")

        val progress = storage.getLocalJobProgress("1001")
        assertEquals(2, progress.photoCount)
        assertEquals("Overview", progress.lastPhotoLabel)
    }
}
