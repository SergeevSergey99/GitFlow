# GitFlowAndroid Architecture Reference

## Quick Architecture Decisions

### Why MVVM?
- ✅ Clear separation UI/business logic
- ✅ ViewModel survives config changes
- ✅ Perfect for Compose reactive UI
- ⚠️ Need to add domain layer

### Why JGit?
- ✅ Pure Java, no native dependencies
- ✅ Comprehensive Git operations
- ✅ Active maintenance
- ⚠️ Performance slower than libgit2
- ⚠️ Memory hungry on large repos

### Why No DI Yet?
- ❌ Technical debt
- 📝 Should add Hilt ASAP
- Current manual instantiation hurts testability

### Why DataStore for Repository List?
- ✅ Async by default
- ✅ Type-safe with Kotlin serialization
- ⚠️ Overkill for simple list
- 💡 Should migrate to Room for complex queries

### Why Foreground Service for Clone?
- ✅ Won't be killed during long operations
- ✅ User visible notification
- ✅ Can be cancelled
- 💡 Consider WorkManager for retry logic

## Data Flow Diagram

```
┌─────────────┐
│   Screen    │ (Composable)
└──────┬──────┘
       │ collectAsState()
       ▼
┌─────────────┐
│  ViewModel  │ (StateFlow)
└──────┬──────┘
       │ suspend fun
       ▼
┌─────────────┐
│ Repository  │ (RealGitRepository)
└──────┬──────┘
       │ JGit API
       ▼
┌─────────────┐
│  Git Repo   │ (File System)
└─────────────┘
```

## OAuth Flow Diagram

```
┌──────────────┐
│  AuthScreen  │ User clicks "Connect GitHub"
└──────┬───────┘
       │
       ▼
┌──────────────┐
│AuthViewModel │ startAuth()
└──────┬───────┘
       │
       ▼
┌──────────────┐
│OAuthActivity │ Opens WebView
└──────┬───────┘
       │ Intercepts redirect
       ▼
┌──────────────┐
│ AuthManager  │ handleAuthCallback()
└──────┬───────┘
       │ Exchange code for token
       ▼
┌──────────────┐
│SharedPrefs   │ ⚠️ INSECURE - Store token
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  API Calls   │ Authenticated requests
└──────────────┘
```

## Class Responsibilities

### `RealGitRepository` (TOO BIG - 2199 lines)

**Current responsibilities:**
- Repository CRUD
- File staging/unstaging
- Commits
- Branches
- Tags
- Merges
- Conflicts
- Cherry-pick
- Rebase
- Diff generation
- File history
- Remote operations
- Clone

**Should be split into:**
```
interface GitRepository {
    val fileOps: FileOperations
    val commits: CommitRepository
    val branches: BranchRepository
    val remote: RemoteRepository
    val merge: MergeRepository
}

class FileOperationsRepository : FileOperations
class CommitRepositoryImpl : CommitRepository
class BranchRepositoryImpl : BranchRepository
class RemoteRepositoryImpl : RemoteRepository
class MergeRepositoryImpl : MergeRepository
```

### `AuthManager`

**Responsibilities:**
- OAuth flow initiation
- Callback handling
- Token storage ⚠️
- Token retrieval
- User info fetching
- Provider management

**Should add:**
- Token refresh
- Token expiration check
- Secure storage
- PKCE implementation

### ViewModels Pattern

All ViewModels follow:
```kotlin
class MyViewModel(
    private val repository: Repository // Should be injected
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Initial)
    val state: StateFlow<State> = _state.asStateFlow()

    fun performAction() {
        viewModelScope.launch {
            _state.value = State.Loading
            val result = repository.operation()
            _state.value = if (result.isSuccess) {
                State.Success(result.getOrNull()!!)
            } else {
                State.Error(result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }
}
```

## Navigation Structure

```
MainActivity (Single Activity)
└── MainScreen
    ├── RepositoryListScreen (Tab 1)
    │   ├── EnhancedGraphScreen (per repo)
    │   └── ChangesScreen (per repo)
    ├── RemoteRepositoriesScreen (Tab 2)
    │   └── Clone action → CloneRepositoryService
    └── SettingsScreen (Tab 3)

OAuthActivity (Separate for WebView)
```

## State Management Patterns

### UI State
```kotlin
sealed class UiState<out T> {
    object Initial : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

### Clone Progress
```kotlin
sealed class CloneStatus {
    object Idle : CloneStatus()
    data class Cloning(
        val taskDescription: String,
        val workCompleted: Int,
        val workTotal: Int,
        val estimatedSecondsRemaining: Long?
    ) : CloneStatus()
    data class Completed(val repoPath: String) : CloneStatus()
    data class Failed(val error: String) : CloneStatus()
    object Cancelled : CloneStatus()
}
```

## Dependency Graph (Current - BAD)

```
Composable
    ↓ (creates directly)
ViewModel
    ↓ (creates directly)
Repository
    ↓ (creates directly)
AuthManager, Settings
    ↓ (requires)
Context, SharedPreferences
```

## Dependency Graph (Target - GOOD)

```
Composable
    ↓ (hiltViewModel())
@HiltViewModel
ViewModel
    ↓ (@Inject constructor)
Repository Interface
    ↑ (implements)
Repository Impl
    ↓ (@Inject)
Data Sources (API, Local)
    ↓ (@Inject)
Context (Application), Preferences
```

## Threading Model

### Coroutine Dispatchers Usage

```kotlin
// Git operations (blocking I/O)
withContext(Dispatchers.IO) {
    Git.open(File(path))
}

// API calls (already async with Retrofit)
withContext(Dispatchers.IO) {
    api.getRepositories()
}

// UI updates (main thread)
withContext(Dispatchers.Main) {
    _state.value = newState
}

// Heavy computation
withContext(Dispatchers.Default) {
    // CPU-intensive work
}
```

### Service Coroutines

```kotlin
class CloneRepositoryService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onDestroy() {
        serviceJob.cancel() // ⚠️ Must cancel!
        super.onDestroy()
    }
}
```

## Error Handling Strategy

### Current (Inconsistent)
- Some methods return `Result<T>`
- Some throw exceptions
- Some return nullable
- Some catch and log

### Recommended (Consistent)
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Exception,
        val userMessage: String
    ) : Result<Nothing>()
}

// All repository methods
suspend fun operation(): Result<Data>

// ViewModels handle
when (result) {
    is Result.Success -> _state.value = UiState.Success(result.data)
    is Result.Error -> _state.value = UiState.Error(result.userMessage)
}
```

## Performance Optimization Strategy

### Problem: Large Repository
- Linux kernel: 1M+ commits
- Current approach loads ALL commits
- UI freezes for minutes

### Solution: Pagination
```kotlin
// Instead of:
fun getCommits(): List<Commit>

// Use:
fun getCommitsPaged(page: Int, pageSize: Int = 50): Page<Commit>

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean
)
```

### Problem: Repeated Calculations
- Every screen visit recalculates commits
- No caching layer

### Solution: Room Cache
```kotlin
@Entity
data class CachedCommit(
    @PrimaryKey val hash: String,
    val repoPath: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val cachedAt: Long
)

// Cache for 5 minutes
fun getCommits(repoPath: String): Flow<List<Commit>> {
    return flow {
        val cached = dao.getCommits(repoPath, System.currentTimeMillis() - 5.minutes)
        if (cached.isNotEmpty()) {
            emit(cached)
        } else {
            val fresh = calculateFromGit(repoPath)
            dao.insertAll(fresh)
            emit(fresh)
        }
    }
}
```

## Security Layers Needed

```
┌──────────────────────────────────────┐
│   User Input Validation              │ ❌ Missing
├──────────────────────────────────────┤
│   Encrypted Storage (Jetpack)       │ ❌ Missing
├──────────────────────────────────────┤
│   Certificate Pinning               │ ❌ Missing
├──────────────────────────────────────┤
│   PKCE (OAuth)                      │ ❌ Missing
├──────────────────────────────────────┤
│   WebView Domain Restrictions       │ ❌ Missing
├──────────────────────────────────────┤
│   ProGuard Code Obfuscation         │ ⚠️ Disabled
├──────────────────────────────────────┤
│   No Logging in Production          │ ❌ Logs everywhere
└──────────────────────────────────────┘
```

## Testing Strategy (To Implement)

### Unit Tests (0% coverage)
```
ViewModels/
├── AuthViewModel.test.kt
├── RemoteRepositoriesViewModel.test.kt
└── MainViewModel.test.kt

Repositories/
├── RealGitRepository.test.kt
└── RepositoryDataStore.test.kt

Managers/
└── AuthManager.test.kt
```

### Integration Tests
```
OAuth Flow Test
Git Operations Test
Clone → Commit → Push Test
Conflict Resolution Test
```

### UI Tests (Espresso/Compose)
```
Repository List Navigation Test
File Staging Test
Commit Creation Test
Settings Persistence Test
```

## Migration Path to Better Architecture

### Phase 1: Foundation (Week 1-2)
1. Add Hilt dependency injection
2. Create repository interfaces
3. Extract Use Cases from ViewModels
4. Add EncryptedSharedPreferences

### Phase 2: Refactoring (Week 3-4)
1. Split RealGitRepository into 5 classes
2. Add domain layer
3. Implement Result<T> everywhere
4. Remove all logging

### Phase 3: Performance (Week 5-6)
1. Add Room database
2. Implement commit pagination
3. Add network caching
4. Optimize diff generation

### Phase 4: Security (Week 7-8)
1. Add PKCE to OAuth
2. Implement certificate pinning
3. Add input validation
4. Security audit

### Phase 5: Quality (Week 9-10)
1. Write unit tests (80% coverage)
2. Add integration tests
3. Add UI tests
4. Configure ProGuard

### Phase 6: Polish (Week 11-12)
1. Update dependencies
2. Performance profiling
3. Memory leak detection
4. Beta testing

---

This architecture evolved organically but needs systematic refactoring for production readiness.
