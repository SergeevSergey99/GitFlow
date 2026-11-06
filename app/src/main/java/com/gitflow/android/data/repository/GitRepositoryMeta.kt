package com.gitflow.android.data.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gitflow.android.R
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import java.util.*

internal suspend fun GitRepository.addRepositoryImpl(path: String): GitResult<Repository> = withContext(Dispatchers.IO) {
    try {
        val repoDir = if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile == null || !documentFile.exists()) {
                return@withContext GitResult.Failure.Generic(text(R.string.git_error_cannot_access_directory))
            }
            return@withContext GitResult.Failure.Generic(text(R.string.git_error_saf_unsupported))
        } else {
            File(path)
        }

        if (!repoDir.exists() || !File(repoDir, ".git").exists()) {
            return@withContext GitResult.Failure.Generic(text(R.string.git_error_not_a_repository, path))
        }

        Git.open(repoDir).use { git ->
            val name = repoDir.name
            val currentBranch = git.repository.branch
            val lastCommit = getLastCommitTime(git)
            val branches = getBranchesInternal(git)
            val hasRemoteOrigin = git.remoteList().call().any { it.name == "origin" }
            val (aheadCount, _) = calculateAheadBehind(git)

            val repository = Repository(
                id = UUID.randomUUID().toString(),
                name = name,
                path = path,
                lastUpdated = lastCommit,
                currentBranch = currentBranch,
                totalBranches = branches.size,
                hasRemoteOrigin = hasRemoteOrigin,
                pendingPushCommits = aheadCount
            )

            dataStore.addRepository(repository)
            GitResult.Success(repository)
        }
    } catch (e: Exception) {
        genericFailure(e)
    }
}

internal suspend fun GitRepository.createRepositoryImpl(name: String, localPath: String): GitResult<Repository> = withContext(Dispatchers.IO) {
    try {
        val repoDir = File(localPath)
        if (repoDir.exists()) {
            return@withContext GitResult.Failure.Generic(text(R.string.git_error_directory_exists, repoDir.absolutePath))
        }

        repoDir.mkdirs()
        Git.init().setDirectory(repoDir).call().use { git ->
            val (authorName, authorEmail) = resolveCommitIdentity(git) ?: run {
                repoDir.deleteRecursively()
                return@withContext identityRequired()
            }
            File(repoDir, "README.md").writeText("# $name\n\nThis repository was created with GitFlow Android.")
            git.add().addFilepattern("README.md").call()
            git.commit()
                .setMessage("Initial commit")
                .setAuthor(authorName, authorEmail)
                .setCommitter(authorName, authorEmail)
                .call()

            val currentBranch = git.repository.branch
            val lastCommit = getLastCommitTime(git)
            val branches = getBranchesInternal(git)
            val (aheadCount, _) = calculateAheadBehind(git)

            val repository = Repository(
                id = UUID.randomUUID().toString(),
                name = name,
                path = repoDir.absolutePath,
                lastUpdated = lastCommit,
                currentBranch = currentBranch,
                totalBranches = branches.size,
                hasRemoteOrigin = false,
                pendingPushCommits = aheadCount
            )

            dataStore.addRepository(repository)
            GitResult.Success(repository)
        }
    } catch (e: Exception) {
        genericFailure(e)
    }
}

internal suspend fun GitRepository.cloneRepositoryImpl(
    url: String,
    localPath: String,
    customDestination: String?,
    progressCallback: CloneProgressCallback?,
    singleBranch: String?
): GitResult<Repository> = withContext(Dispatchers.IO) {
    try {
        val targetDir = if (!customDestination.isNullOrEmpty()) File(customDestination) else File(localPath)

        if (targetDir.exists()) {
            return@withContext GitResult.Failure.Generic(text(R.string.git_error_directory_exists, targetDir.absolutePath))
        }

        targetDir.parentFile?.mkdirs()

        // Resolve credentials (and refresh GitLab token if needed) before starting the clone
        val credentials = resolveCredentialsProvider(url)

        val cloneCommand = Git.cloneRepository()
            .setURI(url)
            .setDirectory(targetDir)

        credentials?.let { cloneCommand.setCredentialsProvider(it) }
        progressCallback?.let { cloneCommand.setProgressMonitor(it) }

        // Single-branch clone: fetch only the given branch (JGit 5.13 has no shallow --depth,
        // but this still cuts traffic for repos with many branches).
        if (!singleBranch.isNullOrBlank()) {
            val ref = "refs/heads/$singleBranch"
            cloneCommand
                .setCloneAllBranches(false)
                .setBranchesToClone(listOf(ref))
                .setBranch(ref)
        }

        val git = try {
            cloneCommand.call()
        } catch (e: Exception) {
            if (progressCallback?.isCancelled() == true) {
                if (targetDir.exists()) targetDir.deleteRecursively()
                return@withContext GitResult.Failure.Cancelled(text(R.string.git_error_clone_cancelled))
            } else {
                throw e
            }
        }

        git.use {
            val currentBranch = it.repository.branch
            val lastCommit = getLastCommitTime(it)
            val branches = getBranchesInternal(it)
            val hasRemoteOrigin = it.remoteList().call().any { remote -> remote.name == "origin" }
            val (aheadCount, _) = calculateAheadBehind(it)

            val repository = Repository(
                id = UUID.randomUUID().toString(),
                name = targetDir.name,
                path = targetDir.absolutePath,
                lastUpdated = lastCommit,
                currentBranch = currentBranch,
                totalBranches = branches.size,
                hasRemoteOrigin = hasRemoteOrigin,
                pendingPushCommits = aheadCount
            )

            dataStore.addRepository(repository)
            GitResult.Success(repository)
        }
    } catch (e: Exception) {
        genericFailure(e)
    }
}

internal suspend fun GitRepository.removeRepositoryWithFilesImpl(repositoryId: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val repository = getRepositories().find { it.id == repositoryId }
            ?: return@withContext GitResult.Failure.Generic(text(R.string.git_error_repository_not_found))

        val repoDir = java.io.File(repository.path)
        if (repoDir.exists()) {
            if (!repoDir.deleteRecursively()) {
                return@withContext GitResult.Failure.Generic(text(R.string.git_error_delete_files_failed))
            }
        }

        dataStore.removeRepository(repositoryId)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        genericFailure(e)
    }
}

internal suspend fun GitRepository.refreshRepositoryImpl(repository: Repository): Repository? =
    withGit(repository) { git ->
        val currentBranch = git.repository.branch
        val lastCommit = getLastCommitTime(git)
        val branches = getBranchesInternal(git)
        val hasRemoteOrigin = git.remoteList().call().any { it.name == "origin" }
        val (aheadCount, _) = calculateAheadBehind(git)

        val updated = repository.copy(
            currentBranch = currentBranch,
            lastUpdated = lastCommit,
            totalBranches = branches.size,
            hasRemoteOrigin = hasRemoteOrigin,
            pendingPushCommits = aheadCount
        )

        updateRepository(updated)
        GitResult.Success(updated)
    }.getOrNull()
