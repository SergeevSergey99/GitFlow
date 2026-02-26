package com.gitflow.android.ui.screens.main

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitflow.android.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.auth.providerDisplayName
import com.gitflow.android.ui.auth.providerIcon
import com.gitflow.android.ui.auth.providerIconColor
import com.gitflow.android.ui.components.dialogs.GraphPresetDialog
import com.gitflow.android.ui.components.dialogs.ThemePresetDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    selectedGraphPreset: String,
    onGraphPresetChanged: (String) -> Unit,
    selectedColorTheme: String,
    onColorThemeChanged: (String) -> Unit,
    selectedDarkMode: String,
    onDarkModeChanged: (String) -> Unit,
    navController: NavController
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var showGraphPresetDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var permissionRefreshKey by remember { mutableStateOf(0) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onStorageFolderSelected(uri)
        }
    }

    val permissionRequirements = remember {
        PermissionRequirement.buildList(context.applicationContext)
    }

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
                viewModel.refreshUsers()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Перезапуск Activity после смены языка
    LaunchedEffect(uiState.recreateActivity) {
        if (uiState.recreateActivity) {
            viewModel.onRecreateConsumed()
            activity?.recreate()
        }
    }

    if (uiState.showPreviewSettings) {
        BackHandler(onBack = { viewModel.setShowPreviewSettings(false) })
        FilePreviewSettingsDetail(
            extensions = uiState.previewExtensions,
            fileNames = uiState.previewFileNames,
            onAddExtension = viewModel::addPreviewExtension,
            onRemoveExtension = viewModel::removePreviewExtension,
            onAddFileName = viewModel::addPreviewFileName,
            onRemoveFileName = viewModel::removePreviewFileName,
            onBack = { viewModel.setShowPreviewSettings(false) }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccountSection(
                connectedAccounts = uiState.connectedAccounts,
                localAuthorName = uiState.localAuthorName,
                localAuthorEmail = uiState.localAuthorEmail,
                onManageAccountsClick = { navController.navigate("auth") }
            )

            ThemeSettingsSection(
                selectedColorTheme = selectedColorTheme,
                onThemeClick = { showThemeDialog = true },
                selectedDarkMode = selectedDarkMode,
                onDarkModeChanged = onDarkModeChanged
            )

            GraphSettingsSection(
                selectedGraphPreset = selectedGraphPreset,
                onPresetClick = { showGraphPresetDialog = true }
            )

            NetworkSettingsSection(
                wifiOnlyDownloads = uiState.wifiOnlyDownloads,
                onWifiOnlyChanged = viewModel::setWifiOnlyDownloads
            )

            LanguageSettingsSection(
                currentLanguage = uiState.currentLanguage,
                onLanguageClick = { showLanguageDialog = true }
            )

            FilePreviewSettingsEntry(
                extensions = uiState.previewExtensions,
                fileNames = uiState.previewFileNames,
                onOpen = { viewModel.setShowPreviewSettings(true) }
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
                    onOpenSettings = openSettings,
                    customStoragePath = uiState.customStoragePath,
                    repositoriesBaseDir = uiState.repositoriesBaseDir,
                    onChooseFolder = { folderPickerLauncher.launch(null) },
                    onResetFolder = viewModel::resetStoragePath
                )
            }

            AboutSection()
        }
    }

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

    if (showThemeDialog) {
        ThemePresetDialog(
            currentTheme = selectedColorTheme,
            onThemeSelected = { theme ->
                onColorThemeChanged(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = uiState.currentLanguage,
            onLanguageSelected = { language ->
                viewModel.setLanguage(language)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun FilePreviewSettingsEntry(
    extensions: List<String>,
    fileNames: List<String>,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_file_preview),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = stringResource(R.string.settings_file_preview_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            val extPreview = remember(extensions) {
                extensions.sorted().take(3).joinToString(", ") { ".${it.removePrefix(".")}" }
            }
            val filenamesPreview = remember(fileNames) {
                fileNames.sorted().take(3).joinToString(", ")
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_extensions_label, extensions.size),
                    fontWeight = FontWeight.Medium
                )
                if (extPreview.isNotEmpty()) {
                    Text(
                        text = extPreview,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_filenames_label, fileNames.size),
                    fontWeight = FontWeight.Medium
                )
                if (filenamesPreview.isNotEmpty()) {
                    Text(
                        text = filenamesPreview,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_configure))
            }
        }
    }
}

@Composable
private fun FilePreviewSettingsDetail(
    extensions: List<String>,
    fileNames: List<String>,
    onAddExtension: (String) -> Unit,
    onRemoveExtension: (String) -> Unit,
    onAddFileName: (String) -> Unit,
    onRemoveFileName: (String) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
            }
            Text(
                text = stringResource(R.string.settings_file_preview),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Text(
            text = stringResource(R.string.settings_file_preview_detail_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        FilePreviewSettingsEditor(
            extensions = extensions,
            fileNames = fileNames,
            onAddExtension = onAddExtension,
            onRemoveExtension = onRemoveExtension,
            onAddFileName = onAddFileName,
            onRemoveFileName = onRemoveFileName
        )
    }
}

@Composable
private fun FilePreviewSettingsEditor(
    extensions: List<String>,
    fileNames: List<String>,
    onAddExtension: (String) -> Unit,
    onRemoveExtension: (String) -> Unit,
    onAddFileName: (String) -> Unit,
    onRemoveFileName: (String) -> Unit
) {
    var extensionInput by remember { mutableStateOf("") }
    var fileNameInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_file_preview),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Text(
                text = stringResource(R.string.settings_file_preview_detail_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Text(
                text = stringResource(R.string.settings_allowed_extensions),
                fontWeight = FontWeight.Medium
            )

            if (extensions.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_extensions),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    extensions.sorted().forEach { extension ->
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = ".${extension.removePrefix(".")}")
                                IconButton(onClick = { onRemoveExtension(extension) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.settings_remove_extension)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = extensionInput,
                onValueChange = { extensionInput = it },
                label = { Text(stringResource(R.string.settings_add_extension_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (extensionInput.isNotBlank()) {
                                onAddExtension(extensionInput.trim())
                                extensionInput = ""
                            }
                        },
                        enabled = extensionInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_extension))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_extensionless_filenames),
                fontWeight = FontWeight.Medium
            )

            if (fileNames.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_filenames),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fileNames.sorted().forEach { name ->
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = name)
                                IconButton(onClick = { onRemoveFileName(name) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.settings_remove_filename)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = fileNameInput,
                onValueChange = { fileNameInput = it },
                label = { Text(stringResource(R.string.settings_add_filename_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (fileNameInput.isNotBlank()) {
                                onAddFileName(fileNameInput.trim())
                                fileNameInput = ""
                            }
                        },
                        enabled = fileNameInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_filename))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AccountSection(
    connectedAccounts: List<ConnectedAccount>,
    localAuthorName: String,
    localAuthorEmail: String,
    onManageAccountsClick: () -> Unit
) {
    val hasAnyAuth = connectedAccounts.isNotEmpty()
    val hasLocalAuthor = localAuthorName.isNotBlank() && localAuthorEmail.isNotBlank()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_account),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Local author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (hasLocalAuthor) {
                        Text(
                            text = localAuthorName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = localAuthorEmail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Автор не задан",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Mini provider icons
                if (hasAnyAuth) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        connectedAccounts.forEach { account ->
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = providerIconColor(account.user.provider).copy(alpha = 0.15f)
                            ) {
                                Icon(
                                    providerIcon(account.user.provider),
                                    contentDescription = providerDisplayName(account.user.provider),
                                    modifier = Modifier.padding(4.dp),
                                    tint = providerIconColor(account.user.provider)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onManageAccountsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (hasAnyAuth || hasLocalAuthor)
                        stringResource(R.string.settings_manage_accounts)
                    else
                        stringResource(R.string.settings_sign_in)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsSection(
    selectedColorTheme: String,
    onThemeClick: () -> Unit,
    selectedDarkMode: String,
    onDarkModeChanged: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_theme_settings),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Переключатель светлая / системная / тёмная
            Text(stringResource(R.string.settings_dark_mode), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    AppSettingsManager.DARK_MODE_LIGHT  to R.string.settings_dark_mode_light,
                    AppSettingsManager.DARK_MODE_SYSTEM to R.string.settings_dark_mode_system,
                    AppSettingsManager.DARK_MODE_DARK   to R.string.settings_dark_mode_dark,
                )
                modes.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = selectedDarkMode == mode,
                        onClick = { onDarkModeChanged(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = selectedDarkMode == mode) {
                                Icon(
                                    imageVector = when (mode) {
                                        AppSettingsManager.DARK_MODE_LIGHT  -> Icons.Default.LightMode
                                        AppSettingsManager.DARK_MODE_DARK   -> Icons.Default.DarkMode
                                        else                                -> Icons.Default.Brightness6
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        }
                    ) {
                        Text(stringResource(labelRes), maxLines = 1)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Цветовая тема
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_theme_preset), fontWeight = FontWeight.Medium)
                    Text(
                        text = selectedColorTheme,
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
                text = stringResource(R.string.settings_theme_preset_description),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                text = stringResource(R.string.settings_graph_settings),
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
                    Text(stringResource(R.string.settings_graph_preset), fontWeight = FontWeight.Medium)
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
                text = stringResource(R.string.settings_graph_preset_description),
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
                text = stringResource(R.string.settings_about),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.settings_about_app_name))
            Text(
                text = stringResource(R.string.settings_about_version),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_about_description),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UserAccountRow(account: ConnectedAccount) {
    val user = account.user
    val provider = user.provider
    val iconColor = providerIconColor(provider)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar: real image or fallback provider icon
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.12f)
        ) {
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    providerIcon(provider),
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = iconColor
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name ?: user.login,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${providerDisplayName(provider)} • @${user.login}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (account.instanceUrl != null) {
                Text(
                    text = account.instanceUrl,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            user.email?.let { email ->
                Text(
                    text = email,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = stringResource(R.string.settings_connected),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LanguageSettingsSection(
    currentLanguage: String,
    onLanguageClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_language), fontWeight = FontWeight.Medium)
                    Text(
                        text = when (currentLanguage) {
                            AppSettingsManager.LANGUAGE_ENGLISH -> stringResource(R.string.settings_language_english)
                            AppSettingsManager.LANGUAGE_RUSSIAN -> stringResource(R.string.settings_language_russian)
                            else -> stringResource(R.string.settings_language_system)
                        },
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
                text = stringResource(R.string.settings_language_description),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                LanguageOption(
                    text = stringResource(R.string.settings_language_system),
                    isSelected = currentLanguage == AppSettingsManager.LANGUAGE_SYSTEM,
                    onClick = { onLanguageSelected(AppSettingsManager.LANGUAGE_SYSTEM) }
                )
                LanguageOption(
                    text = stringResource(R.string.settings_language_english),
                    isSelected = currentLanguage == AppSettingsManager.LANGUAGE_ENGLISH,
                    onClick = { onLanguageSelected(AppSettingsManager.LANGUAGE_ENGLISH) }
                )
                LanguageOption(
                    text = stringResource(R.string.settings_language_russian),
                    isSelected = currentLanguage == AppSettingsManager.LANGUAGE_RUSSIAN,
                    onClick = { onLanguageSelected(AppSettingsManager.LANGUAGE_RUSSIAN) }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun LanguageOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text)
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
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
                text = stringResource(R.string.settings_network),
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
                        text = stringResource(R.string.settings_wifi_only),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_wifi_only_description),
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
private fun StoragePathPicker(
    customStoragePath: String?,
    repositoriesBaseDir: String?,
    onChooseFolder: () -> Unit,
    onReset: () -> Unit
) {
    val isConfigured = customStoragePath != null
    val displayPath = if (isConfigured) repositoriesBaseDir else null

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_storage_title),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Surface(
                    color = if (isConfigured) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(
                            if (isConfigured) R.string.settings_storage_configured
                            else R.string.settings_storage_not_configured
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = if (isConfigured) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_storage_description),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = displayPath ?: stringResource(R.string.settings_storage_default),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onChooseFolder,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_storage_choose))
                }
                if (isConfigured) {
                    OutlinedButton(onClick = onReset) {
                        Text(stringResource(R.string.settings_storage_reset))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    requirements: List<PermissionRequirement>,
    refreshKey: Int,
    onRequest: (PermissionRequirement) -> Unit,
    onOpenSettings: (PermissionRequirement) -> Unit,
    customStoragePath: String?,
    repositoriesBaseDir: String?,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit
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
                text = stringResource(R.string.settings_permissions),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = stringResource(R.string.settings_permissions_description),
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
                if (requirement.id == "storage" && isGranted) {
                    StoragePathPicker(
                        customStoragePath = customStoragePath,
                        repositoriesBaseDir = repositoriesBaseDir,
                        onChooseFolder = onChooseFolder,
                        onReset = onResetFolder
                    )
                }
            }

            // On Android 13+ there's no storage permission requirement,
            // but SAF works without it — show picker directly
            if (requirements.none { it.id == "storage" }) {
                StoragePathPicker(
                    customStoragePath = customStoragePath,
                    repositoriesBaseDir = repositoriesBaseDir,
                    onChooseFolder = onChooseFolder,
                    onReset = onResetFolder
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
    val context = LocalContext.current
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
                    text = requirement.getTitle(context),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                PermissionStatusBadge(isGranted)
            }

            Text(
                text = requirement.getDescription(context),
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
                            Text(stringResource(R.string.settings_permission_grant))
                        }
                        TextButton(onClick = { onOpenSettings(requirement) }) {
                            Text(stringResource(R.string.settings_permission_open_settings))
                        }
                    } else {
                        Button(onClick = { onOpenSettings(requirement) }) {
                            Text(stringResource(R.string.settings_permission_open_settings))
                        }
                    }
                }
            } else {
                TextButton(onClick = { onOpenSettings(requirement) }) {
                    Text(stringResource(R.string.settings_permission_manage))
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
            text = stringResource(
                if (isGranted) R.string.settings_permission_granted
                else R.string.settings_permission_not_granted
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class PermissionRequirement(
    val id: String,
    val titleResId: Int,
    val descriptionResId: Int,
    val permissions: List<String>,
    val settingsIntentBuilder: (Context) -> Intent,
    val statusChecker: (Context) -> Boolean
) {
    fun isGranted(context: Context): Boolean = statusChecker(context)

    fun buildSettingsIntent(context: Context): Intent = settingsIntentBuilder(context)

    fun getTitle(context: Context): String = context.getString(titleResId)

    fun getDescription(context: Context): String = context.getString(descriptionResId)

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
                    titleResId = R.string.settings_permission_notifications,
                    descriptionResId = R.string.settings_permission_notifications_description,
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
                        titleResId = R.string.settings_permission_storage,
                        descriptionResId = R.string.settings_permission_storage_description,
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
