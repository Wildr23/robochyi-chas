package ua.robochyichas.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ua.robochyichas.app.data.cloud.AndroidConnectivityChecker
import ua.robochyichas.app.data.cloud.AndroidSafBackupWriter
import ua.robochyichas.app.data.cloud.AuthOutcome
import ua.robochyichas.app.data.cloud.DriveSyncEngine
import ua.robochyichas.app.data.cloud.DriveSyncWorker
import ua.robochyichas.app.data.cloud.GoogleAuthManager
import ua.robochyichas.app.data.cloud.GoogleDriveApiClient
import ua.robochyichas.app.data.cloud.SyncBackingStore
import ua.robochyichas.app.data.cloud.SyncMetaStore
import ua.robochyichas.app.data.cloud.SyncRunResult
import ua.robochyichas.app.data.local.AppDatabase
import ua.robochyichas.app.data.local.LocalSnapshotStore
import ua.robochyichas.app.data.local.ShiftMappers.toDomain
import ua.robochyichas.app.data.local.ShiftMappers.toEntity
import ua.robochyichas.app.data.local.SyncPreferences
import ua.robochyichas.app.domain.engine.ImportExportEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.domain.model.SyncState
import java.util.UUID

class WorkTimeRepository(
    private val context: Context,
    private val db: AppDatabase,
    val syncPreferences: SyncPreferences,
    val snapshotStore: LocalSnapshotStore,
    val authManager: GoogleAuthManager
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val shiftDao = db.shiftDao()
    private val settingsDao = db.settingsDao()
    private val connectivityChecker = AndroidConnectivityChecker(context)
    private val safWriter = AndroidSafBackupWriter(context)
    private val driveClient = GoogleDriveApiClient()

    private val backingStore = object : SyncBackingStore {
        override suspend fun getAllShiftsIncludingDeleted(): List<ShiftRecord> =
            shiftDao.getAllShiftsIncludingDeleted().map { it.toDomain() }

        override suspend fun getSettings(): AppSettings =
            settingsDao.getSettings()?.toDomain() ?: AppSettings()

        override suspend fun saveMergedState(shifts: List<ShiftRecord>, settings: AppSettings) {
            db.withTransaction {
                val existingByUuid = shiftDao.getAllShiftsIncludingDeleted().associateBy { it.uuid }
                val normalized = shifts.map { incoming ->
                    val existing = existingByUuid[incoming.uuid]
                    incoming.copy(id = existing?.id ?: 0L).toEntity()
                }
                shiftDao.upsertAll(normalized)
                settingsDao.upsert(settings.toEntity())
            }
        }

        override suspend fun writeLocalSnapshot(jsonContent: String) {
            snapshotStore.writeSnapshot(jsonContent)
        }
    }

    private val metaStore = object : SyncMetaStore {
        override val deviceId: String get() = syncPreferences.deviceId
        override val localRevision: Long get() = syncPreferences.localRevision
        override val googleEmail: String? get() = syncPreferences.googleEmail
        override var driveAppDataFileId: String?
            get() = syncPreferences.driveAppDataFileId
            set(value) { syncPreferences.driveAppDataFileId = value }
        override var driveVisibleFolderId: String?
            get() = syncPreferences.driveVisibleFolderId
            set(value) { syncPreferences.driveVisibleFolderId = value }
        override var driveVisibleFileId: String?
            get() = syncPreferences.driveVisibleFileId
            set(value) { syncPreferences.driveVisibleFileId = value }
        override val safBackupUri: String? get() = syncPreferences.safBackupUri
        override var cachedAccessToken: String?
            get() = syncPreferences.cachedAccessToken
            set(value) { syncPreferences.cachedAccessToken = value }

        override fun hasPendingSync(): Boolean = syncPreferences.hasPendingSync()
        override fun markSyncing() = syncPreferences.markSyncing()
        override fun markSynced(syncedRevision: Long, timestampMs: Long) =
            syncPreferences.markSynced(syncedRevision, timestampMs)
        override fun markOfflineQueued(errorMsg: String?) = syncPreferences.markOfflineQueued(errorMsg)
        override fun markAuthRequired(errorMsg: String?, needsSetupHelp: Boolean) =
            syncPreferences.markAuthRequired(errorMsg, needsSetupHelp)
        override fun markError(errorMsg: String) = syncPreferences.markError(errorMsg)
    }

    val syncEngine = DriveSyncEngine(
        backingStore = backingStore,
        metaStore = metaStore,
        tokenProvider = authManager,
        driveClient = driveClient,
        connectivityChecker = connectivityChecker,
        safWriter = safWriter
    )

    val activeShiftsFlow: Flow<List<ShiftRecord>> =
        shiftDao.observeActiveShifts().map { list -> list.map { it.toDomain() } }

    val settingsFlow: Flow<AppSettings> =
        settingsDao.observeSettings().map { it?.toDomain() ?: AppSettings() }

    val syncStateFlow: StateFlow<SyncState> = syncPreferences.syncState

    suspend fun getActiveShifts(): List<ShiftRecord> =
        shiftDao.getActiveShifts().map { it.toDomain() }

    suspend fun getAllShiftsIncludingDeleted(): List<ShiftRecord> =
        shiftDao.getAllShiftsIncludingDeleted().map { it.toDomain() }

    suspend fun getSettings(): AppSettings =
        settingsDao.getSettings()?.toDomain() ?: AppSettings()

    suspend fun upsertShift(record: ShiftRecord): ShiftRecord {
        val now = System.currentTimeMillis()
        val withUuid = record.copy(
            uuid = record.uuid.ifBlank { UUID.randomUUID().toString() },
            createdAtEpochMs = if (record.createdAtEpochMs > 0L) record.createdAtEpochMs else now,
            updatedAtEpochMs = now,
            deletedAtEpochMs = null
        )
        val newId = shiftDao.upsert(withUuid.toEntity())
        val saved = withUuid.copy(id = if (withUuid.id > 0L) withUuid.id else newId)
        onLocalMutation()
        return saved
    }

    suspend fun softDeleteShift(id: Long) {
        val now = System.currentTimeMillis()
        shiftDao.softDeleteById(id = id, deletedAt = now, updatedAt = now)
        onLocalMutation()
    }

    suspend fun restoreSoftDeletedShift(id: Long) {
        val now = System.currentTimeMillis()
        shiftDao.restoreById(id = id, updatedAt = now)
        onLocalMutation()
    }

    suspend fun updateSettings(settings: AppSettings) {
        val stamped = settings.copy(updatedAtEpochMs = System.currentTimeMillis())
        settingsDao.upsert(stamped.toEntity())
        onLocalMutation()
    }

    suspend fun clearAllData() {
        val now = System.currentTimeMillis()
        db.withTransaction {
            shiftDao.softDeleteAllActive(deletedAt = now, updatedAt = now)
            settingsDao.upsert(AppSettings(updatedAtEpochMs = now).toEntity())
        }
        onLocalMutation()
    }

    suspend fun importJsonBackup(rawJson: String, replaceExisting: Boolean = false): Int {
        val imported = ImportExportEngine.importBackupJson(rawJson)
        val now = System.currentTimeMillis()
        db.withTransaction {
            if (replaceExisting) {
                shiftDao.softDeleteAllActive(deletedAt = now, updatedAt = now)
            }
            val existingByUuid = shiftDao.getAllShiftsIncludingDeleted().associateBy { it.uuid }
            val entities = imported.shifts.map { s ->
                val existing = existingByUuid[s.uuid]
                s.copy(
                    id = existing?.id ?: 0L,
                    updatedAtEpochMs = maxOf(s.updatedAtEpochMs, now)
                ).toEntity()
            }
            shiftDao.upsertAll(entities)
            settingsDao.upsert(imported.settings.copy(updatedAtEpochMs = now).toEntity())
        }
        onLocalMutation()
        return imported.shifts.size
    }

    suspend fun exportBackupJsonString(): String {
        val allShifts = getAllShiftsIncludingDeleted()
        val settings = getSettings()
        return ImportExportEngine.exportBackupJson(
            shifts = allShifts,
            settings = settings,
            deviceId = syncPreferences.deviceId,
            revision = syncPreferences.localRevision
        )
    }

    suspend fun onGoogleAuthorized(outcome: AuthOutcome.Authorized): SyncRunResult {
        syncPreferences.cachedAccessToken = outcome.accessToken
        syncPreferences.setGoogleAccount(
            email = outcome.email ?: "Google Акаунт",
            name = outcome.name
        )
        syncPreferences.incrementLocalRevision()
        return runCloudSync(ignoreWifiOnly = true)
    }

    suspend fun signOutGoogle() {
        val token = syncPreferences.cachedAccessToken
        syncPreferences.clearGoogleAccount()
        authManager.revokeTokenBestEffort(token)
    }

    suspend fun linkSafBackup(uriString: String, displayName: String): SyncRunResult {
        syncPreferences.setSafBackup(uriString, displayName)
        syncPreferences.incrementLocalRevision()
        return runCloudSync(ignoreWifiOnly = true)
    }

    fun unlinkSafBackup() {
        syncPreferences.setSafBackup(null, null)
    }

    suspend fun restoreFromSaf(uriString: String): Result<Int> {
        val textRes = safWriter.readBackupFromSaf(uriString)
        val rawJson = textRes.getOrElse { return Result.failure(it) }
        return runCatching { importJsonBackup(rawJson, replaceExisting = true) }
    }

    suspend fun runCloudSync(ignoreWifiOnly: Boolean = false): SyncRunResult {
        return syncEngine.syncNow(ignoreWifiOnlyConstraint = ignoreWifiOnly)
    }

    suspend fun restoreFromGoogleDrive(): SyncRunResult {
        return syncEngine.restoreFromDrive()
    }

    suspend fun listDriveFolders(parentId: String = "root") =
        syncEngine.listDriveFolders(parentId)

    suspend fun createDriveFolder(parentId: String, folderName: String) =
        syncEngine.createDriveFolder(parentId, folderName)

    suspend fun selectDriveFolder(folderId: String, folderPath: String): SyncRunResult {
        syncPreferences.setSelectedDriveFolder(folderId, folderPath)
        syncPreferences.incrementLocalRevision()
        return runCloudSync(ignoreWifiOnly = true)
    }

    private suspend fun onLocalMutation() {
        val rev = syncPreferences.incrementLocalRevision()
        val allShifts = getAllShiftsIncludingDeleted()
        val settings = getSettings()
        val json = ImportExportEngine.exportBackupJson(
            shifts = allShifts,
            settings = settings,
            deviceId = syncPreferences.deviceId,
            revision = rev
        )
        snapshotStore.writeSnapshot(json)

        if (settings.autoSyncEnabled && syncPreferences.hasAnyCloudTarget()) {
            DriveSyncWorker.enqueue(context, settings.syncWifiOnly)
            if (connectivityChecker.isNetworkAvailable()) {
                ioScope.launch {
                    syncEngine.syncNow(ignoreWifiOnlyConstraint = false)
                }
            }
        }
    }
}
