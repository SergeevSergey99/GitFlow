package com.gitflow.android.data.repository

import android.content.Context
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.URI
import java.util.*

// ---------------------------------------------------------------------------
// Clone progress tracking
// ---------------------------------------------------------------------------

data class CloneProgress(
    val stage: String = "",
    val progress: Float = 0f,
    val total: Int = 0,
    val completed: Int = 0,
    val logs: List<String> = emptyList(),
    val estimatedTimeRemaining: String = "",
    val isCancellable: Boolean = true
)

class CloneProgressCallback(private val trackingKey: String? = null) : ProgressMonitor {
    private val _progress = MutableStateFlow(CloneProgress())
    val progress: StateFlow<CloneProgress> = _progress.asStateFlow()

    private val logs = mutableListOf<String>()
    private var currentStage = ""
    private var totalWork = 0
    private var completedWork = 0
    private var cancelled = false
    private var startTime = 0L
    private val progressHistory = mutableListOf<Pair<Long, Int>>()

    override fun start(totalTasks: Int) {
        totalWork = totalTasks
        completedWork = 0
        currentStage = "Starting clone..."
        startTime = System.currentTimeMillis()
        progressHistory.clear()
        addLog("Starting repository clone...")
        updateProgress()
    }

    override fun beginTask(title: String, totalWork: Int) {
        currentStage = title
        this.totalWork = totalWork
        this.completedWork = 0
        addLog("Starting: $title")
        updateProgress()
    }

    override fun update(completed: Int) {
        completedWork += completed
        val now = System.currentTimeMillis()
        progressHistory.add(Pair(now, completedWork))
        if (progressHistory.size > 10) progressHistory.removeAt(0)
        updateProgress()
    }

    override fun endTask() {
        addLog("Completed: $currentStage")
        updateProgress()
    }

    override fun isCancelled(): Boolean = cancelled

    fun cancel() {
        cancelled = true
        addLog("Clone operation cancelled by user")
        updateProgress()
        trackingKey?.let { CloneProgressTracker.markCancelled(it) }
    }

    private fun addLog(message: String) {
        logs.add("[${System.currentTimeMillis() % 100000}] $message")
        if (logs.size > 50) logs.removeAt(0)
    }

    private fun updateProgress() {
        val progressPercentage = if (totalWork > 0)
            (completedWork.toFloat() / totalWork.toFloat()).coerceIn(0f, 1f)
        else 0f

        val newProgress = CloneProgress(
            stage = currentStage,
            progress = progressPercentage,
            total = totalWork,
            completed = completedWork,
            logs = logs.toList(),
            estimatedTimeRemaining = calculateEstimatedTime(),
            isCancellable = !cancelled && totalWork > 0
        )

        _progress.value = newProgress
        trackingKey?.let { CloneProgressTracker.updateProgress(it, newProgress) }
    }

    private fun calculateEstimatedTime(): String {
        if (totalWork <= 0 || completedWork <= 0 || progressHistory.size < 2) return "Calculating..."
        val currentTime = System.currentTimeMillis()
        val recentHistory = progressHistory.filter { currentTime - it.first <= 5000 }
        if (recentHistory.size < 2) return "Calculating..."
        val firstEntry = recentHistory.first()
        val lastEntry = recentHistory.last()
        val timeSpan = lastEntry.first - firstEntry.first
        val workSpan = lastEntry.second - firstEntry.second
        if (timeSpan <= 0 || workSpan <= 0) return "Calculating..."
        val workRemaining = totalWork - completedWork
        val estimatedTimeMs = (workRemaining / (workSpan.toDouble() / timeSpan.toDouble())).toLong()
        return formatTime(estimatedTimeMs)
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            else -> "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }
}

// ---------------------------------------------------------------------------
// Main repository class — delegates to extension files
// ---------------------------------------------------------------------------

class GitRepository(internal val context: Context) : IGitRepository {

    internal val dataStore = RepositoryDataStore(context)
    internal val authManager by lazy { AuthManager(context) }

    // ---------- Simple inline overrides ----------

    override fun getRepositoriesFlow(): Flow<List<Repository>> = dataStore.repositories

    override suspend fun getRepositories(): List<Repository> = withContext(Dispatchers.IO) {
        dataStore.repositories.first()
    }

    override suspend fun removeRepository(repositoryId: String) = withContext(Dispatchers.IO) {
        dataStore.removeRepository(repositoryId)
    }

    override suspend fun updateRepository(repository: Repository) = withContext(Dispatchers.IO) {
        dataStore.updateRepository(repository)
    }

    override suspend fun getMergeConflict(repository: Repository, path: String): MergeConflict? =
        withContext(Dispatchers.IO) { parseConflictFile(repository.path, path) }

    // ---------- Delegating overrides → extension files ----------

    override suspend fun addRepository(path: String): Result<Repository> = addRepositoryImpl(path)
    override suspend fun createRepository(name: String, localPath: String): Result<Repository> = createRepositoryImpl(name, localPath)
    override suspend fun cloneRepository(url: String, localPath: String, customDestination: String?, progressCallback: CloneProgressCallback?): Result<Repository> = cloneRepositoryImpl(url, localPath, customDestination, progressCallback)
    override suspend fun removeRepositoryWithFiles(repositoryId: String): Result<Unit> = removeRepositoryWithFilesImpl(repositoryId)
    override suspend fun refreshRepository(repository: Repository): Repository? = refreshRepositoryImpl(repository)

    override suspend fun getCommits(repository: Repository, page: Int, pageSize: Int): List<Commit> = getCommitsImpl(repository, page, pageSize)
    override suspend fun getCommitDiffs(commit: Commit, repository: Repository): List<FileDiff> = getCommitDiffsImpl(commit, repository)
    override suspend fun getCommitDiffs(commit: Commit): List<FileDiff> = getCommitDiffsAutoImpl(commit)

    override suspend fun getChangedFiles(repository: Repository): List<FileChange> = getChangedFilesImpl(repository)
    override suspend fun stageFile(repository: Repository, file: FileChange): Result<Unit> = stageFileImpl(repository, file)
    override suspend fun unstageFile(repository: Repository, filePath: String): Result<Unit> = unstageFileImpl(repository, filePath)
    override suspend fun stageAll(repository: Repository): Result<Unit> = stageAllImpl(repository)
    override suspend fun getMergeConflicts(repository: Repository): List<MergeConflict> = getMergeConflictsImpl(repository)
    override suspend fun resolveConflict(repository: Repository, path: String, strategy: ConflictResolutionStrategy): Result<Unit> = resolveConflictImpl(repository, path, strategy)
    override suspend fun resolveConflictWithContent(repository: Repository, path: String, resolvedContent: String): Result<Unit> = resolveConflictWithContentImpl(repository, path, resolvedContent)
    override suspend fun commit(repository: Repository, message: String): Result<Unit> = commitImpl(repository, message)

    override suspend fun getBranches(repository: Repository): List<Branch> = getBranchesImpl(repository)
    override suspend fun pull(repository: Repository): PullResult = pullImpl(repository)
    override suspend fun push(repository: Repository): PushResult = pushImpl(repository)
    override suspend fun hardResetToCommit(repository: Repository, commitHash: String): Result<Unit> = hardResetToCommitImpl(repository, commitHash)
    override suspend fun createTag(repository: Repository, tagName: String, commitHash: String, force: Boolean): Result<Unit> = createTagImpl(repository, tagName, commitHash, force)
    override suspend fun deleteTag(repository: Repository, tagName: String): Result<Unit> = deleteTagImpl(repository, tagName)
    override suspend fun getTagsForCommit(repository: Repository, commitHash: String): List<String> = getTagsForCommitImpl(repository, commitHash)
    override suspend fun cherryPickCommit(repository: Repository, commitHash: String): Result<Unit> = cherryPickCommitImpl(repository, commitHash)
    override suspend fun mergeCommitIntoCurrentBranch(repository: Repository, commitHash: String): Result<Unit> = mergeCommitIntoCurrentBranchImpl(repository, commitHash)

    override suspend fun getCommitFileTree(commit: Commit, repository: Repository): FileTreeNode = getCommitFileTreeImpl(commit, repository)
    override suspend fun getCommitFileTree(commit: Commit): FileTreeNode = getCommitFileTreeAutoImpl(commit)
    override suspend fun getFileContent(commit: Commit, filePath: String, repository: Repository): String? = getFileContentImpl(commit, filePath, repository)
    override suspend fun getFileContent(commit: Commit, filePath: String): String? = getFileContentAutoImpl(commit, filePath)
    override suspend fun getFileContentBytes(commit: Commit, filePath: String, repository: Repository): ByteArray? = getFileContentBytesImpl(commit, filePath, repository)
    override suspend fun getFileContentBytes(commit: Commit, filePath: String): ByteArray? = getFileContentBytesAutoImpl(commit, filePath)
    override suspend fun restoreFileToCommit(commit: Commit, filePath: String, repository: Repository): Boolean = restoreFileToCommitImpl(commit, filePath, repository)
    override suspend fun restoreFileToCommit(commit: Commit, filePath: String): Boolean = restoreFileToCommitAutoImpl(commit, filePath)
    override suspend fun restoreFileToParentCommit(commit: Commit, filePath: String, repository: Repository): Boolean = restoreFileToParentCommitImpl(commit, filePath, repository)
    override suspend fun restoreFileToParentCommit(commit: Commit, filePath: String): Boolean = restoreFileToParentCommitAutoImpl(commit, filePath)
    override suspend fun getFileHistory(commit: Commit, filePath: String, repository: Repository): List<Commit> = getFileHistoryImpl(commit, filePath, repository)
    override suspend fun getFileHistory(commit: Commit, filePath: String): List<Commit> = getFileHistoryAutoImpl(commit, filePath)

    // ---------- Shared internal helpers ----------

    internal fun openRepository(path: String): Git? = try {
        Git.open(File(path))
    } catch (e: Exception) {
        null
    }

    internal fun resolveCredentialsProvider(remoteUrl: String?): CredentialsProvider? {
        if (remoteUrl.isNullOrBlank()) return null
        val sanitizedUrl = remoteUrl.trim()
        val parsedUri = runCatching { URI(sanitizedUrl) }.getOrNull()

        val userInfo = parsedUri?.userInfo?.takeIf { it.isNotBlank() }
        if (userInfo != null) {
            val parts = userInfo.split(":", limit = 2)
            val username = parts.getOrNull(0) ?: return null
            val password = parts.getOrNull(1) ?: ""
            return UsernamePasswordCredentialsProvider(username, password)
        }

        val host = parsedUri?.host ?: return null
        val provider = when {
            host.contains("github.com", ignoreCase = true) -> GitProvider.GITHUB
            host.contains("gitlab.com", ignoreCase = true) -> GitProvider.GITLAB
            else -> null
        } ?: return null

        val token = authManager.getAccessToken(provider)?.takeIf { it.isNotBlank() } ?: return null
        return when (provider) {
            GitProvider.GITHUB -> UsernamePasswordCredentialsProvider(token, "")
            GitProvider.GITLAB -> UsernamePasswordCredentialsProvider("oauth2", token)
        }
    }

    internal fun resolveCommitIdentity(git: Git): Pair<String, String>? {
        val config = git.repository.config
        val configuredName = config.getString("user", null, "name")
        val configuredEmail = config.getString("user", null, "email")
        if (!configuredName.isNullOrBlank() && !configuredEmail.isNullOrBlank()) {
            return configuredName to configuredEmail
        }

        val remoteConfigs = runCatching { git.remoteList().call() }.getOrNull().orEmpty()
        val originRemote = remoteConfigs.firstOrNull { it.name == "origin" } ?: remoteConfigs.firstOrNull()
        val remoteUri = originRemote?.urIs?.firstOrNull()

        val provider = remoteUri?.toString()?.lowercase(Locale.ROOT)?.let { uriString ->
            when {
                uriString.contains("github.com") -> GitProvider.GITHUB
                uriString.contains("gitlab.com") -> GitProvider.GITLAB
                else -> null
            }
        }

        val user = provider?.let { authManager.getCurrentUser(it) }
            ?: run {
                for (candidate in listOf(GitProvider.GITHUB, GitProvider.GITLAB)) {
                    val u = authManager.getCurrentUser(candidate)
                    if (u != null) return@run u
                }
                null
            }

        if (user != null) {
            val name = user.name?.takeIf { it.isNotBlank() } ?: user.login
            val email = user.email?.takeIf { it.isNotBlank() } ?: when (user.provider) {
                GitProvider.GITHUB -> "${user.login}@users.noreply.github.com"
                GitProvider.GITLAB -> "${user.login}@gitlab.com"
            }
            if (name.isNotBlank() && email.isNotBlank()) return name to email
        }
        return null
    }

    internal fun getBranchesInternal(git: Git): List<Branch> {
        return try {
            val branches = mutableListOf<Branch>()

            for (ref in git.branchList().call()) {
                val branchName = ref.name.removePrefix("refs/heads/")
                val trackingStatus = runCatching { BranchTrackingStatus.of(git.repository, ref.name) }.getOrNull()
                branches.add(Branch(
                    name = branchName,
                    isLocal = true,
                    lastCommitHash = ref.objectId.name,
                    ahead = trackingStatus?.aheadCount ?: 0,
                    behind = trackingStatus?.behindCount ?: 0
                ))
            }

            for (ref in git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()) {
                branches.add(Branch(
                    name = ref.name.removePrefix("refs/remotes/"),
                    isLocal = false,
                    lastCommitHash = ref.objectId.name,
                    ahead = 0,
                    behind = 0
                ))
            }

            branches.sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    internal fun getLastCommitTime(git: Git): Long = try {
        git.log().setMaxCount(1).call().firstOrNull()?.authorIdent?.`when`?.time
            ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    internal fun calculateAheadBehind(git: Git): Pair<Int, Int> = try {
        val fullBranch = git.repository.fullBranch ?: return 0 to 0
        val trackingStatus = BranchTrackingStatus.of(git.repository, fullBranch) ?: return 0 to 0
        trackingStatus.aheadCount to trackingStatus.behindCount
    } catch (e: Exception) {
        0 to 0
    }

    internal fun getBranchName(ref: Ref): String = when {
        ref.name.startsWith("refs/heads/") -> ref.name.removePrefix("refs/heads/")
        ref.name.startsWith("refs/remotes/") -> ref.name.removePrefix("refs/remotes/")
        else -> ref.name
    }

    internal fun getTagsForCommit(git: Git, commitHash: String): List<String> = try {
        val repository = git.repository
        val targetCommit = repository.resolve(commitHash) ?: return emptyList()
        git.tagList().call().mapNotNull { tagRef ->
            val peeled = repository.refDatabase.peel(tagRef)
            val targetId = peeled.peeledObjectId ?: tagRef.objectId
            if (targetId == targetCommit) tagRef.name.removePrefix("refs/tags/") else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}
