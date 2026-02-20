package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import com.gitflow.android.data.models.GitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk

// ---------------------------------------------------------------------------
// File tree
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getCommitFileTreeImpl(commit: Commit, repository: Repository): FileTreeNode = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyFileTreeNode()
        git.use { g ->
            val jRepo = g.repository
            RevWalk(jRepo).use { rw ->
                val revCommit = rw.parseCommit(jRepo.resolve(commit.hash))
                buildFileTree(collectFileInfo(jRepo, revCommit, commit.timestamp))
            }
        }
    } catch (e: Exception) {
        emptyFileTreeNode()
    }
}

internal suspend fun GitRepository.getCommitFileTreeAutoImpl(commit: Commit): FileTreeNode = withContext(Dispatchers.IO) {
    try {
        val repo = findRepositoryForCommit(commit) ?: return@withContext emptyFileTreeNode()
        getCommitFileTreeImpl(commit, repo)
    } catch (e: Exception) {
        emptyFileTreeNode()
    }
}

// ---------------------------------------------------------------------------
// File content
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getFileContentImpl(commit: Commit, filePath: String, repository: Repository): String? = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext null
        git.use { g ->
            val jRepo = g.repository
            RevWalk(jRepo).use { rw ->
                val revCommit = rw.parseCommit(jRepo.resolve(commit.hash))
                getFileContentFromCommitBytes(jRepo, revCommit, filePath)?.let { String(it) }
            }
        }
    } catch (e: Exception) {
        null
    }
}

internal suspend fun GitRepository.getFileContentAutoImpl(commit: Commit, filePath: String): String? = withContext(Dispatchers.IO) {
    try {
        val repo = findRepositoryForCommit(commit) ?: return@withContext null
        getFileContentImpl(commit, filePath, repo)
    } catch (e: Exception) {
        null
    }
}

internal suspend fun GitRepository.getFileContentBytesImpl(commit: Commit, filePath: String, repository: Repository): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext null
        git.use { g ->
            val jRepo = g.repository
            RevWalk(jRepo).use { rw ->
                val revCommit = rw.parseCommit(jRepo.resolve(commit.hash))
                getFileContentFromCommitBytes(jRepo, revCommit, filePath)
            }
        }
    } catch (e: Exception) {
        null
    }
}

internal suspend fun GitRepository.getFileContentBytesAutoImpl(commit: Commit, filePath: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val repo = findRepositoryForCommit(commit) ?: return@withContext null
        getFileContentBytesImpl(commit, filePath, repo)
    } catch (e: Exception) {
        null
    }
}

// ---------------------------------------------------------------------------
// Restore file
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.restoreFileToCommitImpl(commit: Commit, filePath: String, repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Failed to restore file")
    try {
        val repo = git.repository
        val commitId = repo.resolve(commit.hash) ?: return@withContext GitResult.Failure.Generic("Failed to restore file")
        RevWalk(repo).use { rw -> rw.parseCommit(commitId) }
        git.checkout().setStartPoint(commit.hash).setForce(true).addPath(filePath).call()
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Failed to restore file", e)
    } finally {
        git.close()
    }
}

internal suspend fun GitRepository.restoreFileToCommitAutoImpl(commit: Commit, filePath: String): GitResult<Unit> {
    val repo = findRepositoryForCommit(commit) ?: return GitResult.Failure.Generic("Failed to restore file")
    return restoreFileToCommitImpl(commit, filePath, repo)
}

internal suspend fun GitRepository.restoreFileToParentCommitImpl(commit: Commit, filePath: String, repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Failed to restore file")
    try {
        val repo = git.repository
        val commitId = repo.resolve(commit.hash) ?: return@withContext GitResult.Failure.Generic("Failed to restore file")
        val parentHash = RevWalk(repo).use { rw ->
            rw.parseCommit(commitId).parents.firstOrNull()?.name
        } ?: return@withContext GitResult.Failure.Generic("Failed to restore file")

        git.checkout().setStartPoint(parentHash).setForce(true).addPath(filePath).call()
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Failed to restore file", e)
    } finally {
        git.close()
    }
}

internal suspend fun GitRepository.restoreFileToParentCommitAutoImpl(commit: Commit, filePath: String): GitResult<Unit> {
    val repo = findRepositoryForCommit(commit) ?: return GitResult.Failure.Generic("Failed to restore file")
    return restoreFileToParentCommitImpl(commit, filePath, repo)
}

// ---------------------------------------------------------------------------
// File history
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getFileHistoryImpl(commit: Commit, filePath: String, repository: Repository): List<Commit> = withContext(Dispatchers.IO) {
    val git = openRepository(repository.path) ?: return@withContext emptyList()
    try {
        val jRepo = git.repository
        val commitId = jRepo.resolve(commit.hash) ?: return@withContext emptyList()
        git.log().add(commitId).addPath(filePath).call().map { mapRevCommitToCommit(it) }
    } catch (e: Exception) {
        emptyList()
    } finally {
        git.close()
    }
}

internal suspend fun GitRepository.getFileHistoryAutoImpl(commit: Commit, filePath: String): List<Commit> {
    val repo = findRepositoryForCommit(commit) ?: return emptyList()
    return getFileHistoryImpl(commit, filePath, repo)
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

private suspend fun GitRepository.findRepositoryForCommit(commit: Commit): Repository? {
    return getRepositories().find { repo ->
        try {
            openRepository(repo.path)?.use { git ->
                git.repository.resolve(commit.hash) != null
            } ?: false
        } catch (_: Exception) { false }
    }
}

private fun collectFileInfo(
    jRepo: org.eclipse.jgit.lib.Repository,
    revCommit: RevCommit,
    timestamp: Long
): List<CommitFileInfo> {
    val files = mutableListOf<CommitFileInfo>()
    TreeWalk(jRepo).use { treeWalk ->
        treeWalk.addTree(revCommit.tree)
        treeWalk.isRecursive = true
        while (treeWalk.next()) {
            val objectId = treeWalk.getObjectId(0)
            val size = try {
                jRepo.newObjectReader().use { reader ->
                    reader.getObjectSize(objectId, Constants.OBJ_BLOB).toLong()
                }
            } catch (_: Exception) { 0L }
            files.add(CommitFileInfo(path = treeWalk.pathString, size = size, lastModified = timestamp))
        }
    }
    return files
}

private fun buildFileTree(files: List<CommitFileInfo>): FileTreeNode {
    val root = mutableMapOf<String, Any>()
    files.forEach { file ->
        val parts = file.path.split("/")
        var current = root
        for (i in parts.indices) {
            val part = parts[i]
            if (i == parts.lastIndex) {
                current[part] = file
            } else {
                if (current[part] !is MutableMap<*, *>) current[part] = mutableMapOf<String, Any>()
                @Suppress("UNCHECKED_CAST")
                current = current[part] as MutableMap<String, Any>
            }
        }
    }
    return toFileTreeNode("", "", root)
}

@Suppress("UNCHECKED_CAST")
private fun toFileTreeNode(name: String, path: String, node: Any): FileTreeNode = when (node) {
    is CommitFileInfo -> FileTreeNode(name = name, path = path, type = FileTreeNodeType.FILE, size = node.size, lastModified = node.lastModified)
    is Map<*, *> -> {
        val children = (node as Map<String, Any>).map { (childName, childNode) ->
            val childPath = if (path.isEmpty()) childName else "$path/$childName"
            toFileTreeNode(childName, childPath, childNode)
        }.sortedWith(compareBy<FileTreeNode> { it.type }.thenBy { it.name })
        FileTreeNode(name = name, path = path, type = FileTreeNodeType.DIRECTORY, children = children)
    }
    else -> throw IllegalArgumentException("Unknown node type: ${node::class}")
}

private fun mapRevCommitToCommit(revCommit: RevCommit): Commit {
    val author = revCommit.authorIdent
    return Commit(
        hash = revCommit.name,
        message = revCommit.shortMessage ?: revCommit.fullMessage,
        author = author?.name ?: "Unknown",
        email = author?.emailAddress ?: "",
        timestamp = author?.`when`?.time ?: 0L,
        parents = revCommit.parents.map { it.name },
        description = revCommit.fullMessage,
        isMergeCommit = revCommit.parentCount > 1
    )
}

private fun emptyFileTreeNode() = FileTreeNode("", "", FileTreeNodeType.DIRECTORY)
