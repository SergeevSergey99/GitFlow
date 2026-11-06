# GitFlowAndroid Quick Reference

## Key files (actual)

### Project docs
- `CHANGELOG.md`
- `RECOMMENDATIONS.md`
- `AUTH_ISSUES.md`
- `README.md`

### Repository layer
- `app/src/main/java/com/gitflow/android/data/repository/IGitRepository.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepository.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepositoryIndex.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepositoryFiles.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepositoryBranches.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepositoryCommits.kt`
- `app/src/main/java/com/gitflow/android/data/repository/RepositorySession.kt`

### Screens / ViewModels
- `app/src/main/java/com/gitflow/android/ui/screens/MainScreen.kt`, `MainViewModel.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/EnhancedGraphScreen.kt`, `GraphViewModel.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/main/ChangesScreen.kt`, `ChangesViewModel.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/main/BranchesViewModel.kt`
- `app/src/main/java/com/gitflow/android/ui/components/dialogs/BranchManagementDialog.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/CommitDetailDialog.kt`, `CommitDetailViewModel.kt`

### Auth / settings / storage
- `app/src/main/java/com/gitflow/android/data/auth/AuthManager.kt`
- `app/src/main/java/com/gitflow/android/ui/auth/OAuthActivity.kt`
- `app/src/main/java/com/gitflow/android/data/settings/AppSettingsManager.kt`
- `app/src/main/java/com/gitflow/android/data/storage/SharedStorage.kt`

### DI
- `app/src/main/java/com/gitflow/android/di/AppModule.kt`

## Typical edit map

### Stage/unstage/discard/commit issues
1. `ChangesScreen.kt` (UI events) → `ChangesViewModel.kt` (state, messages)
2. `IGitRepository.kt` + `GitRepositoryIndex.kt`

### Working diff / file history
1. `ChangesViewModel.openDiff` / `openFileHistory`
2. `GitRepositoryIndex.kt` (`workingFileDiff`, `diffStats`), `GitRepositoryFiles.kt` (`getFileHistoryForPathImpl`)

### Graph / commit actions
1. `EnhancedGraphScreen.kt` (dialogs) → `GraphViewModel.kt` (`runOperation`, `operationResult`)
2. `GitRepositoryCommits.kt` (`getCommitsImpl`, `historyOrder`), `GitRepositoryBranches.kt`

### Commit detail / file tree
1. `CommitDetailDialog.kt` → `CommitDetailViewModel.kt`
2. `GitRepositoryCommits.kt` (diffs), `GitRepositoryFiles.kt` (tree, content, restore)

### Branches, merge/rebase, conflict header
1. `BranchManagementDialog.kt` → `BranchesViewModel.kt`
2. `GitRepositoryBranches.kt`
3. Header state: `RepositorySession` → `MainViewModel.snapshot` → `MainScreen.kt`

## Useful commands

```bash
# Unit tests (no gradlew in the repo: Gradle and JBR from Android Studio)
JAVA_HOME="<Android Studio>/jbr" "<GRADLE_USER_HOME>/wrapper/dists/gradle-9.6.0-bin/<hash>/gradle-9.6.0/bin/gradle" :app:testDebugUnitTest

# Find where a method is used
rg -n "getWorkingFileDiff|discardFileChanges|getFileHistoryForPath" app/src/main/java

# Find all markdown docs to keep in sync
rg --files -uu -g '*.md' .claude .
```

### Adding a new ViewModel / dependency

1. Add to `di/AppModule.kt` — `viewModel { MyViewModel(get()) }` or `viewModel { params -> MyViewModel(params.get()) }`.
2. Use `koinViewModel()` in the composable; with a runtime parameter: `koinViewModel(key = entity.id, parameters = { parametersOf(entity) })`.

## Common pitfalls

- A new mutating `IGitRepository` method without `.also { notifyChanged(repository) }` leaves screens stale.
- Robolectric tests need `@Config(application = Application::class)`; git tests that check identity must isolate `~/.gitconfig` (see `GitRepositoryMergeRebaseTest.IsolatedConfigReader`).
- ViewModels using `viewModelScope` in tests need `Dispatchers.setMain(UnconfinedTestDispatcher())`.
- Source files use CRLF; shell `sed -i`/`awk` in Git Bash rewrite them to LF.
- Keep both locales (`values` and `values-ru`) updated.
- `koinInject` is in `org.koin.compose`; the `viewModel {}` DSL import is `org.koin.core.module.dsl.viewModel`.
