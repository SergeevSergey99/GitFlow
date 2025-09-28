package com.gitflow.android.ui.screens.main

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitUser
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.components.dialogs.GraphPresetDialog

@Composable
fun SettingsScreen(
    selectedGraphPreset: String,
    onGraphPresetChanged: (String) -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val settingsManager = remember { AppSettingsManager(context) }

    // Получаем информацию о пользователях
    val githubUser = authManager.getCurrentUser(GitProvider.GITHUB)
    val gitlabUser = authManager.getCurrentUser(GitProvider.GITLAB)
    val hasAnyAuth = githubUser != null || gitlabUser != null

    var showGraphPresetDialog by remember { mutableStateOf(false) }
    var wifiOnlyDownloads by remember { mutableStateOf(settingsManager.isWifiOnlyDownloadsEnabled()) }

    val permissionRequirements = remember {
        PermissionRequirement.buildList(context.applicationContext)
    }

    var permissionRefreshKey by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionRefreshKey++
    }

    val openSettings: (PermissionRequirement) -> Unit = remember(context) {
        { requirement -> openPermissionSettings(context, requirement) }
    }

    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
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

        NetworkSettingsSection(
            wifiOnlyDownloads = wifiOnlyDownloads,
            onWifiOnlyChanged = { enabled ->
                wifiOnlyDownloads = enabled
                settingsManager.setWifiOnlyDownloadsEnabled(enabled)
            }
        )

        if (permissionRequirements.isNotEmpty()) {
            PermissionsSection(
                requirements = permissionRequirements,
                refreshKey = permissionRefreshKey,
                onRequest = { requirement ->
                    if (requirement.permissions.isEmpty()) {
                        openSettings(requirement)
                    } else {
                        permissionLauncher.launch(requirement.permissions.toTypedArray())
                    }
                },
                onOpenSettings = openSettings
            )
        }

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

@Composable
private fun NetworkSettingsSection(
    wifiOnlyDownloads: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Network",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wi-Fi only downloads",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Block cloning on mobile data to save traffic",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = wifiOnlyDownloads,
                    onCheckedChange = onWifiOnlyChanged
                )
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    requirements: List<PermissionRequirement>,
    refreshKey: Int,
    onRequest: (PermissionRequirement) -> Unit,
    onOpenSettings: (PermissionRequirement) -> Unit
) {
    if (requirements.isEmpty()) return

    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Permissions",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Убедитесь, что приложению доступны уведомления и файлы для корректного клонирования.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            requirements.forEach { requirement ->
                val isGranted = remember(refreshKey) {
                    requirement.isGranted(context)
                }
                PermissionCard(
                    requirement = requirement,
                    isGranted = isGranted,
                    onRequest = onRequest,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    requirement: PermissionRequirement,
    isGranted: Boolean,
    onRequest: (PermissionRequirement) -> Unit,
    onOpenSettings: (PermissionRequirement) -> Unit
) {
    val canRequestDirectly = requirement.permissions.isNotEmpty()

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = requirement.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                PermissionStatusBadge(isGranted)
            }

            Text(
                text = requirement.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canRequestDirectly) {
                        Button(onClick = { onRequest(requirement) }) {
                            Text("Разрешить")
                        }
                        TextButton(onClick = { onOpenSettings(requirement) }) {
                            Text("Открыть настройки")
                        }
                    } else {
                        Button(onClick = { onOpenSettings(requirement) }) {
                            Text("Открыть настройки")
                        }
                    }
                }
            } else {
                TextButton(onClick = { onOpenSettings(requirement) }) {
                    Text("Управлять разрешением")
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusBadge(isGranted: Boolean) {
    val backgroundColor = if (isGranted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val textColor = if (isGranted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = if (isGranted) "Выдано" else "Не выдано",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class PermissionRequirement(
    val id: String,
    val title: String,
    val description: String,
    val permissions: List<String>,
    val settingsIntentBuilder: (Context) -> Intent,
    val statusChecker: (Context) -> Boolean
) {
    fun isGranted(context: Context): Boolean = statusChecker(context)

    fun buildSettingsIntent(context: Context): Intent = settingsIntentBuilder(context)

    companion object {
        fun buildList(context: Context): List<PermissionRequirement> {
            val appContext = context.applicationContext
            val requirements = mutableListOf<PermissionRequirement>()

            val notificationPermissions =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }

            requirements.add(
                PermissionRequirement(
                    id = "notifications",
                    title = "Уведомления",
                    description = "Нужны для отображения прогресса клонирования и статуса фоновых задач.",
                    permissions = notificationPermissions,
                    settingsIntentBuilder = { ctx ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                            }
                        }
                    },
                    statusChecker = { ctx ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissions.all { permission ->
                                ContextCompat.checkSelfPermission(
                                    ctx,
                                    permission
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
                        } else {
                            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
                        }
                    }
                )
            )

            val storagePermissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                storagePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                storagePermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (storagePermissions.isNotEmpty()) {
                requirements.add(
                    PermissionRequirement(
                        id = "storage",
                        title = "Доступ к файлам",
                        description = "Позволяет выбирать папку для клонирования и работать с репозиториями на устройстве.",
                        permissions = storagePermissions,
                        settingsIntentBuilder = { ctx ->
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                            }
                        },
                        statusChecker = { ctx ->
                            storagePermissions.all { permission ->
                                ContextCompat.checkSelfPermission(ctx, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
                        }
                    )
                )
            }

            return requirements
        }
    }
}

private fun openPermissionSettings(context: Context, requirement: PermissionRequirement) {
    val intent = requirement.buildSettingsIntent(context).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}
