# GitFlowAndroid Quick Reference

## Key files (actual)

### Repository layer
- `app/src/main/java/com/gitflow/android/data/repository/IGitRepository.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepository.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepositoryIndex.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepositoryFiles.kt`
- `app/src/main/java/com/gitflow/android/data/repository/GitRepositoryBranches.kt`

### Screens / ViewModels
- `app/src/main/java/com/gitflow/android/ui/screens/main/ChangesScreen.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/main/ChangesViewModel.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/CommitDetailDialog.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/EnhancedGraphScreen.kt`

### Auth
- `app/src/main/java/com/gitflow/android/data/auth/AuthManager.kt`
- `app/src/main/java/com/gitflow/android/ui/auth/OAuthActivity.kt`

## Typical edit map

### Stage/unstage/reset issues
1. `ChangesScreen.kt` (UI events)
2. `ChangesViewModel.kt` (flow and messages)
3. `IGitRepository.kt` + `GitRepositoryIndex.kt` (actual git command behavior)

### File diff/history behavior
1. `ChangesScreen.kt` (dialogs and triggers)
2. `IGitRepository.kt`
3. `GitRepositoryIndex.kt` (`getWorkingFileDiff...`)
4. `GitRepositoryFiles.kt` (`getFileHistoryForPath...`)

### Commit detail / file tree behavior
1. `CommitDetailDialog.kt`
2. `CommitDetailViewModel.kt`
3. `GitRepositoryFiles.kt`

## Useful commands

```bash
# Find where method is used
rg -n "getWorkingFileDiff|discardFileChanges|getFileHistoryForPath" app/src/main/java

# Find all markdown docs to keep in sync
rg --files -uu -g '*.md' .claude .

# Find strings that need localization updates
rg -n "changes_|commit_detail_|graph_" app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
```

## Common pitfalls

- Do not assume old file names (`RealGitRepository.kt`) still exist.
- Keep both locales (`values` and `values-ru`) updated.
- For staged/unstaged logic, avoid incompatible JGit reset mode combinations.
- Always test click vs checkbox behavior separately in file lists.
