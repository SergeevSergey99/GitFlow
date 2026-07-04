package com.gitflow.android.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.gitflow.android.R
import com.gitflow.android.data.models.ChangeStatus
import com.gitflow.android.data.models.FileChange

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileChangeCard(
    file: FileChange,
    isStaged: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    isMenuExpanded: Boolean = false,
    onDismissMenu: (() -> Unit)? = null,
    menuContent: (@Composable ColumnScope.() -> Unit)? = null,
    onResolveConflict: ((FileChange) -> Unit)? = null,
    onAcceptOurs: ((FileChange) -> Unit)? = null,
    onAcceptTheirs: ((FileChange) -> Unit)? = null,
    /** Current branch name (ours) for the resolve buttons; null falls back to a generic label. */
    oursBranchLabel: String? = null,
    /** Incoming branch name (theirs) for the resolve buttons. */
    theirsBranchLabel: String? = null
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
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { onLongPress?.invoke() }
            )
    ) {
        Box {
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
                    StartEllipsizedText(
                        text = file.path,
                        modifier = Modifier.fillMaxWidth(),
                        style = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
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
                        ConflictResolveActions(
                            conflictCount = file.conflictSections,
                            onResolveConflict = onResolveConflict,
                            onAcceptOurs = onAcceptOurs,
                            onAcceptTheirs = onAcceptTheirs,
                            oursBranchLabel = oursBranchLabel,
                            theirsBranchLabel = theirsBranchLabel,
                            file = file
                        )
                    }
                }
            }

            if (menuContent != null && onDismissMenu != null) {
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = onDismissMenu
                ) {
                    menuContent()
                }
            }
        }
    }
}

@Composable
private fun ConflictResolveActions(
    conflictCount: Int,
    onResolveConflict: ((FileChange) -> Unit)?,
    onAcceptOurs: ((FileChange) -> Unit)?,
    onAcceptTheirs: ((FileChange) -> Unit)?,
    oursBranchLabel: String?,
    theirsBranchLabel: String?,
    file: FileChange
) {
    // Ours = current branch, theirs = the branch being merged/rebased in. Show the actual
    // branch names when known so this matches the conflict header and the diff view.
    val oursText = oursBranchLabel
        ?.let { stringResource(R.string.changes_conflict_ours_label, it) }
        ?: stringResource(R.string.file_change_accept_ours)
    val theirsText = theirsBranchLabel
        ?.let { stringResource(R.string.changes_conflict_theirs_label, it) }
        ?: stringResource(R.string.file_change_accept_theirs)

    // Single vertical layout so buttons keep a consistent width and don't skew.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (conflictCount > 0) {
                stringResource(R.string.file_change_conflicts_count, conflictCount)
            } else {
                stringResource(R.string.file_change_conflicts_unresolved)
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
        onResolveConflict?.let { resolver ->
            OutlinedButton(
                onClick = { resolver(file) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.file_change_show_conflicts))
            }
        }
        onAcceptOurs?.let { ours ->
            FilledTonalButton(
                onClick = { ours(file) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(oursText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        onAcceptTheirs?.let { theirs ->
            FilledTonalButton(
                onClick = { theirs(file) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(theirsText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
