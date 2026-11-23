package ua.robochyichas.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.PauseInterval
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.domain.model.ThemeMode

@Entity(
    tableName = "shifts",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["date"])
    ]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val date: String,
    val entryType: String,
    val start: String,
    val end: String,
    val normMinutes: Int,
    val pausesJson: String,
    val manualPauseMinutes: Int,
    val overtimeDeltaMinutes: Int,
    val note: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val firstWorkMonth: String,
    val carryOverCapMinutes: Int,
    val initialBalanceMinutes: Int,
    val defaultNormMinutes: Int,
    val defaultPause1Min: Int,
    val defaultPause2Min: Int,
    val defaultPause3Min: Int,
    val themeMode: String,
    val autoSyncEnabled: Boolean,
    val syncWifiOnly: Boolean,
    val updatedAtEpochMs: Long
)

object ShiftMappers {
    private val gson = Gson()
    private val pauseListType = object : TypeToken<List<PauseInterval>>() {}.type

    fun pausesToJson(pauses: List<PauseInterval>): String = gson.toJson(pauses)

    fun jsonToPauses(json: String): List<PauseInterval> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<PauseInterval>>(json, pauseListType) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun ShiftEntity.toDomain(): ShiftRecord = ShiftRecord(
        id = id,
        uuid = uuid,
        date = date,
        entryType = runCatching { EntryType.valueOf(entryType) }.getOrDefault(EntryType.SHIFT),
        start = start,
        end = end,
        normMinutes = normMinutes,
        pauses = jsonToPauses(pausesJson),
        manualPauseMinutes = manualPauseMinutes,
        overtimeDeltaMinutes = overtimeDeltaMinutes,
        note = note,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs
    )

    fun ShiftRecord.toEntity(): ShiftEntity = ShiftEntity(
        id = id,
        uuid = uuid,
        date = date,
        entryType = entryType.name,
        start = start,
        end = end,
        normMinutes = normMinutes,
        pausesJson = pausesToJson(pauses),
        manualPauseMinutes = manualPauseMinutes,
        overtimeDeltaMinutes = overtimeDeltaMinutes,
        note = note,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs
    )

    fun SettingsEntity.toDomain(): AppSettings = AppSettings(
        firstWorkMonth = firstWorkMonth,
        carryOverCapMinutes = carryOverCapMinutes,
        initialBalanceMinutes = initialBalanceMinutes,
        defaultNormMinutes = defaultNormMinutes,
        defaultPause1Min = defaultPause1Min,
        defaultPause2Min = defaultPause2Min,
        defaultPause3Min = defaultPause3Min,
        themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.DARK),
        autoSyncEnabled = autoSyncEnabled,
        syncWifiOnly = syncWifiOnly,
        updatedAtEpochMs = updatedAtEpochMs
    )

    fun AppSettings.toEntity(): SettingsEntity = SettingsEntity(
        id = 1,
        firstWorkMonth = firstWorkMonth,
        carryOverCapMinutes = carryOverCapMinutes,
        initialBalanceMinutes = initialBalanceMinutes,
        defaultNormMinutes = defaultNormMinutes,
        defaultPause1Min = defaultPause1Min,
        defaultPause2Min = defaultPause2Min,
        defaultPause3Min = defaultPause3Min,
        themeMode = themeMode.name,
        autoSyncEnabled = autoSyncEnabled,
        syncWifiOnly = syncWifiOnly,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

class RoomTypeConverters {
    @TypeConverter
    fun fromPauseList(pauses: List<PauseInterval>): String = ShiftMappers.pausesToJson(pauses)

    @TypeConverter
    fun toPauseList(json: String): List<PauseInterval> = ShiftMappers.jsonToPauses(json)
}
