package ua.robochyichas.app.domain.model

import com.google.gson.annotations.SerializedName

enum class EntryType(val labelUa: String, val shortUa: String) {
    @SerializedName("SHIFT")
    SHIFT("Робоча зміна", "Зміна"),

    @SerializedName("OVERTIME")
    OVERTIME("Лише наднормові", "Наднорм."),

    @SerializedName("VACATION")
    VACATION("Відпустка", "Відпустка"),

    @SerializedName("SICK_LEAVE")
    SICK_LEAVE("Лікарняний", "Лікарн."),

    @SerializedName("DAY_OFF")
    DAY_OFF("Вихідний / Відгул", "Відгул")
}

enum class ThemeMode(val labelUa: String) {
    @SerializedName("DARK")
    DARK("Темна"),

    @SerializedName("LIGHT")
    LIGHT("Світла"),

    @SerializedName("SYSTEM")
    SYSTEM("Авто")
}

data class PauseInterval(
    @SerializedName("start") val start: String = "",
    @SerializedName("end") val end: String = ""
) {
    val isOpen: Boolean get() = start.isNotBlank() && end.isBlank()
    val isComplete: Boolean get() = start.isNotBlank() && end.isNotBlank()
}

data class ShiftRecord(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("uuid") val uuid: String = "",
    @SerializedName("date") val date: String = "",
    @SerializedName("entryType") val entryType: EntryType = EntryType.SHIFT,
    @SerializedName("start") val start: String = "",
    @SerializedName("end") val end: String = "",
    @SerializedName("normMinutes") val normMinutes: Int = 420,
    @SerializedName("pauses") val pauses: List<PauseInterval> = emptyList(),
    @SerializedName("manualPauseMinutes") val manualPauseMinutes: Int = 0,
    @SerializedName("overtimeDeltaMinutes") val overtimeDeltaMinutes: Int = 0,
    @SerializedName("note") val note: String = "",
    @SerializedName("createdAtEpochMs") val createdAtEpochMs: Long = 0L,
    @SerializedName("updatedAtEpochMs") val updatedAtEpochMs: Long = 0L,
    @SerializedName("deletedAtEpochMs") val deletedAtEpochMs: Long? = null
)

data class AppSettings(
    @SerializedName("firstWorkMonth") val firstWorkMonth: String = "",
    @SerializedName("carryOverCapMinutes") val carryOverCapMinutes: Int = 2100, // 35:00
    @SerializedName("initialBalanceMinutes") val initialBalanceMinutes: Int = 0,
    @SerializedName("defaultNormMinutes") val defaultNormMinutes: Int = 420, // 07:00
    @SerializedName("defaultPause1Min") val defaultPause1Min: Int = 15,
    @SerializedName("defaultPause2Min") val defaultPause2Min: Int = 15,
    @SerializedName("defaultPause3Min") val defaultPause3Min: Int = 5,
    @SerializedName("themeMode") val themeMode: ThemeMode = ThemeMode.DARK,
    @SerializedName("autoSyncEnabled") val autoSyncEnabled: Boolean = true,
    @SerializedName("syncWifiOnly") val syncWifiOnly: Boolean = false,
    @SerializedName("updatedAtEpochMs") val updatedAtEpochMs: Long = 0L
)

data class ComputedShift(
    val shift: ShiftRecord,
    val yearMonth: String,
    val rowNum: Int,
    val dayNameUa: String,
    val dateUa: String,
    val isWeekend: Boolean,
    val isOvernight: Boolean,
    val pauseMinutes: Int,
    val workedMinutes: Int,
    val effectiveNormMinutes: Int,
    val dailyOvertimeMinutes: Int,
    val cumulativeOvertimeMinutes: Int,
    val isComplete: Boolean,
    val hasOpenPause: Boolean
)

data class MonthSummary(
    val yearMonth: String,
    val labelFull: String,
    val labelShort: String,
    val shiftCount: Int,
    val workShiftCount: Int,
    val startBalanceMinutes: Int,
    val endBalanceMinutes: Int,
    val monthDeltaMinutes: Int,
    val workedMinutes: Int,
    val pauseMinutes: Int,
    val cappedLostMinutes: Int,
    val overCapMinutes: Int,
    val avgWorkedMinutes: Int,
    val isFirstWorkMonth: Boolean
)

data class TimelineResult(
    val shifts: List<ComputedShift>,
    val monthSummaries: List<MonthSummary>,
    val monthStartBalances: Map<String, Int>,
    val currentBankMinutes: Int
)

enum class SyncStatus {
    LOCAL_ONLY,
    SYNCED,
    OFFLINE_QUEUED,
    SYNCING,
    AUTH_REQUIRED,
    ERROR
}

data class SyncState(
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
    val googleEmail: String? = null,
    val googleName: String? = null,
    val lastSyncTimestampMs: Long? = null,
    val pendingChangesCount: Int = 0,
    val lastError: String? = null,
    val safBackupDisplayName: String? = null,
    val driveFolderPath: String = "Мій Диск / Робочий час",
    val isOnline: Boolean = true,
    val needsCloudSetupHelp: Boolean = false
)

data class BackupEnvelope(
    @SerializedName("schemaVersion") val schemaVersion: Int = 3,
    @SerializedName("appVersion") val appVersion: String = "3.2.1",
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("exportedAtEpochMs") val exportedAtEpochMs: Long = 0L,
    @SerializedName("revision") val revision: Long = 0L,
    @SerializedName("sha256") val sha256: String = "",
    @SerializedName("settings") val settings: AppSettings = AppSettings(),
    @SerializedName("shifts") val shifts: List<ShiftRecord> = emptyList()
)
