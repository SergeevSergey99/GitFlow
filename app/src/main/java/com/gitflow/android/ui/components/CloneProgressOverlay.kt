package com.gitflow.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitflow.android.R
import com.gitflow.android.data.repository.CloneProgressTracker
import com.gitflow.android.data.repository.CloneStatus
import com.gitflow.android.data.repository.CloneTaskState
import com.gitflow.android.services.CloneRepositoryService
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun CloneProgressOverlay(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cloneMap by CloneProgressTracker.activeClones.collectAsState()
    val clones = remember(cloneMap) {
        cloneMap.values.sortedBy { it.startedAt }
    }

    if (clones.isEmpty()) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        clones.forEach { state ->
            CloneProgressCard(
                state = state,
                onCancel = { CloneRepositoryService.cancel(context) },
                onDismiss = { CloneProgressTracker.dismiss(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CloneProgressCard(
    state: CloneTaskState,
    onCancel: () -> Unit,
    onDismiss: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.repoName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.repoFullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (state.status != CloneStatus.RUNNING) {
                    IconButton(onClick = { onDismiss(state.key) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.clone_progress_dismiss)
                        )
                    }
                }
            }

            when (state.status) {
                CloneStatus.RUNNING -> {
                    val stage = state.progress.stage.ifBlank { stringResource(R.string.clone_progress_preparing) }
                    Text(
                        text = stage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.progress.total > 0) {
                        LinearProgressIndicator(
                            progress = state.progress.progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (state.progress.total > 0) {
                            Text(
                                text = "${state.progress.completed} / ${state.progress.total} (${(state.progress.progress * 100).roundToInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.clone_progress_calculating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.progress.estimatedTimeRemaining.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.clone_progress_eta, state.progress.estimatedTimeRemaining),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    state.approximateSize?.let { size ->
                        Text(
                            text = stringResource(R.string.clone_progress_download, formatSize(size)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onCancel) {
                            Text(text = stringResource(R.string.clone_progress_cancel))
                        }
                    }
                }

                CloneStatus.SUCCESS -> {
                    Text(
                        text = stringResource(R.string.clone_progress_completed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    state.approximateSize?.let { size ->
                        Text(
                            text = stringResource(R.string.clone_progress_downloaded, formatSize(size)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onDismiss(state.key) }) {
                            Text(text = stringResource(R.string.clone_progress_dismiss_button))
                        }
                    }
                }

                CloneStatus.FAILED -> {
                    Text(
                        text = state.errorMessage?.let { stringResource(R.string.clone_progress_failed, it) }
                            ?: stringResource(R.string.clone_progress_failed_generic),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onDismiss(state.key) }) {
                            Text(text = stringResource(R.string.clone_progress_dismiss_button))
                        }
                    }
                }

                CloneStatus.CANCELLED -> {
                    Text(
                        text = state.errorMessage ?: stringResource(R.string.clone_progress_cancelled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onDismiss(state.key) }) {
                            Text(text = stringResource(R.string.clone_progress_dismiss_button))
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(sizeBytes: Long): String {
    val megabytes = sizeBytes / 1024.0 / 1024.0
    return if (megabytes >= 1024) {
        String.format(Locale.getDefault(), "%.1f GB", megabytes / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", megabytes)
    }
}
