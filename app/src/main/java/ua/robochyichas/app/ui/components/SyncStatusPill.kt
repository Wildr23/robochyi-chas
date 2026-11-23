package ua.robochyichas.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.robochyichas.app.domain.model.SyncState
import ua.robochyichas.app.domain.model.SyncStatus
import ua.robochyichas.app.ui.theme.LocalAppColors

@Composable
fun SyncStatusPill(
    syncState: SyncState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pendingSuffix = if (syncState.pendingChangesCount > 0) " (${syncState.pendingChangesCount})" else ""

    val (bg, fg, icon, label) = when (syncState.status) {
        SyncStatus.SYNCED -> Quadruple(
            colors.emeraldContainer,
            colors.emeraldOnContainer,
            Icons.Rounded.CloudDone,
            "Drive OK"
        )
        SyncStatus.OFFLINE_QUEUED -> Quadruple(
            colors.amberContainer,
            colors.amberOnContainer,
            Icons.Rounded.CloudOff,
            "Офлайн$pendingSuffix"
        )
        SyncStatus.SYNCING -> Quadruple(
            colors.skyContainer,
            colors.skyOnContainer,
            Icons.Rounded.Sync,
            "Синхр..."
        )
        SyncStatus.AUTH_REQUIRED -> Quadruple(
            colors.coralContainer,
            colors.coralOnContainer,
            Icons.Rounded.WarningAmber,
            "Вхід Drive"
        )
        SyncStatus.ERROR -> Quadruple(
            colors.coralContainer,
            colors.coralOnContainer,
            Icons.Rounded.ErrorOutline,
            "Помилка$pendingSuffix"
        )
        SyncStatus.LOCAL_ONLY -> Quadruple(
            colors.surfaceElevated,
            colors.textSecondary,
            Icons.Rounded.CloudQueue,
            "Локально"
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier
                .size(15.dp)
                .then(if (syncState.status == SyncStatus.SYNCING) Modifier.rotate(rotation) else Modifier)
        )
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
