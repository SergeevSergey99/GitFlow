---
name: gitflow-android-context
description: Provides comprehensive context about the GitFlowAndroid project - an Android Git client. Use this skill when working on any task related to this project to understand architecture, known issues, tech stack, security concerns, and codebase structure. Automatically invoke when user asks about project improvements, refactoring, or needs context about the codebase.
---

# GitFlowAndroid Project Context

## Project Overview

**GitFlowAndroid** - полнофункциональный Git-клиент для Android с поддержкой OAuth авторизации для GitHub и GitLab, визуализацией истории коммитов, фоновым клонированием репозиториев и полным набором Git-операций.

**Language:** Kotlin
**UI Framework:** Jetpack Compose (Material 3)
**Architecture:** MVVM с чистой архитектурой
**Git Library:** JGit 5.13.3
**Build System:** Gradle (Kotlin DSL)
**Min SDK:** 26 (Android 8.0)
**Target SDK:** 34 (Android 14)

## Tech Stack

### Core Dependencies
- **Kotlin:** 1.9.0
- **Compose BOM:** 2024.04.01
- **JGit:** 5.13.3.202401111512-r
- **Retrofit:** 2.9.0 (для GitHub/GitLab API)
- **Gson:** 2.10.1
- **DataStore:** 1.0.0 (preferences)
- **Coroutines:** 1.7.3
- **AndroidX Core KTX:** 1.12.0

### Notable Libraries
- Material 3 components
- Navigation Compose
- Lifecycle ViewModel Compose
- Work Manager (planned for background operations)

## Project Structure

```
app/src/main/java/com/gitflow/android/
├── GitFlowApplication.kt          # Application entry point
├── MainActivity.kt                # Single activity host
├── data/
│   ├── auth/
│   │   ├── AuthManager.kt         # OAuth flow management (618 lines)
│   │   ├── OAuthConfig.kt         # OAuth credentials loader
│   │   ├── GitHubApi.kt           # GitHub REST API
│   │   └── GitLabApi.kt           # GitLab REST API
│   ├── models/
│   │   └── Models.kt              # Data classes
│   ├── repository/
│   │   ├── RealGitRepository.kt   # Git operations (2199 lines!) ⚠️
│   │   ├── RepositoryDataStore.kt # Repository list persistence
│   │   └── CloneProgressCallback.kt # Clone progress tracking
│   └── settings/
│       └── AppSettingsManager.kt  # App preferences
├── ui/
│   ├── auth/
│   │   ├── AuthScreen.kt          # OAuth start screen
│   │   ├── AuthViewModel.kt       # Auth state management
│   │   └── OAuthActivity.kt       # WebView for OAuth
│   ├── components/
│   │   ├── CloneProgressOverlay.kt
│   │   ├── FileChangeCard.kt
│   │   ├── RepositoryCard.kt
│   │   └── dialogs/
│   ├── config/
│   │   └── GraphConfig.kt         # Commit graph presets
│   ├── repositories/
│   │   ├── RemoteRepositoriesScreen.kt
│   │   └── RemoteRepositoriesViewModel.kt
│   ├── screens/
│   │   ├── MainScreen.kt          # Main navigation
│   │   ├── EnhancedGraphScreen.kt # Commit graph visualization
│   │   ├── CommitDetailDialog.kt
│   │   └── main/
│   │       ├── ChangesScreen.kt   # Working directory changes
│   │       ├── RepositoryListScreen.kt
│   │       └── SettingsScreen.kt
│   └── theme/
│       └── Theme.kt               # Material 3 theming
└── services/
    └── CloneRepositoryService.kt  # Foreground service for cloning
```

## Key Features Implemented

1. **Repository Management**
   - Create/open/clone/delete local repositories
   - Browse remote GitHub/GitLab repositories
   - SAF (Storage Access Framework) support (partial)

2. **OAuth Authentication**
   - GitHub OAuth 2.0 flow
   - GitLab OAuth 2.0 flow
   - Token storage and management
   - User profile retrieval

3. **Git Operations**
   - Stage/unstage files
   - Commit with message
   - Pull/push
   - Fetch
   - Merge (with conflict detection)
   - Cherry-pick
   - Rebase
   - Branch create/delete/checkout
   - Tag operations
   - File history tracking
   - Diff generation with hunks

4. **UI Features**
   - Commit graph visualization with presets
   - File changes view (staged/unstaged)
   - Conflict resolution UI
   - Background cloning with notifications
   - Progress tracking with ETA
   - Dark/Light theme support
   - Russian/English localization

5. **Background Processing**
   - Foreground service for long-running clones
   - Cancellable operations
   - Progress notifications

## Architecture Details

### MVVM Pattern
- **Models:** Data classes in `data/models/Models.kt`
- **ViewModels:** Manage UI state with StateFlow
- **Views:** Jetpack Compose screens

### State Management
- `StateFlow` for reactive UI updates
- `Flow` for repository list changes
- `MutableState` in Composables for local state

### Async Operations
- Coroutines with `Dispatchers.IO` for Git operations
- Foreground service for background cloning
- SupervisorJob for service coroutines

### Data Persistence
- **DataStore:** Repository list (JSON serialization)
- **SharedPreferences:** OAuth tokens ⚠️ INSECURE
- **Assets:** OAuth client credentials

## Critical Issues (MUST FIX)

### 🚨 SECURITY VULNERABILITIES (HIGH PRIORITY)

1. **Insecure Token Storage** (`AuthManager.kt:473-480`)
   - OAuth tokens stored in plain SharedPreferences
   - **Fix:** Use EncryptedSharedPreferences from Jetpack Security
   ```kotlin
   // Current (INSECURE):
   private val preferences: SharedPreferences =
       context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

   // Should be:
   EncryptedSharedPreferences.create(...)
   ```

2. **Credential Logging** (Multiple files)
   - `AuthManager.kt:128-148, 434-447` - logs access tokens
   - `RealGitRepository.kt:338-353` - logs token prefixes
   - **Fix:** Remove ALL credential logging
   ```kotlin
   // NEVER DO THIS:
   Log.d("AuthManager", "Access Token: ${token.accessToken}")
   ```

3. **Tokens Embedded in URLs** (`RealGitRepository.kt:440, 444`)
   - Tokens inserted into clone URLs and logged
   - **Fix:** Use JGit's CredentialsProvider
   ```kotlin
   // Instead of embedding in URL, use:
   val credentialsProvider = UsernamePasswordCredentialsProvider(
       token.accessToken, ""
   )
   Git.cloneRepository()
       .setCredentialsProvider(credentialsProvider)
   ```

4. **No PKCE in OAuth Flow**
   - Mobile OAuth without PKCE is vulnerable to code interception
   - **Fix:** Implement PKCE (code_verifier/code_challenge)

5. **Client Secrets in APK** (`OAuthConfig.kt`)
   - Secrets can be extracted from APK
   - **Fix:** Use backend proxy or GitHub Device Flow

6. **WebView Security** (`OAuthActivity.kt:153`)
   - JavaScript enabled without domain restrictions
   - **Fix:** Validate URLs, restrict to GitHub/GitLab domains

### ⚡ PERFORMANCE PROBLEMS

1. **Loading All Commits** (`RealGitRepository.kt:489-574`)
   - Loads entire commit history synchronously
   - Freezes UI on large repositories (e.g., Linux kernel)
   - **Fix:** Implement pagination (50-100 commits per page)

2. **Synchronous Diff Processing** (`RealGitRepository.kt:1013-1107`)
   - Parses all diffs at once
   - **Fix:** Process in chunks, lazy loading

3. **No Caching**
   - Commit data recalculated on every access
   - API responses not cached
   - **Fix:** Add Room database for caching

4. **Memory Issues**
   - Large repositories can cause OOM
   - RevWalk objects not always disposed
   - **Fix:** Proper resource management, pagination

### 🏗️ ARCHITECTURE ISSUES

1. **No Dependency Injection**
   - Manual instantiation in Composables (`MainScreen.kt:31`)
   - Hard to test and maintain
   - **Fix:** Add Hilt or Koin
   ```kotlin
   // Current:
   val gitRepository = remember { RealGitRepository(context) }

   // Should be:
   @HiltViewModel
   class MainViewModel @Inject constructor(
       private val gitRepository: GitRepository
   ) : ViewModel()
   ```

2. **God Class** - `RealGitRepository.kt` (2199 lines)
   - Too many responsibilities
   - **Fix:** Split into:
     - `CommitRepository`
     - `BranchRepository`
     - `FileOperationsRepository`
     - `MergeRepository`
     - `RemoteRepository`

3. **No Interfaces**
   - `RealGitRepository` is concrete class
   - Can't mock for testing
   - **Fix:** Create `interface GitRepository`

4. **Missing Domain Layer**
   - ViewModels directly use repositories
   - Business logic mixed with data access
   - **Fix:** Add Use Cases/Interactors

5. **Context Dependencies**
   - `AuthManager`, `RealGitRepository` require Context
   - Tight coupling to Android framework
   - **Fix:** Inject Application context via DI

### 🐛 OTHER ISSUES

1. **Error Handling Inconsistency**
   - Mix of `Result<T>`, exceptions, and nullable returns
   - **Fix:** Standardize on `Result<T>` sealed class

2. **No Testing**
   - Minimal unit tests
   - No integration tests
   - No UI tests
   - **Fix:** Add comprehensive test coverage

3. **Excessive Logging**
   - 100+ Log.d/Log.e statements
   - **Fix:** Use Timber, remove in production builds

4. **Missing Error Recovery**
   - No retry logic for network failures
   - No offline mode
   - No exponential backoff

5. **Concurrent Operation Issues**
   - No locks for git operations
   - Multiple simultaneous clones possible
   - Race conditions on repository list

6. **Memory Leaks**
   - WebView not destroyed (`OAuthActivity.kt`)
   - SupervisorJob not cancelled in all paths
   - Flow collectors may outlive lifecycle

## Important File Locations

### Configuration
- **OAuth Credentials:** `app/src/main/assets/oauth.properties` (gitignored)
- **OAuth Example:** `app/src/main/assets/oauth.properties.example`
- **ProGuard Rules:** `app/proguard-rules.pro` (needs JGit rules)
- **App Settings:** Via DataStore, managed by `AppSettingsManager.kt`

### Resources
- **Strings (EN):** `app/src/main/res/values/strings.xml`
- **Strings (RU):** `app/src/main/res/values-ru/strings.xml`
- **Colors:** `app/src/main/res/values/colors.xml`
- **Themes:** `app/src/main/res/values/themes.xml`

### Build
- **Root Build:** `build.gradle.kts`
- **App Build:** `app/build.gradle.kts`
- **Gradle Properties:** `gradle.properties`
- **Settings:** `settings.gradle.kts`

## Dependencies to Update

1. **JGit:** 5.13.3 → 6.x (current latest: 6.10.0)
2. **Kotlin:** 1.9.0 → 2.0+ (K2 compiler)
3. **Compose BOM:** Check for latest
4. **AGP:** Check for latest version

## Recommendations Summary

### Must Do (Before Release)
1. ✅ Implement EncryptedSharedPreferences
2. ✅ Remove all credential logging
3. ✅ Use CredentialsProvider instead of URL embedding
4. ✅ Add PKCE to OAuth flow
5. ✅ Setup Hilt for DI
6. ✅ Add pagination for commits
7. ✅ Add basic unit tests

### Should Do (For Quality)
1. 📝 Split RealGitRepository into smaller classes
2. 📝 Create GitRepository interface
3. 📝 Add domain layer with Use Cases
4. 📝 Implement Room for caching
5. 📝 Add retry logic and offline support
6. 📝 Configure ProGuard/R8 properly
7. 📝 Add comprehensive error handling

### Nice to Have (Future)
1. 💡 Git stash operations
2. 💡 Enhanced diff viewer UI
3. 💡 Commit search/filter
4. 💡 Biometric authentication
5. 💡 Certificate pinning
6. 💡 WorkManager for background operations
7. 💡 Multi-account support

## Code Patterns to Follow

### Git Operations
```kotlin
suspend fun gitOperation(repoPath: String): Result<T> = withContext(Dispatchers.IO) {
    try {
        val git = Git.open(File(repoPath))
        git.use {
            // Perform operation
            Result.success(data)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### StateFlow in ViewModel
```kotlin
private val _state = MutableStateFlow<UiState>(UiState.Initial)
val state: StateFlow<UiState> = _state.asStateFlow()

fun performAction() {
    viewModelScope.launch {
        _state.value = UiState.Loading
        val result = repository.operation()
        _state.value = when {
            result.isSuccess -> UiState.Success(result.getOrNull()!!)
            else -> UiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }
}
```

### Composable State Collection
```kotlin
@Composable
fun Screen(viewModel: MyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    when (state) {
        is UiState.Loading -> LoadingIndicator()
        is UiState.Success -> Content(state.data)
        is UiState.Error -> ErrorMessage(state.message)
    }
}
```

## Git Status Snapshot (Last Known)

**Branch:** master
**Main Branch:** master

**Modified Files (43):**
- Core architecture files modified
- All major screens and components updated
- Build configuration changed
- Resources updated

**Untracked:**
- `RECOMMENDATIONS.md` - Analysis document
- `build/` - Build artifacts

**Recent Commits:**
- `0651fe1` - extra operations
- `1216762` - merge conflicts
- `f8efe67` - smthng
- `54e7873` - update diff view
- `cd32aab` - fix

## Common Tasks Guide

### Adding New Git Operation
1. Add method to `RealGitRepository.kt` (or better: create new repository class)
2. Use `suspend fun` with `withContext(Dispatchers.IO)`
3. Return `Result<T>` for error handling
4. Add corresponding UI in ViewModel
5. Update UI screen to call ViewModel method

### Adding New Screen
1. Create Composable in `ui/screens/`
2. Create ViewModel if needed
3. Add navigation route in `MainScreen.kt`
4. Add strings to `strings.xml` (both EN and RU)

### Modifying OAuth
1. Update `AuthManager.kt` for auth logic
2. Update `OAuthActivity.kt` for WebView flow
3. Update API interfaces (`GitHubApi.kt`, `GitLabApi.kt`)
4. Update models in `Models.kt`

### Adding Settings
1. Add to `AppSettingsManager.kt`
2. Update `SettingsScreen.kt` UI
3. Use in relevant ViewModels/Repositories

## Testing Guidance

### Unit Tests Needed For
- `AuthManager` - OAuth flow logic
- `RealGitRepository` - Git operations
- ViewModels - State management
- Extension functions - Data mapping

### Integration Tests Needed For
- End-to-end OAuth flow
- Git clone → commit → push flow
- Conflict resolution workflow

### UI Tests Needed For
- Repository list navigation
- File staging/unstaging
- Commit creation
- Settings changes

## Known Bugs/TODOs

1. **Line 215 in RealGitRepository.kt** - SAF directory support incomplete
2. **Lines 70-72 in MainScreen.kt** - Commented code should be removed
3. **OAuth token refresh** - Not implemented for GitLab
4. **Network error handling** - Minimal retry logic
5. **Large file handling** - No LFS support
6. **Submodules** - Not supported yet

## Build Configuration Notes

- **minifyEnabled:** Currently `false`, should be `true` for release
- **ProGuard:** Needs JGit-specific keep rules
- **Signing:** Not configured in build files
- **Build variants:** Only debug/release, no staging

## Security Checklist

Before release:
- [ ] EncryptedSharedPreferences implemented
- [ ] All credential logging removed
- [ ] PKCE added to OAuth
- [ ] Certificate pinning configured
- [ ] ProGuard rules tested
- [ ] Input validation added
- [ ] WebView domain restrictions added
- [ ] OAuth secrets moved to backend (or documented risk)
- [ ] Security audit performed

## Performance Checklist

Before release:
- [ ] Commit pagination implemented
- [ ] Room caching added
- [ ] Large repository tested (e.g., Linux kernel)
- [ ] Memory profiling completed
- [ ] UI rendering profiled
- [ ] Network requests optimized
- [ ] Background operations moved to WorkManager

---

**Last Updated:** 2025-11-15
**Analysis By:** Claude (Sonnet 4.5)
**Project Version:** In active development (pre-release)
