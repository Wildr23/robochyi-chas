package ua.robochyichas.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ua.robochyichas.app.ui.FolderPickerState
import ua.robochyichas.app.ui.theme.LocalAppColors

@Composable
fun DriveFolderPickerDialog(
    pickerState: FolderPickerState,
    onDismiss: () -> Unit,
    onNavigateInto: (id: String, name: String) -> Unit,
    onNavigateBreadcrumb: (index: Int) -> Unit,
    onCreateSubfolder: (name: String) -> Unit,
    onRetryLoad: () -> Unit,
    onSelectCurrentFolder: () -> Unit
) {
    val colors = LocalAppColors.current
    var showCreateBox by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Папка на Google Диску",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрити", tint = colors.textMuted)
                }
            }

            // Breadcrumbs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(pickerState.breadcrumbs) { idx, crumb ->
                    val isLast = idx == pickerState.breadcrumbs.lastIndex
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isLast) colors.emeraldContainer else colors.surfaceElevated)
                                .clickable { onNavigateBreadcrumb(idx) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = crumb.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isLast) colors.emeraldOnContainer else colors.textSecondary
                            )
                        }
                        if (!isLast) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // New subfolder toggle + input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Папки додатку на Диску • «Нова папка» створить власну",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showCreateBox = !showCreateBox },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.amber)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Нова папка", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.amber)
                }
            }

            AnimatedVisibility(visible = showCreateBox) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Назва папки") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val trimmed = newFolderName.trim()
                            if (trimmed.isNotEmpty()) {
                                onCreateSubfolder(trimmed)
                                newFolderName = ""
                                showCreateBox = false
                            }
                        },
                        enabled = newFolderName.trim().isNotEmpty() && !pickerState.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = colors.borderSubtle)

            // Folder list body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 280.dp)
            ) {
                when {
                    pickerState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.emerald)
                        }
                    }

                    pickerState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = pickerState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.coral
                            )
                            OutlinedButton(onClick = onRetryLoad) {
                                Text("Повторити")
                            }
                        }
                    }

                    pickerState.folders.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Створених додатком папок тут ще немає.\nНатисніть «Нова папка» або оберіть поточну (drive.file бачить лише папки додатку).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted
                            )
                        }
                    }

                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(pickerState.folders, key = { it.id }) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceElevated)
                                        .clickable { onNavigateInto(folder.id, folder.name) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = colors.amber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Скасувати")
                }
                Button(
                    onClick = onSelectCurrentFolder,
                    enabled = !pickerState.isLoading,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.emerald,
                        contentColor = Color(0xFF022C22)
                    )
                ) {
                    Text("Обрати цю папку", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
