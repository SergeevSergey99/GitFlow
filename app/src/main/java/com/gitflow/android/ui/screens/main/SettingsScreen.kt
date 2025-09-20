package com.gitflow.android.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitUser
import com.gitflow.android.ui.components.dialogs.GraphPresetDialog

@Composable
fun SettingsScreen(
    selectedGraphPreset: String,
    onGraphPresetChanged: (String) -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }

    // Получаем информацию о пользователях
    val githubUser = authManager.getCurrentUser(GitProvider.GITHUB)
    val gitlabUser = authManager.getCurrentUser(GitProvider.GITLAB)
    val hasAnyAuth = githubUser != null || gitlabUser != null

    var showGraphPresetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AccountSection(
            githubUser = githubUser,
            gitlabUser = gitlabUser,
            hasAnyAuth = hasAnyAuth,
            onManageAccountsClick = {
                navController.navigate("auth")
            }
        )

        GraphSettingsSection(
            selectedGraphPreset = selectedGraphPreset,
            onPresetClick = { showGraphPresetDialog = true }
        )

        AboutSection()
    }

    // Диалог выбора пресета графа
    if (showGraphPresetDialog) {
        GraphPresetDialog(
            currentPreset = selectedGraphPreset,
            onPresetSelected = { preset ->
                onGraphPresetChanged(preset)
                showGraphPresetDialog = false
            },
            onDismiss = { showGraphPresetDialog = false }
        )
    }
}

@Composable
private fun AccountSection(
    githubUser: GitUser?,
    gitlabUser: GitUser?,
    hasAnyAuth: Boolean,
    onManageAccountsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Account",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (hasAnyAuth) {
                // Показываем информацию об авторизованных аккаунтах
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    githubUser?.let { user ->
                        UserAccountRow(
                            user = user,
                            provider = GitProvider.GITHUB
                        )
                    }
                    gitlabUser?.let { user ->
                        UserAccountRow(
                            user = user,
                            provider = GitProvider.GITLAB
                        )
                    }
                }
            } else {
                // Показываем состояние "гость"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Guest User", fontWeight = FontWeight.Medium)
                        Text(
                            "Not signed in",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onManageAccountsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (hasAnyAuth) Icons.Default.Settings else Icons.Default.Login,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (hasAnyAuth) "Manage Accounts" else "Sign In")
            }
        }
    }
}

@Composable
private fun GraphSettingsSection(
    selectedGraphPreset: String,
    onPresetClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Graph Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPresetClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Graph Preset", fontWeight = FontWeight.Medium)
                    Text(
                        text = selectedGraphPreset,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose how the commit graph should be displayed",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "About",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("GitFlow for Android")
            Text(
                text = "Version 1.0.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A modern Git client for Android devices",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UserAccountRow(
    user: GitUser,
    provider: GitProvider
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = when (provider) {
                GitProvider.GITHUB -> Color(0xFF24292F).copy(alpha = 0.1f)
                GitProvider.GITLAB -> Color(0xFFFC6D26).copy(alpha = 0.1f)
            }
        ) {
            Icon(
                when (provider) {
                    GitProvider.GITHUB -> Icons.Default.Code
                    GitProvider.GITLAB -> Icons.Default.Storage
                },
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = when (provider) {
                    GitProvider.GITHUB -> Color(0xFF24292F)
                    GitProvider.GITLAB -> Color(0xFFFC6D26)
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name ?: user.login,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${provider.name} • @${user.login}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "Connected",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}