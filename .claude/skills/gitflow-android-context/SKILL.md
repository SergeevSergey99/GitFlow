---
name: gitflow-android-context
description: Context skill for the GitFlowAndroid project. Use when tasks touch architecture, repository layer, screens, OAuth/auth, or project-level recommendations/documentation.
---

# GitFlowAndroid Context Skill

## Purpose

This skill provides **up-to-date project context** for `GitFlowAndroid`, so changes are made against current architecture instead of historical structure.

## When to use

Use this skill when user asks to:
- analyze the project,
- refactor architecture,
- fix screen/repository/auth bugs,
- update docs/checklists/recommendations.

## Current project snapshot (important)

- Stack: Kotlin 2.3.10, AGP 9.5.1 (Gradle 9.6.0), Compose BOM 2026.01.01, JGit 5.13.3, **Koin 4.0**.
- Pattern: MVVM + `StateFlow`; composables never call git — only ViewModels do.
- DI: `di/AppModule.kt` — singletons `AuthManager`, `AppSettingsManager`, `IGitRepository` (`GitRepository`), `RepositorySession`; all ViewModels via `viewModel {}`.
- State of the selected repository is owned by **`RepositorySession`** (snapshot: `Repository`, `ConflictInfo`, pending pull count). It recomputes on `IGitRepository.repositoryChanges` (emitted by `GitRepository` after every mutating operation) and on DataStore updates.
- Every git operation goes through **`GitRepository.withGit`**; reads return `GitResult<T>` instead of empty lists/null on failure.
- Repository implementation is split by domains: `GitRepositoryMeta.kt`, `GitRepositoryIndex.kt`, `GitRepositoryBranches.kt`, `GitRepositoryCommits.kt`, `GitRepositoryFiles.kt`, `GitRepositoryStash.kt`.

Details: `ARCHITECTURE.md`, file map and commands: `QUICK_REFERENCE.md`.

## Practical guidance

1. **Always verify real file names before edits** (`rg --files`, `rg -n`).
2. A new mutating operation must be added to the `.also { notifyChanged(repository) }` list in `GitRepository` — otherwise screens and the conflict header won't refresh.
3. Do not add refresh counters/signals between screens: subscribe to `RepositorySession.snapshot` or `repositoryChanges`.
4. User-facing messages (data layer included) go to `values/` and `values-ru/` strings.
5. Keep docs (`CHANGELOG.md`, `RECOMMENDATIONS.md`, `AUTH_ISSUES.md`, this skill) synchronized with actual code paths and versions.

## Known risk zones

- God files: `CommitDetailDialog.kt`, `ChangesScreen.kt`, `EnhancedGraphScreen.kt`, `SettingsScreen.kt`, `AuthManager.kt`.
- Network operations (fetch/pull/push) can't be cancelled.
- `MANAGE_EXTERNAL_STORAGE` requires a Google Play declaration.
- `client_secret` ships in the APK (`assets/oauth.properties`).
