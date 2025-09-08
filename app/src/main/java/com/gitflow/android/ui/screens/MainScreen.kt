package com.gitflow.android.ui.screens

import androidx.compose.animation.*
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
import com.gitflow.android.data.models.*
import com.gitflow.android.data.repository.MockGitRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val gitRepository = remember { MockGitRepository() }
    var repositories by remember { mutableStateOf(gitRepository.getRepositories()) }
    var selectedRepository by remember { mutableStateOf<Repository?>(null) }
    var showOperationsSheet by remember { mutableStateOf(false) }

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
                    },
                    onRepositoryAdded = { repo ->
                        repositories = repositories + repo
                    }
                )
                1 -> {
                    // Use the new enhanced graph view
                    EnhancedGraphView(
                        repository = selectedRepository,
                        gitRepository = gitRepository
                    )
                }
                2 -> ChangesView(
                    repository = selectedRepository,
                    gitRepository = gitRepository
                )
                3 -> SettingsView()
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
    onRepositorySelected: (Repository) -> Unit,
    onRepositoryAdded: (Repository) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                        onClick = { onRepositorySelected(repository) }
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
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                scope.launch {
                    val newRepo = Repository(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        path = "/storage/emulated/0/GitFlow/$name",
                        lastUpdated = System.currentTimeMillis(),
                        currentBranch = "main"
                    )
                    onRepositoryAdded(newRepo)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun RepositoryCard(
    repository: Repository,
    onClick: () -> Unit
) {
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

            Icon(Icons.Default.ChevronRight, contentDescription = "Open")
        }
    }
}

@Composable
fun GraphView(
    repository: Repository?,
    gitRepository: MockGitRepository
) {
    if (repository == null) {
        EmptyStateMessage("Select a repository to view commits")
        return
    }

    val commits = remember(repository) { gitRepository.getCommits(repository) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(commits) { commit ->
            CommitCard(commit)
        }
    }
}

@Composable
fun CommitCard(commit: Commit) {
    val branchColors = mapOf(
        "main" to Color(0xFF4CAF50),
        "develop" to Color(0xFF2196F3),
        "feature" to Color(0xFFFF9800),
        "hotfix" to Color(0xFFF44336)
    )

    val color = commit.branch?.let { branch ->
        branchColors.entries.firstOrNull { branch.contains(it.key) }?.value
    } ?: Color(0xFF9E9E9E)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Graph visualization
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (commit.parents.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(8.dp)
                            .background(color)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, Color.White, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(color)
                )
            }

            // Commit info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commit.message,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = commit.hash.take(7),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = commit.author,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeAgo(commit.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                commit.branch?.let { branch ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = color.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = branch,
                            fontSize = 10.sp,
                            color = color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesView(
    repository: Repository?,
    gitRepository: MockGitRepository
) {
    if (repository == null) {
        EmptyStateMessage("Select a repository to view changes")
        return
    }

    var stagedFiles by remember { mutableStateOf(listOf<FileChange>()) }
    var unstagedFiles by remember { mutableStateOf(gitRepository.getChangedFiles(repository)) }
    var commitMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                                // Mock commit
                                delay(500)
                                stagedFiles = emptyList()
                                commitMessage = ""
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
    gitRepository: MockGitRepository,
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
                            delay(1000) // Simulate operation
                            operationResult = "✓ Pulled latest changes"
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
                            delay(1000)
                            operationResult = "✓ Pushed to origin/main"
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
                            operationResult = "✓ Created new branch"
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
                        Text("New Branch", fontWeight = FontWeight.Medium)
                        Text(
                            "Create and switch to new branch",
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
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
fun SettingsView() {
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
}

@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

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
                        text = { Text("Create") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // Clone tab
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Repository URL") },
                            placeholder = { Text("https://github.com/user/repo.git") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            }
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
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick clone options
                        Text(
                            text = "Quick clone:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    url = "https://github.com/torvalds/linux.git"
                                    name = "linux"
                                },
                                label = { Text("Linux", fontSize = 12.sp) }
                            )
                            AssistChip(
                                onClick = {
                                    url = "https://github.com/facebook/react.git"
                                    name = "react"
                                },
                                label = { Text("React", fontSize = 12.sp) }
                            )
                            AssistChip(
                                onClick = {
                                    url = "https://github.com/flutter/flutter.git"
                                    name = "flutter"
                                },
                                label = { Text("Flutter", fontSize = 12.sp) }
                            )
                        }
                    }
                    1 -> {
                        // Create tab
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Repository name") },
                            placeholder = { Text("my-new-project") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Create, contentDescription = null)
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "This will create a new Git repository locally",
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
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty()) {
                                onAdd(name, url)
                            }
                        },
                        enabled = name.isNotEmpty()
                    ) {
                        Text(if (selectedTab == 0) "Clone" else "Create")
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
