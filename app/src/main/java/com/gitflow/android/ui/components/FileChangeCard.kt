package com.gitflow.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitflow.android.data.models.ChangeStatus
import com.gitflow.android.data.models.FileChange

@Composable
fun FileChangeCard(
    file: FileChange,
    isStaged: Boolean,
    onToggle: () -> Unit,
    onResolveConflict: ((FileChange) -> Unit)? = null,
    onAcceptOurs: ((FileChange) -> Unit)? = null,
    onAcceptTheirs: ((FileChange) -> Unit)? = null
) {
    val statusColor = when (file.status) {
        ChangeStatus.ADDED -> Color(0xFF4CAF50)
        ChangeStatus.MODIFIED -> Color(0xFFFF9800)
        ChangeStatus.DELETED -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isStaged,
                onCheckedChange = { onToggle() }
            )

            Icon(
                when (file.status) {
                    ChangeStatus.ADDED -> Icons.Default.Add
                    ChangeStatus.MODIFIED -> Icons.Default.Edit
                    ChangeStatus.DELETED -> Icons.Default.Delete
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.path,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (file.additions > 0 || file.deletions > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "+${file.additions}",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "-${file.deletions}",
                            fontSize = 11.sp,
                            color = Color(0xFFF44336)
                        )
                    }
                }

                if (file.hasConflicts) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChipRow(
                        conflictCount = file.conflictSections,
                        onResolveConflict = onResolveConflict,
                        onAcceptOurs = onAcceptOurs,
                        onAcceptTheirs = onAcceptTheirs,
                        file = file
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistChipRow(
    conflictCount: Int,
    onResolveConflict: ((FileChange) -> Unit)?,
    onAcceptOurs: ((FileChange) -> Unit)?,
    onAcceptTheirs: ((FileChange) -> Unit)?,
    file: FileChange
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (conflictCount > 0) {
                "Конфликтов: $conflictCount"
            } else {
                "Есть нерешенные конфликты"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            onResolveConflict?.let { resolver ->
                AssistChip(
                    onClick = { resolver(file) },
                    label = { Text("Показать конфликты") },
                    leadingIcon = {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            onAcceptOurs?.let { ours ->
                FilledTonalButton(
                    onClick = { ours(file) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Версия текущей ветки")
                }
            }
            onAcceptTheirs?.let { theirs ->
                OutlinedButton(
                    onClick = { theirs(file) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Версия удаленной ветки")
                }
            }
        }
    }
}
