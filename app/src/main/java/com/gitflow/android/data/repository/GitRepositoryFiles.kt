package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import com.gitflow.android.data.models.GitResult
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk

// ---------------------------------------------------------------------------
// File tree
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getCommitFileTreeImpl(commit: Commit, repository: Repository): GitResult<FileTreeNode> =
    withGit(repository) { git ->
        val jRepo = git.repository
        val tree = RevWalk(jRepo).use { revWalk ->
            val revCommit = revWalk.parseCommit(jRepo.resolve(commit.hash))
            buildFileTree(collectFileInfo(jRepo, revCommit, commit.timestamp))
        }
        GitResult.Success(tree)
    }

// ---------------------------------------------------------------------------
// File content
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getFileContentImpl(commit: Commit, filePath: String, repository: Repository): GitResult<String> =
    when (val bytes = getFileContentBytesImpl(commit, filePath, repository)) {
        is GitResult.Success -> GitResult.Success(String(bytes.data))
        is GitResult.Failure -> bytes
    }

internal suspend fun GitRepository.getFileContentBytesImpl(commit: Commit, filePath: String, repository: Repository): GitResult<ByteArray> =
    withGit(repository) { git ->
        val jRepo = git.repository
        val bytes = RevWalk(jRepo).use { revWalk ->
            val revCommit = revWalk.parseCommit(jRepo.resolve(commit.hash))
            getFileContentFromCommitBytes(jRepo, revCommit, filePath)
        } ?: return@withGit GitResult.Failure.Generic("File not found in commit: $filePath")
        GitResult.Success(bytes)
    }

// ---------------------------------------------------------------------------
// Restore file
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.restoreFileToCommitImpl(commit: Commit, filePath: String, repository: Repository): GitResult<Unit> =
    withGit(repository) { git ->
        git.repository.resolve(commit.hash)
            ?: return@withGit GitResult.Failure.Generic("Cannot resolve commit: ${commit.hash}")
        git.checkout().setStartPoint(commit.hash).setForce(true).addPath(filePath).call()
        GitResult.Success(Unit)
    }

internal suspend fun GitRepository.restoreFileToParentCommitImpl(commit: Commit, filePath: String, repository: Repository): GitResult<Unit> =
    withGit(repository) { git ->
        val jRepo = git.repository
        val commitId = jRepo.resolve(commit.hash)
            ?: return@withGit GitResult.Failure.Generic("Cannot resolve commit: ${commit.hash}")
        val parentHash = RevWalk(jRepo).use { revWalk ->
            revWalk.parseCommit(commitId).parents.firstOrNull()?.name
        } ?: return@withGit GitResult.Failure.Generic("Commit has no parent: ${commit.hash}")

        git.checkout().setStartPoint(parentHash).setForce(true).addPath(filePath).call()
        GitResult.Success(Unit)
    }

// ---------------------------------------------------------------------------
// File history
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getFileHistoryImpl(commit: Commit, filePath: String, repository: Repository): GitResult<List<Commit>> =
    withGit(repository) { git ->
        val commitId = git.repository.resolve(commit.hash)
            ?: return@withGit GitResult.Failure.Generic("Cannot resolve commit: ${commit.hash}")
        GitResult.Success(git.log().add(commitId).addPath(filePath).call().map { mapRevCommitToCommit(it) })
    }

internal suspend fun GitRepository.getFileHistoryForPathImpl(repository: Repository, filePath: String): GitResult<List<Commit>> =
    withGit(repository) { git ->
        GitResult.Success(git.log().addPath(filePath).setMaxCount(100).call().map { mapRevCommitToCommit(it) })
    }

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

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
