# GitFlowAndroid — Architecture Reference (Actual)

## 1. High-level flow

```
Composable Screen
    ↓ collectAsState()
ViewModel (StateFlow)
    ↓ suspend calls
IGitRepository
    ↓ JGit / local storage / auth providers
Git repo on filesystem + persisted app state
```

## 2. Main modules

### Data layer
- `data/models/Models.kt` — domain models (`Repository`, `Commit`, `FileChange`, `FileDiff`, etc.)
- `data/repository/IGitRepository.kt` — stable contract for all git operations
- `data/repository/GitRepository.kt` — composition/delegation entry point
- `data/repository/GitRepositoryMeta.kt` — repository CRUD/clone/refresh
- `data/repository/GitRepositoryIndex.kt` — status, stage/unstage, commit, conflicts, working diff
- `data/repository/GitRepositoryBranches.kt` — fetch/pull/push/branches/checkoutBranch/createBranch/deleteBranch/tags/cherry-pick/merge
- `data/repository/GitRepositoryCommits.kt` — commit list + commit diffs
- `data/repository/GitRepositoryFiles.kt` — file tree/content/restore/history
- `data/repository/GitRepositoryStash.kt` — stash operations
- `data/repository/RepositoryDataStore.kt` — persisted repository list

### Auth layer
- `data/auth/AuthManager.kt` — OAuth flow, token storage, provider handling
- `ui/auth/OAuthActivity.kt` — WebView-based auth UI

### DI layer (Koin 4.0)
- `di/AppModule.kt` — Koin module: `single { AuthManager }`, `single { AppSettingsManager }`, `single<IGitRepository> { GitRepository }`, all 8 ViewModels via `viewModel {}` / `viewModel { params -> ... }`
- `GitFlowApplication.kt` — `startKoin { androidContext; modules(appModule) }`
- Services use `KoinComponent` + `by inject()` (e.g. `CloneRepositoryService`)
- Screens use `koinViewModel()` and `koinInject()` — no manual Factory classes anywhere

### UI layer
- `ui/screens/MainScreen.kt` — top-level tabs/navigation; CallSplit branch button in TopAppBar
- `ui/screens/main/ChangesScreen.kt` — working tree/staging UX (list+tree, file actions, diff dialog)
- `ui/screens/main/ChangesViewModel.kt` — operations orchestration for changes screen
- `ui/screens/main/BranchesViewModel.kt` — branch list, checkout/create/delete with guard-mutex
- `ui/screens/EnhancedGraphScreen.kt` — commit graph
- `ui/screens/CommitDetailDialog.kt` — commit details, commit file tree, commit diff/history dialogs
- `ui/components/dialogs/BranchManagementDialog.kt` — branch dialog: local/remote list, search, ahead/behind, new branch form, delete confirm

## 3. Current design choices

- MVVM with unidirectional updates via `StateFlow`
- Business operations in repository layer, UI orchestration in ViewModels
- One repository interface (`IGitRepository`) to keep UI decoupled from implementation
- Heavy Git I/O executed on `Dispatchers.IO`

## 4. Current pain points

- Large UI files still contain mixed presentation + interaction orchestration.
- Regression tests for critical git flows are still insufficient.

## 5. Safe-change checklist

Before refactoring:
1. Verify exact call chain: Screen → ViewModel → `IGitRepository` method.
2. Prefer adding/changing method in `IGitRepository` first, then implementation.
3. Keep UI behavior backward-compatible for stage/unstage/diff/history.
4. Update EN/RU strings and markdown docs in same change-set.
