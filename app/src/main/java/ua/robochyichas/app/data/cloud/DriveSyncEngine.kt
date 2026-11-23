package ua.robochyichas.app.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.robochyichas.app.domain.engine.ImportExportEngine
import ua.robochyichas.app.domain.engine.SyncMergeEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ShiftRecord
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

interface SyncBackingStore {
    suspend fun getAllShiftsIncludingDeleted(): List<ShiftRecord>
    suspend fun getSettings(): AppSettings
    suspend fun saveMergedState(shifts: List<ShiftRecord>, settings: AppSettings)
    suspend fun writeLocalSnapshot(jsonContent: String)
}

interface SyncMetaStore {
    val deviceId: String
    val localRevision: Long
    val googleEmail: String?
    var driveAppDataFileId: String?
    var driveVisibleFolderId: String?
    var driveVisibleFileId: String?
    val safBackupUri: String?
    var cachedAccessToken: String?
    fun hasPendingSync(): Boolean
    fun markSyncing()
    fun markSynced(syncedRevision: Long, timestampMs: Long)
    fun markOfflineQueued(errorMsg: String? = null)
    fun markAuthRequired(errorMsg: String?, needsSetupHelp: Boolean = false)
    fun markError(errorMsg: String)
}

interface ConnectivityChecker {
    fun isNetworkAvailable(): Boolean
    fun isUnmeteredNetwork(): Boolean
}

interface SafBackupWriter {
    suspend fun writeBackupToSaf(uriString: String, jsonContent: String): Result<Unit>
    suspend fun readBackupFromSaf(uriString: String): Result<String>
}

class AndroidConnectivityChecker(private val context: Context) : ConnectivityChecker {
    override fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun isUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

class AndroidSafBackupWriter(private val context: Context) : SafBackupWriter {
    override suspend fun writeBackupToSaf(uriString: String, jsonContent: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val pfd = context.contentResolver.openFileDescriptor(uri, "wt")
                    ?: error("Не вдалося відкрити файл для запису")
                pfd.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).use { fos ->
                        fos.write(jsonContent.toByteArray(StandardCharsets.UTF_8))
                        fos.flush()
                        runCatching { fos.fd.sync() }
                    }
                    Unit
                }
            }
        }

    override suspend fun readBackupFromSaf(uriString: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use {
                    it.readText()
                } ?: error("Не вдалося прочитати файл")
            }
        }
}

sealed class SyncRunResult {
    data class Success(val pulledRemoteChanges: Boolean, val uploadedChanges: Boolean) : SyncRunResult()
    data class OfflineQueued(val reason: String) : SyncRunResult()
    data class AuthRequired(val message: String, val setupHelp: Boolean) : SyncRunResult()
    data class RetryableError(val message: String) : SyncRunResult()
}

class DriveSyncEngine(
    private val backingStore: SyncBackingStore,
    private val metaStore: SyncMetaStore,
    private val tokenProvider: AccessTokenProvider,
    private val driveClient: GoogleDriveApiClient,
    private val connectivityChecker: ConnectivityChecker,
    private val safWriter: SafBackupWriter,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val syncMutex = Mutex()

    suspend fun syncNow(ignoreWifiOnlyConstraint: Boolean = false): SyncRunResult = syncMutex.withLock {
        val localShifts = backingStore.getAllShiftsIncludingDeleted()
        val localSettings = backingStore.getSettings()
        val capturedRevision = metaStore.localRevision
        val safUri = metaStore.safBackupUri
        val googleEmail = metaStore.googleEmail

        if (googleEmail.isNullOrBlank() && safUri.isNullOrBlank()) {
            return@withLock SyncRunResult.Success(pulledRemoteChanges = false, uploadedChanges = false)
        }

        // Write SAF backup first (Android Drive DocumentsProvider queues offline writes natively!)
        var safSucceeded = false
        var safError: String? = null
        if (!safUri.isNullOrBlank()) {
            val snapshotJson = ImportExportEngine.exportBackupJson(
                shifts = localShifts,
                settings = localSettings,
                deviceId = metaStore.deviceId,
                revision = capturedRevision,
                exportedAtEpochMs = nowProvider()
            )
            backingStore.writeLocalSnapshot(snapshotJson)
            val safRes = safWriter.writeBackupToSaf(safUri, snapshotJson)
            if (safRes.isSuccess) {
                safSucceeded = true
            } else {
                safError = safRes.exceptionOrNull()?.message
            }
        }

        if (googleEmail.isNullOrBlank()) {
            return@withLock if (safSucceeded) {
                metaStore.markSynced(capturedRevision, nowProvider())
                SyncRunResult.Success(pulledRemoteChanges = false, uploadedChanges = true)
            } else {
                val msg = safError ?: "Не вдалося записати прив'язаний файл"
                metaStore.markError(msg)
                SyncRunResult.RetryableError(msg)
            }
        }

        if (!connectivityChecker.isNetworkAvailable()) {
            val msg = "Немає інтернету — зміни в черзі на збереження"
            metaStore.markOfflineQueued(msg)
            return@withLock SyncRunResult.OfflineQueued(msg)
        }

        if (!ignoreWifiOnlyConstraint && localSettings.syncWifiOnly && !connectivityChecker.isUnmeteredNetwork()) {
            val msg = "Очікування Wi-Fi для синхронізації"
            metaStore.markOfflineQueued(msg)
            return@withLock SyncRunResult.OfflineQueued(msg)
        }

        metaStore.markSyncing()

        return@withLock try {
            val authOutcome = tokenProvider.getAccessTokenSilently()
            when (authOutcome) {
                is AuthOutcome.Authorized -> {
                    metaStore.cachedAccessToken = authOutcome.accessToken
                    performDriveSyncWithRetryOn401(
                        initialToken = authOutcome.accessToken,
                        localShifts = localShifts,
                        localSettings = localSettings,
                        capturedRevision = capturedRevision,
                        safUri = safUri
                    )
                }
                is AuthOutcome.NeedsResolution -> {
                    val msg = "Потрібно повторно увійти в Google Диск"
                    metaStore.markAuthRequired(msg, needsSetupHelp = false)
                    SyncRunResult.AuthRequired(msg, setupHelp = false)
                }
                is AuthOutcome.SetupRequired -> {
                    metaStore.markAuthRequired(authOutcome.message, needsSetupHelp = true)
                    SyncRunResult.AuthRequired(authOutcome.message, setupHelp = true)
                }
                is AuthOutcome.Failed -> {
                    if (!connectivityChecker.isNetworkAvailable()) {
                        metaStore.markOfflineQueued("Немає інтернету — зміни в черзі")
                        SyncRunResult.OfflineQueued("Немає інтернету — зміни в черзі")
                    } else {
                        metaStore.markError(authOutcome.message)
                        SyncRunResult.RetryableError(authOutcome.message)
                    }
                }
            }
        } catch (e: DriveNetworkException) {
            val msg = "Збій мережі — автоматично повторимо при відновленні зв'язку"
            metaStore.markOfflineQueued(msg)
            SyncRunResult.OfflineQueued(msg)
        } catch (e: DriveUnauthorizedException) {
            val msg = "Потрібна повторна авторизація Google Диску"
            metaStore.markAuthRequired(msg, needsSetupHelp = false)
            SyncRunResult.AuthRequired(msg, setupHelp = false)
        } catch (e: Exception) {
            val msg = e.message ?: "Помилка синхронізації з Google Диском"
            metaStore.markError(msg)
            SyncRunResult.RetryableError(msg)
        }
    }

    private suspend fun performDriveSyncWithRetryOn401(
        initialToken: String,
        localShifts: List<ShiftRecord>,
        localSettings: AppSettings,
        capturedRevision: Long,
        safUri: String?
    ): SyncRunResult {
        return try {
            performDriveSync(initialToken, localShifts, localSettings, capturedRevision, safUri)
        } catch (_: DriveUnauthorizedException) {
            when (val retryAuth = tokenProvider.getAccessTokenSilently()) {
                is AuthOutcome.Authorized -> {
                    metaStore.cachedAccessToken = retryAuth.accessToken
                    performDriveSync(retryAuth.accessToken, localShifts, localSettings, capturedRevision, safUri)
                }
                is AuthOutcome.NeedsResolution -> {
                    val msg = "Потрібне підтвердження входу в Google Диск"
                    metaStore.markAuthRequired(msg, needsSetupHelp = false)
                    SyncRunResult.AuthRequired(msg, setupHelp = false)
                }
                is AuthOutcome.SetupRequired -> {
                    metaStore.markAuthRequired(retryAuth.message, needsSetupHelp = true)
                    SyncRunResult.AuthRequired(retryAuth.message, setupHelp = true)
                }
                is AuthOutcome.Failed -> {
                    metaStore.markError(retryAuth.message)
                    SyncRunResult.RetryableError(retryAuth.message)
                }
            }
        }
    }

    private suspend fun performDriveSync(
        token: String,
        localShifts: List<ShiftRecord>,
        localSettings: AppSettings,
        capturedRevision: Long,
        safUri: String?
    ): SyncRunResult {
        // 1. Locate existing canonical sync file in hidden appDataFolder
        val appDataFile = metaStore.driveAppDataFileId?.let { id ->
            DriveFileMetadata(id = id, name = APPDATA_FILE_NAME)
        } ?: driveClient.findFileInAppData(token, APPDATA_FILE_NAME)?.also {
            metaStore.driveAppDataFileId = it.id
        }

        // 2. If not found in appData, also check visible backup folder on first link
        var remotePayloadJson: String? = null
        if (appDataFile != null) {
            remotePayloadJson = try {
                driveClient.downloadFile(token, appDataFile.id)
            } catch (_: DriveNotFoundException) {
                metaStore.driveAppDataFileId = null
                null
            }
        } else {
            val folderId = metaStore.driveVisibleFolderId ?: driveClient.findVisibleFolder(token, VISIBLE_FOLDER_NAME)?.id?.also {
                metaStore.driveVisibleFolderId = it
            }
            if (folderId != null) {
                val visibleFile = driveClient.findVisibleFileInFolder(token, folderId, VISIBLE_FILE_NAME)?.also {
                    metaStore.driveVisibleFileId = it.id
                }
                if (visibleFile != null) {
                    remotePayloadJson = runCatching {
                        driveClient.downloadFile(token, visibleFile.id)
                    }.getOrNull()
                }
            }
        }

        var mergedShifts = localShifts
        var mergedSettings = localSettings
        var pulledRemoteChanges = false
        var shouldUpload = metaStore.hasPendingSync() || appDataFile == null || metaStore.driveVisibleFileId == null

        if (!remotePayloadJson.isNullOrBlank()) {
            val importedRemote = runCatching {
                ImportExportEngine.importBackupJson(remotePayloadJson, nowProvider())
            }.getOrNull()

            if (importedRemote != null) {
                val mergeRes = SyncMergeEngine.merge(
                    localShifts = localShifts,
                    localSettings = localSettings,
                    remoteShifts = importedRemote.shifts,
                    remoteSettings = importedRemote.settings
                )
                mergedShifts = mergeRes.mergedShifts
                mergedSettings = mergeRes.mergedSettings
                pulledRemoteChanges = mergeRes.localChanged
                if (mergeRes.remoteChanged) {
                    shouldUpload = true
                }
                if (mergeRes.localChanged) {
                    backingStore.saveMergedState(mergedShifts, mergedSettings)
                }
            }
        }

        val exportJson = ImportExportEngine.exportBackupJson(
            shifts = mergedShifts,
            settings = mergedSettings,
            deviceId = metaStore.deviceId,
            revision = capturedRevision,
            exportedAtEpochMs = nowProvider()
        )
        backingStore.writeLocalSnapshot(exportJson)

        var uploadedChanges = false
        if (shouldUpload) {
            // Upload to private appDataFolder (create or patch in-place)
            val updatedAppData = try {
                driveClient.uploadOrUpdateJsonFile(
                    accessToken = token,
                    existingFileId = metaStore.driveAppDataFileId,
                    fileName = APPDATA_FILE_NAME,
                    parentIds = listOf("appDataFolder"),
                    jsonContent = exportJson
                )
            } catch (_: DriveNotFoundException) {
                metaStore.driveAppDataFileId = null
                driveClient.uploadOrUpdateJsonFile(
                    accessToken = token,
                    existingFileId = null,
                    fileName = APPDATA_FILE_NAME,
                    parentIds = listOf("appDataFolder"),
                    jsonContent = exportJson
                )
            }
            metaStore.driveAppDataFileId = updatedAppData.id

            // Also mirror to user-visible folder "Робочий час/work_time_backup.json"
            val folderId = metaStore.driveVisibleFolderId ?: driveClient.ensureVisibleFolder(token, VISIBLE_FOLDER_NAME).id.also {
                metaStore.driveVisibleFolderId = it
            }
            val existingVisibleFileId = metaStore.driveVisibleFileId ?: driveClient.findVisibleFileInFolder(
                token, folderId, VISIBLE_FILE_NAME
            )?.id?.also { metaStore.driveVisibleFileId = it }

            val updatedVisible = try {
                driveClient.uploadOrUpdateJsonFile(
                    accessToken = token,
                    existingFileId = existingVisibleFileId,
                    fileName = VISIBLE_FILE_NAME,
                    parentIds = listOf(folderId),
                    jsonContent = exportJson
                )
            } catch (_: DriveNotFoundException) {
                metaStore.driveVisibleFileId = null
                try {
                    driveClient.uploadOrUpdateJsonFile(
                        accessToken = token,
                        existingFileId = null,
                        fileName = VISIBLE_FILE_NAME,
                        parentIds = listOf(folderId),
                        jsonContent = exportJson
                    )
                } catch (_: DriveNotFoundException) {
                    val fallbackFolderId = driveClient.ensureVisibleFolder(token, VISIBLE_FOLDER_NAME).id
                    metaStore.driveVisibleFolderId = fallbackFolderId
                    driveClient.uploadOrUpdateJsonFile(
                        accessToken = token,
                        existingFileId = null,
                        fileName = VISIBLE_FILE_NAME,
                        parentIds = listOf(fallbackFolderId),
                        jsonContent = exportJson
                    )
                }
            }
            metaStore.driveVisibleFileId = updatedVisible.id

            if (!safUri.isNullOrBlank() && pulledRemoteChanges) {
                safWriter.writeBackupToSaf(safUri, exportJson)
            }
            uploadedChanges = true
        }

        metaStore.markSynced(capturedRevision, nowProvider())
        return SyncRunResult.Success(pulledRemoteChanges = pulledRemoteChanges, uploadedChanges = uploadedChanges)
    }

    suspend fun restoreFromDrive(): SyncRunResult = syncMutex.withLock {
        if (!connectivityChecker.isNetworkAvailable()) {
            val msg = "Немає інтернету для відновлення з Google Диску"
            metaStore.markOfflineQueued(msg)
            return@withLock SyncRunResult.OfflineQueued(msg)
        }
        val authOutcome = tokenProvider.getAccessTokenSilently()
        if (authOutcome !is AuthOutcome.Authorized) {
            return@withLock when (authOutcome) {
                is AuthOutcome.NeedsResolution -> {
                    metaStore.markAuthRequired("Потрібен вхід у Google Диск", needsSetupHelp = false)
                    SyncRunResult.AuthRequired("Потрібен вхід у Google Диск", setupHelp = false)
                }
                is AuthOutcome.SetupRequired -> {
                    metaStore.markAuthRequired(authOutcome.message, needsSetupHelp = true)
                    SyncRunResult.AuthRequired(authOutcome.message, setupHelp = true)
                }
                is AuthOutcome.Failed -> {
                    metaStore.markError(authOutcome.message)
                    SyncRunResult.RetryableError(authOutcome.message)
                }
                else -> SyncRunResult.RetryableError("Помилка авторизації")
            }
        }

        metaStore.markSyncing()
        return@withLock try {
            val token = authOutcome.accessToken
            // Save local safety snapshot before replacing
            val localShifts = backingStore.getAllShiftsIncludingDeleted()
            val localSettings = backingStore.getSettings()
            val safetyJson = ImportExportEngine.exportBackupJson(
                shifts = localShifts,
                settings = localSettings,
                deviceId = metaStore.deviceId,
                revision = metaStore.localRevision,
                exportedAtEpochMs = nowProvider()
            )
            backingStore.writeLocalSnapshot(safetyJson)

            val appDataMeta = driveClient.findFileInAppData(token, APPDATA_FILE_NAME)
            val remoteJson = if (appDataMeta != null) {
                metaStore.driveAppDataFileId = appDataMeta.id
                driveClient.downloadFile(token, appDataMeta.id)
            } else {
                val folderId = metaStore.driveVisibleFolderId ?: driveClient.findVisibleFolder(token, VISIBLE_FOLDER_NAME)?.id?.also {
                    metaStore.driveVisibleFolderId = it
                }
                val visibleFile = folderId?.let { driveClient.findVisibleFileInFolder(token, it, VISIBLE_FILE_NAME) }
                visibleFile?.let {
                    metaStore.driveVisibleFileId = it.id
                    driveClient.downloadFile(token, it.id)
                }
            }

            if (remoteJson.isNullOrBlank()) {
                val msg = "На Google Диску ще немає збереженої резервної копії"
                metaStore.markError(msg)
                return@withLock SyncRunResult.RetryableError(msg)
            }

            val imported = ImportExportEngine.importBackupJson(remoteJson, nowProvider())
            backingStore.saveMergedState(imported.shifts, imported.settings)
            val newJson = ImportExportEngine.exportBackupJson(
                shifts = imported.shifts,
                settings = imported.settings,
                deviceId = metaStore.deviceId,
                revision = metaStore.localRevision,
                exportedAtEpochMs = nowProvider()
            )
            backingStore.writeLocalSnapshot(newJson)
            metaStore.markSynced(metaStore.localRevision, nowProvider())
            SyncRunResult.Success(pulledRemoteChanges = true, uploadedChanges = false)
        } catch (e: Exception) {
            val msg = e.message ?: "Помилка відновлення з Google Диску"
            metaStore.markError(msg)
            SyncRunResult.RetryableError(msg)
        }
    }

    suspend fun listDriveFolders(parentId: String = "root"): Result<List<DriveFileMetadata>> =
        executeWithAuth { token ->
            driveClient.listFoldersInParent(accessToken = token, parentId = parentId)
        }

    suspend fun createDriveFolder(parentId: String, folderName: String): Result<DriveFileMetadata> =
        executeWithAuth { token ->
            driveClient.createFolderInParent(
                accessToken = token,
                folderName = folderName.trim(),
                parentId = parentId
            )
        }

    private suspend fun <T> executeWithAuth(block: suspend (token: String) -> T): Result<T> {
        if (!connectivityChecker.isNetworkAvailable()) {
            return Result.failure(IllegalStateException("Немає інтернету"))
        }
        val firstAuth = tokenProvider.getAccessTokenSilently()
        val token = when (firstAuth) {
            is AuthOutcome.Authorized -> {
                metaStore.cachedAccessToken = firstAuth.accessToken
                firstAuth.accessToken
            }
            is AuthOutcome.NeedsResolution ->
                return Result.failure(IllegalStateException("Потрібно повторно увійти в Google Диск"))
            is AuthOutcome.SetupRequired ->
                return Result.failure(IllegalStateException(firstAuth.message))
            is AuthOutcome.Failed ->
                return Result.failure(IllegalStateException(firstAuth.message))
        }
        return try {
            Result.success(block(token))
        } catch (_: DriveUnauthorizedException) {
            when (val retryAuth = tokenProvider.getAccessTokenSilently()) {
                is AuthOutcome.Authorized -> {
                    metaStore.cachedAccessToken = retryAuth.accessToken
                    runCatching { block(retryAuth.accessToken) }
                }
                else -> Result.failure(IllegalStateException("Потрібна повторна авторизація Google"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val APPDATA_FILE_NAME = "work_time_sync.json"
        const val VISIBLE_FOLDER_NAME = "Робочий час"
        const val VISIBLE_FILE_NAME = "work_time_backup.json"
    }
}
