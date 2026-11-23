package ua.robochyichas.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.PauseInterval
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftEditorSheet(
    initialRecord: ShiftRecord,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (ShiftRecord) -> Unit,
    onDelete: (Long) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditingExisting = initialRecord.id > 0L

    var date by remember(initialRecord) { mutableStateOf(initialRecord.date) }
    var entryType by remember(initialRecord) { mutableStateOf(initialRecord.entryType) }
    var start by remember(initialRecord) { mutableStateOf(initialRecord.start) }
    var end by remember(initialRecord) { mutableStateOf(initialRecord.end) }
    var normStr by remember(initialRecord) {
        mutableStateOf(TimeCalcEngine.formatHoursMinutesPadded(initialRecord.normMinutes))
    }
    val pauses = remember(initialRecord) {
        mutableStateListOf<PauseInterval>().apply {
            addAll(initialRecord.pauses)
        }
    }
    var manualPauseStr by remember(initialRecord) {
        mutableStateOf(if (initialRecord.manualPauseMinutes > 0) initialRecord.manualPauseMinutes.toString() else "")
    }
    var overSign by remember(initialRecord) {
        mutableIntStateOf(if (initialRecord.overtimeDeltaMinutes < 0) -1 else 1)
    }
    var overHoursStr by remember(initialRecord) {
        val absM = kotlin.math.abs(initialRecord.overtimeDeltaMinutes)
        mutableStateOf(if (initialRecord.entryType == EntryType.OVERTIME) (absM / 60).toString() else "1")
    }
    var overMinsStr by remember(initialRecord) {
        val absM = kotlin.math.abs(initialRecord.overtimeDeltaMinutes)
        mutableStateOf(if (initialRecord.entryType == EntryType.OVERTIME) (absM % 60).toString() else "0")
    }
    var note by remember(initialRecord) { mutableStateOf(initialRecord.note) }

    fun pickDate() {
        val parsed = TimeCalcEngine.parseLocalDate(date) ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                date = "%04d-%02d-%02d".format(y, m + 1, d)
            },
            parsed.year,
            parsed.monthValue - 1,
            parsed.dayOfMonth
        ).show()
    }

    fun pickTime(currentVal: String, defaultFallback: String = "09:00", onPicked: (String) -> Unit) {
        val parsed = runCatching { LocalTime.parse(currentVal, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
            ?: runCatching { LocalTime.parse(defaultFallback, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
            ?: LocalTime.now()
        TimePickerDialog(
            context,
            { _, h, m -> onPicked("%02d:%02d".format(h, m)) },
            parsed.hour,
            parsed.minute,
            true
        ).show()
    }

    val manualPauseMin = manualPauseStr.toIntOrNull()?.coerceIn(0, 480) ?: 0
    val previewPauseMin = TimeCalcEngine.calcPauseMinutes(pauses.toList(), manualPauseMin)
    val previewShift = TimeCalcEngine.calcShiftWorked(start, end, previewPauseMin)
    val isWeekend = TimeCalcEngine.isWeekendDate(date)
    val parsedNormMin = (TimeCalcEngine.timeToMinutes(normStr) ?: settings.defaultNormMinutes).coerceAtLeast(0)
    val effectiveNorm = if (isWeekend || entryType != EntryType.SHIFT) 0 else parsedNormMin

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditingExisting) "Редагувати (${TimeCalcEngine.formatDateUa(date)})" else "Новий запис",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрити", tint = colors.textMuted)
                }
            }

            // Entry Type selector chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EntryType.entries.forEach { type ->
                    FilterChip(
                        selected = entryType == type,
                        onClick = { entryType = type },
                        label = { Text(type.shortUa, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    )
                }
            }

            // Date & Norm row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PickerTile(
                    label = "Дата (${TimeCalcEngine.getDayNameUa(date)})",
                    value = TimeCalcEngine.formatDateUa(date),
                    icon = Icons.Rounded.CalendarToday,
                    onClick = ::pickDate,
                    modifier = Modifier.weight(1f)
                )
                if (entryType == EntryType.SHIFT) {
                    PickerTile(
                        label = if (isWeekend) "Норма (вих 0:00)" else "Денна норма",
                        value = normStr,
                        icon = Icons.Rounded.Schedule,
                        onClick = { pickTime(normStr, "07:00") { normStr = it } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (entryType == EntryType.SHIFT) {
                // Start & End Time pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PickerTile(
                        label = "Початок зміни",
                        value = start.ifBlank { "--:--" },
                        icon = Icons.Rounded.Schedule,
                        accent = colors.emerald,
                        onClick = {
                            val nowStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                            pickTime(start, nowStr) { start = it }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PickerTile(
                        label = "Кінець зміни",
                        value = end.ifBlank { "Не завершено" },
                        icon = Icons.Rounded.Schedule,
                        accent = colors.coral,
                        onClick = {
                            val nowStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                            pickTime(end, nowStr) { end = it }
                        },
                        onClear = if (end.isNotBlank()) ({ end = "" }) else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live Shift Preview Banner
                if (previewShift.isComplete) {
                    val dailyDelta = previewShift.workedMinutes - effectiveNorm
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (previewShift.isOvernight) "🌙 Нічна • " else "") +
                                "Факт: ${TimeCalcEngine.formatMinutes(previewShift.workedMinutes)}",
                            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Δ ${TimeCalcEngine.formatMinutes(dailyDelta, showPlus = true)}",
                            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.ExtraBold),
                            color = if (dailyDelta >= 0) colors.emerald else colors.coral
                        )
                    }
                }

                // Pauses Section (Dynamic Unlimited Pauses!)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated.copy(alpha = 0.65f))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Перерви (${pauses.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Разом: ${TimeCalcEngine.formatMinutes(previewPauseMin)}",
                            style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.Bold),
                            color = colors.amber
                        )
                    }

                    pauses.forEachIndexed { idx, pause ->
                        val defaultPreset = when (idx) {
                            0 -> settings.defaultPause1Min
                            1 -> settings.defaultPause2Min
                            else -> settings.defaultPause3Min
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val nowStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                                    pickTime(pause.start, nowStr) { picked ->
                                        val autoEnd = pause.end.ifBlank {
                                            TimeCalcEngine.addMinutesToTimeStr(picked, defaultPreset)
                                        }
                                        pauses[idx] = pause.copy(start = picked, end = autoEnd)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "p${idx + 1}+ ${pause.start.ifBlank { "--:--" }}",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = colors.textPrimary
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val fallback = if (pause.start.isNotBlank()) {
                                        TimeCalcEngine.addMinutesToTimeStr(pause.start, defaultPreset)
                                    } else {
                                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                                    }
                                    pickTime(pause.end, fallback) { picked ->
                                        pauses[idx] = pause.copy(end = picked)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "p${idx + 1}- ${pause.end.ifBlank { "..." }}",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = colors.textPrimary
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (pause.start.isNotBlank()) {
                                        pauses[idx] = pause.copy(
                                            end = TimeCalcEngine.addMinutesToTimeStr(pause.start, defaultPreset)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("+${defaultPreset}м", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.emerald)
                            }

                            IconButton(
                                onClick = { pauses.removeAt(idx) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Видалити паузу", tint = colors.coral, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val nextIdx = pauses.size
                                val preset = when (nextIdx) {
                                    0 -> settings.defaultPause1Min
                                    1 -> settings.defaultPause2Min
                                    else -> settings.defaultPause3Min
                                }
                                val nowStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                                pauses.add(
                                    PauseInterval(
                                        start = nowStr,
                                        end = TimeCalcEngine.addMinutesToTimeStr(nowStr, preset)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Додати паузу", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (pauses.isEmpty()) {
                            OutlinedTextField(
                                value = manualPauseStr,
                                onValueChange = { manualPauseStr = it.filter { ch -> ch.isDigit() }.take(3) },
                                label = { Text("Або ручна (хв)", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else if (entryType == EntryType.OVERTIME) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.amberContainer.copy(alpha = 0.45f))
                        .border(1.dp, colors.amber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Коригування банку наднормових",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.amberOnContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = overSign > 0,
                            onClick = { overSign = 1 },
                            label = { Text("+ Додати", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = overSign < 0,
                            onClick = { overSign = -1 },
                            label = { Text("− Відняти", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = overHoursStr,
                            onValueChange = { overHoursStr = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Годин") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = overMinsStr,
                            onValueChange = { overMinsStr = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Хвилин (0–59)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Note field (available for ALL entry types!)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Примітка (необов'язково)") },
                placeholder = { Text("Коментар, об'єкт, причина коригування...") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isEditingExisting) {
                    OutlinedButton(
                        onClick = { onDelete(initialRecord.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.coral),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Видалити")
                    }
                }
                Button(
                    onClick = {
                        val h = overHoursStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val m = overMinsStr.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        val overDelta = if (entryType == EntryType.OVERTIME) overSign * (h * 60 + m) else 0
                        val saved = initialRecord.copy(
                            date = date,
                            entryType = entryType,
                            start = if (entryType == EntryType.SHIFT) start else "",
                            end = if (entryType == EntryType.SHIFT) end else "",
                            normMinutes = parsedNormMin,
                            pauses = if (entryType == EntryType.SHIFT) pauses.filter { it.start.isNotBlank() || it.end.isNotBlank() } else emptyList(),
                            manualPauseMinutes = if (entryType == EntryType.SHIFT && pauses.isEmpty()) manualPauseMin else 0,
                            overtimeDeltaMinutes = overDelta,
                            note = note.trim()
                        )
                        onSave(saved)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.emerald,
                        contentColor = Color(0xFF022C22)
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(
                        text = if (isEditingExisting) "Зберегти зміни" else "Додати запис",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClear: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = accent ?: colors.textPrimary
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Очистити", tint = colors.textMuted, modifier = Modifier.size(16.dp))
            }
        } else {
            Icon(icon, contentDescription = null, tint = accent ?: colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}
