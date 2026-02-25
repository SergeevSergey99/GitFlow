package com.gitflow.android.ui.screens

import androidx.compose.foundation.layout.*
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

    // Обновляем выбранный репозиторий если он изменился в списке
    LaunchedEffect(repositories) {
        viewModel.updateSelectedRepositoryIfChanged(repositories)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 48.dp,
                title = {
                    selectedRepository?.let { repo ->
                        Text(
                            text = stringResource(R.string.main_screen_repo_info, repo.name, repo.currentBranch),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    selectedRepository?.let {
                        IconButton(onClick = { showBranchDialog = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = stringResource(R.string.branches_title)
                            )
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
                    gitRepository = gitRepository
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
                onBranchChanged = {
                    showBranchDialog = false
                    viewModel.refreshRepositories()
                }
            )
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
