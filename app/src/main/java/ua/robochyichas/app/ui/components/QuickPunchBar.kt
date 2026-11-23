package ua.robochyichas.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.ComputedShift
import ua.robochyichas.app.ui.TodayShiftPhase
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular

@Composable
fun QuickPunchBar(
    phase: TodayShiftPhase,
    todayShift: ComputedShift?,
    liveShiftSeconds: Long,
    livePauseSeconds: Long,
    defaultPause1Min: Int,
    onStartOrResume: () -> Unit,
    onStartPause: (presetMinutes: Int?) -> Unit,
    onEndPause: () -> Unit,
    onEndShift: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current

    val borderCol by animateColorAsState(
        targetValue = when (phase) {
            TodayShiftPhase.WORKING -> colors.emerald.copy(alpha = 0.6f)
            TodayShiftPhase.ON_PAUSE -> colors.amber.copy(alpha = 0.7f)
            TodayShiftPhase.COMPLETED -> colors.sky.copy(alpha = 0.4f)
            TodayShiftPhase.IDLE -> colors.borderSubtle
        },
        label = "punchBorder"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .border(1.dp, borderCol, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        when (phase) {
            TodayShiftPhase.IDLE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сьогоднішня зміна",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Натисніть, щоб зафіксувати старт",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted
                        )
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartOrResume()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.emerald,
                            contentColor = Color(0xFF022C22)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Почати зміну", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }

            TodayShiftPhase.WORKING -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.emerald)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Зміна триває (з ${todayShift?.shift?.start.orEmpty()})",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.emerald
                            )
                            Text(
                                text = TimeCalcEngine.formatDurationWithSeconds(liveShiftSeconds),
                                style = MaterialTheme.typography.titleLarge.tabular().copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = colors.textPrimary
                            )
                        }
                    }
                    if ((todayShift?.pauseMinutes ?: 0) > 0) {
                        Text(
                            text = "Пауз: ${TimeCalcEngine.formatMinutes(todayShift?.pauseMinutes)}",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartPause(null)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.amber,
                            contentColor = Color(0xFF271300)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Пауза", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartPause(defaultPause1Min)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.amber)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+${defaultPause1Min}хв", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.textPrimary)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndShift()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.coral,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Кінець", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            TodayShiftPhase.ON_PAUSE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.amber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ПЕРЕРВА ТРИВАЄ",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.amber
                            )
                            Text(
                                text = TimeCalcEngine.formatDurationWithSeconds(livePauseSeconds),
                                style = MaterialTheme.typography.titleLarge.tabular().copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = colors.amberBright
                            )
                        }
                    }
                    Text(
                        text = "Зміна: ${TimeCalcEngine.formatDurationWithSeconds(liveShiftSeconds)}",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndPause()
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.sky,
                            contentColor = Color(0xFF082F49)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Продовжити роботу", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndShift()
                        },
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.coral,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Кінець", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            TodayShiftPhase.COMPLETED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сьогодні завершено (${todayShift?.shift?.start}–${todayShift?.shift?.end})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.emerald
                        )
                        Text(
                            text = "Факт: ${TimeCalcEngine.formatMinutes(todayShift?.workedMinutes)} • Пауза: ${TimeCalcEngine.formatMinutes(todayShift?.pauseMinutes)}",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = colors.textSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartOrResume()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.sky)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Відновити", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sky)
                    }
                }
            }
        }
    }
}
