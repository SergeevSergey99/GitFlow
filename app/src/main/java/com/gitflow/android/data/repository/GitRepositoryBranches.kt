package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.revwalk.RevWalk

internal suspend fun GitRepository.getBranchesImpl(repository: Repository): List<Branch> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyList()
        git.use { g -> getBranchesInternal(g) }
    } catch (e: Exception) {
        emptyList()
    }
}

internal suspend fun GitRepository.pullImpl(repository: Repository): PullResult = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext PullResult(false, 0, listOf("Repository not found"))
        git.use { g ->
            val remoteUrl = g.repository.config.let {
                it.getString("remote", "origin", "pushurl") ?: it.getString("remote", "origin", "url")
            }
            val pullCommand = g.pull()
            resolveCredentialsProvider(remoteUrl)?.let { pullCommand.setCredentialsProvider(it) }
            val result = pullCommand.call()
            if (result.isSuccessful) PullResult(true, 0, emptyList())
            else PullResult(false, 0, listOf("Pull failed"))
        }
    } catch (e: Exception) {
        PullResult(false, 0, listOf(e.message ?: "Unknown error"))
    }
}

internal suspend fun GitRepository.pushImpl(repository: Repository): PushResult = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext PushResult(false, 0, "Repository not found")
        git.use { g ->
            val remoteUrl = g.repository.config.let {
                it.getString("remote", "origin", "pushurl") ?: it.getString("remote", "origin", "url")
            }
            val pushCommand = g.push()
            resolveCredentialsProvider(remoteUrl)?.let { pushCommand.setCredentialsProvider(it) }
            val result = pushCommand.call()
            if (result.any { it.messages.isEmpty() }) {
                refreshRepository(repository)
                PushResult(true, 0, "Push successful")
            } else {
                PushResult(false, 0, "Push failed")
            }
        }
    } catch (e: Exception) {
        PushResult(false, 0, e.message ?: "Unknown error")
    }
}

internal suspend fun GitRepository.hardResetToCommitImpl(repository: Repository, commitHash: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: throw IllegalStateException("Repository not found")
        git.use { g ->
            g.reset().setRef(commitHash).setMode(ResetCommand.ResetType.HARD).call()
        }
        refreshRepository(repository)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun GitRepository.createTagImpl(
    repository: Repository, tagName: String, commitHash: String, force: Boolean
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: throw IllegalStateException("Repository not found")
        git.use { g ->
            val existing = g.tagList().call().any { it.name.removePrefix("refs/tags/") == tagName }
            if (existing && !force) throw IllegalStateException("Tag already exists")

            val objectId = g.repository.resolve(commitHash)
                ?: throw IllegalArgumentException("Cannot resolve commit")
            val revObject = RevWalk(g.repository).use { rw -> rw.parseAny(objectId) }

            g.tag().setName(tagName).setObjectId(revObject).setAnnotated(false).setForceUpdate(force).call()
        }
        refreshRepository(repository)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun GitRepository.deleteTagImpl(repository: Repository, tagName: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: throw IllegalStateException("Repository not found")
        git.use { g -> g.tagDelete().setTags(tagName).call() }
        refreshRepository(repository)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
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

internal suspend fun GitRepository.cherryPickCommitImpl(repository: Repository, commitHash: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: throw IllegalStateException("Repository not found")
        git.use { g ->
            val objectId = g.repository.resolve(commitHash)
                ?: throw IllegalArgumentException("Cannot resolve commit")
            val result = g.cherryPick().include(objectId).call()
            when (result.status) {
                CherryPickResult.CherryPickStatus.OK -> {
                    refreshRepository(repository)
                    Result.success(Unit)
                }
                CherryPickResult.CherryPickStatus.CONFLICTING -> {
                    val conflicts = result.failingPaths?.keys?.joinToString(", ") ?: "Conflicts detected"
                    Result.failure(IllegalStateException("Cherry-pick conflict: $conflicts"))
                }
                else -> Result.failure(IllegalStateException("Cherry-pick failed: ${result.status}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun GitRepository.mergeCommitIntoCurrentBranchImpl(repository: Repository, commitHash: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: throw IllegalStateException("Repository not found")
        git.use { g ->
            val objectId = g.repository.resolve(commitHash)
                ?: throw IllegalArgumentException("Cannot resolve commit")
            val result = g.merge().include(objectId).setCommit(true).call()
            when (result.mergeStatus) {
                MergeResult.MergeStatus.FAST_FORWARD,
                MergeResult.MergeStatus.ALREADY_UP_TO_DATE,
                MergeResult.MergeStatus.MERGED,
                MergeResult.MergeStatus.MERGED_NOT_COMMITTED -> {
                    refreshRepository(repository)
                    Result.success(Unit)
                }
                MergeResult.MergeStatus.CONFLICTING -> {
                    val conflicts = result.conflicts?.keys?.joinToString(", ") ?: "Conflicts detected"
                    Result.failure(IllegalStateException("Merge produced conflicts: $conflicts"))
                }
                else -> Result.failure(IllegalStateException("Merge failed: ${result.mergeStatus}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
