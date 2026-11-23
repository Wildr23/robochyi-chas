package ua.robochyichas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.robochyichas.app.domain.engine.SyncMergeEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ShiftRecord

class SyncMergeEngineTest {

    @Test
    fun `concurrent edits on two devices merge cleanly by uuid and timestamp`() {
        val localOnly = ShiftRecord(id = 1, uuid = "u-local", date = "2026-11-20", start = "09:00", end = "17:00", updatedAtEpochMs = 100L)
        val sharedLocalOlder = ShiftRecord(id = 2, uuid = "u-shared", date = "2026-11-21", start = "09:00", end = "16:00", updatedAtEpochMs = 200L)

        val remoteOnly = ShiftRecord(id = 99, uuid = "u-remote", date = "2026-11-22", start = "10:00", end = "18:00", updatedAtEpochMs = 150L)
        val sharedRemoteNewer = ShiftRecord(id = 88, uuid = "u-shared", date = "2026-11-21", start = "09:00", end = "19:00", updatedAtEpochMs = 300L)

        val localSettings = AppSettings(defaultNormMinutes = 420, updatedAtEpochMs = 500L)
        val remoteSettings = AppSettings(defaultNormMinutes = 480, updatedAtEpochMs = 400L)

        val res = SyncMergeEngine.merge(
            localShifts = listOf(localOnly, sharedLocalOlder),
            localSettings = localSettings,
            remoteShifts = listOf(remoteOnly, sharedRemoteNewer),
            remoteSettings = remoteSettings
        )

        assertTrue(res.localChanged)
        assertTrue(res.remoteChanged)
        assertEquals(3, res.mergedShifts.size)

        val mergedShared = res.mergedShifts.first { it.uuid == "u-shared" }
        assertEquals("19:00", mergedShared.end)
        assertEquals(2L, mergedShared.id) // preserved local Room row id!

        val mergedRemote = res.mergedShifts.first { it.uuid == "u-remote" }
        assertEquals(0L, mergedRemote.id) // new to local Room -> auto-increment id = 0

        assertEquals(420, res.mergedSettings.defaultNormMinutes) // local settings newer
    }

    @Test
    fun `soft delete tombstone propagates and newer restore overrides older delete`() {
        val activeOlder = ShiftRecord(id = 5, uuid = "u1", date = "2026-11-23", updatedAtEpochMs = 100L, deletedAtEpochMs = null)
        val deletedNewer = ShiftRecord(id = 9, uuid = "u1", date = "2026-11-23", updatedAtEpochMs = 200L, deletedAtEpochMs = 200L)

        val resDelete = SyncMergeEngine.merge(
            localShifts = listOf(activeOlder),
            localSettings = AppSettings(),
            remoteShifts = listOf(deletedNewer),
            remoteSettings = AppSettings()
        )
        assertTrue(resDelete.localChanged)
        assertNotNull(resDelete.mergedShifts.first().deletedAtEpochMs)

        // Now local restores it with newer timestamp 300L
        val restoredNewer = deletedNewer.copy(id = 5L, updatedAtEpochMs = 300L, deletedAtEpochMs = null)
        val resRestore = SyncMergeEngine.merge(
            localShifts = listOf(restoredNewer),
            localSettings = AppSettings(),
            remoteShifts = listOf(deletedNewer),
            remoteSettings = AppSettings()
        )
        assertFalse(resRestore.localChanged)
        assertTrue(resRestore.remoteChanged)
        assertNull(resRestore.mergedShifts.first().deletedAtEpochMs)
    }

    @Test
    fun `identical states produce no changes`() {
        val s = ShiftRecord(id = 1, uuid = "u1", date = "2026-11-23", start = "09:00", end = "17:00", updatedAtEpochMs = 100L)
        val settings = AppSettings(updatedAtEpochMs = 100L)
        val res = SyncMergeEngine.merge(listOf(s), settings, listOf(s.copy(id = 77)), settings)
        assertFalse(res.localChanged)
        assertFalse(res.remoteChanged)
    }
}
