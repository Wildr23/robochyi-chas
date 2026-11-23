package ua.robochyichas.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.ShiftRecord
import ua.robochyichas.app.ui.WorkTimeUiState
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableScreen(
    state: WorkTimeUiState,
    onShiftClick: (ShiftRecord) -> Unit,
    onExportCsvSave: () -> Unit,
    onExportCsvShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val hScroll = rememberScrollState()
    val rows = state.filteredShifts

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Таблиця змін",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                Text(
                    text = "Показано: ${rows.size} • Натисніть рядок для редагування",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onExportCsvSave,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.emerald)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
                OutlinedButton(
                    onClick = onExportCsvShare,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.sky)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
        ) {
            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Немає записів для таблиці",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll)
                ) {
                    stickyHeader {
                        Row(
                            modifier = Modifier
                                .background(colors.surfaceElevated)
                                .border(0.5.dp, colors.borderSubtle)
                        ) {
                            HeaderCell("№", 38.dp)
                            HeaderCell("День", 46.dp)
                            HeaderCell("Дата", 76.dp)
                            HeaderCell("Тип", 72.dp)
                            HeaderCell("Почат", 64.dp)
                            HeaderCell("Кінець", 64.dp)
                            HeaderCell("Пауза", 68.dp)
                            HeaderCell("Факт", 72.dp)
                            HeaderCell("Норма", 68.dp)
                            HeaderCell("Δ Щод", 76.dp)
                            HeaderCell("Банк", 82.dp)
                            HeaderCell("p1+", 56.dp)
                            HeaderCell("p1-", 56.dp)
                            HeaderCell("p2+", 56.dp)
                            HeaderCell("p2-", 56.dp)
                            HeaderCell("p3+", 56.dp)
                            HeaderCell("p3-", 56.dp)
                            HeaderCell("Примітка", 150.dp)
                        }
                    }

                    items(rows, key = { it.shift.uuid }) { item ->
                        val s = item.shift
                        val p1 = s.pauses.getOrNull(0)
                        val p2 = s.pauses.getOrNull(1)
                        val p3 = s.pauses.getOrNull(2)
                        val normStr = if (item.isWeekend || s.entryType != EntryType.SHIFT) {
                            "0:00:00"
                        } else {
                            TimeCalcEngine.formatMinutes(item.effectiveNormMinutes, withSeconds = true)
                        }

                        val dailyBg = when {
                            !item.isComplete -> Color.Transparent
                            item.dailyOvertimeMinutes < 0 -> colors.coralContainer
                            else -> colors.emeraldContainer
                        }
                        val dailyFg = when {
                            !item.isComplete -> colors.textMuted
                            item.dailyOvertimeMinutes < 0 -> colors.coralOnContainer
                            else -> colors.emeraldOnContainer
                        }

                        Row(
                            modifier = Modifier
                                .clickable { onShiftClick(s) }
                                .border(0.5.dp, colors.borderSubtle.copy(alpha = 0.5f))
                        ) {
                            DataCell(item.rowNum.toString(), 38.dp, colors.textMuted)
                            DataCell(
                                item.dayNameUa,
                                46.dp,
                                if (item.isWeekend) colors.sky else colors.textPrimary,
                                bold = true
                            )
                            DataCell(item.dateUa, 76.dp, colors.textPrimary, bold = true)
                            DataCell(s.entryType.shortUa, 72.dp, colors.textSecondary)
                            DataCell(
                                if (s.entryType == EntryType.SHIFT) s.start else "—",
                                64.dp,
                                if (s.entryType == EntryType.SHIFT && s.start.isNotBlank()) colors.emeraldOnContainer else colors.textMuted,
                                bg = if (s.entryType == EntryType.SHIFT && s.start.isNotBlank()) colors.emeraldContainer.copy(alpha = 0.5f) else Color.Transparent,
                                bold = true
                            )
                            DataCell(
                                if (s.entryType == EntryType.SHIFT) s.end else "—",
                                64.dp,
                                if (s.entryType == EntryType.SHIFT && s.end.isNotBlank()) colors.emeraldOnContainer else colors.textMuted,
                                bg = if (s.entryType == EntryType.SHIFT && s.end.isNotBlank()) colors.emeraldContainer.copy(alpha = 0.5f) else Color.Transparent,
                                bold = true
                            )
                            DataCell(TimeCalcEngine.formatMinutes(item.pauseMinutes, true), 68.dp, colors.textSecondary)
                            DataCell(
                                if (item.isComplete) TimeCalcEngine.formatMinutes(item.workedMinutes, true) else "—",
                                72.dp,
                                colors.textPrimary,
                                bold = true
                            )
                            DataCell(normStr, 68.dp, colors.textMuted)
                            DataCell(
                                if (item.isComplete) TimeCalcEngine.formatMinutes(item.dailyOvertimeMinutes, true, showPlus = true) else "—",
                                76.dp,
                                dailyFg,
                                bg = dailyBg,
                                bold = true
                            )
                            DataCell(
                                TimeCalcEngine.formatMinutes(item.cumulativeOvertimeMinutes, true),
                                82.dp,
                                colors.amberOnContainer,
                                bg = colors.amberContainer.copy(alpha = 0.85f),
                                bold = true
                            )
                            DataCell(p1?.start.orEmpty(), 56.dp, colors.textSecondary)
                            DataCell(p1?.end.orEmpty(), 56.dp, colors.textSecondary)
                            DataCell(p2?.start.orEmpty(), 56.dp, colors.textSecondary)
                            DataCell(p2?.end.orEmpty(), 56.dp, colors.textSecondary)
                            DataCell(p3?.start.orEmpty(), 56.dp, colors.textSecondary)
                            DataCell(p3?.end.orEmpty(), 56.dp, colors.textSecondary)
                            DataCell(s.note, 150.dp, colors.textMuted, alignLeft = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    val colors = LocalAppColors.current
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
        color = colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DataCell(
    text: String,
    width: Dp,
    fg: Color,
    bg: Color = Color.Transparent,
    bold: Boolean = false,
    alignLeft: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        textAlign = if (alignLeft) TextAlign.Start else TextAlign.Center,
        style = MaterialTheme.typography.labelMedium.tabular().copy(
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp
        ),
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
