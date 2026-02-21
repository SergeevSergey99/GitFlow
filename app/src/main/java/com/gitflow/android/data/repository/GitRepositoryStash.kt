package com.gitflow.android.data.repository

import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.StashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.revwalk.RevWalk

internal suspend fun GitRepository.stashSaveImpl(
    repository: Repository,
    message: String
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val cmd = g.stashCreate()
            if (message.isNotBlank()) cmd.setWorkingDirectoryMessage(message)
            cmd.call() ?: return@withContext GitResult.Failure.Generic("Nothing to stash")
            GitResult.Success(Unit)
        }
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.stashListImpl(repository: Repository): List<StashEntry> =
    withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext emptyList()
            git.use { g ->
                val stashes = g.stashList().call()
                RevWalk(g.repository).use { rw ->
                    stashes.mapIndexed { index, ref ->
                        val commit = rw.parseCommit(ref.objectId)
                        val fullMessage = commit.fullMessage.trim()
                        // Format: "On <branch>: <message>" or "WIP on <branch>: <message>"
                        val branchAndMsg = fullMessage.removePrefix("WIP on ").removePrefix("On ")
                        val colonIdx = branchAndMsg.indexOf(':')
                        val branch = if (colonIdx >= 0) branchAndMsg.substring(0, colonIdx).trim() else ""
                        val msg = if (colonIdx >= 0) branchAndMsg.substring(colonIdx + 1).trim() else fullMessage

                        StashEntry(
                            index = index,
                            message = msg,
                            branch = branch,
                            timestamp = commit.committerIdent.`when`.time,
                            objectId = ref.objectId.name
                        )
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

internal suspend fun GitRepository.stashApplyImpl(
    repository: Repository,
    stashIndex: Int
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val stashes = g.stashList().call().toList()
            if (stashIndex >= stashes.size) return@withContext GitResult.Failure.Generic("Invalid stash index")
            g.stashApply()
                .setStashRef(stashes[stashIndex].name)
                .call()
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.stashPopImpl(
    repository: Repository,
    stashIndex: Int
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val stashes = g.stashList().call().toList()
            if (stashIndex >= stashes.size) return@withContext GitResult.Failure.Generic("Invalid stash index")
            g.stashApply()
                .setStashRef(stashes[stashIndex].name)
                .call()
            g.stashDrop().setStashRef(stashIndex).call()
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.stashDropImpl(
    repository: Repository,
    stashIndex: Int
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            g.stashDrop().setStashRef(stashIndex).call()
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}
