package ua.robochyichas.app.domain.engine

import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ComputedShift
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.MonthSummary
import ua.robochyichas.app.domain.model.PauseInterval
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.domain.model.TimelineResult
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object TimeCalcEngine {

    private val FULL_MONTHS_UA = mapOf(
        "01" to "Січень",
        "02" to "Лютий",
        "03" to "Березень",
        "04" to "Квітень",
        "05" to "Травень",
        "06" to "Червень",
        "07" to "Липень",
        "08" to "Серпень",
        "09" to "Вересень",
        "10" to "Жовтень",
        "11" to "Листопад",
        "12" to "Грудень"
    )

    private val SHORT_MONTHS_UA = mapOf(
        "01" to "Січ",
        "02" to "Лют",
        "03" to "Бер",
        "04" to "Кві",
        "05" to "Тра",
        "06" to "Чер",
        "07" to "Лип",
        "08" to "Сер",
        "09" to "Вер",
        "10" to "Жов",
        "11" to "Лис",
        "12" to "Гру"
    )

    data class ShiftDurationResult(
        val workedMinutes: Int,
        val isComplete: Boolean,
        val isOvernight: Boolean
    )

    fun timeToMinutes(str: String?): Int? {
        if (str.isNullOrBlank()) return null
        val trimmed = str.trim()
        val isNeg = trimmed.startsWith("-")
        val clean = if (isNeg) trimmed.substring(1).trim() else trimmed
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h < 0 || m < 0 || m >= 60) return null
        val total = h * 60 + m
        return if (isNeg) -total else total
    }

    fun formatMinutes(
        totalMinutes: Int?,
        withSeconds: Boolean = false,
        showPlus: Boolean = false
    ): String {
        if (totalMinutes == null) return if (withSeconds) "0:00:00" else "0:00"
        val isNeg = totalMinutes < 0
        val absMin = abs(totalMinutes)
        val h = absMin / 60
        val m = absMin % 60
        val sign = when {
            isNeg -> "-"
            showPlus && absMin > 0 -> "+"
            else -> ""
        }
        val base = "$sign$h:${m.toString().padStart(2, '0')}"
        return if (withSeconds) "$base:00" else base
    }

    fun formatHoursMinutesPadded(minutes: Int): String {
        val clamped = minutes.coerceAtLeast(0)
        val h = clamped / 60
        val m = clamped % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    fun formatDurationWithSeconds(totalSeconds: Long): String {
        val safeSec = totalSeconds.coerceAtLeast(0L)
        val h = safeSec / 3600L
        val m = (safeSec % 3600L) / 60L
        val s = safeSec % 60L
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    fun addMinutesToTimeStr(timeStr: String, deltaMinutes: Int): String {
        val startMin = timeToMinutes(timeStr) ?: return ""
        val wrapped = ((startMin + deltaMinutes) % 1440 + 1440) % 1440
        val h = wrapped / 60
        val m = wrapped % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    fun defaultPauseDurationForIndex(pauseIndex: Int, settings: AppSettings): Int {
        val raw = when (pauseIndex) {
            0 -> settings.defaultPause1Min
            1 -> settings.defaultPause2Min
            else -> settings.defaultPause3Min
        }
        return raw.coerceIn(1, 180)
    }

    fun createAutoPause(start: String, pauseIndex: Int, settings: AppSettings): PauseInterval {
        val durationMin = defaultPauseDurationForIndex(pauseIndex, settings)
        return PauseInterval(
            start = start,
            end = addMinutesToTimeStr(start, durationMin)
        )
    }

    fun updatePauseStartWithAutoEnd(
        pauseIndex: Int,
        newStart: String,
        settings: AppSettings
    ): PauseInterval = createAutoPause(newStart, pauseIndex, settings)

    private val hhMmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun resolveActiveWindow(
        pause: PauseInterval,
        now: LocalDateTime
    ): Pair<LocalDateTime, LocalDateTime>? {
        if (!pause.isComplete) return null
        val startTime = runCatching { LocalTime.parse(pause.start.trim(), hhMmFormatter) }.getOrNull() ?: return null
        val endTime = runCatching { LocalTime.parse(pause.end.trim(), hhMmFormatter) }.getOrNull() ?: return null
        if (startTime == endTime) return null

        val isOvernight = endTime.isBefore(startTime)
        val today = now.toLocalDate()
        val startToday = LocalDateTime.of(today, startTime)
        val endToday = if (isOvernight) {
            LocalDateTime.of(today.plusDays(1), endTime)
        } else {
            LocalDateTime.of(today, endTime)
        }
        if (!now.isBefore(startToday) && now.isBefore(endToday)) {
            return startToday to endToday
        }

        if (isOvernight) {
            val startPrev = LocalDateTime.of(today.minusDays(1), startTime)
            val endPrev = LocalDateTime.of(today, endTime)
            if (!now.isBefore(startPrev) && now.isBefore(endPrev)) {
                return startPrev to endPrev
            }
        }
        return null
    }

    fun remainingScheduledPauseSeconds(pause: PauseInterval, now: LocalDateTime): Long? {
        val (_, endDt) = resolveActiveWindow(pause, now) ?: return null
        return Duration.between(now, endDt).seconds.coerceAtLeast(1L)
    }

    fun elapsedScheduledPauseSeconds(pause: PauseInterval, now: LocalDateTime): Long? {
        val (startDt, _) = resolveActiveWindow(pause, now) ?: return null
        return Duration.between(startDt, now).seconds.coerceAtLeast(0L)
    }

    fun trimActivePausesForShiftEnd(
        pauses: List<PauseInterval>,
        nowTimeStr: String,
        now: LocalDateTime
    ): List<PauseInterval> {
        return pauses.map { p ->
            when {
                p.isOpen -> p.copy(end = nowTimeStr)
                remainingScheduledPauseSeconds(p, now) != null -> p.copy(end = nowTimeStr)
                else -> p
            }
        }
    }

    fun formatDateUa(isoDate: String): String {
        val parts = isoDate.split("-")
        if (parts.size != 3) return isoDate
        val y = parts[0]
        val m = parts[1]
        val d = parts[2]
        val shortY = if (y.length >= 4) y.takeLast(2) else y
        return "$d.$m.$shortY"
    }

    fun parseLocalDate(isoDate: String): LocalDate? =
        runCatching { LocalDate.parse(isoDate) }.getOrNull()

    fun getDayNameUa(isoDate: String): String {
        val date = parseLocalDate(isoDate) ?: return ""
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "пн"
            DayOfWeek.TUESDAY -> "вт"
            DayOfWeek.WEDNESDAY -> "ср"
            DayOfWeek.THURSDAY -> "чт"
            DayOfWeek.FRIDAY -> "пт"
            DayOfWeek.SATURDAY -> "сб"
            DayOfWeek.SUNDAY -> "нд"
            else -> ""
        }
    }

    fun isWeekendDate(isoDate: String): Boolean {
        val date = parseLocalDate(isoDate) ?: return false
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }

    fun getMonthLabel(yearMonth: String, full: Boolean = true): String {
        val parts = yearMonth.split("-")
        if (parts.size != 2) return yearMonth
        val y = parts[0]
        val m = parts[1]
        return if (full) {
            val name = FULL_MONTHS_UA[m] ?: m
            "$name $y"
        } else {
            val name = SHORT_MONTHS_UA[m] ?: m
            "$name ${y.takeLast(2)}"
        }
    }

    fun calcPauseMinutes(
        pauses: List<PauseInterval>,
        manualPauseMinutes: Int = 0
    ): Int {
        var total = 0
        var hasCompletedIntervals = false
        for (pause in pauses) {
            val startM = timeToMinutes(pause.start)
            val endM = timeToMinutes(pause.end)
            if (startM != null && endM != null) {
                val diff = if (endM >= startM) endM - startM else (endM + 1440) - startM
                total += diff
                hasCompletedIntervals = true
            }
        }
        return if (!hasCompletedIntervals && manualPauseMinutes > 0) {
            manualPauseMinutes
        } else {
            total
        }
    }

    fun calcShiftWorked(
        start: String,
        end: String,
        pauseMinutes: Int
    ): ShiftDurationResult {
        val startM = timeToMinutes(start)
        val endM = timeToMinutes(end)
        if (startM == null || endM == null) {
            return ShiftDurationResult(workedMinutes = 0, isComplete = false, isOvernight = false)
        }
        val isOvernight = endM < startM
        val grossMinutes = if (isOvernight) (endM + 1440) - startM else endM - startM
        val worked = (grossMinutes - pauseMinutes).coerceAtLeast(0)
        return ShiftDurationResult(
            workedMinutes = worked,
            isComplete = true,
            isOvernight = isOvernight
        )
    }

    fun computeTimeline(
        shifts: List<ShiftRecord>,
        settings: AppSettings
    ): TimelineResult {
        val active = shifts
            .filter { it.deletedAtEpochMs == null && it.date.length >= 7 }
            .sortedWith(
                compareBy<ShiftRecord>(
                    { it.date },
                    { it.createdAtEpochMs },
                    { it.id },
                    { it.uuid }
                )
            )

        if (active.isEmpty()) {
            return TimelineResult(
                shifts = emptyList(),
                monthSummaries = emptyList(),
                monthStartBalances = emptyMap(),
                currentBankMinutes = settings.initialBalanceMinutes
            )
        }

        val capMin = settings.carryOverCapMinutes.coerceAtLeast(0)
        val firstWorkMonth = settings.firstWorkMonth.trim()

        val monthStartBalances = linkedMapOf<String, Int>()
        val monthCappedLost = mutableMapOf<String, Int>()
        var runningBalance = 0
        var currentMonth = ""
        var monthRowNum = 0
        var hasAppliedFirstReset = false

        val computedShifts = active.map { s ->
            val ym = s.date.take(7)
            if (ym != currentMonth) {
                if (currentMonth.isEmpty()) {
                    runningBalance = settings.initialBalanceMinutes
                    if (firstWorkMonth.isBlank() || ym >= firstWorkMonth) {
                        hasAppliedFirstReset = true
                    }
                } else if (!hasAppliedFirstReset && firstWorkMonth.isNotBlank() && ym >= firstWorkMonth) {
                    monthCappedLost[currentMonth] = 0
                    runningBalance = settings.initialBalanceMinutes
                    hasAppliedFirstReset = true
                } else {
                    val lost = if (runningBalance > capMin) runningBalance - capMin else 0
                    monthCappedLost[currentMonth] = lost
                    runningBalance = if (runningBalance >= capMin) capMin else runningBalance
                }
                monthStartBalances[ym] = runningBalance
                currentMonth = ym
                monthRowNum = 1
            } else {
                monthRowNum++
            }

            val dayName = getDayNameUa(s.date)
            val isWeekend = isWeekendDate(s.date)
            val hasOpenPause = s.entryType == EntryType.SHIFT && s.pauses.any { it.isOpen }

            val effectiveNorm = when {
                s.entryType != EntryType.SHIFT -> 0
                isWeekend -> 0
                else -> s.normMinutes.coerceAtLeast(0)
            }

            val pauseM = when (s.entryType) {
                EntryType.SHIFT -> calcPauseMinutes(s.pauses, s.manualPauseMinutes)
                else -> 0
            }

            var workedM = 0
            var dailyOverM = 0
            var isComplete = false
            var isOvernight = false

            when (s.entryType) {
                EntryType.SHIFT -> {
                    val dur = calcShiftWorked(s.start, s.end, pauseM)
                    isOvernight = dur.isOvernight
                    if (dur.isComplete) {
                        workedM = dur.workedMinutes
                        dailyOverM = workedM - effectiveNorm
                        runningBalance += dailyOverM
                        isComplete = true
                    }
                }
                EntryType.OVERTIME -> {
                    workedM = 0
                    dailyOverM = s.overtimeDeltaMinutes
                    runningBalance += dailyOverM
                    isComplete = true
                }
                EntryType.VACATION, EntryType.SICK_LEAVE, EntryType.DAY_OFF -> {
                    workedM = 0
                    dailyOverM = 0
                    isComplete = true
                }
            }

            ComputedShift(
                shift = s,
                yearMonth = ym,
                rowNum = monthRowNum,
                dayNameUa = dayName,
                dateUa = formatDateUa(s.date),
                isWeekend = isWeekend,
                isOvernight = isOvernight,
                pauseMinutes = pauseM,
                workedMinutes = workedM,
                effectiveNormMinutes = effectiveNorm,
                dailyOvertimeMinutes = dailyOverM,
                cumulativeOvertimeMinutes = runningBalance,
                isComplete = isComplete,
                hasOpenPause = hasOpenPause
            )
        }

        val groupedByMonth = computedShifts.groupBy { it.yearMonth }
        val sortedMonths = groupedByMonth.keys.sorted()
        val effectiveFirstMonth = firstWorkMonth.ifBlank { sortedMonths.firstOrNull().orEmpty() }

        val summaries = sortedMonths.map { ym ->
            val items = groupedByMonth[ym].orEmpty()
            val startBal = monthStartBalances[ym] ?: settings.initialBalanceMinutes
            val endBal = items.lastOrNull()?.cumulativeOvertimeMinutes ?: startBal
            val worked = items.sumOf { if (it.isComplete) it.workedMinutes else 0 }
            val pauses = items.sumOf { if (it.isComplete) it.pauseMinutes else 0 }
            val completedWorkShifts = items.count { it.shift.entryType == EntryType.SHIFT && it.isComplete }
            val avgWorked = if (completedWorkShifts > 0) worked / completedWorkShifts else 0
            val lost = monthCappedLost[ym] ?: 0
            val overCap = (endBal - capMin).coerceAtLeast(0)

            MonthSummary(
                yearMonth = ym,
                labelFull = getMonthLabel(ym, full = true),
                labelShort = getMonthLabel(ym, full = false),
                shiftCount = items.size,
                workShiftCount = completedWorkShifts,
                startBalanceMinutes = startBal,
                endBalanceMinutes = endBal,
                monthDeltaMinutes = endBal - startBal,
                workedMinutes = worked,
                pauseMinutes = pauses,
                cappedLostMinutes = lost,
                overCapMinutes = overCap,
                avgWorkedMinutes = avgWorked,
                isFirstWorkMonth = ym == effectiveFirstMonth
            )
        }

        return TimelineResult(
            shifts = computedShifts,
            monthSummaries = summaries,
            monthStartBalances = monthStartBalances,
            currentBankMinutes = runningBalance
        )
    }

    fun computeAll(shifts: List<ShiftRecord>, settings: AppSettings): List<ComputedShift> =
        computeTimeline(shifts, settings).shifts
}
