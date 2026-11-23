package ua.robochyichas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.PauseInterval
import ua.robochyichas.app.domain.model.ShiftRecord
import java.time.LocalDateTime

class TimeCalcEngineTest {

    private val defaultSettings = AppSettings(
        firstWorkMonth = "",
        carryOverCapMinutes = 2100, // 35:00
        initialBalanceMinutes = 0,
        defaultNormMinutes = 420 // 07:00
    )

    @Test
    fun `timeToMinutes and formatMinutes handle positive negative and invalid inputs`() {
        assertEquals(420, TimeCalcEngine.timeToMinutes("07:00"))
        assertEquals(2100, TimeCalcEngine.timeToMinutes("35:00"))
        assertEquals(-330, TimeCalcEngine.timeToMinutes("-5:30"))
        assertNull(TimeCalcEngine.timeToMinutes(""))
        assertNull(TimeCalcEngine.timeToMinutes("abc"))
        assertNull(TimeCalcEngine.timeToMinutes("12:75"))

        assertEquals("+1:30", TimeCalcEngine.formatMinutes(90, withSeconds = false, showPlus = true))
        assertEquals("-1:30:00", TimeCalcEngine.formatMinutes(-90, withSeconds = true, showPlus = true))
        assertEquals("0:00", TimeCalcEngine.formatMinutes(0, withSeconds = false, showPlus = true))
    }

    @Test
    fun `standard shift calculates pause worked and overtime accurately`() {
        val shift = ShiftRecord(
            id = 1L,
            uuid = "s1",
            date = "2026-11-23", // Monday
            entryType = EntryType.SHIFT,
            start = "08:00",
            end = "17:00",
            normMinutes = 420,
            pauses = listOf(
                PauseInterval("10:00", "10:15"),
                PauseInterval("13:00", "13:30"),
                PauseInterval("15:30", "15:35"),
                PauseInterval("16:15", "16:25") // 4th pause supported!
            )
        )
        val result = TimeCalcEngine.computeTimeline(listOf(shift), defaultSettings)
        assertEquals(1, result.shifts.size)
        val c = result.shifts.first()
        assertTrue(c.isComplete)
        assertFalse(c.isWeekend)
        assertFalse(c.isOvernight)
        assertEquals(60, c.pauseMinutes) // 15 + 30 + 5 + 10
        assertEquals(480, c.workedMinutes) // 9h - 1h = 8h
        assertEquals(420, c.effectiveNormMinutes)
        assertEquals(60, c.dailyOvertimeMinutes)
        assertEquals(60, c.cumulativeOvertimeMinutes)
    }

    @Test
    fun `overnight shift and midnight crossing pause calculate correctly`() {
        val shift = ShiftRecord(
            id = 1L,
            uuid = "night-1",
            date = "2026-11-24", // Tuesday
            entryType = EntryType.SHIFT,
            start = "22:00",
            end = "06:00",
            normMinutes = 420,
            pauses = listOf(
                PauseInterval("23:50", "00:05"), // 15m crossing midnight
                PauseInterval("03:00", "") // open pause ignored until closed
            )
        )
        val result = TimeCalcEngine.computeTimeline(listOf(shift), defaultSettings)
        val c = result.shifts.first()
        assertTrue(c.isComplete)
        assertTrue(c.isOvernight)
        assertTrue(c.hasOpenPause)
        assertEquals(15, c.pauseMinutes)
        assertEquals(465, c.workedMinutes) // 480 - 15
        assertEquals(45, c.dailyOvertimeMinutes)
    }

    @Test
    fun `manual pause fallback applies only when no completed intervals exist and clamps to zero`() {
        val shiftManual = ShiftRecord(
            id = 1L,
            uuid = "m1",
            date = "2026-11-23",
            entryType = EntryType.SHIFT,
            start = "09:00",
            end = "10:00",
            normMinutes = 420,
            pauses = emptyList(),
            manualPauseMinutes = 90 // exceeds 60m shift -> worked clamps to 0
        )
        val c = TimeCalcEngine.computeAll(listOf(shiftManual), defaultSettings).first()
        assertEquals(90, c.pauseMinutes)
        assertEquals(0, c.workedMinutes)
        assertEquals(-420, c.dailyOvertimeMinutes)
    }

    @Test
    fun `weekend norm is zero and overtime-only and status entries behave properly`() {
        val weekendShift = ShiftRecord(
            id = 1L, uuid = "w1", date = "2026-11-21", // Saturday
            entryType = EntryType.SHIFT, start = "09:00", end = "14:00", normMinutes = 420
        )
        val overtimePlus = ShiftRecord(
            id = 2L, uuid = "o1", date = "2026-11-23",
            entryType = EntryType.OVERTIME, overtimeDeltaMinutes = 120, note = "Бонус"
        )
        val overtimeMinus = ShiftRecord(
            id = 3L, uuid = "o2", date = "2026-11-24",
            entryType = EntryType.OVERTIME, overtimeDeltaMinutes = -60, note = "Відгул"
        )
        val vacation = ShiftRecord(
            id = 4L, uuid = "v1", date = "2026-11-25",
            entryType = EntryType.VACATION
        )

        val computed = TimeCalcEngine.computeAll(
            listOf(weekendShift, overtimePlus, overtimeMinus, vacation),
            defaultSettings
        )
        assertEquals(4, computed.size)
        assertTrue(computed[0].isWeekend)
        assertEquals(0, computed[0].effectiveNormMinutes)
        assertEquals(300, computed[0].dailyOvertimeMinutes)

        assertEquals(0, computed[1].workedMinutes)
        assertEquals(0, computed[1].effectiveNormMinutes)
        assertEquals(120, computed[1].dailyOvertimeMinutes)
        assertEquals(420, computed[1].cumulativeOvertimeMinutes)

        assertEquals(-60, computed[2].dailyOvertimeMinutes)
        assertEquals(360, computed[2].cumulativeOvertimeMinutes)

        assertEquals(0, computed[3].workedMinutes)
        assertEquals(0, computed[3].dailyOvertimeMinutes)
        assertEquals(360, computed[3].cumulativeOvertimeMinutes)
    }

    @Test
    fun `month carry over caps at 35h when exceeded and tracks cappedLostMinutes`() {
        // Month 1 (2026-10): accumulate +40:00 (2400 min) across weekend shifts
        val octShifts = listOf(
            ShiftRecord(id = 1, uuid = "oct1", date = "2026-10-03", start = "08:00", end = "20:00", normMinutes = 420), // Sat +12h = 720
            ShiftRecord(id = 2, uuid = "oct2", date = "2026-10-04", start = "08:00", end = "20:00", normMinutes = 420), // Sun +12h = 720 -> 1440
            ShiftRecord(id = 3, uuid = "oct3", date = "2026-10-10", start = "08:00", end = "20:00", normMinutes = 420), // Sat +12h = 720 -> 2160
            ShiftRecord(id = 4, uuid = "oct4", date = "2026-10-11", start = "08:00", end = "12:00", normMinutes = 420)  // Sun +4h = 240 -> 2400 (40:00)
        )
        // Month 2 (2026-11): start must be capped to 2100 (35:00), losing 300m (5:00). Work 07:00 -> +0 -> end 2100
        val novShift = ShiftRecord(
            id = 5, uuid = "nov1", date = "2026-11-02", start = "09:00", end = "16:00", normMinutes = 420 // Mon
        )
        // Month 3 (2026-12): lose 120m (short shift) -> 1980 (33:00)
        val decShift = ShiftRecord(
            id = 6, uuid = "dec1", date = "2026-12-01", start = "09:00", end = "14:00", normMinutes = 420 // Tue -> -120m
        )
        // Month 4 (2027-01): exact carry over 1980 (< 2100)
        val janShift = ShiftRecord(
            id = 7, uuid = "jan1", date = "2027-01-04", start = "09:00", end = "16:00", normMinutes = 420 // Mon -> 0
        )

        val timeline = TimeCalcEngine.computeTimeline(octShifts + novShift + decShift + janShift, defaultSettings)
        assertEquals(4, timeline.monthSummaries.size)

        val octSum = timeline.monthSummaries[0]
        assertEquals(2400, octSum.endBalanceMinutes)
        assertEquals(300, octSum.cappedLostMinutes) // 2400 - 2100 = 300m lost!

        val novSum = timeline.monthSummaries[1]
        assertEquals(2100, novSum.startBalanceMinutes)
        assertEquals(2100, novSum.endBalanceMinutes)

        val decSum = timeline.monthSummaries[2]
        assertEquals(2100, decSum.startBalanceMinutes)
        assertEquals(1980, decSum.endBalanceMinutes)

        val janSum = timeline.monthSummaries[3]
        assertEquals(1980, janSum.startBalanceMinutes)
        assertEquals(1980, janSum.endBalanceMinutes)
    }

    @Test
    fun `negative balance carries over completely without capping`() {
        val oct = ShiftRecord(id = 1, uuid = "n1", date = "2026-10-05", start = "09:00", end = "11:00", normMinutes = 420) // -300
        val nov = ShiftRecord(id = 2, uuid = "n2", date = "2026-11-02", start = "09:00", end = "16:00", normMinutes = 420) // 0
        val timeline = TimeCalcEngine.computeTimeline(listOf(oct, nov), defaultSettings)
        assertEquals(-300, timeline.monthSummaries[1].startBalanceMinutes)
        assertEquals(-300, timeline.currentBankMinutes)
    }

    @Test
    fun `firstWorkMonth resets balance even if that exact month has no entries`() {
        val aug = ShiftRecord(id = 1, uuid = "a1", date = "2026-08-01", start = "09:00", end = "15:00", normMinutes = 420) // Sat -> +360
        // Skip 2026-09 completely!
        val oct = ShiftRecord(id = 2, uuid = "o1", date = "2026-10-05", start = "09:00", end = "16:00", normMinutes = 420) // Mon -> 0
        val settings = defaultSettings.copy(firstWorkMonth = "2026-09", initialBalanceMinutes = 0)

        val timeline = TimeCalcEngine.computeTimeline(listOf(aug, oct), settings)
        assertEquals(360, timeline.monthSummaries[0].endBalanceMinutes)
        assertEquals(0, timeline.monthSummaries[1].startBalanceMinutes)
    }

    @Test
    fun `dynamic Ukrainian month labels work for any year`() {
        assertEquals("Січень 2025", TimeCalcEngine.getMonthLabel("2025-01", full = true))
        assertEquals("Листопад 2027", TimeCalcEngine.getMonthLabel("2027-11", full = true))
        assertEquals("Січ 25", TimeCalcEngine.getMonthLabel("2025-01", full = false))
    }

    @Test
    fun `soft deleted records are excluded from calculations`() {
        val active = ShiftRecord(id = 1, uuid = "a", date = "2026-11-23", start = "09:00", end = "17:00", normMinutes = 420)
        val deleted = ShiftRecord(id = 2, uuid = "b", date = "2026-11-24", start = "09:00", end = "20:00", normMinutes = 420, deletedAtEpochMs = 123456L)
        val res = TimeCalcEngine.computeTimeline(listOf(active, deleted), defaultSettings)
        assertEquals(1, res.shifts.size)
        assertEquals(60, res.currentBankMinutes)
    }

    @Test
    fun `auto pause durations follow p1=15 p2=15 p3plus=5 and custom settings`() {
        assertEquals(15, TimeCalcEngine.defaultPauseDurationForIndex(0, defaultSettings))
        assertEquals(15, TimeCalcEngine.defaultPauseDurationForIndex(1, defaultSettings))
        assertEquals(5, TimeCalcEngine.defaultPauseDurationForIndex(2, defaultSettings))
        assertEquals(5, TimeCalcEngine.defaultPauseDurationForIndex(3, defaultSettings))

        val custom = defaultSettings.copy(defaultPause1Min = 20, defaultPause2Min = 10, defaultPause3Min = 7)
        assertEquals(PauseInterval("10:00", "10:20"), TimeCalcEngine.createAutoPause("10:00", 0, custom))
        assertEquals(PauseInterval("13:00", "13:10"), TimeCalcEngine.createAutoPause("13:00", 1, custom))
        assertEquals(PauseInterval("16:00", "16:07"), TimeCalcEngine.createAutoPause("16:00", 2, custom))
    }

    @Test
    fun `updating pause start always recalculates end including midnight wrap`() {
        val p1Updated = TimeCalcEngine.updatePauseStartWithAutoEnd(0, "10:30", defaultSettings)
        assertEquals(PauseInterval("10:30", "10:45"), p1Updated)

        val p3Midnight = TimeCalcEngine.updatePauseStartWithAutoEnd(2, "23:58", defaultSettings)
        assertEquals(PauseInterval("23:58", "00:03"), p3Midnight)
    }

    @Test
    fun `remainingScheduledPauseSeconds detects active and overnight pauses accurately`() {
        val dayPause = PauseInterval("14:30", "14:45")
        val insideDay = LocalDateTime.of(2026, 11, 23, 14, 35, 30)
        assertEquals(570L, TimeCalcEngine.remainingScheduledPauseSeconds(dayPause, insideDay))
        assertEquals(330L, TimeCalcEngine.elapsedScheduledPauseSeconds(dayPause, insideDay))

        val afterDay = LocalDateTime.of(2026, 11, 23, 14, 45, 0)
        assertNull(TimeCalcEngine.remainingScheduledPauseSeconds(dayPause, afterDay))

        val nightPause = PauseInterval("23:55", "00:10")
        val afterMidnight = LocalDateTime.of(2026, 11, 24, 0, 2, 0)
        assertEquals(480L, TimeCalcEngine.remainingScheduledPauseSeconds(nightPause, afterMidnight))
    }

    @Test
    fun `trimActivePausesForShiftEnd trims only active or open pauses`() {
        val pauses = listOf(
            PauseInterval("10:00", "10:15"), // already finished
            PauseInterval("14:30", "14:45"), // currently in progress at 14:38
            PauseInterval("15:00", "")       // open pause
        )
        val now = LocalDateTime.of(2026, 11, 23, 14, 38, 0)
        val trimmed = TimeCalcEngine.trimActivePausesForShiftEnd(pauses, "14:38", now)

        assertEquals(PauseInterval("10:00", "10:15"), trimmed[0])
        assertEquals(PauseInterval("14:30", "14:38"), trimmed[1])
        assertEquals(PauseInterval("15:00", "14:38"), trimmed[2])
    }
}
