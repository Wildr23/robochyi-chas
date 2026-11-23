package ua.robochyichas.app

import android.app.Application
import ua.robochyichas.app.data.cloud.ConnectivitySyncMonitor
import ua.robochyichas.app.data.cloud.DriveSyncWorker
import ua.robochyichas.app.data.cloud.GoogleAuthManager
import ua.robochyichas.app.data.local.AppDatabase
import ua.robochyichas.app.data.local.LocalSnapshotStore
import ua.robochyichas.app.data.local.SyncPreferences
import ua.robochyichas.app.data.repository.WorkTimeRepository

class WorkTimeApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var syncPreferences: SyncPreferences
        private set
    lateinit var snapshotStore: LocalSnapshotStore
        private set
    lateinit var authManager: GoogleAuthManager
        private set
    lateinit var repository: WorkTimeRepository
        private set
    lateinit var connectivityMonitor: ConnectivitySyncMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        syncPreferences = SyncPreferences(this)
        snapshotStore = LocalSnapshotStore(this)
        authManager = GoogleAuthManager(this)
        repository = WorkTimeRepository(
            context = this,
            db = database,
            syncPreferences = syncPreferences,
            snapshotStore = snapshotStore,
            authManager = authManager
        )

        connectivityMonitor = ConnectivitySyncMonitor(
            context = this,
            syncPreferences = syncPreferences,
            onNetworkBecameAvailable = {
                val settings = repository.getSettings()
                if (settings.autoSyncEnabled && syncPreferences.hasPendingSync() && syncPreferences.hasAnyCloudTarget()) {
                    DriveSyncWorker.enqueue(this, settings.syncWifiOnly)
                    repository.runCloudSync(ignoreWifiOnly = false)
                }
            }
        )
        connectivityMonitor.start()
    }
}
