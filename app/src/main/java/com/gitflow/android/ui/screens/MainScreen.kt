package com.gitflow.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gitflow.android.R
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.ui.config.GraphConfig
import com.gitflow.android.ui.components.CloneProgressOverlay
import com.gitflow.android.ui.screens.main.ChangesScreen
import com.gitflow.android.ui.screens.main.RepositoryListScreen
import com.gitflow.android.ui.screens.main.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    val viewModel: MainViewModel = viewModel()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedRepository by viewModel.selectedRepository.collectAsState()
    val selectedGraphPreset by viewModel.selectedGraphPreset.collectAsState()
    val repositories by viewModel.repositoriesFlow.collectAsState(initial = emptyList())
    val gitRepository = viewModel.getGitRepository()

    var showOperationsSheet by remember { mutableStateOf(false) }

    // Обновляем выбранный репозиторий если он изменился в списке
    LaunchedEffect(repositories) {
        viewModel.updateSelectedRepositoryIfChanged(repositories)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.main_screen_title),
                            fontWeight = FontWeight.Bold
                        )
                        selectedRepository?.let { repo ->
                            Text(
                                text = stringResource(R.string.main_screen_repo_info, repo.name, repo.currentBranch),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {},
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
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AccountTree, contentDescription = stringResource(R.string.main_screen_tab_graph)) },
                    label = { Text(stringResource(R.string.main_screen_tab_graph)) },
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.main_screen_tab_changes)) },
                    label = { Text(stringResource(R.string.main_screen_tab_changes)) },
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.main_screen_tab_settings)) },
                    label = { Text(stringResource(R.string.main_screen_tab_settings)) },
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) }
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
                0 -> RepositoryListScreen(
                    repositories = repositories,
                    gitRepository = gitRepository,
                    onRepositorySelected = { viewModel.selectRepository(it) },
                    navController = navController
                )
                1 -> {
                    EnhancedGraphView(
                        repository = selectedRepository,
                        gitRepository = gitRepository,
                        config = getGraphConfig(selectedGraphPreset)
                    )
                }
                2 -> ChangesScreen(
                    repository = selectedRepository,
                    gitRepository = gitRepository
                )
                3 -> SettingsScreen(
                    selectedGraphPreset = selectedGraphPreset,
                    onGraphPresetChanged = { viewModel.changeGraphPreset(it) },
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
    if (showOperationsSheet && selectedRepository != null) {
        GitOperationsSheet(
            repository = selectedRepository!!,
            gitRepository = gitRepository,
            onDismiss = { showOperationsSheet = false }
        )
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
