package ua.robochyichas.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.SyncStatus
import ua.robochyichas.app.domain.model.ThemeMode
import ua.robochyichas.app.ui.WorkTimeUiState
import ua.robochyichas.app.ui.components.SyncStatusPill
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CloudSettingsScreen(
    state: WorkTimeUiState,
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onOpenDriveFolderPicker: () -> Unit,
    onLinkSafCreate: () -> Unit,
    onLinkSafExisting: () -> Unit,
    onUnlinkSaf: () -> Unit,
    onRestoreFromSaf: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onExportJsonSave: () -> Unit,
    onExportJsonShare: () -> Unit,
    onImportJsonPick: () -> Unit,
    onExportCsvSave: () -> Unit,
    onClearAllData: () -> Unit,
    onCopyToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val sync = state.syncState
    val settings = state.settings

    var showSetupGuide by remember { mutableStateOf(sync.needsCloudSetupHelp) }
    LaunchedEffect(sync.needsCloudSetupHelp) {
        if (sync.needsCloudSetupHelp) showSetupGuide = true
    }

    var showRestoreDriveConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    var firstMonthInput by remember(settings.firstWorkMonth) { mutableStateOf(settings.firstWorkMonth) }
    var carryCapInput by remember(settings.carryOverCapMinutes) {
        mutableStateOf(TimeCalcEngine.formatMinutes(settings.carryOverCapMinutes))
    }
    var initBalInput by remember(settings.initialBalanceMinutes) {
        mutableStateOf(TimeCalcEngine.formatMinutes(settings.initialBalanceMinutes))
    }
    var defaultNormInput by remember(settings.defaultNormMinutes) {
        mutableStateOf(TimeCalcEngine.formatHoursMinutesPadded(settings.defaultNormMinutes))
    }

    val formattedLastSync = remember(sync.lastSyncTimestampMs) {
        sync.lastSyncTimestampMs?.let { ts ->
            val dt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDateTime()
            dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
        } ?: "Ще не синхронізовано"
    }

    if (showRestoreDriveConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreDriveConfirm = false },
            title = { Text("Відновити з Google Диску?") },
            text = {
                Text("Локальні дані будуть збережені у внутрішній знімок безпеки, після чого завантажиться остання копія з вашого Google Диску.")
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreDriveConfirm = false
                    onRestoreFromDrive()
                }) {
                    Text("Відновити")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDriveConfirm = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Очистити всі записи?") },
            text = {
                Text("Всі зміни та налаштування буде очищено. Якщо підключено Google Диск, видалення також синхронізується.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.coral)
                ) {
                    Text("Очистити", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Хмара та Налаштування",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                SyncStatusPill(syncState = sync, onClick = onSyncNow)
            }
        }

        // 1. Native Google Drive Account & Sync Card
        item {
            SectionCard(title = "☁️ Google Диск (автосинхронізація)") {
                if (sync.googleEmail.isNullOrBlank()) {
                    Text(
                        text = "Увійдіть у Google акаунт, щоб кожна зміна миттєво зберігалась локально й автоматично завантажувалась на Google Диск, як тільки з'явиться інтернет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Button(
                        onClick = onGoogleSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.emerald,
                            contentColor = Color(0xFF022C22)
                        ),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Увійти через Google Диск", fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceElevated)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colors.emerald),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sync.googleEmail.first().uppercase(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF022C22)
                                )
                            }
                            Column {
                                if (!sync.googleName.isNullOrBlank()) {
                                    Text(
                                        text = sync.googleName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary
                                    )
                                }
                                Text(
                                    text = sync.googleEmail,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.emerald
                                )
                            }
                        }
                        IconButton(onClick = onGoogleSignOut) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Вийти", tint = colors.coral)
                        }
                    }

                    // Selectable Google Drive Folder Tile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenDriveFolderPicker)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.amber, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "Папка бекапу на Диску",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                    color = colors.textMuted
                                )
                                Text(
                                    text = "${sync.driveFolderPath} / work_time_backup.json",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary
                                )
                            }
                        }
                        Text(
                            text = "Змінити",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.sky
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Остання синхр.:", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(formattedLastSync, style = MaterialTheme.typography.labelMedium.tabular(), color = colors.textPrimary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Локальна черга:", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(
                            text = if (sync.pendingChangesCount > 0) "${sync.pendingChangesCount} несинхр. змін" else "Усе збережено",
                            style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.Bold),
                            color = if (sync.pendingChangesCount > 0) colors.amber else colors.emerald
                        )
                    }

                    if (!sync.lastError.isNullOrBlank()) {
                        Text(
                            text = "⚠ ${sync.lastError}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sync.status == SyncStatus.OFFLINE_QUEUED) colors.amber else colors.coral
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncNow,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.emerald,
                                contentColor = Color(0xFF022C22)
                            )
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Синхронізувати", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { showRestoreDriveConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.sky)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Відновити", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.sky)
                        }
                    }

                    if (sync.status == SyncStatus.AUTH_REQUIRED) {
                        OutlinedButton(
                            onClick = onGoogleSignIn,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Повторно підтвердити вхід Google", color = colors.amber, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = colors.borderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автосинхронізація", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                        Text("Завантажувати на Диск одразу при появі мережі", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                    Switch(
                        checked = settings.autoSyncEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoSyncEnabled = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Лише через Wi-Fi", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                        Text("Економити мобільний трафік у фоні", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                    Switch(
                        checked = settings.syncWifiOnly,
                        onCheckedChange = { onUpdateSettings(settings.copy(syncWifiOnly = it)) }
                    )
                }
            }
        }

        // 2. Collapsible Google Cloud Console Setup Guide (live runtime SHA-1)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceCard)
                    .border(
                        1.dp,
                        if (sync.needsCloudSetupHelp) colors.amber else colors.borderSubtle,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSetupGuide = !showSetupGuide },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.amber, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Налаштування OAuth Google Cloud (SHA-1)",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            color = colors.textPrimary
                        )
                    }
                    Icon(
                        imageVector = if (showSetupGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.textMuted
                    )
                }

                AnimatedVisibility(visible = showSetupGuide) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Google Drive API вимагає OAuth Client ID, прив'язаний до підпису цього APK у вашому Google Cloud Console (проєкт безкоштовний):",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )

                        CopyableCredentialRow(
                            label = "Package name",
                            value = state.appPackageName,
                            onCopy = {
                                clipboard.setText(AnnotatedString(state.appPackageName))
                                onCopyToast("Package name скопійовано")
                            }
                        )
                        CopyableCredentialRow(
                            label = "SHA-1 сертифіката",
                            value = state.appSha1,
                            onCopy = {
                                clipboard.setText(AnnotatedString(state.appSha1))
                                onCopyToast("SHA-1 скопійовано")
                            }
                        )

                        Text(
                            text = "1. Google Cloud Console → APIs & Services → увімкніть Google Drive API.\n" +
                                "2. Google Auth platform → Audience: External → потім натисніть Publish App (In production), щоб могли заходити всі користувачі.\n" +
                                "3. Data Access → додайте scopes drive.file та drive.appdata.\n" +
                                "4. Clients → Create Client → Android → вставте Package name та SHA-1 вище (через 2–5 хв запрацює без перезбірки APK!).",
                            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp),
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // 3. Zero-Setup SAF Drive File Link
        item {
            SectionCard(title = "📁 Пряма прив'язка файлу на Google Диску (без налаштувань)") {
                Text(
                    text = "Працює одразу без Google Cloud Console: виберіть або створіть файл безпосередньо в додатку «Google Диск» через системний провідник Android.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )

                if (!sync.safBackupDisplayName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.emeraldContainer.copy(alpha = 0.6f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = colors.emeraldOnContainer, modifier = Modifier.size(18.dp))
                            Text(
                                text = sync.safBackupDisplayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.emeraldOnContainer
                            )
                        }
                        TextButton(onClick = onUnlinkSaf) {
                            Text("Відв'язати", color = colors.coral, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSyncNow,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.emerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Записати зараз", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = onRestoreFromSaf,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.sky)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Завантажити з файлу", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sky)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onLinkSafCreate,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.emerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Створити на Диску", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = onLinkSafExisting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.sky)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обрати існуючий", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sky)
                        }
                    }
                }
            }
        }

        // 4. Work Rules & Theme Card
        item {
            SectionCard(title = "⚙️ Правила розрахунку та Тема") {
                OutlinedTextField(
                    value = firstMonthInput,
                    onValueChange = { firstMonthInput = it },
                    label = { Text("Перший робочий місяць (РРРР-ММ)") },
                    placeholder = { Text("Напр.: 2026-07 або порожньо") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = carryCapInput,
                        onValueChange = { carryCapInput = it },
                        label = { Text("Ліміт (ГГ:ХХ)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = initBalInput,
                        onValueChange = { initBalInput = it },
                        label = { Text("Старт 1-го міс.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = defaultNormInput,
                        onValueChange = { defaultNormInput = it },
                        label = { Text("Норма дня") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val cap = TimeCalcEngine.timeToMinutes(carryCapInput)?.coerceAtLeast(0) ?: settings.carryOverCapMinutes
                        val init = TimeCalcEngine.timeToMinutes(initBalInput) ?: settings.initialBalanceMinutes
                        val norm = TimeCalcEngine.timeToMinutes(defaultNormInput)?.coerceAtLeast(0) ?: settings.defaultNormMinutes
                        onUpdateSettings(
                            settings.copy(
                                firstWorkMonth = firstMonthInput.trim(),
                                carryOverCapMinutes = cap,
                                initialBalanceMinutes = init,
                                defaultNormMinutes = norm
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Застосувати правила", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = colors.borderSubtle)

                Text("Тема оформлення", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateSettings(settings.copy(themeMode = mode)) },
                            label = { Text(mode.labelUa, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Export / Import / Reset Card
        item {
            SectionCard(title = "💾 Резервне копіювання та Експорт") {
                Text(
                    text = "Повна сумісність із файлами work_time_backup.json з попередньої версії (v2.1) та новим форматом з перевіркою SHA-256.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportJsonSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Експорт JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                    OutlinedButton(
                        onClick = onExportJsonShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.sky)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Поділитись JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sky)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportJsonPick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.emerald)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Імпорт JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.emerald)
                    }
                    OutlinedButton(
                        onClick = onExportCsvSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Експорт CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.coral)
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Очистити всі записи", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary
        )
        content()
    }
}

@Composable
private fun CopyableCredentialRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onCopy)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = colors.textMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = colors.amberBright
            )
        }
        Icon(Icons.Rounded.ContentCopy, contentDescription = "Копіювати", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
    }
}
