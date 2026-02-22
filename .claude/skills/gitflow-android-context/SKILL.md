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

- Stack: Kotlin 2.3.10, AGP 8.13.2, Compose BOM 2026.01.01, JGit 5.13.3.
- Pattern: MVVM + `StateFlow`.
- Repository contract: `IGitRepository`.
- Repository implementation is split by domains:
  - `GitRepositoryMeta.kt`
  - `GitRepositoryIndex.kt`
  - `GitRepositoryBranches.kt`
  - `GitRepositoryCommits.kt`
  - `GitRepositoryFiles.kt`
  - `GitRepositoryStash.kt`

## Practical guidance

1. **Always verify real file names before edits** (`rg --files`, `rg -n`).
2. Do not reference old names like `RealGitRepository.kt`.
3. For changes in working tree/stage logic, check:
   - `GitRepositoryIndex.kt`
   - `ChangesViewModel.kt`
   - `ChangesScreen.kt`
4. For commit/file history and content loading, check:
   - `GitRepositoryFiles.kt`
   - `CommitDetailDialog.kt`
5. Keep docs synchronized with actual code paths and versions.

## Known risk zones

- OAuth/WebView hardening (domain allowlist/security posture).
- Large UI files (`ChangesScreen.kt`, `CommitDetailDialog.kt`) still complex.
- Limited automated regression coverage for critical Git flows.
