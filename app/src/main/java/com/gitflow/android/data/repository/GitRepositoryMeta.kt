package com.gitflow.android.data.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gitflow.android.data.models.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import java.util.*

internal suspend fun GitRepository.addRepositoryImpl(path: String): Result<Repository> = withContext(Dispatchers.IO) {
    try {
        val repoDir = if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile == null || !documentFile.exists()) {
                return@withContext Result.failure(Exception("Cannot access directory"))
            }
            return@withContext Result.failure(Exception("SAF URI support needs additional implementation"))
        } else {
            File(path)
        }

        if (!repoDir.exists() || !File(repoDir, ".git").exists()) {
            return@withContext Result.failure(Exception("Directory is not a Git repository: $path"))
        }

        val git = Git.open(repoDir)
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
        git.close()
        Result.success(repository)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun GitRepository.createRepositoryImpl(name: String, localPath: String): Result<Repository> = withContext(Dispatchers.IO) {
    try {
        val repoDir = File(localPath)
        if (repoDir.exists()) {
            return@withContext Result.failure(Exception("Directory already exists: ${repoDir.absolutePath}"))
        }

        repoDir.mkdirs()
        val git = Git.init().setDirectory(repoDir).call()

        File(repoDir, "README.md").writeText("# $name\n\nThis repository was created with GitFlow Android.")
        git.add().addFilepattern("README.md").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("GitFlow Android", "gitflow@android.local")
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
        git.close()
        Result.success(repository)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun GitRepository.cloneRepositoryImpl(
    url: String,
    localPath: String,
    customDestination: String?,
    progressCallback: CloneProgressCallback?
): Result<Repository> = withContext(Dispatchers.IO) {
    try {
        val targetDir = if (!customDestination.isNullOrEmpty()) File(customDestination) else File(localPath)

        if (targetDir.exists()) {
            return@withContext Result.failure(Exception("Directory already exists: ${targetDir.absolutePath}"))
        }

        targetDir.parentFile?.mkdirs()

        val cloneCommand = Git.cloneRepository()
            .setURI(url)
            .setDirectory(targetDir)

        resolveCredentialsProvider(url)?.let { cloneCommand.setCredentialsProvider(it) }
        progressCallback?.let { cloneCommand.setProgressMonitor(it) }

        val git = try {
            cloneCommand.call()
        } catch (e: Exception) {
            if (progressCallback?.isCancelled() == true) {
                if (targetDir.exists()) targetDir.deleteRecursively()
                return@withContext Result.failure(Exception("Clone cancelled by user"))
            } else {
                throw e
            }
        }

        val currentBranch = git.repository.branch
        val lastCommit = getLastCommitTime(git)
        val branches = getBranchesInternal(git)
        val hasRemoteOrigin = git.remoteList().call().any { it.name == "origin" }
        val (aheadCount, _) = calculateAheadBehind(git)

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
        git.close()
        Result.success(repository)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun GitRepository.removeRepositoryWithFilesImpl(repositoryId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val repository = getRepositories().find { it.id == repositoryId }
            ?: return@withContext Result.failure(Exception("Repository not found"))

        val repoDir = java.io.File(repository.path)
        if (repoDir.exists()) {
            if (!repoDir.deleteRecursively()) {
                return@withContext Result.failure(Exception("Failed to delete repository files"))
            }
        }

        dataStore.removeRepository(repositoryId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun GitRepository.refreshRepositoryImpl(repository: Repository): Repository? = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext null
        git.use { g ->
            val currentBranch = g.repository.branch
            val lastCommit = getLastCommitTime(g)
            val branches = getBranchesInternal(g)
            val hasRemoteOrigin = g.remoteList().call().any { it.name == "origin" }
            val (aheadCount, _) = calculateAheadBehind(g)

            val updated = repository.copy(
                currentBranch = currentBranch,
                lastUpdated = lastCommit,
                totalBranches = branches.size,
                hasRemoteOrigin = hasRemoteOrigin,
                pendingPushCommits = aheadCount
            )

            updateRepository(updated)
            updated
        }
    } catch (e: Exception) {
        null
    }
}
