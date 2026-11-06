# GitFlowAndroid — Architecture Reference (Actual)

## 1. High-level flow

```
Composable Screen
    ↓ collectAsState(), intents (button → viewModel.x())
ViewModel (StateFlow UiState)
    ↓ suspend calls returning GitResult<T>
IGitRepository ── repositoryChanges (repo id after each mutation)
    ↓ withGit { git -> ... }                      ↓
JGit on the filesystem        RepositorySession (selected repo snapshot)
```

## 2. Main modules

### Data layer
- `data/models/Models.kt` — domain models (`Repository`, `Commit`, `FileChange`, `FileDiff`, `MergeConflict`, ...).
- `data/models/GitResult.kt` — `Success` / `Failure` (`Generic`, `Conflict`, `NoStagedChanges`, `IdentityRequired`, `TagAlreadyExists`, `Cancelled`, `NetworkError`, `AuthRequired`); failures carry a localized message.
- `data/repository/IGitRepository.kt` — contract for all git operations + `repositoryChanges: Flow<String>`.
- `data/repository/GitRepository.kt` — delegation entry point; `withGit`, `notifyChanged`, credentials (`resolveCredentialsProvider`), commit identity (`resolveCommitIdentity`, `applyCommitIdentity`), remembered history order (`historyOrder`), `text()` for string resources.
- `data/repository/GitRepositoryMeta.kt` — add/create/clone/remove/refresh repositories.
- `data/repository/GitRepositoryIndex.kt` — status with line stats, stage/unstage/discard, working diff, conflicts, commit/amend.
- `data/repository/GitRepositoryBranches.kt` — branches, fetch/pull/push, reset, tags, cherry-pick, merge/rebase, targeted abort, `ConflictInfo`.
- `data/repository/GitRepositoryCommits.kt` — history pages (topological order cached until branch tips change), commit diffs, shared `parseDiffText`.
- `data/repository/GitRepositoryFiles.kt` — commit file tree/content, restore, file history.
- `data/repository/GitRepositoryStash.kt` — stash (optionally with untracked files).
- `data/repository/RepositorySession.kt` — selected repository (persists `lastRepositoryId`), `snapshot: StateFlow<RepositorySnapshot?>`, `isRestoring`; clears the selection when the repository is removed or its folder disappears.
- `data/repository/RepositoryDataStore.kt` — persisted repository list.
- `data/settings/AppSettingsManager.kt` — preferences; observed values (`colorThemeFlow`, `darkModeFlow`, `graphPresetFlow`, `previewExtensionsFlow`, `previewFileNamesFlow`) are updated by one `SharedPreferences` listener.
- `data/storage/SharedStorage.kt` — `treeUriToFile` (picked folder → path, SD cards included), `hasSharedStorageAccess` ("All files access" on Android 11+).

### Auth layer
- `data/auth/AuthManager.kt` — OAuth (PKCE, pending verifiers in encrypted prefs), PAT, tokens, provider APIs, PR lists.
- `ui/auth/OAuthActivity.kt` — Custom Tab + redirect via `.OAuthCallbackActivity` alias (`singleTask`, `onNewIntent`); returns code, state and provider.
- `services/TokenRefreshWorker.kt` — periodic GitLab/Bitbucket refresh, notifies about an expired session once.

### DI layer (Koin 4.0)
- `di/AppModule.kt` — singletons + ViewModels; runtime parameters via `parametersOf(repository)` / `parametersOf(commit, repository)`.
- `GitFlowApplication.kt` — `startKoin`, schedules `TokenRefreshWorker`.
- Non-composable code reads singletons from Koin (`by inject()`, `GlobalContext.get().get()`), never constructs them.

### UI layer
- `ui/screens/MainScreen.kt` + `MainViewModel.kt` — tabs, conflict header from `RepositorySession.snapshot`.
- `ui/screens/EnhancedGraphScreen.kt` + `GraphViewModel.kt` — commit graph, commit actions (`operationResult` one-shot).
- `ui/screens/main/ChangesScreen.kt` + `ChangesViewModel.kt` — working tree, staging, diff/history dialogs, conflicts, stash, sync.
- `ui/components/dialogs/BranchManagementDialog.kt` + `ui/screens/main/BranchesViewModel.kt` — branch operations, merge/rebase banner.
- `ui/screens/CommitDetailDialog.kt` + `CommitDetailViewModel.kt` — commit details, file tree, file viewer; the dialog owns its `ViewModelStoreOwner`.
- `ui/screens/main/SettingsScreen.kt` + `SettingsViewModel.kt` — accounts, permissions (incl. "All files access"), storage folder, preview settings.

## 3. Current design choices

- One owner of the selected repository state (`RepositorySession`); screens react to `repositoryChanges` instead of passing refresh signals.
- Reads report failures (`GitResult.Failure`), screens keep the last good data and show the error.
- Merge/rebase abort is targeted: only paths changed by the incoming side are rolled back.
- Commit identity is never invented; operations without an identity return `IdentityRequired`.

## 4. Current pain points

- Large UI files still mix presentation and interaction code.
- Network operations are not cancellable.
- Auth failure detection relies on `TransportException` message text.

## 5. Safe-change checklist

Before refactoring:
1. Verify the call chain: Screen → ViewModel → `IGitRepository` method.
2. Change `IGitRepository` first, then the implementation; mutating operations get `notifyChanged`.
3. Run the unit tests (see `QUICK_REFERENCE.md`).
4. Update EN/RU strings and markdown docs in the same change-set.
