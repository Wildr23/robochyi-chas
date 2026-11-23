package ua.robochyichas.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.domain.model.MonthSummary
import ua.robochyichas.app.ui.WorkTimeUiState
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular

@Composable
fun SummaryScreen(
    state: WorkTimeUiState,
    onMonthClick: (String) -> Unit,
    onMarkFirstMonth: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val summariesChrono = state.timeline.monthSummaries
    val summariesNewestFirst = summariesChrono.asReversed()
    val allComputed = state.timeline.shifts

    val totalWorked = allComputed.sumOf { if (it.isComplete) it.workedMinutes else 0 }
    val totalPause = allComputed.sumOf { if (it.isComplete) it.pauseMinutes else 0 }
    val totalOverDelta = allComputed.sumOf { if (it.isComplete) it.dailyOvertimeMinutes else 0 }
    val workShiftCount = allComputed.count { it.shift.entryType == EntryType.SHIFT && it.isComplete }
    val avgShift = if (workShiftCount > 0) totalWorked / workShiftCount else 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Підсумки та аналітика",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
        }

        // Overall summary card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.heroStart)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ЗАГАЛОМ ЗА ${summariesChrono.size} МІС. ($workShiftCount РОБОЧИХ ЗМІН)",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF94A3B8)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverallStatTile(
                        label = "Відпрацьовано",
                        value = TimeCalcEngine.formatMinutes(totalWorked),
                        valueColor = Color(0xFF34D399),
                        modifier = Modifier.weight(1f)
                    )
                    OverallStatTile(
                        label = "Паузи",
                        value = TimeCalcEngine.formatMinutes(totalPause),
                        valueColor = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )
                    OverallStatTile(
                        label = "Середня зміна",
                        value = TimeCalcEngine.formatMinutes(avgShift),
                        valueColor = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f)
                    )
                    OverallStatTile(
                        label = "Наднормові Σ",
                        value = TimeCalcEngine.formatMinutes(totalOverDelta, showPlus = true),
                        valueColor = Color(0xFFFCD34D),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Custom Compose Canvas Monthly Bar Chart
        if (summariesChrono.isNotEmpty()) {
            item {
                MonthlyBarChartCard(summaries = summariesChrono.takeLast(8))
            }
        }

        if (summariesNewestFirst.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Додайте зміни, щоб побачити місячну аналітику",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            }
        } else {
            items(summariesNewestFirst, key = { it.yearMonth }) { m ->
                MonthSummaryCard(
                    summary = m,
                    onClick = { onMonthClick(m.yearMonth) },
                    onMarkFirstMonth = { onMarkFirstMonth(m.yearMonth) }
                )
            }
        }
    }
}

@Composable
private fun OverallStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabular().copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun MonthlyBarChartCard(summaries: List<MonthSummary>) {
    val colors = LocalAppColors.current
    val maxWorked = summaries.maxOfOrNull { it.workedMinutes }?.coerceAtLeast(60) ?: 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Динаміка відпрацьованих годин",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = "Макс: ${TimeCalcEngine.formatMinutes(maxWorked)}",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = colors.emerald
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val count = summaries.size.coerceAtLeast(1)
            val slotW = size.width / count
            val barW = (slotW * 0.52f).coerceIn(16.dp.toPx(), 44.dp.toPx())
            val chartH = size.height - 8.dp.toPx()

            // Subtle baseline
            drawLine(
                color = colors.borderSubtle,
                start = Offset(0f, chartH),
                end = Offset(size.width, chartH),
                strokeWidth = 1.dp.toPx()
            )

            summaries.forEachIndexed { idx, item ->
                val ratio = (item.workedMinutes.toFloat() / maxWorked.toFloat()).coerceIn(0.04f, 1f)
                val barH = chartH * ratio
                val x = idx * slotW + (slotW - barW) / 2f
                val y = chartH - barH
                val barColor = if (item.monthDeltaMinutes >= 0) colors.emerald else colors.amber

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            summaries.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.labelShort.substringBefore(" "),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = colors.textSecondary
                    )
                    Text(
                        text = "${item.workedMinutes / 60}г",
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontSize = 10.sp),
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(
    summary: MonthSummary,
    onClick: () -> Unit,
    onMarkFirstMonth: () -> Unit
) {
    val colors = LocalAppColors.current
    val isPos = summary.monthDeltaMinutes >= 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = summary.labelFull,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "(${summary.shiftCount})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (summary.isFirstWorkMonth) colors.emeraldContainer else colors.surfaceElevated)
                        .clickable(onClick = onMarkFirstMonth)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (summary.isFirstWorkMonth) "★ 1-й робочий міс (0:00)" else "Зробити 1-м міс",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = if (summary.isFirstWorkMonth) colors.emeraldOnContainer else colors.textMuted
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isPos) colors.emeraldContainer else colors.coralContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = TimeCalcEngine.formatMinutes(summary.monthDeltaMinutes, showPlus = true),
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.Bold),
                        color = if (isPos) colors.emeraldOnContainer else colors.coralOnContainer
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.amberContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = TimeCalcEngine.formatMinutes(summary.endBalanceMinutes),
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.amberOnContainer
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceElevated.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Старт → Кінець", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = colors.textMuted)
                Text(
                    text = "${TimeCalcEngine.formatMinutes(summary.startBalanceMinutes)} → ${TimeCalcEngine.formatMinutes(summary.endBalanceMinutes)}",
                    style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Факт годин", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = colors.textMuted)
                Text(
                    text = TimeCalcEngine.formatMinutes(summary.workedMinutes),
                    style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.emerald
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Паузи", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = colors.textMuted)
                Text(
                    text = TimeCalcEngine.formatMinutes(summary.pauseMinutes),
                    style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary
                )
            }
        }

        if (summary.cappedLostMinutes > 0) {
            Text(
                text = "⚠ При переході на наступний місяць зрізано понад ліміт: ${TimeCalcEngine.formatMinutes(summary.cappedLostMinutes)}",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = colors.amber
            )
        }
    }
}
