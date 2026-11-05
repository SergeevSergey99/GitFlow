package com.gitflow.android.data.repository

import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.StashEntry

internal suspend fun GitRepository.stashSaveImpl(
    repository: Repository,
    message: String
): GitResult<Unit> = withGit(repository) { git ->
    if (!applyCommitIdentity(git)) return@withGit GitResult.Failure.IdentityRequired()
    val stashCommand = git.stashCreate()
    if (message.isNotBlank()) stashCommand.setWorkingDirectoryMessage(message)
    stashCommand.call() ?: return@withGit GitResult.Failure.Generic("Nothing to stash")
    GitResult.Success(Unit)
}

internal suspend fun GitRepository.stashListImpl(repository: Repository): GitResult<List<StashEntry>> =
    withGit(repository) { git ->
        val entries = git.stashList().call().mapIndexed { index, revCommit ->
            val fullMessage = revCommit.fullMessage.trim()
            // Format: "On <branch>: <message>" or "WIP on <branch>: <message>"
            val branchAndMessage = fullMessage.removePrefix("WIP on ").removePrefix("On ")
            val colonIndex = branchAndMessage.indexOf(':')
            val branch = if (colonIndex >= 0) branchAndMessage.substring(0, colonIndex).trim() else ""
            val stashMessage = if (colonIndex >= 0) branchAndMessage.substring(colonIndex + 1).trim() else fullMessage

            StashEntry(
                index = index,
                message = stashMessage,
                branch = branch,
                timestamp = revCommit.committerIdent.`when`.time,
                objectId = revCommit.name
            )
        }
        GitResult.Success(entries)
    }

internal suspend fun GitRepository.stashApplyImpl(
    repository: Repository,
    stashIndex: Int
): GitResult<Unit> = withGit(repository) { git ->
    val stashes = git.stashList().call().toList()
    if (stashIndex >= stashes.size) return@withGit GitResult.Failure.Generic("Invalid stash index")
    git.stashApply()
        .setStashRef(stashes[stashIndex].name)
        .call()
    GitResult.Success(Unit)
}

internal suspend fun GitRepository.stashPopImpl(
    repository: Repository,
    stashIndex: Int
): GitResult<Unit> = withGit(repository) { git ->
    val stashes = git.stashList().call().toList()
    if (stashIndex >= stashes.size) return@withGit GitResult.Failure.Generic("Invalid stash index")
    git.stashApply()
        .setStashRef(stashes[stashIndex].name)
        .call()
    git.stashDrop().setStashRef(stashIndex).call()
    GitResult.Success(Unit)
}

internal suspend fun GitRepository.stashDropImpl(
    repository: Repository,
    stashIndex: Int
): GitResult<Unit> = withGit(repository) { git ->
    git.stashDrop().setStashRef(stashIndex).call()
    GitResult.Success(Unit)
}
