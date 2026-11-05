package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.ByteArrayOutputStream

internal suspend fun GitRepository.getCommitsImpl(
    repository: Repository, page: Int, pageSize: Int
): GitResult<List<Commit>> = withGit(repository) { git ->
    val jRepo = git.repository
    val branchRefs = git.branchList().call() +
        git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
    val tips = branchRefs.associate { it.name to it.objectId }
    val order = historyOrder?.takeIf { it.repositoryPath == repository.path && it.tips == tips }
        ?: HistoryOrder(repository.path, tips, topologicalOrder(jRepo, tips.values)).also { historyOrder = it }

    val branchHeadsByCommit = branchRefs.groupBy({ it.objectId.name }, { getBranchName(it) })

    // Build tag map once — O(1) per commit instead of O(N)
    val tagsByCommit = mutableMapOf<String, MutableList<String>>()
    try {
        git.tagList().call().forEach { tagRef ->
            val peeled = jRepo.refDatabase.peel(tagRef)
            val targetId = (peeled.peeledObjectId ?: tagRef.objectId).name
            tagsByCommit.getOrPut(targetId) { mutableListOf() }
                .add(tagRef.name.removePrefix("refs/tags/"))
        }
    } catch (_: Exception) {}

    val pageIds = order.commitIds.drop(page * pageSize).take(pageSize)
    val pageCommits = RevWalk(jRepo).use { revWalk ->
        pageIds.map { commitId ->
            val commit = revWalk.parseCommit(commitId)
            val branchHeads = branchHeadsByCommit[commit.id.name] ?: emptyList()
            Commit(
                hash = commit.id.name,
                message = commit.shortMessage,
                description = commit.fullMessage.removePrefix(commit.shortMessage).trim(),
                author = commit.authorIdent.name,
                email = commit.authorIdent.emailAddress,
                timestamp = commit.authorIdent.`when`.time,
                parents = commit.parents.map { it.id.name },
                branch = branchHeads.firstOrNull() ?: "unknown",
                tags = tagsByCommit[commit.id.name] ?: emptyList(),
                branchHeads = branchHeads,
                isMergeCommit = commit.parentCount > 1
            )
        }
    }
    GitResult.Success(pageCommits)
}

/** Topological order of every commit reachable from the branch [tips]: ids only, no message bodies. */
private fun topologicalOrder(
    repository: org.eclipse.jgit.lib.Repository,
    tips: Collection<ObjectId>
): List<ObjectId> = RevWalk(repository).use { revWalk ->
    revWalk.isRetainBody = false
    revWalk.sort(RevSort.TOPO)
    for (tip in tips) {
        try {
            revWalk.markStart(revWalk.parseCommit(tip))
        } catch (_: Exception) {}
    }
    revWalk.map { it.toObjectId() }
}

// ---------------------------------------------------------------------------
// getCommitByHash
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getCommitByHashImpl(hash: String, repository: Repository): GitResult<Commit> =
    withGit(repository) { git ->
        val objectId = git.repository.resolve(hash)
            ?: return@withGit GitResult.Failure.Generic("Commit not found: $hash")
        val revCommit = RevWalk(git.repository).use { revWalk -> revWalk.parseCommit(objectId) }
        GitResult.Success(Commit(
            hash = revCommit.id.name,
            message = revCommit.shortMessage,
            description = revCommit.fullMessage.removePrefix(revCommit.shortMessage).trim(),
            author = revCommit.authorIdent.name,
            email = revCommit.authorIdent.emailAddress,
            timestamp = revCommit.authorIdent.`when`.time,
            parents = revCommit.parents.map { it.id.name },
            isMergeCommit = revCommit.parentCount > 1
        ))
    }

// ---------------------------------------------------------------------------
// getCommitDiffs
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getCommitDiffsImpl(commit: Commit, repository: Repository): GitResult<List<FileDiff>> =
    withGit(repository) { git ->
        val jRepo = git.repository
        val fileDiffs = RevWalk(jRepo).use { revWalk ->
            val revCommit = revWalk.parseCommit(jRepo.resolve(commit.hash))
            val diffs = mutableListOf<FileDiff>()

            if (revCommit.parentCount > 0) {
                val parent = revWalk.parseCommit(revCommit.getParent(0).id)
                val oldParser = CanonicalTreeParser()
                val newParser = CanonicalTreeParser()
                jRepo.newObjectReader().use { reader ->
                    oldParser.reset(reader, parent.tree)
                    newParser.reset(reader, revCommit.tree)
                }
                git.diff().setOldTree(oldParser).setNewTree(newParser).call().forEach { entry ->
                    val output = ByteArrayOutputStream()
                    DiffFormatter(output).use { formatter ->
                        formatter.setRepository(jRepo)
                        formatter.format(entry)
                    }
                    diffs.add(parseDiffText(entry, output.toString()))
                }
            } else {
                TreeWalk(jRepo).use { treeWalk ->
                    treeWalk.addTree(revCommit.tree)
                    treeWalk.isRecursive = true
                    while (treeWalk.next()) {
                        diffs.add(buildAddedFileDiff(jRepo, revCommit, treeWalk.pathString))
                    }
                }
            }
            diffs
        }
        GitResult.Success(fileDiffs)
    }

// ---------------------------------------------------------------------------
// Private helpers (file-local)
// ---------------------------------------------------------------------------

private fun buildAddedFileDiff(
    jRepo: org.eclipse.jgit.lib.Repository,
    revCommit: org.eclipse.jgit.revwalk.RevCommit,
    path: String
): FileDiff {
    val content = getFileContentFromCommitBytes(jRepo, revCommit, path)?.let { String(it) }
    val lines = content?.split('\n') ?: emptyList()
    return FileDiff(
        path = path,
        status = FileStatus.ADDED,
        additions = lines.size,
        deletions = 0,
        hunks = listOf(DiffHunk(
            header = "@@ -0,0 +1,${lines.size} @@",
            oldStart = 0, oldLines = 0, newStart = 1, newLines = lines.size,
            lines = lines.mapIndexed { i, line ->
                DiffLine(type = LineType.ADDED, content = line, lineNumber = i + 1, newLineNumber = i + 1)
            }
        ))
    )
}

// [diffText] must hold a single file's DiffFormatter output: file headers are skipped only
// before its first hunk.
internal fun parseDiffText(diffEntry: org.eclipse.jgit.diff.DiffEntry, diffText: String): FileDiff {
    val lines = diffText.split('\n')
    val hunks = mutableListOf<DiffHunk>()
    var currentHunk: DiffHunk? = null
    val hunkLines = mutableListOf<DiffLine>()
    var additions = 0
    var deletions = 0
    var oldLineNum = 0
    var newLineNum = 0

    for (line in lines) {
        if (line.startsWith("@@")) {
            currentHunk?.let { hunks.add(it.copy(lines = hunkLines.toList())); hunkLines.clear() }
            val range = parseHunkHeader(line)
            oldLineNum = range.first; newLineNum = range.third
            currentHunk = DiffHunk(header = line, oldStart = range.first, oldLines = range.second, newStart = range.third, newLines = range.fourth, lines = emptyList())
            continue
        }
        // Before the first hunk come the file headers ("--- a/x", "+++ b/x"); inside a hunk the
        // first character alone is the line type, so content like "-- comment" must not be
        // mistaken for a header.
        if (currentHunk == null) continue
        when (line.firstOrNull()) {
            '+' -> {
                additions++
                hunkLines.add(DiffLine(type = LineType.ADDED, content = line.drop(1), lineNumber = null, oldLineNumber = null, newLineNumber = newLineNum++))
            }
            '-' -> {
                deletions++
                hunkLines.add(DiffLine(type = LineType.DELETED, content = line.drop(1), lineNumber = null, oldLineNumber = oldLineNum++, newLineNumber = null))
            }
            ' ' -> {
                hunkLines.add(DiffLine(type = LineType.CONTEXT, content = line.drop(1), lineNumber = oldLineNum, oldLineNumber = oldLineNum++, newLineNumber = newLineNum++))
            }
        }
    }
    currentHunk?.let { hunks.add(it.copy(lines = hunkLines.toList())) }

    val status = when (diffEntry.changeType) {
        org.eclipse.jgit.diff.DiffEntry.ChangeType.ADD -> FileStatus.ADDED
        org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE -> FileStatus.DELETED
        org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME -> FileStatus.RENAMED
        else -> FileStatus.MODIFIED
    }

    return FileDiff(
        path = diffEntry.newPath ?: diffEntry.oldPath,
        oldPath = if (diffEntry.oldPath != diffEntry.newPath) diffEntry.oldPath else null,
        status = status,
        additions = additions,
        deletions = deletions,
        hunks = hunks
    )
}

private data class Tuple4<T1, T2, T3, T4>(val first: T1, val second: T2, val third: T3, val fourth: T4)

private fun parseHunkHeader(header: String): Tuple4<Int, Int, Int, Int> {
    // A range's line count is omitted when it equals 1 ("@@ -5 +5 @@").
    val match = """@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""".toRegex().find(header)
    return if (match != null) {
        val (oldStart, oldLines, newStart, newLines) = match.destructured
        Tuple4(oldStart.toInt(), oldLines.toIntOrNull() ?: 1, newStart.toInt(), newLines.toIntOrNull() ?: 1)
    } else {
        Tuple4(0, 0, 0, 0)
    }
}

/** Null when [filePath] is absent from [commit]; read errors propagate. Shared with GitRepositoryFiles. */
internal fun getFileContentFromCommitBytes(
    repository: org.eclipse.jgit.lib.Repository,
    commit: org.eclipse.jgit.revwalk.RevCommit,
    filePath: String
): ByteArray? {
    TreeWalk(repository).use { treeWalk ->
        treeWalk.addTree(commit.tree)
        treeWalk.isRecursive = true
        treeWalk.filter = org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath)
        if (!treeWalk.next()) return null
        return repository.newObjectReader().use { reader -> reader.open(treeWalk.getObjectId(0)).bytes }
    }
}
