package ua.robochyichas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.robochyichas.app.domain.engine.ImportExportEngine
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.PauseInterval
import ua.robochyichas.app.domain.model.ShiftRecord

class ImportExportEngineTest {

    @Test
    fun `legacy v2 backup JSON imports shifts pauses overtime and settings cleanly`() {
        val legacyJson = """
            {
              "firstWorkMonth": "2026-09",
              "carryOverCapMin": 2100,
              "initialBalanceMin": 60,
              "defaultNorm": "07:00",
              "shifts": [
                {
                  "id": 1,
                  "date": "2026-11-10",
                  "entryType": "shift",
                  "norm": "07:00",
                  "start": "08:30",
                  "end": "17:00",
                  "p1s": "10:00", "p1e": "10:15",
                  "p2s": "13:00", "p2e": "13:15",
                  "p3s": "", "p3e": "",
                  "manualPause": 0
                },
                {
                  "id": 2,
                  "date": "2026-11-11",
                  "entryType": "overtime",
                  "overtimeDeltaMin": -90,
                  "overNote": "Відпросився раніше",
                  "norm": "07:00"
                }
              ]
            }
        """.trimIndent()

        val imported = ImportExportEngine.importBackupJson(legacyJson, nowEpochMs = 1000L)
        assertEquals(2, imported.sourceSchemaVersion)
        assertEquals("2026-09", imported.settings.firstWorkMonth)
        assertEquals(2100, imported.settings.carryOverCapMinutes)
        assertEquals(60, imported.settings.initialBalanceMinutes)
        assertEquals(420, imported.settings.defaultNormMinutes)

        assertEquals(2, imported.shifts.size)
        val s1 = imported.shifts[0]
        assertEquals(EntryType.SHIFT, s1.entryType)
        assertEquals("08:30", s1.start)
        assertEquals("17:00", s1.end)
        assertEquals(2, s1.pauses.size)
        assertTrue(s1.uuid.isNotBlank())

        val s2 = imported.shifts[1]
        assertEquals(EntryType.OVERTIME, s2.entryType)
        assertEquals(-90, s2.overtimeDeltaMinutes)
        assertEquals("Відпросився раніше", s2.note)
    }

    @Test
    fun `v3 backup round trip verifies sha256 and rejects tampered JSON`() {
        val settings = AppSettings(firstWorkMonth = "2026-10", carryOverCapMinutes = 1800)
        val shifts = listOf(
            ShiftRecord(
                id = 1L,
                uuid = "uuid-1",
                date = "2026-11-23",
                entryType = EntryType.SHIFT,
                start = "09:00",
                end = "18:00",
                normMinutes = 420,
                pauses = listOf(PauseInterval("12:00", "12:30")),
                note = "Тест; з крапкою з комою",
                createdAtEpochMs = 5000L,
                updatedAtEpochMs = 6000L
            )
        )
        val json = ImportExportEngine.exportBackupJson(
            shifts = shifts,
            settings = settings,
            deviceId = "dev-123",
            revision = 4L,
            exportedAtEpochMs = 7000L
        )
        val imported = ImportExportEngine.importBackupJson(json)
        assertEquals(3, imported.sourceSchemaVersion)
        assertEquals(settings, imported.settings)
        assertEquals(shifts, imported.shifts)

        // Tamper with note while leaving old sha256 untouched
        val tampered = json.replace("09:00", "08:00")
        var failed = false
        try {
            ImportExportEngine.importBackupJson(tampered)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue("Tampered v3 JSON must fail SHA-256 verification", failed)
    }

    @Test
    fun `exportCsv fixes weekday overtime norm bug and includes BOM and escaped notes`() {
        val settings = AppSettings()
        val overtimeWeekday = ShiftRecord(
            id = 1L,
            uuid = "o1",
            date = "2026-11-23", // Monday
            entryType = EntryType.OVERTIME,
            normMinutes = 420,
            overtimeDeltaMinutes = 60,
            note = "Примітка; з \"лапками\""
        )
        val computed = TimeCalcEngine.computeAll(listOf(overtimeWeekday), settings)
        val csv = ImportExportEngine.exportCsv(computed)

        assertTrue(csv.startsWith("\uFEFF"))
        val lines = csv.trim().split("\n")
        assertEquals(2, lines.size)
        // Norm column (index 8) must be 0:00:00 for weekday overtime-only entry!
        assertTrue(lines[1].contains(";Наднорм.;;;0:00:00;0:00:00;0:00:00;1:00:00;1:00:00;"))
        assertTrue(lines[1].contains("\"Примітка; з \"\"лапками\"\"\""))
    }
}
