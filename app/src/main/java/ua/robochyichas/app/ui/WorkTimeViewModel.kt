package ua.robochyichas.app.ui

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.robochyichas.app.data.cloud.AuthOutcome
import ua.robochyichas.app.data.cloud.DriveFileMetadata
import ua.robochyichas.app.data.cloud.SyncRunResult
import ua.robochyichas.app.data.repository.WorkTimeRepository
import ua.robochyichas.app.domain.engine.ImportExportEngine
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ComputedShift
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.PauseInterval
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.domain.model.SyncState
import ua.robochyichas.app.domain.model.TimelineResult
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class MainTab(val titleUa: String) {
    SHIFTS("Зміни"),
    TABLE("Таблиця"),
    SUMMARY("Підсумки"),
    CLOUD("Хмара")
}

enum class TodayShiftPhase {
    IDLE,
    WORKING,
    ON_PAUSE,
    COMPLETED
}

sealed class UiEffect {
    data class ShowMessage(val message: String, val undoDeleteId: Long? = null) : UiEffect()
    data class LaunchGoogleAuthIntent(val pendingIntent: PendingIntent) : UiEffect()
    data class ExportContentReady(val content: String, val suggestedFileName: String, val mimeType: String, val shareOnly: Boolean) : UiEffect()
}

data class DriveFolderCrumb(
    val id: String,
    val name: String
)

data class FolderPickerState(
    val isOpen: Boolean = false,
    val breadcrumbs: List<DriveFolderCrumb> = listOf(DriveFolderCrumb("root", "Мій Диск")),
    val folders: List<DriveFileMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class WorkTimeUiState(
    val activeTab: MainTab = MainTab.SHIFTS,
    val timeline: TimelineResult = TimelineResult(emptyList(), emptyList(), emptyMap(), 0),
    val settings: AppSettings = AppSettings(),
    val syncState: SyncState = SyncState(),
    val selectedMonths: Set<String> = emptySet(),
    val selectedEntryTypeFilter: EntryType? = null,
    val searchQuery: String = "",
    val todayPhase: TodayShiftPhase = TodayShiftPhase.IDLE,
    val todayShift: ComputedShift? = null,
    val liveShiftElapsedSeconds: Long = 0L,
    val livePauseElapsedSeconds: Long = 0L,
    val livePauseRemainingSeconds: Long = 0L,
    val activePauseNumber: Int? = null,
    val activePauseReturnTime: String? = null,
    val isEditorOpen: Boolean = false,
    val editingShift: ShiftRecord? = null,
    val folderPicker: FolderPickerState = FolderPickerState(),
    val appSha1: String = "",
    val appPackageName: String = ""
) {
    val availableMonthsSorted: List<String>
        get() = timeline.monthSummaries.map { it.yearMonth }

    val filteredShifts: List<ComputedShift>
        get() {
            val activeMonths = selectedMonths.ifEmpty {
                availableMonthsSorted.lastOrNull()?.let { setOf(it) } ?: emptySet()
            }
            val q = searchQuery.trim().lowercase()
            return timeline.shifts.filter { item ->
                val monthOk = activeMonths.isEmpty() || item.yearMonth in activeMonths
                val typeOk = selectedEntryTypeFilter == null || item.shift.entryType == selectedEntryTypeFilter
                val queryOk = q.isEmpty() ||
                    item.shift.note.lowercase().contains(q) ||
                    item.dateUa.contains(q) ||
                    item.dayNameUa.contains(q) ||
                    item.shift.entryType.labelUa.lowercase().contains(q)
                monthOk && typeOk && queryOk
            }
        }
}

class WorkTimeViewModel(
    private val repository: WorkTimeRepository,
    private val appSha1: String,
    private val appPackageName: String
) : ViewModel() {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val activeTab = MutableStateFlow(MainTab.SHIFTS)
    private val selectedMonths = MutableStateFlow<Set<String>>(emptySet())
    private val selectedEntryFilter = MutableStateFlow<EntryType?>(null)
    private val searchQuery = MutableStateFlow("")
    private val editorState = MutableStateFlow<Pair<Boolean, ShiftRecord?>>(false to null)
    private val folderPickerState = MutableStateFlow(FolderPickerState())
    private val tickerNow = MutableStateFlow(LocalDateTime.now())

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    private data class FilterSlice(
        val tab: MainTab,
        val months: Set<String>,
        val entryFilter: EntryType?,
        val query: String,
        val editor: Pair<Boolean, ShiftRecord?>,
        val folderPicker: FolderPickerState,
        val now: LocalDateTime
    )

    private val filterFlow = combine(
        activeTab,
        selectedMonths,
        selectedEntryFilter,
        searchQuery,
        editorState
    ) { tab, months, entryFilter, query, editor ->
        FilterSlice(tab, months, entryFilter, query, editor, folderPickerState.value, tickerNow.value)
    }.combine(folderPickerState) { slice, picker ->
        slice.copy(folderPicker = picker)
    }.combine(tickerNow) { slice, now ->
        slice.copy(now = now)
    }

    val uiState: StateFlow<WorkTimeUiState> = combine(
        repository.activeShiftsFlow,
        repository.settingsFlow,
        repository.syncStateFlow,
        filterFlow
    ) { shifts, settings, syncState, filter ->
        val timeline = TimeCalcEngine.computeTimeline(shifts, settings)
        val allMonths = timeline.monthSummaries.map { it.yearMonth }.toSet()

        val validSelected = filter.months.filter { it in allMonths }.toSet()
        val effectiveSelected = if (validSelected.isNotEmpty()) {
            validSelected
        } else {
            timeline.monthSummaries.lastOrNull()?.yearMonth?.let { setOf(it) } ?: emptySet()
        }

        val todayIso = filter.now.toLocalDate().toString()
        val todayComputed = timeline.shifts.lastOrNull {
            it.shift.date == todayIso && it.shift.entryType == EntryType.SHIFT
        }
        val livePhase = computeLivePhase(todayComputed, filter.now)

        WorkTimeUiState(
            activeTab = filter.tab,
            timeline = timeline,
            settings = settings,
            syncState = syncState,
            selectedMonths = effectiveSelected,
            selectedEntryTypeFilter = filter.entryFilter,
            searchQuery = filter.query,
            todayPhase = livePhase.phase,
            todayShift = todayComputed,
            liveShiftElapsedSeconds = livePhase.shiftElapsedSec,
            livePauseElapsedSeconds = livePhase.pauseElapsedSec,
            livePauseRemainingSeconds = livePhase.pauseRemainingSec,
            activePauseNumber = livePhase.activePauseNumber,
            activePauseReturnTime = livePhase.activePauseReturnTime,
            isEditorOpen = filter.editor.first,
            editingShift = filter.editor.second,
            folderPicker = filter.folderPicker,
            appSha1 = appSha1,
            appPackageName = appPackageName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WorkTimeUiState(appSha1 = appSha1, appPackageName = appPackageName)
    )

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                tickerNow.value = LocalDateTime.now()
            }
        }
    }

    private data class LivePhaseResult(
        val phase: TodayShiftPhase,
        val shiftElapsedSec: Long = 0L,
        val pauseElapsedSec: Long = 0L,
        val pauseRemainingSec: Long = 0L,
        val activePauseNumber: Int? = null,
        val activePauseReturnTime: String? = null
    )

    private fun computeLivePhase(
        todayComputed: ComputedShift?,
        now: LocalDateTime
    ): LivePhaseResult {
        val s = todayComputed?.shift ?: return LivePhaseResult(TodayShiftPhase.IDLE)
        if (s.start.isBlank()) return LivePhaseResult(TodayShiftPhase.IDLE)
        if (s.end.isNotBlank()) {
            return LivePhaseResult(
                phase = TodayShiftPhase.COMPLETED,
                shiftElapsedSec = todayComputed.workedMinutes * 60L
            )
        }

        val today = now.toLocalDate()
        val startTime = parseLocalTimeOrNull(s.start) ?: return LivePhaseResult(TodayShiftPhase.WORKING)
        val startDt = LocalDateTime.of(today, startTime).let { dt ->
            if (dt.isAfter(now)) dt.minusDays(1) else dt
        }
        val grossSec = Duration.between(startDt, now).seconds.coerceAtLeast(0L)

        val openIdx = s.pauses.indexOfLast { it.isOpen }
        val scheduledActiveIdx = if (openIdx == -1) {
            s.pauses.indexOfLast { TimeCalcEngine.remainingScheduledPauseSeconds(it, now) != null }
        } else {
            -1
        }
        val activeIdx = if (openIdx >= 0) openIdx else scheduledActiveIdx

        val completedPauses = if (activeIdx >= 0) {
            s.pauses.filterIndexed { idx, _ -> idx != activeIdx }
        } else {
            s.pauses
        }
        val completedPauseMin = if (s.pauses.isEmpty() && s.manualPauseMinutes > 0) {
            s.manualPauseMinutes
        } else {
            TimeCalcEngine.calcPauseMinutes(completedPauses, 0)
        }
        val completedPauseSec = completedPauseMin * 60L

        if (openIdx >= 0) {
            val openPause = s.pauses[openIdx]
            var openElapsedSec = 0L
            val pStart = parseLocalTimeOrNull(openPause.start)
            if (pStart != null) {
                val pStartDt = LocalDateTime.of(today, pStart).let { dt ->
                    if (dt.isAfter(now)) dt.minusDays(1) else dt
                }
                openElapsedSec = Duration.between(pStartDt, now).seconds.coerceAtLeast(0L)
            }
            val netSec = (grossSec - completedPauseSec - openElapsedSec).coerceAtLeast(0L)
            return LivePhaseResult(
                phase = TodayShiftPhase.ON_PAUSE,
                shiftElapsedSec = netSec,
                pauseElapsedSec = openElapsedSec,
                pauseRemainingSec = 0L,
                activePauseNumber = openIdx + 1,
                activePauseReturnTime = null
            )
        }

        if (scheduledActiveIdx >= 0) {
            val activePause = s.pauses[scheduledActiveIdx]
            val remSec = TimeCalcEngine.remainingScheduledPauseSeconds(activePause, now) ?: 0L
            val elapsedSec = TimeCalcEngine.elapsedScheduledPauseSeconds(activePause, now) ?: 0L
            val netSec = (grossSec - completedPauseSec - elapsedSec).coerceAtLeast(0L)
            return LivePhaseResult(
                phase = TodayShiftPhase.ON_PAUSE,
                shiftElapsedSec = netSec,
                pauseElapsedSec = elapsedSec,
                pauseRemainingSec = remSec,
                activePauseNumber = scheduledActiveIdx + 1,
                activePauseReturnTime = activePause.end
            )
        }

        val netSec = (grossSec - completedPauseSec).coerceAtLeast(0L)
        return LivePhaseResult(
            phase = TodayShiftPhase.WORKING,
            shiftElapsedSec = netSec
        )
    }

    private fun parseLocalTimeOrNull(str: String): LocalTime? =
        runCatching { LocalTime.parse(str, timeFmt) }.getOrNull()

    fun selectTab(tab: MainTab) {
        activeTab.value = tab
    }

    fun toggleMonthSelection(ym: String) {
        val current = uiState.value.selectedMonths
        if (ym in current) {
            if (current.size > 1) {
                selectedMonths.value = current - ym
            } else {
                emitMessage("Має бути обраний хоча б 1 місяць")
            }
        } else {
            selectedMonths.value = current + ym
        }
    }

    fun selectSingleMonthAndOpenShifts(ym: String) {
        selectedMonths.value = setOf(ym)
        activeTab.value = MainTab.SHIFTS
    }

    fun toggleSelectAllMonths() {
        val all = uiState.value.availableMonthsSorted.toSet()
        if (all.isEmpty()) return
        selectedMonths.value = if (uiState.value.selectedMonths.size == all.size) {
            setOf(all.last())
        } else {
            all
        }
    }

    fun setEntryTypeFilter(type: EntryType?) {
        selectedEntryFilter.value = if (selectedEntryFilter.value == type) null else type
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun openNewShiftEditor(initialDate: String? = null) {
        val nowTime = LocalTime.now().format(timeFmt)
        val today = initialDate ?: LocalDate.now().toString()
        val settings = uiState.value.settings
        val template = ShiftRecord(
            date = today,
            entryType = EntryType.SHIFT,
            start = nowTime,
            end = "",
            normMinutes = settings.defaultNormMinutes
        )
        editorState.value = true to template
    }

    fun openEditShiftEditor(shift: ShiftRecord) {
        editorState.value = true to shift
    }

    fun closeShiftEditor() {
        editorState.value = false to null
    }

    fun saveShift(record: ShiftRecord) {
        viewModelScope.launch {
            val saved = repository.upsertShift(record)
            val ym = saved.date.take(7)
            if (ym.length == 7 && ym !in selectedMonths.value) {
                selectedMonths.value = setOf(ym)
            }
            editorState.value = false to null
            emitMessage("Запис збережено")
        }
    }

    fun deleteShift(id: Long) {
        viewModelScope.launch {
            repository.softDeleteShift(id)
            editorState.value = false to null
            _effects.emit(UiEffect.ShowMessage("Запис видалено", undoDeleteId = id))
        }
    }

    fun undoDeleteShift(id: Long) {
        viewModelScope.launch {
            repository.restoreSoftDeletedShift(id)
            emitMessage("Запис відновлено")
        }
    }

    // Bulletproof Quick Punch State Machine
    fun punchStartOrResumeShift() {
        viewModelScope.launch {
            val nowTime = LocalTime.now().format(timeFmt)
            val today = LocalDate.now().toString()
            val settings = uiState.value.settings
            val existingToday = repository.getActiveShifts().lastOrNull {
                it.date == today && it.entryType == EntryType.SHIFT
            }

            val updated = if (existingToday == null) {
                ShiftRecord(
                    date = today,
                    entryType = EntryType.SHIFT,
                    start = nowTime,
                    end = "",
                    normMinutes = settings.defaultNormMinutes
                )
            } else {
                existingToday.copy(
                    start = existingToday.start.ifBlank { nowTime },
                    end = ""
                )
            }
            repository.upsertShift(updated)
            selectedMonths.value = setOf(today.take(7))
            emitMessage("Початок зміни: $nowTime")
        }
    }

    fun punchAutoPause() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val nowTime = now.toLocalTime().format(timeFmt)
            val today = now.toLocalDate().toString()
            val settings = uiState.value.settings
            val existing = repository.getActiveShifts().lastOrNull {
                it.date == today && it.entryType == EntryType.SHIFT
            } ?: run {
                emitMessage("Спочатку почніть зміну")
                return@launch
            }

            if (existing.pauses.any { it.isOpen || TimeCalcEngine.remainingScheduledPauseSeconds(it, now) != null }) {
                emitMessage("Пауза вже триває")
                return@launch
            }

            val nextIdx = existing.pauses.size
            val presetMin = TimeCalcEngine.defaultPauseDurationForIndex(nextIdx, settings)
            val newPause = TimeCalcEngine.createAutoPause(nowTime, nextIdx, settings)

            repository.upsertShift(
                existing.copy(
                    end = "",
                    pauses = existing.pauses + newPause
                )
            )
            emitMessage("Пауза ${nextIdx + 1} (+${presetMin} хв): $nowTime – ${newPause.end}")
        }
    }

    fun updateLastPauseEnd(customEndTime: String? = null) {
        viewModelScope.launch {
            val nowTime = LocalTime.now().format(timeFmt)
            val today = LocalDate.now().toString()
            val existing = repository.getActiveShifts().lastOrNull {
                it.date == today && it.entryType == EntryType.SHIFT
            } ?: return@launch

            if (existing.pauses.isEmpty()) {
                emitMessage("Немає пауз за сьогодні")
                return@launch
            }

            val lastIdx = existing.pauses.lastIndex
            val targetEnd = customEndTime?.takeIf { it.isNotBlank() } ?: nowTime
            val updatedPauses = existing.pauses.toMutableList()
            updatedPauses[lastIdx] = updatedPauses[lastIdx].copy(end = targetEnd)
            repository.upsertShift(existing.copy(pauses = updatedPauses))
            emitMessage("Кінець паузи ${lastIdx + 1}: $targetEnd")
        }
    }

    fun punchEndShift() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val nowTime = now.toLocalTime().format(timeFmt)
            val today = now.toLocalDate().toString()
            val existing = repository.getActiveShifts().lastOrNull {
                it.date == today && it.entryType == EntryType.SHIFT
            } ?: run {
                emitMessage("Сьогодні ще немає активної зміни")
                return@launch
            }

            val closedPauses = TimeCalcEngine.trimActivePausesForShiftEnd(
                pauses = existing.pauses,
                nowTimeStr = nowTime,
                now = now
            )
            repository.upsertShift(
                existing.copy(
                    end = nowTime,
                    pauses = closedPauses
                )
            )
            emitMessage("Зміну завершено о $nowTime")
        }
    }

    fun markFirstWorkMonth(ym: String) {
        viewModelScope.launch {
            val cur = uiState.value.settings
            repository.updateSettings(cur.copy(firstWorkMonth = ym, initialBalanceMinutes = 0))
            emitMessage("Перший місяць: ${TimeCalcEngine.getMonthLabel(ym, true)} (старт 0:00)")
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
            emitMessage("Налаштування збережено")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            selectedMonths.value = emptySet()
            emitMessage("Усі записи очищено")
        }
    }

    fun startGoogleSignIn() {
        viewModelScope.launch {
            when (val outcome = repository.authManager.requestAuthorizationInteractive()) {
                is AuthOutcome.Authorized -> {
                    val res = repository.onGoogleAuthorized(outcome)
                    handleSyncResultToast(res, connectedNow = true)
                }
                is AuthOutcome.NeedsResolution -> {
                    _effects.emit(UiEffect.LaunchGoogleAuthIntent(outcome.pendingIntent))
                }
                is AuthOutcome.SetupRequired -> {
                    repository.syncPreferences.markAuthRequired(outcome.message, needsSetupHelp = true)
                    emitMessage("Потрібне налаштування OAuth Client ID (див. інструкцію)")
                }
                is AuthOutcome.Failed -> {
                    emitMessage(outcome.message)
                }
            }
        }
    }

    fun handleGoogleAuthResolutionResult(data: android.content.Intent?) {
        viewModelScope.launch {
            when (val outcome = repository.authManager.handleResolutionIntent(data)) {
                is AuthOutcome.Authorized -> {
                    val res = repository.onGoogleAuthorized(outcome)
                    handleSyncResultToast(res, connectedNow = true)
                }
                is AuthOutcome.SetupRequired -> {
                    repository.syncPreferences.markAuthRequired(outcome.message, needsSetupHelp = true)
                    emitMessage("Потрібне налаштування Google Cloud Console")
                }
                is AuthOutcome.Failed -> emitMessage(outcome.message)
                is AuthOutcome.NeedsResolution -> emitMessage("Потрібне повторне підтвердження")
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            repository.signOutGoogle()
            emitMessage("Ви вийшли з Google Диску")
        }
    }

    fun syncNowManual() {
        viewModelScope.launch {
            val res = repository.runCloudSync(ignoreWifiOnly = true)
            handleSyncResultToast(res, connectedNow = false)
        }
    }

    fun restoreFromGoogleDrive() {
        viewModelScope.launch {
            val res = repository.restoreFromGoogleDrive()
            when (res) {
                is SyncRunResult.Success -> emitMessage("Дані відновлено з Google Диску")
                is SyncRunResult.OfflineQueued -> emitMessage(res.reason)
                is SyncRunResult.AuthRequired -> emitMessage(res.message)
                is SyncRunResult.RetryableError -> emitMessage(res.message)
            }
        }
    }

    fun openDriveFolderPicker() {
        val rootCrumbs = listOf(DriveFolderCrumb("root", "Мій Диск"))
        folderPickerState.value = FolderPickerState(
            isOpen = true,
            breadcrumbs = rootCrumbs,
            isLoading = true
        )
        loadDriveFoldersForCurrentParent("root")
    }

    fun closeDriveFolderPicker() {
        folderPickerState.value = folderPickerState.value.copy(isOpen = false)
    }

    fun navigateIntoDriveFolder(folderId: String, folderName: String) {
        val nextCrumbs = folderPickerState.value.breadcrumbs + DriveFolderCrumb(folderId, folderName)
        folderPickerState.value = folderPickerState.value.copy(
            breadcrumbs = nextCrumbs,
            isLoading = true,
            errorMessage = null
        )
        loadDriveFoldersForCurrentParent(folderId)
    }

    fun navigateDriveFolderBreadcrumb(index: Int) {
        val current = folderPickerState.value.breadcrumbs
        if (index !in current.indices) return
        val sliced = current.take(index + 1)
        val targetId = sliced.last().id
        folderPickerState.value = folderPickerState.value.copy(
            breadcrumbs = sliced,
            isLoading = true,
            errorMessage = null
        )
        loadDriveFoldersForCurrentParent(targetId)
    }

    fun retryLoadDriveFolders() {
        val targetId = folderPickerState.value.breadcrumbs.lastOrNull()?.id ?: "root"
        folderPickerState.value = folderPickerState.value.copy(isLoading = true, errorMessage = null)
        loadDriveFoldersForCurrentParent(targetId)
    }

    fun createDriveSubfolder(folderName: String) {
        val parentId = folderPickerState.value.breadcrumbs.lastOrNull()?.id ?: "root"
        viewModelScope.launch {
            folderPickerState.value = folderPickerState.value.copy(isLoading = true, errorMessage = null)
            repository.createDriveFolder(parentId, folderName).onSuccess { created ->
                val nextCrumbs = folderPickerState.value.breadcrumbs + DriveFolderCrumb(created.id, created.name)
                folderPickerState.value = folderPickerState.value.copy(breadcrumbs = nextCrumbs)
                loadDriveFoldersForCurrentParent(created.id)
                emitMessage("Створено папку «${created.name}»")
            }.onFailure { err ->
                folderPickerState.value = folderPickerState.value.copy(
                    isLoading = false,
                    errorMessage = err.message ?: "Не вдалося створити папку"
                )
            }
        }
    }

    fun selectCurrentDriveFolder() {
        val crumbs = folderPickerState.value.breadcrumbs
        val target = crumbs.lastOrNull() ?: DriveFolderCrumb("root", "Мій Диск")
        val path = crumbs.joinToString(" / ") { it.name }
        folderPickerState.value = folderPickerState.value.copy(isOpen = false)
        viewModelScope.launch {
            val res = repository.selectDriveFolder(target.id, path)
            handleSyncResultToast(res, connectedNow = false)
        }
    }

    private fun loadDriveFoldersForCurrentParent(parentId: String) {
        viewModelScope.launch {
            repository.listDriveFolders(parentId).onSuccess { list ->
                folderPickerState.value = folderPickerState.value.copy(
                    folders = list,
                    isLoading = false,
                    errorMessage = null
                )
            }.onFailure { err ->
                folderPickerState.value = folderPickerState.value.copy(
                    isLoading = false,
                    errorMessage = err.message ?: "Помилка завантаження папок"
                )
            }
        }
    }

    fun linkSafBackup(uriString: String, displayName: String) {
        viewModelScope.launch {
            val res = repository.linkSafBackup(uriString, displayName)
            when (res) {
                is SyncRunResult.Success -> emitMessage("Прив'язано файл: $displayName")
                else -> emitMessage("Файл прив'язано (синхронізація при підключенні)")
            }
        }
    }

    fun unlinkSafBackup() {
        repository.unlinkSafBackup()
        emitMessage("Прив'язку файлу вимкнено")
    }

    fun restoreFromSafFile(uriString: String) {
        viewModelScope.launch {
            val result = repository.restoreFromSaf(uriString)
            result.onSuccess { count ->
                emitMessage("Відновлено $count записів із файлу")
            }.onFailure { err ->
                emitMessage(err.message ?: "Помилка читання файлу")
            }
        }
    }

    fun importBackupJson(rawJson: String, replaceExisting: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                repository.importJsonBackup(rawJson, replaceExisting)
            }.onSuccess { count ->
                emitMessage("Імпортовано $count записів")
            }.onFailure { err ->
                emitMessage(err.message ?: "Помилка імпорту JSON")
            }
        }
    }

    fun exportCsv(shareOnly: Boolean) {
        viewModelScope.launch {
            val csv = ImportExportEngine.exportCsv(uiState.value.timeline.shifts)
            val year = LocalDate.now().year
            _effects.emit(
                UiEffect.ExportContentReady(
                    content = csv,
                    suggestedFileName = "work_time_$year.csv",
                    mimeType = "text/csv",
                    shareOnly = shareOnly
                )
            )
        }
    }

    fun exportJsonBackup(shareOnly: Boolean) {
        viewModelScope.launch {
            val json = repository.exportBackupJsonString()
            val today = LocalDate.now().toString()
            _effects.emit(
                UiEffect.ExportContentReady(
                    content = json,
                    suggestedFileName = "work_time_backup_$today.json",
                    mimeType = "application/json",
                    shareOnly = shareOnly
                )
            )
        }
    }

    private fun handleSyncResultToast(res: SyncRunResult, connectedNow: Boolean) {
        when (res) {
            is SyncRunResult.Success -> {
                val msg = when {
                    connectedNow -> "Google Диск підключено і синхронізовано!"
                    res.pulledRemoteChanges && res.uploadedChanges -> "Синхронізовано (об'єднано зміни)"
                    res.pulledRemoteChanges -> "Отримано оновлення з Google Диску"
                    res.uploadedChanges -> "Збережено на Google Диск"
                    else -> "Всі дані вже актуальні"
                }
                emitMessage(msg)
            }
            is SyncRunResult.OfflineQueued -> emitMessage(res.reason)
            is SyncRunResult.AuthRequired -> emitMessage(res.message)
            is SyncRunResult.RetryableError -> emitMessage(res.message)
        }
    }

    fun emitMessage(msg: String) {
        _effects.tryEmit(UiEffect.ShowMessage(msg))
    }

    class Factory(
        private val repository: WorkTimeRepository,
        private val appSha1: String,
        private val appPackageName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorkTimeViewModel(repository, appSha1, appPackageName) as T
        }
    }
}
