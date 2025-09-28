package com.gitflow.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.RealGitRepository
import com.gitflow.android.ui.config.GraphConfig
import com.gitflow.android.ui.components.CloneProgressOverlay
import com.gitflow.android.ui.screens.main.ChangesScreen
import com.gitflow.android.ui.screens.main.RepositoryListScreen
import com.gitflow.android.ui.screens.main.SettingsScreen
import kotlinx.coroutines.launch

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
                        /*IconButton(onClick = { showOperationsSheet = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Operations")
                        }*/
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
                0 -> RepositoryListScreen(
                    repositories = repositories,
                    onRepositorySelected = {
                        selectedRepository = it
                        selectedTab = 1 // Switch to graph view
                    },
                    navController = navController
                )
                1 -> {
                    // Use the enhanced graph view with selected config
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
                    onGraphPresetChanged = { selectedGraphPreset = it },
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

// Извлечем также GitOperationsSheet и EnhancedGraphView, если они еще в MainScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitOperationsSheet(
    repository: Repository,
    gitRepository: RealGitRepository,
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
                "Git Operations for ${repository.name}",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pull, Push, Fetch, etc.")
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}