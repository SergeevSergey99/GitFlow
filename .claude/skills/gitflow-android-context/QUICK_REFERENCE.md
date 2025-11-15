# GitFlowAndroid Quick Reference

## File Locations Cheatsheet

### Most Edited Files
```
# Main Git operations
app/src/main/java/com/gitflow/android/data/repository/RealGitRepository.kt

# OAuth logic
app/src/main/java/com/gitflow/android/data/auth/AuthManager.kt

# Main navigation
app/src/main/java/com/gitflow/android/ui/screens/MainScreen.kt

# Changes screen (staging/unstaging)
app/src/main/java/com/gitflow/android/ui/screens/main/ChangesScreen.kt

# Graph visualization
app/src/main/java/com/gitflow/android/ui/screens/EnhancedGraphScreen.kt

# Remote repos (GitHub/GitLab)
app/src/main/java/com/gitflow/android/ui/repositories/RemoteRepositoriesScreen.kt
```

### Configuration Files
```
# OAuth credentials (DO NOT COMMIT)
app/src/main/assets/oauth.properties

# OAuth example template
app/src/main/assets/oauth.properties.example

# Build config
app/build.gradle.kts

# ProGuard rules
app/proguard-rules.pro
```

### Resources
```
# English strings
app/src/main/res/values/strings.xml

# Russian strings
app/src/main/res/values-ru/strings.xml

# Theme colors
app/src/main/res/values/colors.xml

# App themes
app/src/main/res/values/themes.xml
```

## Common Code Snippets

### Opening Git Repository
```kotlin
suspend fun operation(repoPath: String): Result<T> = withContext(Dispatchers.IO) {
    try {
        val git = Git.open(File(repoPath))
        git.use {
            val result = // ... perform operation
            Result.success(result)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Getting Repository Instance
```kotlin
val repository = Git.open(File(repoPath))
val jgitRepo = repository.repository
```

### Working with Commits
```kotlin
// Get commits
val log = git.log().call()
for (commit in log) {
    val hash = commit.id.name
    val message = commit.fullMessage
    val shortMessage = commit.shortMessage
    val author = commit.authorIdent.name
    val email = commit.authorIdent.emailAddress
    val time = commit.authorIdent.`when`.time
}

// Get specific commit
val objectId = repository.resolve("HEAD")
val commit = RevWalk(repository.repository).parseCommit(objectId)

// Get commit parents
val parents = commit.parents.map { it.name }
```

### Staging/Unstaging Files
```kotlin
// Stage file
git.add()
    .addFilepattern(relativePath)
    .call()

// Stage all
git.add()
    .addFilepattern(".")
    .call()

// Unstage file
git.reset()
    .addPath(relativePath)
    .call()
```

### Creating Commits
```kotlin
git.commit()
    .setMessage(message)
    .setAuthor(name, email)
    .call()
```

### Branch Operations
```kotlin
// List branches
val branches = git.branchList().call()

// Create branch
git.branchCreate()
    .setName(branchName)
    .call()

// Checkout branch
git.checkout()
    .setName(branchName)
    .call()

// Delete branch
git.branchDelete()
    .setBranchNames(branchName)
    .setForce(force)
    .call()
```

### Remote Operations
```kotlin
// Fetch
git.fetch()
    .setCredentialsProvider(credentialsProvider)
    .call()

// Pull
git.pull()
    .setCredentialsProvider(credentialsProvider)
    .call()

// Push
git.push()
    .setCredentialsProvider(credentialsProvider)
    .call()
```

### Getting File Status
```kotlin
val status = git.status().call()

// Modified files
status.modified.forEach { path ->
    // File modified
}

// Untracked files
status.untracked.forEach { path ->
    // File untracked
}

// Added files (staged)
status.added.forEach { path ->
    // File staged
}

// Conflicting files
status.conflicting.forEach { path ->
    // File has conflicts
}
```

### Diff Operations
```kotlin
// Get diff between commits
val oldTree = oldCommit.tree
val newTree = newCommit.tree

val diffFormatter = DiffFormatter(ByteArrayOutputStream())
diffFormatter.setRepository(repository)
val diffs = diffFormatter.scan(oldTree, newTree)

for (diff in diffs) {
    val path = diff.newPath
    val changeType = diff.changeType // ADD, MODIFY, DELETE, etc.
}
```

### Merge Operations
```kotlin
// Merge
val result = git.merge()
    .include(repository.resolve(branchName))
    .setCommit(true)
    .call()

// Check merge status
when (result.mergeStatus) {
    MergeResult.MergeStatus.CONFLICTING -> {
        // Handle conflicts
        val conflicts = result.conflicts
    }
    MergeResult.MergeStatus.MERGED -> {
        // Success
    }
    else -> {
        // Other status
    }
}
```

### Cherry-pick
```kotlin
val commitId = repository.resolve(commitHash)
val result = git.cherryPick()
    .include(commitId)
    .call()
```

## ViewModel State Pattern

### Standard ViewModel Setup
```kotlin
class MyViewModel(
    private val repository: GitRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Initial)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effect = Channel<UiEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.LoadData -> loadData()
            is UiEvent.PerformAction -> performAction(event.param)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = repository.getData()
            _state.value = when {
                result.isSuccess -> UiState.Success(result.getOrNull()!!)
                else -> UiState.Error(result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }

    private fun performAction(param: String) {
        viewModelScope.launch {
            val result = repository.performAction(param)
            if (result.isSuccess) {
                _effect.send(UiEffect.ShowSuccess)
            } else {
                _effect.send(UiEffect.ShowError(result.exceptionOrNull()?.message ?: "Error"))
            }
        }
    }
}

sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class Success(val data: Data) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class UiEvent {
    object LoadData : UiEvent()
    data class PerformAction(val param: String) : UiEvent()
}

sealed class UiEffect {
    object ShowSuccess : UiEffect()
    data class ShowError(val message: String) : UiEffect()
}
```

### Composable Screen Setup
```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Handle one-time effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is UiEffect.ShowSuccess -> {
                    // Show success message
                }
                is UiEffect.ShowError -> {
                    // Show error message
                }
            }
        }
    }

    when (state) {
        is UiState.Initial -> {
            // Initial state
        }
        is UiState.Loading -> {
            CircularProgressIndicator()
        }
        is UiState.Success -> {
            val data = (state as UiState.Success).data
            // Display data
        }
        is UiState.Error -> {
            val message = (state as UiState.Error).message
            Text(message)
        }
    }
}
```

## API Integration Pattern

### Retrofit Setup
```kotlin
interface GitHubApi {
    @GET("user/repos")
    suspend fun getUserRepositories(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubRepository>
}

// Usage
val api = Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(GitHubApi::class.java)

// Call
val repos = api.getUserRepositories("Bearer $token")
```

### OAuth Flow Steps
```kotlin
// 1. Start OAuth
fun startAuth() {
    val authUrl = "https://github.com/login/oauth/authorize" +
        "?client_id=$clientId" +
        "&scope=repo,user" +
        "&redirect_uri=$redirectUri"

    // Open in WebView or browser
}

// 2. Handle callback (in OAuthActivity)
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val uri = intent.data
    val code = uri?.getQueryParameter("code")

    if (code != null) {
        exchangeCodeForToken(code)
    }
}

// 3. Exchange code for token
suspend fun exchangeCodeForToken(code: String) {
    val response = api.getAccessToken(
        clientId = clientId,
        clientSecret = clientSecret,
        code = code
    )

    // Store token (use EncryptedSharedPreferences!)
    saveToken(response.accessToken)
}
```

## DataStore Usage

### Preferences DataStore
```kotlin
// Define preferences
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferencesKeys {
    val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val THEME_MODE = stringPreferencesKey("theme_mode")
}

// Write
suspend fun saveWifiOnlySetting(enabled: Boolean) {
    context.dataStore.edit { preferences ->
        preferences[PreferencesKeys.WIFI_ONLY] = enabled
    }
}

// Read
val wifiOnlyFlow: Flow<Boolean> = context.dataStore.data
    .map { preferences ->
        preferences[PreferencesKeys.WIFI_ONLY] ?: false
    }
```

### Proto DataStore (for complex data)
```kotlin
// Define proto schema
message RepositoryList {
    repeated Repository repositories = 1;
}

message Repository {
    string name = 1;
    string path = 2;
    int64 last_opened = 3;
}

// Serializer
object RepositoryListSerializer : Serializer<RepositoryList> {
    override val defaultValue: RepositoryList = RepositoryList.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): RepositoryList {
        return RepositoryList.parseFrom(input)
    }

    override suspend fun writeTo(t: RepositoryList, output: OutputStream) {
        t.writeTo(output)
    }
}

// DataStore
val Context.repositoryDataStore: DataStore<RepositoryList> by dataStore(
    fileName = "repositories.pb",
    serializer = RepositoryListSerializer
)
```

## Compose Tips

### Remember vs RememberSaveable
```kotlin
// Lost on configuration change
val state = remember { mutableStateOf("") }

// Survives configuration change
val state = rememberSaveable { mutableStateOf("") }
```

### LaunchedEffect Use Cases
```kotlin
// Run once on composition
LaunchedEffect(Unit) {
    viewModel.loadData()
}

// Run when key changes
LaunchedEffect(repositoryId) {
    viewModel.loadRepository(repositoryId)
}

// Collect flows
LaunchedEffect(Unit) {
    viewModel.effect.collect { effect ->
        handleEffect(effect)
    }
}
```

### DisposableEffect for Cleanup
```kotlin
DisposableEffect(Unit) {
    // Setup
    val listener = createListener()
    registerListener(listener)

    onDispose {
        // Cleanup
        unregisterListener(listener)
    }
}
```

## Debugging Tips

### Enable JGit Logging
```kotlin
// In Application.onCreate()
if (BuildConfig.DEBUG) {
    System.setProperty("org.eclipse.jgit.util.FS.debug", "true")
}
```

### Check Git Repository State
```kotlin
val git = Git.open(File(repoPath))
println("Current branch: ${git.repository.branch}")
println("Has changes: ${!git.status().call().isClean}")
println("HEAD: ${git.repository.resolve("HEAD")}")
```

### Profile Performance
```kotlin
val startTime = System.currentTimeMillis()
// ... operation
val duration = System.currentTimeMillis() - startTime
Log.d("Performance", "Operation took ${duration}ms")
```

### Memory Profiling
```kotlin
val runtime = Runtime.getRuntime()
val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
Log.d("Memory", "Used: ${usedMemory}MB")
```

## Build & Run Commands

### Build APK
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

### Install on Device
```bash
./gradlew installDebug
```

### Run Tests
```bash
./gradlew test
./gradlew connectedAndroidTest
```

### Clean Build
```bash
./gradlew clean build
```

### Check Dependencies
```bash
./gradlew dependencies
```

### Lint Check
```bash
./gradlew lint
```

## Common Issues & Solutions

### Issue: Git operation fails with "Not a git repository"
**Solution:** Check if directory contains .git folder
```kotlin
val gitDir = File(repoPath, ".git")
if (!gitDir.exists()) {
    // Not a git repo
}
```

### Issue: Out of memory on large repository
**Solution:** Implement pagination and increase heap size
```
android {
    dexOptions {
        javaMaxHeapSize "4g"
    }
}
```

### Issue: OAuth callback not received
**Solution:** Check AndroidManifest.xml for intent-filter
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="gitflow"
        android:host="oauth" />
</intent-filter>
```

### Issue: SharedPreferences not updating
**Solution:** Use apply() or commit()
```kotlin
// Async (preferred)
preferences.edit {
    putString("key", "value")
} // Automatically calls apply()

// Sync (blocks)
preferences.edit()
    .putString("key", "value")
    .commit()
```

### Issue: Compose recomposition storm
**Solution:** Use remember and derivedStateOf
```kotlin
val expensiveValue = remember(dependency) {
    calculateExpensiveValue()
}

val derived = remember {
    derivedStateOf {
        calculation(state1.value, state2.value)
    }
}
```

## Resource Strings Convention

### Adding New String
```xml
<!-- English (values/strings.xml) -->
<string name="feature_action_name">Action Name</string>
<string name="feature_description">Description text</string>
<string name="error_feature_failed">Operation failed: %1$s</string>

<!-- Russian (values-ru/strings.xml) -->
<string name="feature_action_name">Название действия</string>
<string name="feature_description">Текст описания</string>
<string name="error_feature_failed">Операция не удалась: %1$s</string>
```

### Using in Code
```kotlin
// Simple string
Text(stringResource(R.string.feature_action_name))

// With arguments
Text(stringResource(R.string.error_feature_failed, errorMessage))

// In ViewModel (requires Context)
getString(R.string.feature_action_name)
```

## Useful Android Studio Shortcuts

- **Ctrl+Shift+A**: Find action
- **Ctrl+N**: Find class
- **Ctrl+Shift+N**: Find file
- **Ctrl+Alt+Shift+N**: Find symbol
- **Alt+Enter**: Show intention actions
- **Ctrl+Alt+L**: Reformat code
- **Ctrl+Shift+F**: Find in files
- **Ctrl+B**: Go to declaration
- **Alt+F7**: Find usages

---

**Pro Tip:** Keep this file open in a second monitor while coding!
