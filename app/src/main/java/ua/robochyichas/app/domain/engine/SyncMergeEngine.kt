package ua.robochyichas.app.domain.engine

import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ShiftRecord

object SyncMergeEngine {

    data class MergeResult(
        val mergedShifts: List<ShiftRecord>,
        val mergedSettings: AppSettings,
        val localChanged: Boolean,
        val remoteChanged: Boolean
    )

    private fun ShiftRecord.sameContentAs(other: ShiftRecord): Boolean =
        uuid == other.uuid &&
            date == other.date &&
            entryType == other.entryType &&
            start == other.start &&
            end == other.end &&
            normMinutes == other.normMinutes &&
            pauses == other.pauses &&
            manualPauseMinutes == other.manualPauseMinutes &&
            overtimeDeltaMinutes == other.overtimeDeltaMinutes &&
            note == other.note &&
            createdAtEpochMs == other.createdAtEpochMs &&
            updatedAtEpochMs == other.updatedAtEpochMs &&
            deletedAtEpochMs == other.deletedAtEpochMs

    fun merge(
        localShifts: List<ShiftRecord>,
        localSettings: AppSettings,
        remoteShifts: List<ShiftRecord>,
        remoteSettings: AppSettings
    ): MergeResult {
        val localByUuid = localShifts.filter { it.uuid.isNotBlank() }.associateBy { it.uuid }
        val remoteByUuid = remoteShifts.filter { it.uuid.isNotBlank() }.associateBy { it.uuid }
        val allUuids = (localByUuid.keys + remoteByUuid.keys).sorted()

        val merged = ArrayList<ShiftRecord>(allUuids.size)
        var localChanged = false
        var remoteChanged = false

        for (uuid in allUuids) {
            val local = localByUuid[uuid]
            val remote = remoteByUuid[uuid]
            when {
                local != null && remote == null -> {
                    merged.add(local)
                    remoteChanged = true
                }
                local == null && remote != null -> {
                    // Preserve incoming record with local id = 0 so Room assigns auto-increment id
                    merged.add(remote.copy(id = 0L))
                    localChanged = true
                }
                local != null && remote != null -> {
                    val winner = when {
                        remote.updatedAtEpochMs > local.updatedAtEpochMs -> {
                            val resolved = remote.copy(id = local.id)
                            if (!local.sameContentAs(resolved)) localChanged = true
                            resolved
                        }
                        local.updatedAtEpochMs > remote.updatedAtEpochMs -> {
                            if (!local.sameContentAs(remote)) remoteChanged = true
                            local
                        }
                        else -> {
                            // Equal timestamp: if one is deleted tombstone, prefer tombstone for safety
                            when {
                                remote.deletedAtEpochMs != null && local.deletedAtEpochMs == null -> {
                                    localChanged = true
                                    remote.copy(id = local.id)
                                }
                                local.deletedAtEpochMs != null && remote.deletedAtEpochMs == null -> {
                                    remoteChanged = true
                                    local
                                }
                                else -> local
                            }
                        }
                    }
                    merged.add(winner)
                }
            }
        }

        val mergedSettings = when {
            remoteSettings.updatedAtEpochMs > localSettings.updatedAtEpochMs -> {
                if (remoteSettings != localSettings) localChanged = true
                remoteSettings
            }
            localSettings.updatedAtEpochMs > remoteSettings.updatedAtEpochMs -> {
                if (remoteSettings != localSettings) remoteChanged = true
                localSettings
            }
            else -> localSettings
        }

        return MergeResult(
            mergedShifts = merged,
            mergedSettings = mergedSettings,
            localChanged = localChanged,
            remoteChanged = remoteChanged
        )
    }
}
