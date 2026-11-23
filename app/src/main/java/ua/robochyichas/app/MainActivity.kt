package ua.robochyichas.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ua.robochyichas.app.data.cloud.GoogleAuthManager
import ua.robochyichas.app.domain.model.ThemeMode
import ua.robochyichas.app.ui.MainTab
import ua.robochyichas.app.ui.UiEffect
import ua.robochyichas.app.ui.WorkTimeViewModel
import ua.robochyichas.app.ui.components.DriveFolderPickerDialog
import ua.robochyichas.app.ui.components.ShiftEditorSheet
import ua.robochyichas.app.ui.screens.CloudSettingsScreen
import ua.robochyichas.app.ui.screens.ShiftsScreen
import ua.robochyichas.app.ui.screens.SummaryScreen
import ua.robochyichas.app.ui.screens.TableScreen
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.RobochyiChasTheme
import java.io.File
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val viewModel: WorkTimeViewModel by viewModels {
        val app = application as WorkTimeApp
        WorkTimeViewModel.Factory(
            repository = app.repository,
            appSha1 = GoogleAuthManager.getAppSigningSha1(this),
            appPackageName = packageName
        )
    }

    private var pendingExportContent: String? = null

    private val googleAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleGoogleAuthResolutionResult(result.data)
    }

    private val createJsonExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        writePendingExportToUri(uri, "JSON резервну копію збережено")
    }

    private val createCsvExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        writePendingExportToUri(uri, "CSV файл збережено")
    }

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use {
                it.readText()
            } ?: error("Не вдалося відкрити файл")
        }.onSuccess { text ->
            viewModel.importBackupJson(text)
        }.onFailure { err ->
            viewModel.emitMessage(err.message ?: "Помилка читання файлу")
        }
    }

    private val createSafLinkLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        takePersistablePermission(uri)
        val name = queryDisplayName(uri) ?: "work_time_drive_sync.json"
        viewModel.linkSafBackup(uri.toString(), name)
    }

    private val openSafLinkLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        takePersistablePermission(uri)
        val name = queryDisplayName(uri) ?: "work_time_drive_sync.json"
        viewModel.linkSafBackup(uri.toString(), name)
    }

    private val restoreSafLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.restoreFromSafFile(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            val systemDark = isSystemInDarkTheme()
            val isDark = when (state.settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            LaunchedEffect(isDark) {
                if (isDark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }

            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is UiEffect.ShowMessage -> {
                            val result = snackbarHostState.showSnackbar(
                                message = effect.message,
                                actionLabel = if (effect.undoDeleteId != null) "Скасувати" else null,
                                duration = if (effect.undoDeleteId != null) SnackbarDuration.Long else SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed && effect.undoDeleteId != null) {
                                viewModel.undoDeleteShift(effect.undoDeleteId)
                            }
                        }
                        is UiEffect.LaunchGoogleAuthIntent -> {
                            val req = IntentSenderRequest.Builder(effect.pendingIntent.intentSender).build()
                            googleAuthLauncher.launch(req)
                        }
                        is UiEffect.ExportContentReady -> {
                            if (effect.shareOnly) {
                                shareTextFile(effect.content, effect.suggestedFileName, effect.mimeType)
                            } else {
                                pendingExportContent = effect.content
                                if (effect.mimeType.contains("csv")) {
                                    createCsvExportLauncher.launch(effect.suggestedFileName)
                                } else {
                                    createJsonExportLauncher.launch(effect.suggestedFileName)
                                }
                            }
                        }
                    }
                }
            }

            RobochyiChasTheme(themeMode = state.settings.themeMode) {
                val colors = LocalAppColors.current

                Scaffold(
                    containerColor = colors.bgPrimary,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    floatingActionButton = {
                        if (state.activeTab == MainTab.SHIFTS || state.activeTab == MainTab.TABLE) {
                            FloatingActionButton(
                                onClick = { viewModel.openNewShiftEditor() },
                                containerColor = colors.emerald,
                                contentColor = Color(0xFF022C22),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Додати запис")
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = colors.surfaceCard,
                            tonalElevation = 0.dp
                        ) {
                            MainTab.entries.forEach { tab ->
                                val icon = when (tab) {
                                    MainTab.SHIFTS -> Icons.Rounded.ViewAgenda
                                    MainTab.TABLE -> Icons.Rounded.TableChart
                                    MainTab.SUMMARY -> Icons.Rounded.BarChart
                                    MainTab.CLOUD -> Icons.Rounded.Cloud
                                }
                                NavigationBarItem(
                                    selected = state.activeTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(icon, contentDescription = tab.titleUa) },
                                    label = { Text(tab.titleUa) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (state.activeTab) {
                            MainTab.SHIFTS -> ShiftsScreen(
                                state = state,
                                onSyncPillClick = {
                                    if (state.syncState.googleEmail != null || state.syncState.safBackupDisplayName != null) {
                                        viewModel.syncNowManual()
                                    } else {
                                        viewModel.selectTab(MainTab.CLOUD)
                                    }
                                },
                                onStartOrResumeShift = { viewModel.punchStartOrResumeShift() },
                                onStartPause = { preset -> viewModel.punchStartPause(preset) },
                                onEndPause = { viewModel.punchEndPause() },
                                onEndShift = { viewModel.punchEndShift() },
                                onToggleMonth = { ym -> viewModel.toggleMonthSelection(ym) },
                                onToggleSelectAllMonths = { viewModel.toggleSelectAllMonths() },
                                onEntryTypeFilterChange = { type -> viewModel.setEntryTypeFilter(type) },
                                onSearchQueryChange = { q -> viewModel.setSearchQuery(q) },
                                onShiftClick = { shift -> viewModel.openEditShiftEditor(shift) },
                                onDeleteShift = { id -> viewModel.deleteShift(id) },
                                onAddNewShift = { viewModel.openNewShiftEditor() }
                            )

                            MainTab.TABLE -> TableScreen(
                                state = state,
                                onShiftClick = { shift -> viewModel.openEditShiftEditor(shift) },
                                onExportCsvSave = { viewModel.exportCsv(shareOnly = false) },
                                onExportCsvShare = { viewModel.exportCsv(shareOnly = true) }
                            )

                            MainTab.SUMMARY -> SummaryScreen(
                                state = state,
                                onMonthClick = { ym -> viewModel.selectSingleMonthAndOpenShifts(ym) },
                                onMarkFirstMonth = { ym -> viewModel.markFirstWorkMonth(ym) }
                            )

                            MainTab.CLOUD -> CloudSettingsScreen(
                                state = state,
                                onGoogleSignIn = { viewModel.startGoogleSignIn() },
                                onGoogleSignOut = { viewModel.signOutGoogle() },
                                onSyncNow = { viewModel.syncNowManual() },
                                onRestoreFromDrive = { viewModel.restoreFromGoogleDrive() },
                                onOpenDriveFolderPicker = { viewModel.openDriveFolderPicker() },
                                onLinkSafCreate = { createSafLinkLauncher.launch("work_time_drive_sync.json") },
                                onLinkSafExisting = { openSafLinkLauncher.launch(arrayOf("application/json", "*/*")) },
                                onUnlinkSaf = { viewModel.unlinkSafBackup() },
                                onRestoreFromSaf = { restoreSafLauncher.launch(arrayOf("application/json", "*/*")) },
                                onUpdateSettings = { s -> viewModel.updateSettings(s) },
                                onExportJsonSave = { viewModel.exportJsonBackup(shareOnly = false) },
                                onExportJsonShare = { viewModel.exportJsonBackup(shareOnly = true) },
                                onImportJsonPick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                                onExportCsvSave = { viewModel.exportCsv(shareOnly = false) },
                                onClearAllData = { viewModel.clearAllData() },
                                onCopyToast = { msg -> viewModel.emitMessage(msg) }
                            )
                        }
                    }
                }

                val editingShift = state.editingShift
                if (state.isEditorOpen && editingShift != null) {
                    ShiftEditorSheet(
                        initialRecord = editingShift,
                        settings = state.settings,
                        onDismiss = { viewModel.closeShiftEditor() },
                        onSave = { updated -> viewModel.saveShift(updated) },
                        onDelete = { id -> viewModel.deleteShift(id) }
                    )
                }

                if (state.folderPicker.isOpen) {
                    DriveFolderPickerDialog(
                        pickerState = state.folderPicker,
                        onDismiss = { viewModel.closeDriveFolderPicker() },
                        onNavigateInto = { id, name -> viewModel.navigateIntoDriveFolder(id, name) },
                        onNavigateBreadcrumb = { idx -> viewModel.navigateDriveFolderBreadcrumb(idx) },
                        onCreateSubfolder = { name -> viewModel.createDriveSubfolder(name) },
                        onRetryLoad = { viewModel.retryLoadDriveFolders() },
                        onSelectCurrentFolder = { viewModel.selectCurrentDriveFolder() }
                    )
                }
            }
        }
    }

    private fun writePendingExportToUri(uri: Uri?, successMessage: String) {
        val content = pendingExportContent ?: return
        pendingExportContent = null
        uri ?: return
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(StandardCharsets.UTF_8))
                out.flush()
            } ?: error("Не вдалося записати файл")
        }.onSuccess {
            viewModel.emitMessage(successMessage)
        }.onFailure { err ->
            viewModel.emitMessage(err.message ?: "Помилка збереження файлу")
        }
    }

    private fun shareTextFile(content: String, fileName: String, mimeType: String) {
        runCatching {
            val exportDir = File(cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            outFile.writeText(content, StandardCharsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Поділитись файлом"))
        }.onFailure { err ->
            viewModel.emitMessage(err.message ?: "Не вдалося відкрити меню надсилання")
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }
}
