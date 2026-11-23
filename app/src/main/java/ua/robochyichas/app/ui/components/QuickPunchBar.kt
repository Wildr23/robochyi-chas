package ua.robochyichas.app.ui.components

import android.app.TimePickerDialog
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.engine.TimeCalcEngine
import ua.robochyichas.app.domain.model.AppSettings
import ua.robochyichas.app.domain.model.ComputedShift
import ua.robochyichas.app.ui.TodayShiftPhase
import ua.robochyichas.app.ui.theme.LocalAppColors
import ua.robochyichas.app.ui.theme.tabular
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun QuickPunchBar(
    phase: TodayShiftPhase,
    todayShift: ComputedShift?,
    liveShiftSeconds: Long,
    livePauseSeconds: Long,
    livePauseRemainingSeconds: Long,
    activePauseNumber: Int?,
    activePauseReturnTime: String?,
    settings: AppSettings,
    onStartOrResume: () -> Unit,
    onStartAutoPause: () -> Unit,
    onFinishPauseNow: () -> Unit,
    onEditLastPauseEnd: (String) -> Unit,
    onEndShift: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    fun openLastPauseEndPicker(currentEnd: String?) {
        val parsed = runCatching {
            LocalTime.parse(currentEnd.orEmpty(), DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrNull() ?: LocalTime.now()
        TimePickerDialog(
            context,
            { _, h, m -> onEditLastPauseEnd("%02d:%02d".format(h, m)) },
            parsed.hour,
            parsed.minute,
            true
        ).show()
    }

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
                        Text("Почати зміну", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                }
            }

            TodayShiftPhase.WORKING -> {
                val pauses = todayShift?.shift?.pauses.orEmpty()
                val nextIndex = pauses.size
                val nextNum = nextIndex + 1
                val nextPreset = TimeCalcEngine.defaultPauseDurationForIndex(nextIndex, settings)

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
                            onStartAutoPause()
                        },
                        modifier = Modifier.weight(1.35f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.amber,
                            contentColor = Color(0xFF271300)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Пауза $nextNum (+${nextPreset}хв)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndShift()
                        },
                        modifier = Modifier.weight(0.65f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.coral,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Кінець", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                }

                if (pauses.isNotEmpty()) {
                    val lastPause = pauses.last()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated.copy(alpha = 0.65f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Остання: p${pauses.size} ${lastPause.start}–${lastPause.end.ifBlank { "..." }}",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = colors.textSecondary
                        )
                        OutlinedButton(
                            onClick = { openLastPauseEndPicker(lastPause.end) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.sky)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Змінити кінець", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.sky, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            TodayShiftPhase.ON_PAUSE -> {
                val pNum = activePauseNumber ?: 1
                val lastPauseEnd = todayShift?.shift?.pauses?.lastOrNull()?.end
                val isScheduled = !activePauseReturnTime.isNullOrBlank() && livePauseRemainingSeconds > 0L

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.amber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isScheduled) {
                                    "ПЕРЕРВА #$pNum • Повернутись о $activePauseReturnTime"
                                } else {
                                    "ПЕРЕРВА #$pNum ТРИВАЄ"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.amber
                            )
                            val timerLabel = if (isScheduled) {
                                val remMin = livePauseRemainingSeconds / 60L
                                val remSec = livePauseRemainingSeconds % 60L
                                "залишилось ${remMin.toString().padStart(2, '0')}:${remSec.toString().padStart(2, '0')}"
                            } else {
                                TimeCalcEngine.formatDurationWithSeconds(livePauseSeconds)
                            }
                            Text(
                                text = timerLabel,
                                style = MaterialTheme.typography.titleLarge.tabular().copy(
                                    fontSize = 22.sp,
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
                            onFinishPauseNow()
                        },
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.sky,
                            contentColor = Color(0xFF082F49)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Завершити зараз", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = { openLastPauseEndPicker(lastPauseEnd) },
                        modifier = Modifier.weight(0.75f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.amber)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Кінець", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.textPrimary, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndShift()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.coral,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Кінець зміни", modifier = Modifier.size(18.dp))
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
                        Text("Відновити", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sky, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
