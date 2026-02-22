package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.revwalk.RevWalk

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
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
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
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
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
            if (result.any { it.messages.isEmpty() }) {
                refreshRepository(repository)
                GitResult.Success(Unit)
            } else {
                GitResult.Failure.Generic("Push failed")
            }
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
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
            if (result.any { it.messages.isEmpty() }) {
                refreshRepository(repository)
                GitResult.Success(Unit)
            } else {
                GitResult.Failure.Generic("Push failed")
            }
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.hardResetToCommitImpl(repository: Repository, commitHash: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            g.reset().setRef(commitHash).setMode(ResetCommand.ResetType.HARD).call()
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
