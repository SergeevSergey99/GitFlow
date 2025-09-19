package com.gitflow.android.ui.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    
    val githubUser by viewModel.githubUser.collectAsState()
    val gitlabUser by viewModel.gitlabUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val oauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(OAuthActivity.RESULT_CODE)
            val provider = viewModel.currentProvider
            if (code != null && provider != null) {
                viewModel.handleAuthCallback(provider, code, authManager)
            }
        } else {
            val error = result.data?.getStringExtra(OAuthActivity.RESULT_ERROR) ?: "Авторизация отменена"
            viewModel.setError(error)
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.initializeAuth(authManager)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление аккаунтами") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            
            // GitHub аккаунт
            AccountCard(
                provider = GitProvider.GITHUB,
                user = githubUser,
                isLoading = isLoading,
                onLogin = {
                    viewModel.startAuth(GitProvider.GITHUB, authManager) { intent ->
                        oauthLauncher.launch(intent)
                    }
                },
                onLogout = {
                    viewModel.logout(GitProvider.GITHUB, authManager)
                }
            )
            
            // GitLab аккаунт
            AccountCard(
                provider = GitProvider.GITLAB,
                user = gitlabUser,
                isLoading = isLoading,
                onLogin = {
                    viewModel.startAuth(GitProvider.GITLAB, authManager) { intent ->
                        oauthLauncher.launch(intent)
                    }
                },
                onLogout = {
                    viewModel.logout(GitProvider.GITLAB, authManager)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
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
                        text = "• Подключение аккаунтов позволит вам клонировать частные репозитории\n" +
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
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
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
                    when (provider) {
                        GitProvider.GITHUB -> Icons.Default.Code
                        GitProvider.GITLAB -> Icons.Default.Storage
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = when (provider) {
                        GitProvider.GITHUB -> Color(0xFF24292F)
                        GitProvider.GITLAB -> Color(0xFFFC6D26)
                    }
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (provider) {
                            GitProvider.GITHUB -> "GitHub"
                            GitProvider.GITLAB -> "GitLab"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (user != null) {
                        Text(
                            text = "${user.name ?: user.login}",
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
                        onClick = onLogin,
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
}
