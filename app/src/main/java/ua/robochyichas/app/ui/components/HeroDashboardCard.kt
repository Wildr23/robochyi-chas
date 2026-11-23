package ua.robochyichas.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ComputedShift
import ua.robochyichas.app.domain.model.TimelineResult
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular

@Composable
fun HeroDashboardCard(
    timeline: TimelineResult,
    filteredShifts: List<ComputedShift>,
    selectedMonths: Set<String>,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val isSingleMonth = selectedMonths.size == 1
    val singleYm = selectedMonths.firstOrNull()

    val latestItem = filteredShifts.lastOrNull()
    val displayBankMin = latestItem?.cumulativeOvertimeMinutes ?: settings.initialBalanceMinutes
    val periodWorkedMin = filteredShifts.sumOf { if (it.isComplete) it.workedMinutes else 0 }
    val periodDeltaMin = if (isSingleMonth && singleYm != null) {
        val startBal = timeline.monthStartBalances[singleYm] ?: settings.initialBalanceMinutes
        displayBankMin - startBal
    } else {
        filteredShifts.sumOf { if (it.isComplete) it.dailyOvertimeMinutes else 0 }
    }

    val capMin = settings.carryOverCapMinutes.coerceAtLeast(1)
    val capRatioTarget = (displayBankMin.toFloat() / capMin.toFloat()).coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = capRatioTarget,
        animationSpec = tween(durationMillis = 450),
        label = "capProgress"
    )
    val isOverCap = displayBankMin > capMin
    val overCapMin = (displayBankMin - capMin).coerceAtLeast(0)

    val headerLabel = when {
        selectedMonths.isEmpty() -> "БАНК НАДНОРМОВИХ"
        isSingleMonth && singleYm != null -> "НАДНОРМОВІ • ${TimeCalcEngine.getMonthLabel(singleYm, true).uppercase()}"
        else -> "ОБРАНО МІСЯЦІВ: ${selectedMonths.size}"
    }

    val hintLine = if (isSingleMonth && singleYm != null) {
        val startBal = timeline.monthStartBalances[singleYm] ?: settings.initialBalanceMinutes
        "Старт міс: ${TimeCalcEngine.formatMinutes(startBal)}"
    } else {
        "Записів: ${filteredShifts.size}"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(colors.heroStart, colors.heroEnd)
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFF334155),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerLabel,
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TimeCalcEngine.formatMinutes(displayBankMin, withSeconds = true),
                        color = when {
                            displayBankMin < 0 -> Color(0xFFFDA4AF)
                            isOverCap -> Color(0xFFFDE047)
                            else -> Color(0xFFFCD34D)
                        },
                        style = MaterialTheme.typography.headlineLarge.tabular().copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val deltaColor = if (periodDeltaMin >= 0) Color(0xFF34D399) else Color(0xFFFB7185)
                    val deltaPrefix = if (isSingleMonth) "За місяць: " else "Приріст: "
                    Text(
                        text = "$deltaPrefix${TimeCalcEngine.formatMinutes(periodDeltaMin, showPlus = true)}",
                        color = deltaColor,
                        style = MaterialTheme.typography.labelMedium.tabular().copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Факт за період",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = TimeCalcEngine.formatMinutes(periodWorkedMin),
                        color = Color(0xFF34D399),
                        style = MaterialTheme.typography.titleLarge.tabular().copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hintLine,
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontSize = 11.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Carry-over cap progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ліміт перенесення: ${TimeCalcEngine.formatMinutes(capMin)}",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelMedium.tabular().copy(fontSize = 11.sp)
                )
                if (isOverCap) {
                    Text(
                        text = "Понад ліміт +${TimeCalcEngine.formatMinutes(overCapMin)} (згорить)",
                        color = Color(0xFFFBBF24),
                        style = MaterialTheme.typography.labelMedium.tabular().copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    val pct = (capRatioTarget * 100).toInt()
                    Text(
                        text = "$pct%",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontSize = 11.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1E293B))
            ) {
                val barBrush = if (isOverCap) {
                    Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFF43F5E)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFFF59E0B)))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedRatio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(barBrush)
                )
            }
        }
    }
}
