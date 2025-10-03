package com.gitflow.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitflow.android.R
import com.gitflow.android.data.models.Repository
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RepositoryCard(
    repository: Repository,
    onClick: () -> Unit,
    onDelete: (Repository, Boolean) -> Unit = { _, _ -> }
) {
    var showDeleteMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repository.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountTree,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = repository.currentBranch,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = timeAgo(repository.lastUpdated),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showDeleteMenu = true }
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.repo_card_options))
                }

                DropdownMenu(
                    expanded = showDeleteMenu,
                    onDismissRequest = { showDeleteMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.repo_card_remove)) },
                        onClick = {
                            showDeleteMenu = false
                            onDelete(repository, false)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.repo_card_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showDeleteMenu = false
                            onDelete(repository, true)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun timeAgo(timestamp: Long): String {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> context.getString(R.string.repo_card_time_just_now)
        diff < 3_600_000L -> context.getString(R.string.repo_card_time_minutes, diff / 60_000L)
        diff < 86_400_000L -> context.getString(R.string.repo_card_time_hours, diff / 3_600_000L)
        diff < 604_800_000L -> context.getString(R.string.repo_card_time_days, diff / 86_400_000L)
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}