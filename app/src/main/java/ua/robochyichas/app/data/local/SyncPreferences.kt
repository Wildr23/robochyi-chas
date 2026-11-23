package ua.robochyichas.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.robochyichas.app.domain.model.SyncState
import ua.robochyichas.app.domain.model.SyncStatus
import java.util.UUID

class SyncPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("robochyi_chas_sync_prefs", Context.MODE_PRIVATE)

    private val _syncState = MutableStateFlow(readState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    @Volatile
    private var isOnlineFlag: Boolean = true

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    var localRevision: Long
        get() = prefs.getLong(KEY_LOCAL_REVISION, 0L)
        private set(value) {
            prefs.edit().putLong(KEY_LOCAL_REVISION, value).apply()
        }

    var lastSyncedRevision: Long
        get() = prefs.getLong(KEY_LAST_SYNCED_REVISION, 0L)
        private set(value) {
            prefs.edit().putLong(KEY_LAST_SYNCED_REVISION, value).apply()
        }

    var googleEmail: String?
        get() = prefs.getString(KEY_GOOGLE_EMAIL, null)
        private set(value) {
            prefs.edit().putString(KEY_GOOGLE_EMAIL, value).apply()
        }

    var googleName: String?
        get() = prefs.getString(KEY_GOOGLE_NAME, null)
        private set(value) {
            prefs.edit().putString(KEY_GOOGLE_NAME, value).apply()
        }

    var driveAppDataFileId: String?
        get() = prefs.getString(KEY_DRIVE_APPDATA_FILE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_DRIVE_APPDATA_FILE_ID, value).apply()
        }

    var driveVisibleFolderId: String?
        get() = prefs.getString(KEY_DRIVE_VISIBLE_FOLDER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_DRIVE_VISIBLE_FOLDER_ID, value).apply()
        }

    var driveVisibleFolderPath: String
        get() = prefs.getString(KEY_DRIVE_VISIBLE_FOLDER_PATH, DEFAULT_DRIVE_FOLDER_PATH) ?: DEFAULT_DRIVE_FOLDER_PATH
        set(value) {
            prefs.edit().putString(KEY_DRIVE_VISIBLE_FOLDER_PATH, value).apply()
        }

    var driveVisibleFileId: String?
        get() = prefs.getString(KEY_DRIVE_VISIBLE_FILE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_DRIVE_VISIBLE_FILE_ID, value).apply()
        }

    var safBackupUri: String?
        get() = prefs.getString(KEY_SAF_BACKUP_URI, null)
        private set(value) {
            prefs.edit().putString(KEY_SAF_BACKUP_URI, value).apply()
        }

    var safBackupDisplayName: String?
        get() = prefs.getString(KEY_SAF_BACKUP_NAME, null)
        private set(value) {
            prefs.edit().putString(KEY_SAF_BACKUP_NAME, value).apply()
        }

    var cachedAccessToken: String?
        get() = prefs.getString(KEY_CACHED_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_CACHED_TOKEN, value).apply()
        }

    fun hasPendingSync(): Boolean = localRevision > lastSyncedRevision

    fun hasAnyCloudTarget(): Boolean = !googleEmail.isNullOrBlank() || !safBackupUri.isNullOrBlank()

    fun incrementLocalRevision(): Long {
        val next = localRevision + 1L
        localRevision = next
        if (hasAnyCloudTarget() && !isOnlineFlag) {
            prefs.edit().putString(KEY_SYNC_STATUS, SyncStatus.OFFLINE_QUEUED.name).apply()
        }
        refreshFlow()
        return next
    }

    fun setGoogleAccount(email: String, name: String?) {
        prefs.edit()
            .putString(KEY_GOOGLE_EMAIL, email)
            .putString(KEY_GOOGLE_NAME, name)
            .putBoolean(KEY_NEEDS_CLOUD_SETUP_HELP, false)
            .apply()
        refreshFlow()
    }

    fun setSelectedDriveFolder(folderId: String, folderPath: String) {
        prefs.edit()
            .putString(KEY_DRIVE_VISIBLE_FOLDER_ID, folderId)
            .putString(KEY_DRIVE_VISIBLE_FOLDER_PATH, folderPath)
            .remove(KEY_DRIVE_VISIBLE_FILE_ID)
            .apply()
        refreshFlow()
    }

    fun clearGoogleAccount() {
        prefs.edit()
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_NAME)
            .remove(KEY_DRIVE_APPDATA_FILE_ID)
            .remove(KEY_DRIVE_VISIBLE_FOLDER_ID)
            .remove(KEY_DRIVE_VISIBLE_FOLDER_PATH)
            .remove(KEY_DRIVE_VISIBLE_FILE_ID)
            .remove(KEY_CACHED_TOKEN)
            .putString(
                KEY_SYNC_STATUS,
                if (!safBackupUri.isNullOrBlank()) SyncStatus.OFFLINE_QUEUED.name else SyncStatus.LOCAL_ONLY.name
            )
            .remove(KEY_LAST_SYNC_ERROR)
            .apply()
        refreshFlow()
    }

    fun setSafBackup(uri: String?, displayName: String?) {
        prefs.edit()
            .putString(KEY_SAF_BACKUP_URI, uri)
            .putString(KEY_SAF_BACKUP_NAME, displayName)
            .apply()
        refreshFlow()
    }

    fun setOnline(online: Boolean) {
        isOnlineFlag = online
        refreshFlow()
    }

    fun markSyncing() {
        prefs.edit()
            .putString(KEY_SYNC_STATUS, SyncStatus.SYNCING.name)
            .remove(KEY_LAST_SYNC_ERROR)
            .apply()
        refreshFlow()
    }

    fun markSynced(syncedRevision: Long, timestampMs: Long) {
        val currentRev = localRevision
        val finalSyncedRev = syncedRevision.coerceAtMost(currentRev)
        prefs.edit()
            .putLong(KEY_LAST_SYNCED_REVISION, finalSyncedRev)
            .putLong(KEY_LAST_SYNC_TS, timestampMs)
            .putString(KEY_SYNC_STATUS, SyncStatus.SYNCED.name)
            .remove(KEY_LAST_SYNC_ERROR)
            .putBoolean(KEY_NEEDS_CLOUD_SETUP_HELP, false)
            .apply()
        refreshFlow()
    }

    fun markOfflineQueued(errorMsg: String? = null) {
        prefs.edit()
            .putString(KEY_SYNC_STATUS, SyncStatus.OFFLINE_QUEUED.name)
            .putString(KEY_LAST_SYNC_ERROR, errorMsg)
            .apply()
        refreshFlow()
    }

    fun markAuthRequired(errorMsg: String?, needsSetupHelp: Boolean = false) {
        prefs.edit()
            .putString(KEY_SYNC_STATUS, SyncStatus.AUTH_REQUIRED.name)
            .putString(KEY_LAST_SYNC_ERROR, errorMsg)
            .putBoolean(KEY_NEEDS_CLOUD_SETUP_HELP, needsSetupHelp)
            .apply()
        refreshFlow()
    }

    fun markError(errorMsg: String) {
        prefs.edit()
            .putString(KEY_SYNC_STATUS, SyncStatus.ERROR.name)
            .putString(KEY_LAST_SYNC_ERROR, errorMsg)
            .apply()
        refreshFlow()
    }

    fun markSetupHelpNeeded(needed: Boolean, errorMsg: String? = null) {
        prefs.edit()
            .putBoolean(KEY_NEEDS_CLOUD_SETUP_HELP, needed)
            .apply {
                if (errorMsg != null) putString(KEY_LAST_SYNC_ERROR, errorMsg)
            }
            .apply()
        refreshFlow()
    }

    private fun refreshFlow() {
        _syncState.value = readState()
    }

    private fun readState(): SyncState {
        val email = prefs.getString(KEY_GOOGLE_EMAIL, null)
        val safName = prefs.getString(KEY_SAF_BACKUP_NAME, null)
        val storedStatus = runCatching {
            SyncStatus.valueOf(prefs.getString(KEY_SYNC_STATUS, SyncStatus.LOCAL_ONLY.name) ?: SyncStatus.LOCAL_ONLY.name)
        }.getOrDefault(SyncStatus.LOCAL_ONLY)

        val pending = (localRevision - lastSyncedRevision).coerceAtLeast(0L).toInt()
        val effectiveStatus = when {
            email.isNullOrBlank() && safName.isNullOrBlank() && storedStatus != SyncStatus.AUTH_REQUIRED -> SyncStatus.LOCAL_ONLY
            pending > 0 && !isOnlineFlag && storedStatus != SyncStatus.SYNCING -> SyncStatus.OFFLINE_QUEUED
            else -> storedStatus
        }

        val lastTs = prefs.getLong(KEY_LAST_SYNC_TS, 0L).takeIf { it > 0L }
        return SyncState(
            status = effectiveStatus,
            googleEmail = email,
            googleName = prefs.getString(KEY_GOOGLE_NAME, null),
            lastSyncTimestampMs = lastTs,
            pendingChangesCount = pending,
            lastError = prefs.getString(KEY_LAST_SYNC_ERROR, null),
            safBackupDisplayName = safName,
            driveFolderPath = driveVisibleFolderPath,
            isOnline = isOnlineFlag,
            needsCloudSetupHelp = prefs.getBoolean(KEY_NEEDS_CLOUD_SETUP_HELP, false)
        )
    }

    companion object {
        private const val DEFAULT_DRIVE_FOLDER_PATH = "Мій Диск / Робочий час"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LOCAL_REVISION = "local_revision"
        private const val KEY_LAST_SYNCED_REVISION = "last_synced_revision"
        private const val KEY_LAST_SYNC_TS = "last_sync_ts"
        private const val KEY_SYNC_STATUS = "sync_status"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_GOOGLE_NAME = "google_name"
        private const val KEY_DRIVE_APPDATA_FILE_ID = "drive_appdata_file_id"
        private const val KEY_DRIVE_VISIBLE_FOLDER_ID = "drive_visible_folder_id"
        private const val KEY_DRIVE_VISIBLE_FOLDER_PATH = "drive_visible_folder_path"
        private const val KEY_DRIVE_VISIBLE_FILE_ID = "drive_visible_file_id"
        private const val KEY_SAF_BACKUP_URI = "saf_backup_uri"
        private const val KEY_SAF_BACKUP_NAME = "saf_backup_name"
        private const val KEY_CACHED_TOKEN = "cached_access_token"
        private const val KEY_NEEDS_CLOUD_SETUP_HELP = "needs_cloud_setup_help"
    }
}
