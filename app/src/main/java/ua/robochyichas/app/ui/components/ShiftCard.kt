package ua.robochyichas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.ComputedShift
import ua.robochyichas.app.domain.model.EntryType
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftCard(
    item: ComputedShift,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val s = item.shift
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Видалити запис?") },
            text = {
                Text("Запис за ${item.dateUa} (${item.dayNameUa}) буде видалено.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.coral)
                ) {
                    Text("Видалити", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    val dailyBg = when {
        !item.isComplete -> colors.amberContainer
        item.dailyOvertimeMinutes < 0 -> colors.coralContainer
        else -> colors.emeraldContainer
    }
    val dailyFg = when {
        !item.isComplete -> colors.amberOnContainer
        item.dailyOvertimeMinutes < 0 -> colors.coralOnContainer
        else -> colors.emeraldOnContainer
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // Top header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.rowNum.toString(),
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.Bold),
                        color = colors.textSecondary
                    )
                }

                Text(
                    text = item.dateUa,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = if (item.isWeekend) colors.sky else colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (item.isWeekend) colors.skyContainer else colors.surfaceElevated)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.dayNameUa,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (item.isWeekend) colors.skyOnContainer else colors.textSecondary
                    )
                }

                if (item.isOvernight) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(colors.skyContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Rounded.NightsStay,
                            contentDescription = "Нічна зміна",
                            tint = colors.skyOnContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "+1д",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = colors.skyOnContainer
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(dailyBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (item.isComplete) {
                            TimeCalcEngine.formatMinutes(item.dailyOvertimeMinutes, showPlus = true)
                        } else {
                            "В процесі"
                        },
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.Bold),
                        color = dailyFg
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.amberContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = TimeCalcEngine.formatMinutes(item.cumulativeOvertimeMinutes),
                        style = MaterialTheme.typography.labelMedium.tabular().copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.amberOnContainer
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Видалити",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Body based on EntryType
        when (s.entryType) {
            EntryType.SHIFT -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Зміна", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = colors.textMuted)
                        Text(
                            text = "${s.start.ifBlank { "--:--" }} – ${s.end.ifBlank { "..." }}",
                            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.Bold),
                            color = colors.emerald
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Пауза", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = colors.textMuted)
                        Text(
                            text = TimeCalcEngine.formatMinutes(item.pauseMinutes),
                            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Факт / Норма", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = colors.textMuted)
                        val workedStr = if (item.isComplete) TimeCalcEngine.formatMinutes(item.workedMinutes) else "--:--"
                        val normStr = if (item.isWeekend) "0:00 (вих)" else TimeCalcEngine.formatMinutes(item.effectiveNormMinutes)
                        Text(
                            text = "$workedStr / $normStr",
                            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                    }
                }

                if (s.pauses.isNotEmpty() || (s.pauses.isEmpty() && s.manualPauseMinutes > 0)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        s.pauses.forEachIndexed { idx, p ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (p.isOpen) colors.amberContainer else colors.surfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "p${idx + 1}: ${p.start.ifBlank { "?" }}–${p.end.ifBlank { "..." }}",
                                    style = MaterialTheme.typography.labelMedium.tabular().copy(fontSize = 11.sp),
                                    color = if (p.isOpen) colors.amberOnContainer else colors.textMuted
                                )
                            }
                        }
                        if (s.pauses.isEmpty() && s.manualPauseMinutes > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Ручна пауза: ${s.manualPauseMinutes} хв",
                                    style = MaterialTheme.typography.labelMedium.tabular().copy(fontSize = 11.sp),
                                    color = colors.textMuted
                                )
                            }
                        }
                    }
                }
            }

            EntryType.OVERTIME -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.amberContainer.copy(alpha = 0.65f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ Коригування наднормових",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.amberOnContainer
                        )
                        if (s.note.isNotBlank()) {
                            Text(
                                text = s.note,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Факт: 0:00",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = colors.textMuted
                    )
                }
            }

            EntryType.VACATION, EntryType.SICK_LEAVE, EntryType.DAY_OFF -> {
                val badge = when (s.entryType) {
                    EntryType.VACATION -> "🌴 Відпустка"
                    EntryType.SICK_LEAVE -> "🏥 Лікарняний"
                    else -> "🛌 Вихідний / Відгул"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.skyContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.skyOnContainer
                    )
                    Text(
                        text = "Норма: 0:00",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = colors.skyOnContainer
                    )
                }
            }
        }

        if (s.entryType != EntryType.OVERTIME && s.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "📝 ${s.note}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
