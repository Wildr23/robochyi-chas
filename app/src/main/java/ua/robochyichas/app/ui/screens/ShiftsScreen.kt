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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.ui.WorkTimeUiState
import ua.robochyichas.app.ui.components.HeroDashboardCard
import ua.robochyichas.app.ui.components.QuickPunchBar
import ua.robochyichas.app.ui.components.ShiftCard
import ua.robochyichas.app.ui.components.SyncStatusPill
import ua.robochyichas.app.ui.theme.LocalAppColors

@Composable
fun ShiftsScreen(
    state: WorkTimeUiState,
    onSyncPillClick: () -> Unit,
    onStartOrResumeShift: () -> Unit,
    onStartAutoPause: () -> Unit,
    onFinishPauseNow: () -> Unit,
    onEditLastPauseEnd: (String) -> Unit,
    onEndShift: () -> Unit,
    onToggleMonth: (String) -> Unit,
    onToggleSelectAllMonths: () -> Unit,
    onEntryTypeFilterChange: (EntryType?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onShiftClick: (ShiftRecord) -> Unit,
    onDeleteShift: (Long) -> Unit,
    onAddNewShift: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    var showSearch by remember { mutableStateOf(false) }

    val filteredReversed = remember(state.filteredShifts) {
        state.filteredShifts.asReversed()
    }
    val monthsNewestFirst = remember(state.timeline.monthSummaries) {
        state.timeline.monthSummaries.asReversed()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.emerald),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = Color(0xFF022C22),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Робочий час",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Записів: ${state.timeline.shifts.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SyncStatusPill(
                        syncState = state.syncState,
                        onClick = onSyncPillClick
                    )
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) onSearchQueryChange("")
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Пошук",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Optional Search Input
        item {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Пошук за датою, приміткою, типом...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Очистити")
                            }
                        }
                    }
                )
            }
        }

        // Hero Overtime Bank Card
        item {
            HeroDashboardCard(
                timeline = state.timeline,
                filteredShifts = state.filteredShifts,
                selectedMonths = state.selectedMonths,
                settings = state.settings
            )
        }

        // Live Quick Punch Bar
        item {
            QuickPunchBar(
                phase = state.todayPhase,
                todayShift = state.todayShift,
                liveShiftSeconds = state.liveShiftElapsedSeconds,
                livePauseSeconds = state.livePauseElapsedSeconds,
                livePauseRemainingSeconds = state.livePauseRemainingSeconds,
                activePauseNumber = state.activePauseNumber,
                activePauseReturnTime = state.activePauseReturnTime,
                settings = state.settings,
                onStartOrResume = onStartOrResumeShift,
                onStartAutoPause = onStartAutoPause,
                onFinishPauseNow = onFinishPauseNow,
                onEditLastPauseEnd = onEditLastPauseEnd,
                onEndShift = onEndShift
            )
        }

        // Month & Type Filters
        if (monthsNewestFirst.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Місяці (можна обрати кілька)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted
                        )
                        val allSelected = state.selectedMonths.size == monthsNewestFirst.size
                        TextButton(
                            onClick = onToggleSelectAllMonths,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (allSelected) "Лише останній" else "Обрати всі (${monthsNewestFirst.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = colors.emerald
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(monthsNewestFirst, key = { it.yearMonth }) { summary ->
                            val selected = summary.yearMonth in state.selectedMonths
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.emerald else colors.surfaceCard)
                                    .border(
                                        1.dp,
                                        if (selected) colors.emerald else colors.borderSubtle,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onToggleMonth(summary.yearMonth) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${if (selected) "✓ " else ""}${summary.labelShort} (${summary.shiftCount})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) Color(0xFF022C22) else colors.textSecondary
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedEntryTypeFilter == null,
                                onClick = { onEntryTypeFilterChange(null) },
                                label = { Text("Усі типи", fontSize = 11.sp) }
                            )
                        }
                        items(EntryType.entries) { type ->
                            FilterChip(
                                selected = state.selectedEntryTypeFilter == type,
                                onClick = { onEntryTypeFilterChange(type) },
                                label = { Text(type.shortUa, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Empty State or Shift Cards
        if (filteredReversed.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(22.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "⏱",
                        fontSize = 38.sp
                    )
                    Text(
                        text = if (state.timeline.shifts.isEmpty()) "Поки що немає записів" else "Нічого не знайдено",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (state.timeline.shifts.isEmpty()) {
                            "Натисніть «Почати зміну» зверху або кнопку «+», щоб додати першу зміну чи імпортувати резервну копію у вкладці «Хмара»."
                        } else {
                            "Змініть фільтр місяців, тип запису або пошуковий запит."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center
                    )
                    if (state.timeline.shifts.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onAddNewShift,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.emerald,
                                contentColor = Color(0xFF022C22)
                            )
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Додати запис", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(filteredReversed, key = { it.shift.uuid }) { item ->
                ShiftCard(
                    item = item,
                    onClick = { onShiftClick(item.shift) },
                    onDelete = { onDeleteShift(item.shift.id) }
                )
            }
        }
    }
}
