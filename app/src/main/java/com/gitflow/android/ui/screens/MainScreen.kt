package com.gitflow.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.navigation.NavController
import com.gitflow.android.R
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.ui.config.GraphConfig
import com.gitflow.android.ui.components.CloneProgressOverlay
import com.gitflow.android.ui.components.dialogs.BranchManagementDialog
import com.gitflow.android.ui.screens.main.ChangesScreen
import com.gitflow.android.ui.screens.main.RepositoryListScreen
import com.gitflow.android.ui.screens.main.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    val viewModel: MainViewModel = koinViewModel()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedRepository by viewModel.selectedRepository.collectAsState()
    val selectedGraphPreset by viewModel.selectedGraphPreset.collectAsState()
    val selectedColorTheme by viewModel.selectedColorTheme.collectAsState()
    val selectedDarkMode by viewModel.selectedDarkMode.collectAsState()
    val isRestoringSession by viewModel.isRestoringSession.collectAsState()
    val repositories by viewModel.repositoriesFlow.collectAsState(initial = emptyList())
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val gitRepository = viewModel.getGitRepository()

    var showOperationsSheet by remember { mutableStateOf(false) }
    var showBranchDialog by remember { mutableStateOf(false) }

    // Number of commits the current branch is behind its upstream (i.e. pullable), shown in
    // the header before the branch name — same source the Changes tab uses for "Pull (N)".
    var pendingPullCount by remember { mutableStateOf(0) }

    val operationState by viewModel.operationState.collectAsState()
    val conflictPaths by viewModel.conflictPaths.collectAsState()
    val operationSignal by viewModel.operationSignal.collectAsState()
    val inConflict = operationState != RepoOperationState.NONE
    var showConflictInfo by remember { mutableStateOf(false) }

    // Обновляем выбранный репозиторий если он изменился в списке
    LaunchedEffect(repositories) {
        viewModel.updateSelectedRepositoryIfChanged(repositories)
    }

    LaunchedEffect(selectedRepository) {
        val repo = selectedRepository
        pendingPullCount = if (repo != null) {
            try {
                gitRepository.getBranches(repo)
                    .firstOrNull { it.isLocal && it.name == repo.currentBranch }?.behind ?: 0
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
    }

    // Re-check merge/rebase state whenever the repo or the active tab changes.
    LaunchedEffect(selectedRepository, selectedTab) {
        viewModel.refreshOperationState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 48.dp,
                title = {
                    selectedRepository?.let { repo ->
                        if (inConflict) {
                            // Conflict mode: red header, tap anywhere on the title for details.
                            Row(
                                modifier = Modifier.clickable { showConflictInfo = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = conflictHeaderText(operationState, conflictPaths.size),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${repo.name}  •  ",
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (pendingPullCount > 0) {
                                    Text(
                                        text = "↓$pendingPullCount ",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = repo.currentBranch,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    selectedRepository?.let {
                        if (inConflict) {
                            // Rebase can be continued (once resolved); both can be aborted.
                            if (operationState == RepoOperationState.REBASING) {
                                IconButton(
                                    onClick = { viewModel.continueRebase() },
                                    enabled = conflictPaths.isEmpty()
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.main_conflict_continue),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.abortMergeOrRebase() }) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = stringResource(R.string.main_conflict_abort),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        } else {
                            IconButton(onClick = { showBranchDialog = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.CallSplit,
                                    contentDescription = stringResource(R.string.branches_title)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (inConflict) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = if (inConflict) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = if (inConflict) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.main_screen_tab_repos)) },
                    label = { Text(stringResource(R.string.main_screen_tab_repos)) },
                    selected = selectedTab == MainTab.REPOSITORIES,
                    onClick = { viewModel.selectTab(MainTab.REPOSITORIES) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AccountTree, contentDescription = stringResource(R.string.main_screen_tab_graph)) },
                    label = { Text(stringResource(R.string.main_screen_tab_graph)) },
                    selected = selectedTab == MainTab.GRAPH,
                    onClick = { viewModel.selectTab(MainTab.GRAPH) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.main_screen_tab_changes)) },
                    label = { Text(stringResource(R.string.main_screen_tab_changes)) },
                    selected = selectedTab == MainTab.CHANGES,
                    onClick = { viewModel.selectTab(MainTab.CHANGES) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.main_screen_tab_settings)) },
                    label = { Text(stringResource(R.string.main_screen_tab_settings)) },
                    selected = selectedTab == MainTab.SETTINGS,
                    onClick = { viewModel.selectTab(MainTab.SETTINGS) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isRestoringSession) {
                // Ждём восстановления сессии — ничего не показываем чтобы избежать мигания
                return@Box
            }
            when (selectedTab) {
                MainTab.REPOSITORIES -> RepositoryListScreen(
                    repositories = repositories,
                    selectedRepositoryId = selectedRepository?.id,
                    gitRepository = gitRepository,
                    onRepositorySelected = { viewModel.selectRepository(it) },
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshRepositories() },
                    navController = navController
                )
                MainTab.GRAPH -> {
                    EnhancedGraphView(
                        repository = selectedRepository,
                        gitRepository = gitRepository,
                        config = getGraphConfig(selectedGraphPreset)
                    )
                }
                MainTab.CHANGES -> ChangesScreen(
                    repository = selectedRepository,
                    gitRepository = gitRepository,
                    onGoToSettings = { viewModel.selectTab(MainTab.SETTINGS) },
                    // Keep the conflict header in sync when conflicts are resolved here.
                    onRepoStateChanged = { viewModel.refreshOperationState() },
                    // Bumped when the header aborts/continues so this tab reloads.
                    externalRefreshSignal = operationSignal
                )
                MainTab.SETTINGS -> SettingsScreen(
                    selectedGraphPreset = selectedGraphPreset,
                    onGraphPresetChanged = { viewModel.changeGraphPreset(it) },
                    selectedColorTheme = selectedColorTheme,
                    onColorThemeChanged = { viewModel.changeColorTheme(it) },
                    selectedDarkMode = selectedDarkMode,
                    onDarkModeChanged = { viewModel.changeDarkMode(it) },
                    navController = navController
                )
            }

            CloneProgressOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp)
            )
        }
    }

    // Git Operations Bottom Sheet
    if (showOperationsSheet) {
        selectedRepository?.let { repository ->
            GitOperationsSheet(
                repository = repository,
                gitRepository = gitRepository,
                onDismiss = { showOperationsSheet = false }
            )
        }
    }

    // Branch Management Dialog
    if (showBranchDialog) {
        selectedRepository?.let { repo ->
            BranchManagementDialog(
                repository = repo,
                currentBranch = repo.currentBranch,
                onDismiss = { showBranchDialog = false },
                // Refresh the underlying repo state after a mutation, but let the dialog stay
                // open — merge/rebase conflicts require the user to remain and act on them.
                // A merge/rebase doesn't change Repository fields, so also re-check the
                // operation state directly, otherwise the conflict header lags until a tab switch.
                onBranchChanged = {
                    viewModel.refreshRepositories()
                    viewModel.refreshOperationState()
                }
            )
        }
    }

    if (showConflictInfo) {
        ConflictInfoDialog(
            state = operationState,
            conflictPaths = conflictPaths,
            onOpenChanges = {
                showConflictInfo = false
                viewModel.selectTab(MainTab.CHANGES)
            },
            onDismiss = { showConflictInfo = false }
        )
    }
}

@Composable
private fun conflictHeaderText(state: RepoOperationState, conflictCount: Int): String = when {
    state == RepoOperationState.REBASING && conflictCount == 0 -> stringResource(R.string.main_conflict_rebase_ready)
    state == RepoOperationState.REBASING -> stringResource(R.string.main_conflict_rebase, conflictCount)
    conflictCount == 0 -> stringResource(R.string.main_conflict_merge_ready)
    else -> stringResource(R.string.main_conflict_merge, conflictCount)
}

@Composable
private fun ConflictInfoDialog(
    state: RepoOperationState,
    conflictPaths: List<String>,
    onOpenChanges: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_conflict_info_title)) },
        text = {
            if (conflictPaths.isEmpty()) {
                Text(
                    stringResource(
                        if (state == RepoOperationState.REBASING) R.string.main_conflict_info_ready_rebase
                        else R.string.main_conflict_info_ready_merge
                    )
                )
            } else {
                Column {
                    Text(stringResource(R.string.main_conflict_info_resolve_hint))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(conflictPaths) { path ->
                            Text(
                                text = path,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenChanges) {
                Text(stringResource(R.string.main_conflict_info_open_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.commit_detail_close))
            }
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitOperationsSheet(
    repository: Repository,
    gitRepository: IGitRepository,
    onDismiss: () -> Unit
) {
    // TODO: Implement GitOperationsSheet - это большой компонент, оставим пока заглушку
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                stringResource(R.string.main_screen_git_operations, repository.name),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.main_screen_operations_placeholder))
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
