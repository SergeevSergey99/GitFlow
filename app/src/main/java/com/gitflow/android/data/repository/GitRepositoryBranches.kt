package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.revwalk.RevWalk
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Inspects the exception cause chain and maps network-related exceptions to
 * [GitResult.Failure.NetworkError], everything else to [GitResult.Failure.Generic].
 */
internal fun classifyNetworkException(e: Exception): GitResult.Failure {
    var cause: Throwable? = e
    while (cause != null) {
        when (cause) {
            is UnknownHostException,
            is NoRouteToHostException ->
                return GitResult.Failure.NetworkError(
                    message = cause.message ?: "No internet connection",
                    cause = e,
                    isOffline = true
                )
            is ConnectException,
            is SocketTimeoutException,
            is SocketException,
            is SSLException ->
                return GitResult.Failure.NetworkError(
                    message = cause.message ?: "Server unreachable",
                    cause = e,
                    isOffline = false
                )
        }
        cause = cause.cause
    }
    // JGit TransportException — check for auth failure before treating as generic network error
    val msgLower = e.message?.lowercase() ?: ""
    if (e is TransportException || e is org.eclipse.jgit.errors.TransportException) {
        if (msgLower.contains("not authorized") ||
            msgLower.contains("authentication required") ||
            msgLower.contains("invalid credentials") ||
            msgLower.contains("invalid username or password")) {
            return GitResult.Failure.AuthRequired(
                message = e.message ?: "Authentication required",
                cause = e
            )
        }
        return GitResult.Failure.NetworkError(
            message = e.message ?: "Network error",
            cause = e,
            isOffline = false
        )
    }
    return GitResult.Failure.Generic(e.message ?: "Unknown error", e)
}

private class JGitProgressMonitor(
    private val callback: (SyncProgress) -> Unit
) : ProgressMonitor {
    private var currentTask = ""
    private var total = 0
    private var done = 0

    override fun start(totalTasks: Int) {}
    override fun beginTask(title: String?, totalWork: Int) {
        currentTask = title ?: ""
        total = totalWork
        done = 0
        callback(SyncProgress(currentTask, 0, total))
    }
    override fun update(completed: Int) {
        done += completed
        callback(SyncProgress(currentTask, done, total))
    }
    override fun endTask() {
        callback(SyncProgress(currentTask, total, total))
    }
    override fun isCancelled(): Boolean = false
}

private fun evaluatePushResult(results: Iterable<PushResult>): GitResult<Unit> {
    val failedUpdates = mutableListOf<String>()
    var hasUpdates = false

    for (result in results) {
        for (update in result.remoteUpdates) {
            hasUpdates = true
            when (update.status) {
                RemoteRefUpdate.Status.OK,
                RemoteRefUpdate.Status.UP_TO_DATE -> Unit
                else -> {
                    val remoteName = update.remoteName ?: update.srcRef ?: "<unknown>"
                    val details = update.message?.takeIf { it.isNotBlank() } ?: update.status.name
                    failedUpdates += "$remoteName: $details"
                }
            }
        }
    }

    return when {
        !hasUpdates -> GitResult.Success(Unit)
        failedUpdates.isEmpty() -> GitResult.Success(Unit)
        else -> GitResult.Failure.Generic("Push failed: ${failedUpdates.joinToString("; ")}")
    }
}

internal suspend fun GitRepository.getBranchesImpl(repository: Repository): List<Branch> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyList()
        git.use { g -> getBranchesInternal(g) }
    } catch (e: Exception) {
        emptyList()
    }
}

internal suspend fun GitRepository.checkoutBranchImpl(
    repository: Repository,
    branchName: String,
    isLocal: Boolean
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            if (isLocal) {
                g.checkout().setName(branchName).call()
            } else {
                val localName = branchName.removePrefix("origin/")
                try {
                    g.checkout()
                        .setCreateBranch(true)
                        .setName(localName)
                        .setStartPoint(branchName)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .call()
                } catch (e: RefAlreadyExistsException) {
                    g.checkout().setName(localName).call()
                }
            }
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.createBranchImpl(
    repository: Repository,
    branchName: String,
    checkout: Boolean
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            if (checkout) {
                g.checkout().setCreateBranch(true).setName(branchName).call()
            } else {
                g.branchCreate().setName(branchName).call()
            }
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.deleteBranchImpl(
    repository: Repository,
    branchName: String,
    force: Boolean
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        val deleted = git.use { g ->
            g.branchDelete().setBranchNames(branchName).setForce(force).call()
        }
        if (deleted.isEmpty()) return@withContext GitResult.Failure.Generic("Branch not deleted")
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.fetchImpl(repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        val remoteUrl = git.repository.config.let {
            it.getString("remote", "origin", "pushurl") ?: it.getString("remote", "origin", "url")
        }
        val credentials = resolveCredentialsProvider(remoteUrl)
        git.use { g ->
            val fetchCommand = g.fetch()
            credentials?.let { fetchCommand.setCredentialsProvider(it) }
            fetchCommand.call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        classifyNetworkException(e)
    }
}

internal suspend fun GitRepository.pullImpl(repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        // Resolve credentials (and refresh GitLab token if needed) before entering the use block
        val remoteUrl = git.repository.config.let {
            it.getString("remote", "origin", "pushurl") ?: it.getString("remote", "origin", "url")
        }
        val credentials = resolveCredentialsProvider(remoteUrl)
        git.use { g ->
            val pullCommand = g.pull()
            credentials?.let { pullCommand.setCredentialsProvider(it) }
            val result = pullCommand.call()
            if (result.isSuccessful) GitResult.Success(Unit)
            else GitResult.Failure.Generic("Pull failed")
        }
    } catch (e: Exception) {
        classifyNetworkException(e)
    }
}

internal suspend fun GitRepository.pushImpl(repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        // Resolve credentials (and refresh GitLab token if needed) before entering the use block
        val remoteUrl = git.repository.config.let {
            it.getString("remote", "origin", "pushurl") ?: it.getString("remote", "origin", "url")
        }
        val credentials = resolveCredentialsProvider(remoteUrl)
        git.use { g ->
            val pushCommand = g.push()
            credentials?.let { pushCommand.setCredentialsProvider(it) }
            val result = pushCommand.call()
            when (val pushResult = evaluatePushResult(result)) {
                is GitResult.Success -> {
                    refreshRepository(repository)
                    GitResult.Success(Unit)
                }
                is GitResult.Failure -> pushResult
            }
        }
    } catch (e: Exception) {
        classifyNetworkException(e)
    }
}

internal suspend fun GitRepository.pushWithProgressImpl(
    repository: Repository,
    onProgress: (SyncProgress) -> Unit
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        val remoteUrl = git.repository.config.let {
            it.getString("remote", "origin", "pushurl") ?: it.getString("remote", "origin", "url")
        }
        val credentials = resolveCredentialsProvider(remoteUrl)
        git.use { g ->
            val pushCommand = g.push()
            credentials?.let { pushCommand.setCredentialsProvider(it) }
            pushCommand.setProgressMonitor(JGitProgressMonitor(onProgress))
            val result = pushCommand.call()
            when (val pushResult = evaluatePushResult(result)) {
                is GitResult.Success -> {
                    refreshRepository(repository)
                    GitResult.Success(Unit)
                }
                is GitResult.Failure -> pushResult
            }
        }
    } catch (e: Exception) {
        classifyNetworkException(e)
    }
}

internal suspend fun GitRepository.resetToCommitImpl(repository: Repository, commitHash: String, mode: ResetMode): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        val jgitMode = when (mode) {
            ResetMode.SOFT -> ResetCommand.ResetType.SOFT
            ResetMode.MIXED -> ResetCommand.ResetType.MIXED
            ResetMode.HARD -> ResetCommand.ResetType.HARD
        }
        git.use { g ->
            g.reset().setRef(commitHash).setMode(jgitMode).call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.createTagImpl(
    repository: Repository, tagName: String, commitHash: String, force: Boolean
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val existing = g.tagList().call().any { it.name.removePrefix("refs/tags/") == tagName }
            if (existing && !force) return@withContext GitResult.Failure.TagAlreadyExists(tagName)

            val objectId = g.repository.resolve(commitHash)
                ?: return@withContext GitResult.Failure.Generic("Cannot resolve commit: $commitHash")
            val revObject = RevWalk(g.repository).use { rw -> rw.parseAny(objectId) }
            g.tag().setName(tagName).setObjectId(revObject).setAnnotated(false).setForceUpdate(force).call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.deleteTagImpl(repository: Repository, tagName: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g -> g.tagDelete().setTags(tagName).call() }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.getTagsForCommitImpl(repository: Repository, commitHash: String): List<String> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyList()
        git.use { g -> getTagsForCommit(g, commitHash) }
    } catch (e: Exception) {
        emptyList()
    }
}

internal suspend fun GitRepository.cherryPickCommitImpl(repository: Repository, commitHash: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val objectId = g.repository.resolve(commitHash)
                ?: return@withContext GitResult.Failure.Generic("Cannot resolve commit: $commitHash")
            val result = g.cherryPick().include(objectId).call()
            when (result.status) {
                CherryPickResult.CherryPickStatus.OK -> {
                    refreshRepository(repository)
                    GitResult.Success(Unit)
                }
                CherryPickResult.CherryPickStatus.CONFLICTING -> {
                    val paths = result.failingPaths?.keys?.toList() ?: emptyList()
                    GitResult.Failure.Conflict(
                        message = "Cherry-pick produced conflicts: ${paths.joinToString(", ").ifEmpty { "see working tree" }}",
                        paths = paths
                    )
                }
                else -> GitResult.Failure.Generic("Cherry-pick failed: ${result.status}")
            }
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.mergeCommitIntoCurrentBranchImpl(repository: Repository, commitHash: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val objectId = g.repository.resolve(commitHash)
                ?: return@withContext GitResult.Failure.Generic("Cannot resolve commit: $commitHash")
            val result = g.merge().include(objectId).setCommit(true).call()
            when (result.mergeStatus) {
                MergeResult.MergeStatus.FAST_FORWARD,
                MergeResult.MergeStatus.ALREADY_UP_TO_DATE,
                MergeResult.MergeStatus.MERGED,
                MergeResult.MergeStatus.MERGED_NOT_COMMITTED -> {
                    refreshRepository(repository)
                    GitResult.Success(Unit)
                }
                MergeResult.MergeStatus.CONFLICTING -> {
                    val paths = result.conflicts?.keys?.toList() ?: emptyList()
                    GitResult.Failure.Conflict(
                        message = "Merge produced conflicts: ${paths.joinToString(", ").ifEmpty { "see working tree" }}",
                        paths = paths
                    )
                }
                else -> GitResult.Failure.Generic("Merge failed: ${result.mergeStatus}")
            }
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

// ---------------------------------------------------------------------------
// Merge / rebase a branch into the current branch
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.mergeBranchImpl(repository: Repository, branchName: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val mergeCommand = g.merge().setCommit(true)
            // Prefer a named ref so the merge commit message references the branch.
            val ref = g.repository.findRef(branchName)
            if (ref != null) {
                mergeCommand.include(ref)
            } else {
                val objectId = g.repository.resolve(branchName)
                    ?: return@withContext GitResult.Failure.Generic("Cannot resolve branch: $branchName")
                mergeCommand.include(objectId)
            }
            val result = mergeCommand.call()
            when (result.mergeStatus) {
                MergeResult.MergeStatus.FAST_FORWARD,
                MergeResult.MergeStatus.ALREADY_UP_TO_DATE,
                MergeResult.MergeStatus.MERGED,
                MergeResult.MergeStatus.MERGED_NOT_COMMITTED -> {
                    refreshRepository(repository)
                    GitResult.Success(Unit)
                }
                MergeResult.MergeStatus.CONFLICTING -> {
                    val paths = result.conflicts?.keys?.toList() ?: emptyList()
                    GitResult.Failure.Conflict(
                        message = "Merge produced conflicts: ${paths.joinToString(", ").ifEmpty { "see working tree" }}",
                        paths = paths
                    )
                }
                else -> GitResult.Failure.Generic("Merge failed: ${result.mergeStatus}")
            }
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.abortMergeImpl(repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            // JGit has no "merge --abort"; a hard reset to HEAD clears MERGE_HEAD and the
            // conflicted working tree, returning the repo to a clean state.
            g.reset().setMode(ResetCommand.ResetType.HARD).setRef(Constants.HEAD).call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.rebaseCurrentOntoImpl(repository: Repository, branchName: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val result = g.rebase().setUpstream(branchName).call()
            interpretRebaseResult(result, repository, g)
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.rebaseContinueImpl(repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val result = g.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call()
            interpretRebaseResult(result, repository, g)
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.rebaseAbortImpl(repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            g.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

/** Maps a [RebaseResult] to a [GitResult], refreshing repo metadata on a clean finish. */
private suspend fun GitRepository.interpretRebaseResult(
    result: RebaseResult,
    repository: Repository,
    git: Git
): GitResult<Unit> {
    return when (result.status) {
        RebaseResult.Status.OK,
        RebaseResult.Status.UP_TO_DATE,
        RebaseResult.Status.FAST_FORWARD -> {
            refreshRepository(repository)
            GitResult.Success(Unit)
        }
        RebaseResult.Status.STOPPED,
        RebaseResult.Status.CONFLICTS -> {
            // Read conflicting paths from the working tree rather than RebaseResult, keeping
            // the source of truth consistent with the merge path and ChangesScreen.
            val paths = runCatching { git.status().call().conflicting.toList() }.getOrDefault(emptyList())
            GitResult.Failure.Conflict(
                message = "Rebase stopped on conflicts: ${paths.joinToString(", ").ifEmpty { "see working tree" }}",
                paths = paths
            )
        }
        RebaseResult.Status.UNCOMMITTED_CHANGES ->
            GitResult.Failure.Generic("Commit or stash your changes before rebasing")
        else -> GitResult.Failure.Generic("Rebase failed: ${result.status}")
    }
}

private fun mapRepositoryState(state: RepositoryState): RepoOperationState = when (state) {
    RepositoryState.MERGING, RepositoryState.MERGING_RESOLVED -> RepoOperationState.MERGING
    RepositoryState.REBASING, RepositoryState.REBASING_REBASING,
    RepositoryState.REBASING_MERGE, RepositoryState.REBASING_INTERACTIVE -> RepoOperationState.REBASING
    RepositoryState.SAFE, RepositoryState.BARE -> RepoOperationState.NONE
    else -> RepoOperationState.OTHER
}

internal suspend fun GitRepository.getRepositoryStateImpl(repository: Repository): RepoOperationState = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext RepoOperationState.NONE
        git.use { g -> mapRepositoryState(g.repository.repositoryState) }
    } catch (e: Exception) {
        RepoOperationState.NONE
    }
}

internal suspend fun GitRepository.getConflictInfoImpl(repository: Repository): ConflictInfo = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext ConflictInfo()
        git.use { g ->
            val jrepo = g.repository
            val state = mapRepositoryState(jrepo.repositoryState)
            if (state != RepoOperationState.MERGING && state != RepoOperationState.REBASING) {
                return@withContext ConflictInfo(operation = state)
            }
            val headHash = jrepo.resolve(Constants.HEAD)?.name
            val conflictPaths = runCatching { g.status().call().conflicting.toList() }.getOrDefault(emptyList())

            if (state == RepoOperationState.MERGING) {
                val mergeHead = runCatching { jrepo.readMergeHeads()?.firstOrNull()?.name }.getOrNull()
                val mergeMsg = runCatching { jrepo.readMergeCommitMsg() }.getOrNull()
                val source = mergeMsg
                    ?.let { Regex("Merge (?:remote-tracking )?branch '([^']+)'").find(it)?.groupValues?.get(1) }
                    ?: mergeHead?.take(7)
                ConflictInfo(
                    operation = state,
                    sourceLabel = source,
                    targetLabel = jrepo.branch,
                    sourceHash = mergeHead,
                    targetHash = headHash,
                    conflictPaths = conflictPaths
                )
            } else {
                // Rebase state lives in .git/rebase-merge/ (interactive/merge) or .git/rebase-apply/.
                val stateDir = listOf("rebase-merge", "rebase-apply")
                    .map { File(jrepo.directory, it) }
                    .firstOrNull { it.isDirectory }
                val headName = stateDir
                    ?.let { File(it, "head-name").takeIf(File::exists)?.readText()?.trim() }
                    ?.removePrefix("refs/heads/")
                val ontoHash = stateDir
                    ?.let { File(it, "onto").takeIf(File::exists)?.readText()?.trim() }
                ConflictInfo(
                    operation = state,
                    sourceLabel = headName ?: jrepo.branch,
                    targetLabel = ontoHash?.take(7),
                    sourceHash = headHash,
                    targetHash = ontoHash,
                    conflictPaths = conflictPaths
                )
            }
        }
    } catch (e: Exception) {
        ConflictInfo()
    }
}
