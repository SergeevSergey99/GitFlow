package com.gitflow.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.documentfile.provider.DocumentFile
import com.gitflow.android.data.models.*
import com.gitflow.android.data.repository.RealGitRepository
import com.gitflow.android.ui.config.GraphConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val gitRepository = remember { RealGitRepository(context) }
    
    // Используем Flow для автоматического обновления списка репозиториев
    val repositories by gitRepository.getRepositoriesFlow().collectAsState(initial = emptyList())
    
    var selectedRepository by remember { mutableStateOf<Repository?>(null) }
    var showOperationsSheet by remember { mutableStateOf(false) }
    var selectedGraphPreset by remember { mutableStateOf("Default") }
    val scope = rememberCoroutineScope()

    // Обновляем выбранный репозиторий если он изменился в списке
    LaunchedEffect(repositories, selectedRepository) {
        selectedRepository?.let { selected ->
            val updated = repositories.find { it.id == selected.id }
            if (updated != null && updated != selected) {
                selectedRepository = updated
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "GitFlow",
                            fontWeight = FontWeight.Bold
                        )
                        selectedRepository?.let { repo ->
                            Text(
                                text = "${repo.name} • ${repo.currentBranch}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (selectedRepository != null) {
                        IconButton(onClick = { showOperationsSheet = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Operations")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Repos") },
                    label = { Text("Repos") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AccountTree, contentDescription = "Graph") },
                    label = { Text("Graph") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Changes") },
                    label = { Text("Changes") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> RepositoryListView(
                    repositories = repositories,
                    onRepositorySelected = {
                        selectedRepository = it
                        selectedTab = 1 // Switch to graph view
                    }
                )
                1 -> {
                    // Use the new enhanced graph view with selected config
                    EnhancedGraphView(
                        repository = selectedRepository,
                        gitRepository = gitRepository,
                        config = getGraphConfig(selectedGraphPreset)
                    )
                }
                2 -> ChangesView(
                    repository = selectedRepository,
                    gitRepository = gitRepository
                )
                3 -> SettingsView(
                    selectedGraphPreset = selectedGraphPreset,
                    onGraphPresetChanged = { selectedGraphPreset = it }
                )
            }
        }
    }

    // Git Operations Bottom Sheet
    if (showOperationsSheet && selectedRepository != null) {
        GitOperationsSheet(
            repository = selectedRepository!!,
            gitRepository = gitRepository,
            onDismiss = { showOperationsSheet = false }
        )
    }
}

@Composable
fun RepositoryListView(
    repositories: List<Repository>,
    onRepositorySelected: (Repository) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var customDestination by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var repositoryToDelete by remember { mutableStateOf<Repository?>(null) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gitRepository = remember { RealGitRepository(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (repositories.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No repositories yet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Add your first repository to get started",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Repository")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repositories) { repository ->
                    RepositoryCard(
                        repository = repository,
                        onClick = { onRepositorySelected(repository) },
                        onDelete = { repo, deleteFiles ->
                            if (deleteFiles) {
                                showDeleteConfirmDialog = true
                                repositoryToDelete = repo
                            } else {
                                // Просто удаляем из списка
                                scope.launch {
                                    gitRepository.removeRepository(repo.id)
                                }
                            }
                        }
                    )
                }
            }
        }

        if (repositories.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Repository")
            }
        }
    }

    if (showAddDialog) {
        AddRepositoryDialog(
            onDismiss = {
                showAddDialog = false
                errorMessage = null
            },
            isLoading = isLoading,
            errorMessage = errorMessage,
            onAdd = { name, url, isClone ->
                scope.launch {
                    isLoading = true
                    errorMessage = null

                    try {
                        if (isClone && url.isNotEmpty()) {
                            // Клонирование репозитория
                            val appDir = context.getExternalFilesDir(null) ?: context.filesDir
                            val defaultPath = "${appDir.absolutePath}/repositories/$name"
                            
                            // Используем пользовательский путь если указан, иначе стандартный
                            val finalDestination = if (customDestination.isNotEmpty()) {
                                "$customDestination/$name"
                            } else {
                                null
                            }

                            val result = gitRepository.cloneRepository(url, defaultPath, finalDestination)
                            result.onSuccess { repo ->
                                // Репозиторий автоматически добавляется в DataStore через gitRepository.cloneRepository
                                showAddDialog = false
                            }.onFailure { exception ->
                                errorMessage = "Failed to clone repository: ${exception.message}"
                            }
                        } else {
                            // Создание нового репозитория
                            val appDir = context.getExternalFilesDir(null) ?: context.filesDir
                            val localPath = "${appDir.absolutePath}/repositories/$name"
                            
                            val result = gitRepository.createRepository(name, localPath)
                            result.onSuccess { repo ->
                                showAddDialog = false
                            }.onFailure { exception ->
                                errorMessage = "Failed to create repository: ${exception.message}"
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            onAddLocal = { localPath ->
                scope.launch {
                    isLoading = true
                    errorMessage = null

                    try {
                        // Добавление существующего локального репозитория
                        val result = gitRepository.addRepository(localPath)
                        result.onSuccess { repo ->
                            showAddDialog = false
                        }.onFailure { exception ->
                            errorMessage = "Failed to add local repository: ${exception.message}"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
    }

    // Диалог подтверждения удаления
    if (showDeleteConfirmDialog && repositoryToDelete != null) {
        DeleteRepositoryDialog(
            repository = repositoryToDelete!!,
            onDismiss = {
                showDeleteConfirmDialog = false
                repositoryToDelete = null
                deleteMessage = null
            },
            onConfirm = { deleteFiles ->
                scope.launch {
                    val result = if (deleteFiles) {
                        gitRepository.removeRepositoryWithFiles(repositoryToDelete!!.id)
                    } else {
                        gitRepository.removeRepository(repositoryToDelete!!.id)
                        Result.success(Unit)
                    }
                    
                    result.onSuccess {
                        showDeleteConfirmDialog = false
                        repositoryToDelete = null
                        deleteMessage = null
                    }.onFailure { exception ->
                        deleteMessage = "Failed to delete repository: ${exception.message}"
                    }
                }
            },
            errorMessage = deleteMessage
        )
    }
}

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
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                
                DropdownMenu(
                    expanded = showDeleteMenu,
                    onDismissRequest = { showDeleteMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove from list") },
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
                                "Delete repository",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesView(
    repository: Repository?,
    gitRepository: RealGitRepository
) {
    if (repository == null) {
        EmptyStateMessage("Select a repository to view changes")
        return
    }

    var stagedFiles by remember { mutableStateOf(listOf<FileChange>()) }
    var unstagedFiles by remember { mutableStateOf(listOf<FileChange>()) }
    var commitMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Загружаем изменения при смене репозитория
    LaunchedEffect(repository) {
        isLoading = true
        unstagedFiles = gitRepository.getChangedFiles(repository)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Commit section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Commit Changes",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit message") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Stage all files
                            stagedFiles = unstagedFiles
                            unstagedFiles = emptyList()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = unstagedFiles.isNotEmpty()
                    ) {
                        Text("Stage All")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                // TODO: Реализовать создание коммита через JGit
                                delay(500)
                                stagedFiles = emptyList()
                                commitMessage = ""
                                // Перезагружаем изменения
                                unstagedFiles = gitRepository.getChangedFiles(repository)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = stagedFiles.isNotEmpty() && commitMessage.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Commit")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File changes
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (stagedFiles.isNotEmpty()) {
                    item {
                        Text(
                            "Staged Changes (${stagedFiles.size})",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(stagedFiles) { file ->
                        FileChangeCard(
                            file = file,
                            isStaged = true,
                            onToggle = {
                                unstagedFiles = unstagedFiles + file
                                stagedFiles = stagedFiles - file
                            }
                        )
                    }
                }

                if (unstagedFiles.isNotEmpty()) {
                    item {
                        Text(
                            "Unstaged Changes (${unstagedFiles.size})",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(unstagedFiles) { file ->
                        FileChangeCard(
                            file = file,
                            isStaged = false,
                            onToggle = {
                                stagedFiles = stagedFiles + file
                                unstagedFiles = unstagedFiles - file
                            }
                        )
                    }
                }

                if (stagedFiles.isEmpty() && unstagedFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No changes",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileChangeCard(
    file: FileChange,
    isStaged: Boolean,
    onToggle: () -> Unit
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitOperationsSheet(
    repository: Repository,
    gitRepository: RealGitRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var operationResult by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Git Operations",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Pull
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            isLoading = true
                            val result = gitRepository.pull(repository)
                            operationResult = if (result.success) {
                                "✓ Pull completed successfully"
                            } else {
                                "✗ Pull failed: ${result.conflicts.joinToString(", ")}"
                            }
                            isLoading = false
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pull", fontWeight = FontWeight.Medium)
                        Text(
                            "Fetch and merge remote changes",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Push
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            isLoading = true
                            val result = gitRepository.push(repository)
                            operationResult = if (result.success) {
                                "✓ Push completed successfully"
                            } else {
                                "✗ Push failed: ${result.message}"
                            }
                            isLoading = false
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Push", fontWeight = FontWeight.Medium)
                        Text(
                            "Upload local commits",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Branch
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            isLoading = true
                            delay(1000)
                            operationResult = "✓ Branch operations coming soon"
                            isLoading = false
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Branches", fontWeight = FontWeight.Medium)
                        Text(
                            "Manage repository branches",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Result message
            operationResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("✓")) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(12.dp),
                        color = if (result.startsWith("✓")) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun SettingsView(
    selectedGraphPreset: String,
    onGraphPresetChanged: (String) -> Unit
) {
    var showGraphPresetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with GitHub")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Preferences",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark theme")
                    Switch(
                        checked = false,
                        onCheckedChange = { }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show file icons")
                    Switch(
                        checked = true,
                        onCheckedChange = { }
                    )
                }
            }
        }

        // Новая секция для настроек графа
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
                        .clickable { showGraphPresetDialog = true },
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
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onAdd: (String, String, Boolean) -> Unit,
    onAddLocal: (String) -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var localPath by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var customDestination by remember { mutableStateOf("") }
    
    // Launcher for choosing directory for cloning
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { 
            customDestination = getRealPathFromUri(it)
        }
    }
    
    // Launcher for choosing existing repository directory
    val repoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { 
            localPath = getRealPathFromUri(it)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Add Repository",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Clone") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Local") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Create") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // Clone tab
                        OutlinedTextField(
                            value = url,
                            onValueChange = { 
                                url = it
                                // Автоматически извлекаем имя из URL
                                if (it.isNotEmpty()) {
                                    val repoName = extractRepoNameFromUrl(it)
                                    if (repoName.isNotEmpty()) {
                                        name = repoName
                                    }
                                }
                            },
                            label = { Text("Repository URL") },
                            placeholder = { Text("https://github.com/user/repo.git") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            },
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Local name") },
                            placeholder = { Text("my-project") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            },
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Выбор папки назначения
                        OutlinedTextField(
                            value = customDestination,
                            onValueChange = { customDestination = it },
                            label = { Text("Destination (optional)") },
                            placeholder = { Text("Leave empty for default location") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { directoryPickerLauncher.launch(null) },
                                    enabled = !isLoading
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Browse")
                                }
                            },
                            enabled = !isLoading
                        )
                    }
                    1 -> {
                        // Local tab - добавление существующего репозитория
                        OutlinedTextField(
                            value = localPath,
                            onValueChange = { localPath = it },
                            label = { Text("Repository Path") },
                            placeholder = { Text("/storage/emulated/0/MyRepos/project") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { repoPickerLauncher.launch(null) },
                                    enabled = !isLoading
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Browse")
                                }
                            },
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Select an existing Git repository from your device storage",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    2 -> {
                        // Create tab
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Repository name") },
                            placeholder = { Text("my-new-project") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Create, contentDescription = null)
                            },
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "This will create a new Git repository locally",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error message display
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                    }
                }

                // Loading indicator
                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedTab) {
                                0 -> "Cloning repository..."
                                1 -> "Adding local repository..."
                                2 -> "Creating repository..."
                                else -> "Processing..."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when (selectedTab) {
                                0 -> { // Clone
                                    if (name.isNotEmpty() && url.isNotEmpty()) {
                                        onAdd(name, url, true)
                                    }
                                }
                                1 -> { // Local
                                    if (localPath.isNotEmpty()) {
                                        onAddLocal(localPath)
                                    }
                                }
                                2 -> { // Create
                                    if (name.isNotEmpty()) {
                                        onAdd(name, "", false)
                                    }
                                }
                            }
                        },
                        enabled = when (selectedTab) {
                            0 -> name.isNotEmpty() && url.isNotEmpty() && !isLoading
                            1 -> localPath.isNotEmpty() && !isLoading
                            2 -> name.isNotEmpty() && !isLoading
                            else -> false
                        }
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                when (selectedTab) {
                                    0 -> "Clone"
                                    1 -> "Add Local"
                                    2 -> "Create"
                                    else -> "Add"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GraphPresetDialog(
    currentPreset: String,
    onPresetSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        "Default" to "Стандартный размер для большинства экранов",
        "Compact" to "Компактный вид для небольших экранов",
        "Large" to "Крупный вид для больших экранов",
        "Wide" to "Широкий вид для графов с множеством веток"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Graph Preset",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose how the commit graph should be displayed:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                presets.forEach { (preset, description) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPresetSelected(preset) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentPreset == preset) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentPreset == preset,
                                onClick = { onPresetSelected(preset) }
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

// Helper function to get graph config
fun getGraphConfig(preset: String): GraphConfig {
    return when (preset) {
        "Compact" -> GraphConfig.Compact
        "Large" -> GraphConfig.Large
        "Wide" -> GraphConfig.Wide
        else -> GraphConfig.Default
    }
}

// Helper function to extract repository name from URL
fun extractRepoNameFromUrl(url: String): String {
    return try {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        
        // Remove .git suffix if present
        val withoutGit = if (trimmed.endsWith(".git")) {
            trimmed.removeSuffix(".git")
        } else {
            trimmed
        }
        
        // Extract the last part of the URL path
        val parts = withoutGit.split("/")
        val repoName = parts.lastOrNull { it.isNotEmpty() }
        
        // Clean up the name (remove special characters, make it filesystem-safe)
        repoName?.let { name ->
            name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(50) // Limit length
                .trim('_', '-')
        } ?: ""
    } catch (e: Exception) {
        ""
    }
}

// Helper function to get real path from URI
fun getRealPathFromUri(uri: Uri): String {
    return try {
        // Extract path from the URI
        val path = uri.path
        if (path != null) {
            // Clean up the path for external storage
            when {
                path.contains("/tree/primary:") -> {
                    val relativePath = path.substringAfter("/tree/primary:")
                    "/storage/emulated/0/$relativePath"
                }
                path.contains("/tree/") -> {
                    // For other storage locations, try to extract meaningful path
                    path.substringAfterLast("/")
                }
                else -> path
            }
        } else {
            uri.toString()
        }
    } catch (e: Exception) {
        uri.toString()
    }
}

@Composable
fun DeleteRepositoryDialog(
    repository: Repository,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    errorMessage: String? = null
) {
    var deleteFiles by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Заголовок с иконкой предупреждения
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Delete Repository",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Информация о репозитории
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = repository.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            text = repository.path,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Опция удаления файлов
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (deleteFiles) 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteFiles = !deleteFiles }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteFiles,
                            onCheckedChange = { deleteFiles = it }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Also delete repository files",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "This will permanently delete all repository files from your device",
                                fontSize = 12.sp,
                                color = if (deleteFiles) 
                                    MaterialTheme.colorScheme.error
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Сообщение об ошибке
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                    }
                }

                // Текст предупреждения
                if (deleteFiles) {
                    Text(
                        text = "⚠️ This action cannot be undone!",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(deleteFiles) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deleteFiles) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (deleteFiles) Icons.Default.Delete else Icons.Default.RemoveCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (deleteFiles) "Delete Repository" else "Remove from List"
                        )
                    }
                }
            }
        }
    }
}

