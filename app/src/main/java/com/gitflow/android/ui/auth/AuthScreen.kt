package com.gitflow.android.ui.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val githubUser by viewModel.githubUser.collectAsState()
    val gitlabUser by viewModel.gitlabUser.collectAsState()
    val bitbucketUser by viewModel.bitbucketUser.collectAsState()
    val giteaUser by viewModel.giteaUser.collectAsState()
    val azureUser by viewModel.azureUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val oauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(OAuthActivity.RESULT_CODE)
            val state = result.data?.getStringExtra(OAuthActivity.RESULT_STATE)
            val provider = viewModel.currentProvider
            if (code != null && state != null && provider != null) {
                viewModel.handleAuthCallback(provider, code, state)
            }
        } else {
            val error = result.data?.getStringExtra(OAuthActivity.RESULT_ERROR) ?: "Авторизация отменена"
            viewModel.setError(error)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление аккаунтами") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                text = "Подключите ваши Git аккаунты для доступа к частным репозиториям",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // OAuth providers
            AccountCard(
                provider = GitProvider.GITHUB,
                user = githubUser,
                isLoading = isLoading,
                onLoginOAuth = { viewModel.startAuth(GitProvider.GITHUB) { oauthLauncher.launch(it) } },
                onLogout = { viewModel.logout(GitProvider.GITHUB) }
            )

            AccountCard(
                provider = GitProvider.GITLAB,
                user = gitlabUser,
                isLoading = isLoading,
                onLoginOAuth = { viewModel.startAuth(GitProvider.GITLAB) { oauthLauncher.launch(it) } },
                onLogout = { viewModel.logout(GitProvider.GITLAB) }
            )

            AccountCard(
                provider = GitProvider.BITBUCKET,
                user = bitbucketUser,
                isLoading = isLoading,
                onLoginOAuth = { viewModel.startAuth(GitProvider.BITBUCKET) { oauthLauncher.launch(it) } },
                onLogout = { viewModel.logout(GitProvider.BITBUCKET) }
            )

            // PAT providers
            AccountCard(
                provider = GitProvider.GITEA,
                user = giteaUser,
                isLoading = isLoading,
                onLoginPAT = { instanceUrl, username, pat ->
                    viewModel.validatePAT(GitProvider.GITEA, instanceUrl, username, pat)
                },
                onLogout = { viewModel.logout(GitProvider.GITEA) }
            )

            AccountCard(
                provider = GitProvider.AZURE_DEVOPS,
                user = azureUser,
                isLoading = isLoading,
                onLoginPAT = { instanceUrl, _, pat ->
                    // Azure DevOps doesn't use username — org is encoded in the URL
                    viewModel.validatePAT(GitProvider.AZURE_DEVOPS, instanceUrl, "", pat)
                },
                onLogout = { viewModel.logout(GitProvider.AZURE_DEVOPS) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Информация",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• GitHub, GitLab и Bitbucket используют OAuth авторизацию\n" +
                               "• Gitea и Azure DevOps используют Personal Access Token (PAT)\n" +
                               "• Ваши токены доступа хранятся локально на устройстве\n" +
                               "• Вы можете отключить аккаунт в любое время",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AccountCard(
    provider: GitProvider,
    user: GitUser?,
    isLoading: Boolean,
    onLoginOAuth: (() -> Unit)? = null,
    onLoginPAT: ((instanceUrl: String, username: String, pat: String) -> Unit)? = null,
    onLogout: () -> Unit
) {
    var showPATDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    providerIcon(provider),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = providerIconColor(provider)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = providerDisplayName(provider),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (user != null) {
                        Text(
                            text = user.name ?: user.login,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        user.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (onLoginPAT != null) {
                            Text(
                                text = "PAT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "Не подключен",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (user != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Подключен",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = onLogout,
                            enabled = !isLoading
                        ) {
                            Text("Отключить")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            when {
                                onLoginOAuth != null -> onLoginOAuth()
                                onLoginPAT != null -> showPATDialog = true
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Подключить")
                        }
                    }
                }
            }
        }
    }

    if (showPATDialog && onLoginPAT != null) {
        PATLoginDialog(
            provider = provider,
            onDismiss = { showPATDialog = false },
            onConfirm = { instanceUrl, username, pat ->
                showPATDialog = false
                onLoginPAT(instanceUrl, username, pat)
            }
        )
    }
}

@Composable
private fun PATLoginDialog(
    provider: GitProvider,
    onDismiss: () -> Unit,
    onConfirm: (instanceUrl: String, username: String, pat: String) -> Unit
) {
    val isAzure = provider == GitProvider.AZURE_DEVOPS

    var instanceUrl by remember {
        mutableStateOf(if (isAzure) "https://dev.azure.com/myorg" else "https://")
    }
    var username by remember { mutableStateOf("") }
    var pat by remember { mutableStateOf("") }
    var patVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Подключить ${providerDisplayName(provider)}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = instanceUrl,
                    onValueChange = { instanceUrl = it },
                    label = { Text(if (isAzure) "URL организации" else "URL экземпляра") },
                    placeholder = {
                        Text(if (isAzure) "https://dev.azure.com/myorg" else "https://gitea.example.com")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                if (!isAzure) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Имя пользователя") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = pat,
                    onValueChange = { pat = it },
                    label = { Text("Personal Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (patVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { patVisible = !patVisible }) {
                            Icon(
                                if (patVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (patVisible) "Скрыть" else "Показать"
                            )
                        }
                    }
                )

                Text(
                    text = if (isAzure)
                        "Создайте PAT на: dev.azure.com → User settings → Personal access tokens"
                    else
                        "Создайте PAT на: ${instanceUrl.trimEnd('/')}/user/settings/applications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(instanceUrl.trim(), username.trim(), pat.trim()) },
                enabled = instanceUrl.isNotBlank() && pat.isNotBlank() && (isAzure || username.isNotBlank())
            ) {
                Text("Подключить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// ---- Provider display helpers ------------------------------------------------

internal fun providerDisplayName(provider: GitProvider): String = when (provider) {
    GitProvider.GITHUB -> "GitHub"
    GitProvider.GITLAB -> "GitLab"
    GitProvider.BITBUCKET -> "Bitbucket"
    GitProvider.GITEA -> "Gitea / Forgejo"
    GitProvider.AZURE_DEVOPS -> "Azure DevOps"
}

internal fun providerIcon(provider: GitProvider): ImageVector = when (provider) {
    GitProvider.GITHUB -> Icons.Default.Code
    GitProvider.GITLAB -> Icons.Default.Storage
    GitProvider.BITBUCKET -> Icons.Default.Cloud
    GitProvider.GITEA -> Icons.Default.Dns
    GitProvider.AZURE_DEVOPS -> Icons.Default.Business
}

internal fun providerIconColor(provider: GitProvider): Color = when (provider) {
    GitProvider.GITHUB -> Color(0xFF24292F)
    GitProvider.GITLAB -> Color(0xFFFC6D26)
    GitProvider.BITBUCKET -> Color(0xFF0052CC)
    GitProvider.GITEA -> Color(0xFF609926)
    GitProvider.AZURE_DEVOPS -> Color(0xFF0078D4)
}
