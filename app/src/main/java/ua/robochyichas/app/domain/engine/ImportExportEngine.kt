package ua.robochyichas.app.domain.engine

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.BackupEnvelope
import ua.robochyichas.app.domain.model.ComputedShift
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.PauseInterval
import ua.robochyichas.app.domain.model.ShiftRecord
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object ImportExportEngine {

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create()

    private val compactGson: Gson = GsonBuilder()
        .serializeNulls()
        .create()

    // Legacy v2 format models from Робочий час_2_1.apk
    private data class LegacyBackupV2(
        @SerializedName("firstWorkMonth") val firstWorkMonth: String? = null,
        @SerializedName("carryOverCapMin") val carryOverCapMin: Int? = null,
        @SerializedName("initialBalanceMin") val initialBalanceMin: Int? = null,
        @SerializedName("defaultNorm") val defaultNorm: String? = null,
        @SerializedName("shifts") val shifts: List<LegacyShiftV2>? = null
    )

    private data class LegacyShiftV2(
        @SerializedName("id") val id: Long? = null,
        @SerializedName("date") val date: String? = null,
        @SerializedName("entryType") val entryType: String? = null,
        @SerializedName("overtimeDeltaMin") val overtimeDeltaMin: Int? = null,
        @SerializedName("overNote") val overNote: String? = null,
        @SerializedName("norm") val norm: String? = null,
        @SerializedName("start") val start: String? = null,
        @SerializedName("end") val end: String? = null,
        @SerializedName("p1s") val p1s: String? = null,
        @SerializedName("p1e") val p1e: String? = null,
        @SerializedName("p2s") val p2s: String? = null,
        @SerializedName("p2e") val p2e: String? = null,
        @SerializedName("p3s") val p3s: String? = null,
        @SerializedName("p3e") val p3e: String? = null,
        @SerializedName("manualPause") val manualPause: Int? = null
    )

    data class ImportedBackup(
        val settings: AppSettings,
        val shifts: List<ShiftRecord>,
        val envelope: BackupEnvelope?,
        val sourceSchemaVersion: Int
    )

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalPayloadForHash(settings: AppSettings, shifts: List<ShiftRecord>): String {
        val sortedShifts = shifts.sortedBy { it.uuid }
        return compactGson.toJson(mapOf("settings" to settings, "shifts" to sortedShifts))
    }

    fun buildBackupEnvelope(
        shifts: List<ShiftRecord>,
        settings: AppSettings,
        deviceId: String,
        revision: Long,
        exportedAtEpochMs: Long = System.currentTimeMillis()
    ): BackupEnvelope {
        val checksum = sha256Hex(canonicalPayloadForHash(settings, shifts))
        return BackupEnvelope(
            schemaVersion = 3,
            appVersion = "3.2.0",
            deviceId = deviceId,
            exportedAtEpochMs = exportedAtEpochMs,
            revision = revision,
            sha256 = checksum,
            settings = settings,
            shifts = shifts
        )
    }

    fun exportBackupJson(
        shifts: List<ShiftRecord>,
        settings: AppSettings,
        deviceId: String,
        revision: Long,
        exportedAtEpochMs: Long = System.currentTimeMillis()
    ): String {
        val envelope = buildBackupEnvelope(shifts, settings, deviceId, revision, exportedAtEpochMs)
        return gson.toJson(envelope)
    }

    fun importBackupJson(rawJson: String, nowEpochMs: Long = System.currentTimeMillis()): ImportedBackup {
        val rootObj = try {
            JsonParser.parseString(rawJson).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Невірний формат JSON файлу", e)
        }

        val schemaVersion = rootObj.get("schemaVersion")?.takeIf { !it.isJsonNull }?.asInt ?: 2

        if (schemaVersion >= 3 && rootObj.has("settings") && rootObj.has("shifts")) {
            val env = gson.fromJson(rootObj, BackupEnvelope::class.java)
                ?: throw IllegalArgumentException("Не вдалося прочитати резервну копію v3")

            if (env.sha256.isNotBlank()) {
                val expected = sha256Hex(canonicalPayloadForHash(env.settings, env.shifts))
                if (!expected.equals(env.sha256, ignoreCase = true)) {
                    throw IllegalArgumentException("Контрольна сума SHA-256 не збігається — файл пошкоджений або змінений")
                }
            }

            val normalizedShifts = env.shifts.mapIndexed { idx, s ->
                val safeUuid = s.uuid.ifBlank {
                    UUID.nameUUIDFromBytes("${s.date}|${s.id}|$idx".toByteArray(StandardCharsets.UTF_8)).toString()
                }
                val safeCreated = if (s.createdAtEpochMs > 0L) s.createdAtEpochMs else nowEpochMs
                val safeUpdated = if (s.updatedAtEpochMs > 0L) s.updatedAtEpochMs else safeCreated
                s.copy(
                    uuid = safeUuid,
                    createdAtEpochMs = safeCreated,
                    updatedAtEpochMs = safeUpdated
                )
            }

            return ImportedBackup(
                settings = env.settings,
                shifts = normalizedShifts,
                envelope = env.copy(shifts = normalizedShifts),
                sourceSchemaVersion = env.schemaVersion
            )
        }

        // Fallback: legacy v2 format (`work_time_tracker_clean_v2`)
        val legacy = gson.fromJson(rootObj, LegacyBackupV2::class.java)
            ?: throw IllegalArgumentException("Невідомий формат резервної копії")
        val legacyShifts = legacy.shifts
            ?: throw IllegalArgumentException("У файлі відсутній список змін (shifts)")

        val defaultNormMin = TimeCalcEngine.timeToMinutes(legacy.defaultNorm) ?: 420
        val settings = AppSettings(
            firstWorkMonth = legacy.firstWorkMonth.orEmpty().trim(),
            carryOverCapMinutes = (legacy.carryOverCapMin ?: 2100).coerceAtLeast(0),
            initialBalanceMinutes = legacy.initialBalanceMin ?: 0,
            defaultNormMinutes = defaultNormMin.coerceAtLeast(0),
            updatedAtEpochMs = nowEpochMs
        )

        val convertedShifts = legacyShifts.mapIndexedNotNull { idx, item ->
            val date = item.date?.trim().orEmpty()
            if (date.length < 10) return@mapIndexedNotNull null

            val entryType = when (item.entryType?.lowercase()) {
                "overtime" -> EntryType.OVERTIME
                "vacation" -> EntryType.VACATION
                "sick_leave" -> EntryType.SICK_LEAVE
                "day_off" -> EntryType.DAY_OFF
                else -> EntryType.SHIFT
            }

            val pauses = buildList {
                if (!item.p1s.isNullOrBlank() || !item.p1e.isNullOrBlank()) {
                    add(PauseInterval(item.p1s.orEmpty().trim(), item.p1e.orEmpty().trim()))
                }
                if (!item.p2s.isNullOrBlank() || !item.p2e.isNullOrBlank()) {
                    add(PauseInterval(item.p2s.orEmpty().trim(), item.p2e.orEmpty().trim()))
                }
                if (!item.p3s.isNullOrBlank() || !item.p3e.isNullOrBlank()) {
                    add(PauseInterval(item.p3s.orEmpty().trim(), item.p3e.orEmpty().trim()))
                }
            }

            val normMin = TimeCalcEngine.timeToMinutes(item.norm) ?: defaultNormMin
            val legacyId = item.id ?: (idx + 1L)
            val deterministicUuid = UUID.nameUUIDFromBytes(
                "legacy_v2|$legacyId|$date|${item.entryType}|${item.start}|${item.end}|${item.overtimeDeltaMin}".toByteArray(
                    StandardCharsets.UTF_8
                )
            ).toString()

            ShiftRecord(
                id = 0L,
                uuid = deterministicUuid,
                date = date,
                entryType = entryType,
                start = if (entryType == EntryType.SHIFT) item.start.orEmpty().trim() else "",
                end = if (entryType == EntryType.SHIFT) item.end.orEmpty().trim() else "",
                normMinutes = normMin,
                pauses = if (entryType == EntryType.SHIFT) pauses else emptyList(),
                manualPauseMinutes = if (entryType == EntryType.SHIFT) (item.manualPause ?: 0).coerceAtLeast(0) else 0,
                overtimeDeltaMinutes = if (entryType == EntryType.OVERTIME) (item.overtimeDeltaMin ?: 0) else 0,
                note = item.overNote.orEmpty().trim(),
                createdAtEpochMs = nowEpochMs + idx,
                updatedAtEpochMs = nowEpochMs + idx,
                deletedAtEpochMs = null
            )
        }

        return ImportedBackup(
            settings = settings,
            shifts = convertedShifts,
            envelope = null,
            sourceSchemaVersion = 2
        )
    }

    private fun escapeCsvCell(value: String): String {
        val needsQuotes = value.contains(';') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun exportCsv(computed: List<ComputedShift>): String {
        val headers = listOf(
            "N", "День", "Дата", "Тип", "Початок", "Кінець", "Пауза", "Факт",
            "Норма", "Наднорм щод", "Наднормові", "p1+", "p1-", "p2+", "p2-", "p3+", "p3-", "Примітка"
        )
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Excel
        sb.append(headers.joinToString(";"))
        sb.append('\n')

        for (r in computed) {
            val s = r.shift
            val p1 = s.pauses.getOrNull(0)
            val p2 = s.pauses.getOrNull(1)
            val p3 = s.pauses.getOrNull(2)

            val normFormatted = if (r.isWeekend || s.entryType != EntryType.SHIFT) {
                "0:00:00"
            } else {
                TimeCalcEngine.formatMinutes(r.effectiveNormMinutes, withSeconds = true)
            }

            val row = listOf(
                r.rowNum.toString(),
                r.dayNameUa,
                r.dateUa,
                s.entryType.shortUa,
                s.start,
                s.end,
                TimeCalcEngine.formatMinutes(r.pauseMinutes, withSeconds = true),
                if (r.isComplete) TimeCalcEngine.formatMinutes(r.workedMinutes, withSeconds = true) else "",
                normFormatted,
                if (r.isComplete) TimeCalcEngine.formatMinutes(r.dailyOvertimeMinutes, withSeconds = true) else "",
                TimeCalcEngine.formatMinutes(r.cumulativeOvertimeMinutes, withSeconds = true),
                p1?.start.orEmpty(),
                p1?.end.orEmpty(),
                p2?.start.orEmpty(),
                p2?.end.orEmpty(),
                p3?.start.orEmpty(),
                p3?.end.orEmpty(),
                s.note
            )
            sb.append(row.joinToString(";") { escapeCsvCell(it) })
            sb.append('\n')
        }
        return sb.toString()
    }
}
