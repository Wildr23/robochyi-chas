package ua.robochyichas.app

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ua.robochyichas.app.data.cloud.AccessTokenProvider
import ua.robochyichas.app.data.cloud.AuthOutcome
import ua.robochyichas.app.data.cloud.ConnectivityChecker
import ua.robochyichas.app.data.cloud.DriveSyncEngine
import ua.robochyichas.app.data.cloud.GoogleDriveApiClient
import ua.robochyichas.app.data.cloud.SafBackupWriter
import ua.robochyichas.app.data.cloud.SyncBackingStore
import ua.robochyichas.app.data.cloud.SyncMetaStore
import ua.robochyichas.app.data.cloud.SyncRunResult
import ua.robochyichas.app.domain.engine.ImportExportEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.domain.model.SyncStatus

class GoogleDriveApiAndSyncEngineTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private class InMemoryBackingStore(
        var shifts: MutableList<ShiftRecord> = mutableListOf(),
        var settings: AppSettings = AppSettings()
    ) : SyncBackingStore {
        var lastSnapshotJson: String? = null
        override suspend fun getAllShiftsIncludingDeleted(): List<ShiftRecord> = shifts.toList()
        override suspend fun getSettings(): AppSettings = settings
        override suspend fun saveMergedState(shifts: List<ShiftRecord>, settings: AppSettings) {
            this.shifts = shifts.toMutableList()
            this.settings = settings
        }
        override suspend fun writeLocalSnapshot(jsonContent: String) {
            lastSnapshotJson = jsonContent
        }
    }

    private class InMemoryMetaStore(
        override var googleEmail: String? = "roman@example.com"
    ) : SyncMetaStore {
        override val deviceId: String = "test-device"
        override var localRevision: Long = 1L
        var lastSyncedRev: Long = 0L
        var status: SyncStatus = SyncStatus.LOCAL_ONLY
        var lastError: String? = null
        override var driveAppDataFileId: String? = null
        override var driveVisibleFolderId: String? = null
        override var driveVisibleFileId: String? = null
        override val safBackupUri: String? = null
        override var cachedAccessToken: String? = null

        override fun hasPendingSync(): Boolean = localRevision > lastSyncedRev
        override fun markSyncing() { status = SyncStatus.SYNCING }
        override fun markSynced(syncedRevision: Long, timestampMs: Long) {
            lastSyncedRev = syncedRevision
            status = SyncStatus.SYNCED
            lastError = null
        }
        override fun markOfflineQueued(errorMsg: String?) {
            status = SyncStatus.OFFLINE_QUEUED
            lastError = errorMsg
        }
        override fun markAuthRequired(errorMsg: String?, needsSetupHelp: Boolean) {
            status = SyncStatus.AUTH_REQUIRED
            lastError = errorMsg
        }
        override fun markError(errorMsg: String) {
            status = SyncStatus.ERROR
            lastError = errorMsg
        }
    }

    private class ToggleConnectivity(var online: Boolean = true, var unmetered: Boolean = true) : ConnectivityChecker {
        override fun isNetworkAvailable(): Boolean = online
        override fun isUnmeteredNetwork(): Boolean = unmetered
    }

    private class NoOpSafWriter : SafBackupWriter {
        override suspend fun writeBackupToSaf(uriString: String, jsonContent: String) = Result.success(Unit)
        override suspend fun readBackupFromSaf(uriString: String) = Result.failure<String>(IllegalStateException("unused"))
    }

    @Test
    fun `first sync creates appData and visible folder backup then second sync patches in place`() = runTest {
        val backing = InMemoryBackingStore(
            shifts = mutableListOf(
                ShiftRecord(id = 1, uuid = "u-1", date = "2026-11-23", start = "09:00", end = "17:00", updatedAtEpochMs = 100L)
            )
        )
        val meta = InMemoryMetaStore()
        val conn = ToggleConnectivity(online = true)
        val auth = object : AccessTokenProvider {
            override suspend fun getAccessTokenSilently() = AuthOutcome.Authorized("tok-1", "roman@example.com", "Roman")
        }
        val api = GoogleDriveApiClient(baseUrl = server.url("").toString())
        val engine = DriveSyncEngine(backing, meta, auth, api, conn, NoOpSafWriter(), nowProvider = { 123456L })

        // First sync HTTP sequence:
        // 1. findFileInAppData -> empty
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        // 2. findVisibleFolder -> empty
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        // 3. POST multipart create in appDataFolder -> id app-1
        server.enqueue(MockResponse().setBody("""{"id":"app-1","name":"work_time_sync.json"}"""))
        // 4. ensureVisibleFolder -> findVisibleFolder -> empty
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        // 5. createVisibleFolder -> id fld-1
        server.enqueue(MockResponse().setBody("""{"id":"fld-1","name":"Робочий час"}"""))
        // 6. findVisibleFileInFolder -> empty
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        // 7. POST multipart create visible file -> id vis-1
        server.enqueue(MockResponse().setBody("""{"id":"vis-1","name":"work_time_backup.json"}"""))

        val res1 = engine.syncNow()
        assertTrue(res1 is SyncRunResult.Success)
        assertTrue((res1 as SyncRunResult.Success).uploadedChanges)
        assertEquals("app-1", meta.driveAppDataFileId)
        assertEquals("fld-1", meta.driveVisibleFolderId)
        assertEquals("vis-1", meta.driveVisibleFileId)
        assertEquals(SyncStatus.SYNCED, meta.status)

        // Mutate locally -> rev 2 -> Second sync should PATCH existing file IDs!
        meta.localRevision = 2L
        backing.shifts.add(
            ShiftRecord(id = 2, uuid = "u-2", date = "2026-11-24", start = "09:00", end = "18:00", updatedAtEpochMs = 200L)
        )
        val currentRemoteJson = ImportExportEngine.exportBackupJson(
            shifts = listOf(backing.shifts.first()),
            settings = backing.settings,
            deviceId = "other",
            revision = 1L
        )
        // 1. download existing app-1
        server.enqueue(MockResponse().setBody(currentRemoteJson))
        // 2. PATCH app-1
        server.enqueue(MockResponse().setBody("""{"id":"app-1","name":"work_time_sync.json"}"""))
        // 3. PATCH vis-1
        server.enqueue(MockResponse().setBody("""{"id":"vis-1","name":"work_time_backup.json"}"""))

        val res2 = engine.syncNow()
        assertTrue(res2 is SyncRunResult.Success)
        assertEquals(2L, meta.lastSyncedRev)
        // Verify PATCH was used on upload endpoints
        repeat(7) { server.takeRequest() } // drain first sync
        val dlReq = server.takeRequest()
        assertEquals("GET", dlReq.method)
        val patchAppReq = server.takeRequest()
        assertEquals("PATCH", patchAppReq.method)
        assertTrue(patchAppReq.path!!.contains("app-1"))
        val patchVisReq = server.takeRequest()
        assertEquals("PATCH", patchVisReq.method)
        assertTrue(patchVisReq.path!!.contains("vis-1"))
    }

    @Test
    fun `offline mutation stays queued and syncs automatically once internet returns`() = runTest {
        val backing = InMemoryBackingStore(
            shifts = mutableListOf(
                ShiftRecord(id = 1, uuid = "u-local", date = "2026-11-23", start = "09:00", end = "17:00", updatedAtEpochMs = 500L)
            )
        )
        val meta = InMemoryMetaStore()
        val conn = ToggleConnectivity(online = false)
        val auth = object : AccessTokenProvider {
            override suspend fun getAccessTokenSilently() = AuthOutcome.Authorized("tok-1", "roman@example.com", "Roman")
        }
        val api = GoogleDriveApiClient(baseUrl = server.url("").toString())
        val engine = DriveSyncEngine(backing, meta, auth, api, conn, NoOpSafWriter(), nowProvider = { 999L })

        // Attempt sync while offline
        val offlineRes = engine.syncNow()
        assertTrue(offlineRes is SyncRunResult.OfflineQueued)
        assertEquals(SyncStatus.OFFLINE_QUEUED, meta.status)
        assertTrue(meta.hasPendingSync())
        assertEquals(0, server.requestCount)

        // Network returns + remote has a shift from another device
        conn.online = true
        val remoteShift = ShiftRecord(id = 99, uuid = "u-remote", date = "2026-11-22", start = "08:00", end = "16:00", updatedAtEpochMs = 400L)
        val remoteJson = ImportExportEngine.exportBackupJson(listOf(remoteShift), AppSettings(), "other-dev", 1L)

        meta.driveAppDataFileId = "app-existing"
        meta.driveVisibleFolderId = "fld-1"
        meta.driveVisibleFileId = "vis-1"
        server.enqueue(MockResponse().setBody(remoteJson)) // download app-existing
        server.enqueue(MockResponse().setBody("""{"id":"app-existing","name":"work_time_sync.json"}""")) // PATCH app
        server.enqueue(MockResponse().setBody("""{"id":"vis-1","name":"work_time_backup.json"}""")) // PATCH vis

        val onlineRes = engine.syncNow()
        assertTrue(onlineRes is SyncRunResult.Success)
        val sRes = onlineRes as SyncRunResult.Success
        assertTrue(sRes.pulledRemoteChanges)
        assertTrue(sRes.uploadedChanges)
        assertFalse(meta.hasPendingSync())
        assertEquals(2, backing.shifts.size) // Both local and remote shifts preserved!
    }

    @Test
    fun `HTTP 401 triggers silent token refresh and succeeds on retry`() = runTest {
        val backing = InMemoryBackingStore(
            shifts = mutableListOf(ShiftRecord(id = 1, uuid = "u1", date = "2026-11-23", updatedAtEpochMs = 100L))
        )
        val meta = InMemoryMetaStore()
        val conn = ToggleConnectivity(online = true)
        var tokenCalls = 0
        val auth = object : AccessTokenProvider {
            override suspend fun getAccessTokenSilently(): AuthOutcome {
                tokenCalls++
                return AuthOutcome.Authorized("tok-$tokenCalls", "roman@example.com", "Roman")
            }
        }
        val api = GoogleDriveApiClient(baseUrl = server.url("").toString())
        val engine = DriveSyncEngine(backing, meta, auth, api, conn, NoOpSafWriter())

        // First call returns 401
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        // Retry with refreshed token succeeds
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"app-new","name":"work_time_sync.json"}"""))
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"fld-new","name":"Робочий час"}"""))
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"vis-new","name":"work_time_backup.json"}"""))

        val res = engine.syncNow()
        assertTrue(res is SyncRunResult.Success)
        assertEquals(2, tokenCalls)
        assertEquals("tok-2", meta.cachedAccessToken)
        assertEquals(SyncStatus.SYNCED, meta.status)
    }

    @Test
    fun `persistent 401 marks AUTH_REQUIRED cleanly`() = runTest {
        val backing = InMemoryBackingStore()
        val meta = InMemoryMetaStore()
        val conn = ToggleConnectivity(online = true)
        val auth = object : AccessTokenProvider {
            override suspend fun getAccessTokenSilently() = AuthOutcome.Authorized("bad-tok", "roman@example.com", "Roman")
        }
        val api = GoogleDriveApiClient(baseUrl = server.url("").toString())
        val engine = DriveSyncEngine(backing, meta, auth, api, conn, NoOpSafWriter())

        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val res = engine.syncNow()
        assertTrue(res is SyncRunResult.AuthRequired)
        assertEquals(SyncStatus.AUTH_REQUIRED, meta.status)
    }

    @Test
    fun `listDriveFolders and createDriveFolder browse and create inside selected parent`() = runTest {
        val backing = InMemoryBackingStore()
        val meta = InMemoryMetaStore()
        val conn = ToggleConnectivity(online = true)
        val auth = object : AccessTokenProvider {
            override suspend fun getAccessTokenSilently() = AuthOutcome.Authorized("tok-folder", "roman@example.com", "Roman")
        }
        val api = GoogleDriveApiClient(baseUrl = server.url("").toString())
        val engine = DriveSyncEngine(backing, meta, auth, api, conn, NoOpSafWriter())

        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"fld-a","name":"Documents"},{"id":"fld-b","name":"Work"}]}"""
            )
        )
        server.enqueue(
            MockResponse().setBody("""{"id":"fld-new","name":"Timesheets 2026"}""")
        )

        val listRes = engine.listDriveFolders("root")
        assertTrue(listRes.isSuccess)
        assertEquals(2, listRes.getOrThrow().size)
        assertEquals("Work", listRes.getOrThrow()[1].name)

        val createRes = engine.createDriveFolder("fld-b", "Timesheets 2026")
        assertTrue(createRes.isSuccess)
        assertEquals("fld-new", createRes.getOrThrow().id)

        val listReq = server.takeRequest()
        assertTrue(listReq.path!!.contains("root"))
        val createReq = server.takeRequest()
        assertEquals("POST", createReq.method)
        val createBody = createReq.body.readUtf8()
        assertTrue(createBody.contains("Timesheets 2026"))
        assertTrue(createBody.contains("fld-b"))
    }

    @Test
    fun `sync uploads visible backup into user-selected custom folder id`() = runTest {
        val backing = InMemoryBackingStore(
            shifts = mutableListOf(
                ShiftRecord(id = 1, uuid = "u1", date = "2026-11-23", start = "09:00", end = "17:00", updatedAtEpochMs = 100L)
            )
        )
        val meta = InMemoryMetaStore().apply {
            driveVisibleFolderId = "custom-folder-999"
        }
        val conn = ToggleConnectivity(online = true)
        val auth = object : AccessTokenProvider {
            override suspend fun getAccessTokenSilently() = AuthOutcome.Authorized("tok-1", "roman@example.com", "Roman")
        }
        val api = GoogleDriveApiClient(baseUrl = server.url("").toString())
        val engine = DriveSyncEngine(backing, meta, auth, api, conn, NoOpSafWriter())

        // 1. findFileInAppData -> empty
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        // 2. findVisibleFileInFolder (custom-folder-999) -> empty
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        // 3. POST create appData
        server.enqueue(MockResponse().setBody("""{"id":"app-1","name":"work_time_sync.json"}"""))
        // 4. findVisibleFileInFolder (custom-folder-999) -> empty
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        // 5. POST create visible file in custom-folder-999
        server.enqueue(MockResponse().setBody("""{"id":"vis-999","name":"work_time_backup.json"}"""))

        val res = engine.syncNow()
        assertTrue(res is SyncRunResult.Success)
        assertEquals("custom-folder-999", meta.driveVisibleFolderId)
        assertEquals("vis-999", meta.driveVisibleFileId)

        repeat(4) { server.takeRequest() }
        val uploadVisibleReq = server.takeRequest()
        val bodyStr = uploadVisibleReq.body.readUtf8()
        assertTrue("Uploaded multipart metadata must target custom-folder-999", bodyStr.contains("custom-folder-999"))
    }
}
