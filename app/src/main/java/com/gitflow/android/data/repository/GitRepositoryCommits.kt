package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.ByteArrayOutputStream

internal suspend fun GitRepository.getCommitsImpl(
    repository: Repository, page: Int, pageSize: Int
): List<Commit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyList()
        git.use { g ->
            RevWalk(g.repository).use { revWalk ->
                revWalk.sort(RevSort.TOPO)

                val localRefs = g.branchList().call()
                val remoteRefs = g.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
                val allRefs = localRefs + remoteRefs
                val processedCommits = mutableSetOf<String>()

                val commitToBranchHeads = mutableMapOf<String, MutableList<String>>()
                for (ref in allRefs) {
                    try {
                        val head = g.repository.resolve(ref.objectId.name)
                        if (head != null) {
                            commitToBranchHeads.getOrPut(head.name) { mutableListOf() }.add(getBranchName(ref))
                        }
                    } catch (_: Exception) {}
                }

                for (ref in allRefs) {
                    try {
                        val head = g.repository.resolve(ref.objectId.name)
                        if (head != null) revWalk.markStart(revWalk.parseCommit(head))
                    } catch (_: Exception) {}
                }

                // Build tag map once — O(1) per commit instead of O(N)
                val tagsByCommit = mutableMapOf<String, MutableList<String>>()
                try {
                    g.tagList().call().forEach { tagRef ->
                        val peeled = g.repository.refDatabase.peel(tagRef)
                        val targetId = (peeled.peeledObjectId ?: tagRef.objectId).name
                        tagsByCommit.getOrPut(targetId) { mutableListOf() }
                            .add(tagRef.name.removePrefix("refs/tags/"))
                    }
                } catch (_: Exception) {}

                val commits = mutableListOf<Commit>()
                val offset = page * pageSize
                var skipped = 0
                var loaded = 0

                for (commit in revWalk) {
                    if (processedCommits.contains(commit.id.name)) continue
                    processedCommits.add(commit.id.name)

                    if (skipped < offset) { skipped++; continue }
                    if (loaded >= pageSize) break
                    loaded++

                    val branchHeads = commitToBranchHeads[commit.id.name] ?: emptyList()
                    commits.add(Commit(
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
                    ))
                }
                commits
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// ---------------------------------------------------------------------------
// getCommitDiffs — explicit repository overload
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getCommitDiffsImpl(commit: Commit, repository: Repository): List<FileDiff> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyList()
        git.use { g ->
            val jRepo = g.repository
            RevWalk(jRepo).use { rw ->
                val revCommit = rw.parseCommit(jRepo.resolve(commit.hash))
                val diffs = mutableListOf<FileDiff>()

                if (revCommit.parentCount > 0) {
                    val parent = rw.parseCommit(revCommit.getParent(0).id)
                    val oldParser = CanonicalTreeParser()
                    val newParser = CanonicalTreeParser()
                    jRepo.newObjectReader().use { reader ->
                        oldParser.reset(reader, parent.tree)
                        newParser.reset(reader, revCommit.tree)
                    }
                    g.diff().setOldTree(oldParser).setNewTree(newParser).call().forEach { entry ->
                        val out = ByteArrayOutputStream()
                        DiffFormatter(out).use { fmt ->
                            fmt.setRepository(jRepo)
                            fmt.format(entry)
                        }
                        diffs.add(parseDiffText(entry, out.toString()))
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
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// ---------------------------------------------------------------------------
// getCommitDiffs — auto-find repository overload
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getCommitDiffsAutoImpl(commit: Commit): List<FileDiff> = withContext(Dispatchers.IO) {
    try {
        val repo = getRepositories().find { r ->
            try {
                openRepository(r.path)?.use { git -> git.repository.resolve(commit.hash) != null } ?: false
            } catch (_: Exception) { false }
        } ?: return@withContext emptyList()

        getCommitDiffsImpl(commit, repo)
    } catch (e: Exception) {
        emptyList()
    }
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

private fun parseDiffText(diffEntry: org.eclipse.jgit.diff.DiffEntry, diffText: String): FileDiff {
    val lines = diffText.split('\n')
    val hunks = mutableListOf<DiffHunk>()
    var currentHunk: DiffHunk? = null
    val hunkLines = mutableListOf<DiffLine>()
    var additions = 0
    var deletions = 0
    var oldLineNum = 0
    var newLineNum = 0

    for (line in lines) {
        when {
            line.startsWith("@@") -> {
                currentHunk?.let { hunks.add(it.copy(lines = hunkLines.toList())); hunkLines.clear() }
                val h = parseHunkHeader(line)
                oldLineNum = h.first; newLineNum = h.third
                currentHunk = DiffHunk(header = line, oldStart = h.first, oldLines = h.second, newStart = h.third, newLines = h.fourth, lines = emptyList())
            }
            line.startsWith("+") && !line.startsWith("+++") -> {
                additions++
                hunkLines.add(DiffLine(type = LineType.ADDED, content = line.drop(1), lineNumber = null, oldLineNumber = null, newLineNumber = newLineNum++))
            }
            line.startsWith("-") && !line.startsWith("---") -> {
                deletions++
                hunkLines.add(DiffLine(type = LineType.DELETED, content = line.drop(1), lineNumber = null, oldLineNumber = oldLineNum++, newLineNumber = null))
            }
            line.startsWith(" ") -> {
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
    val match = """@@ -(\d+),(\d+) \+(\d+),(\d+) @@""".toRegex().find(header)
    return if (match != null) {
        val (os, ol, ns, nl) = match.destructured
        Tuple4(os.toInt(), ol.toInt(), ns.toInt(), nl.toInt())
    } else {
        Tuple4(0, 0, 0, 0)
    }
}

// Shared with GitRepositoryFiles — internal so it can be reused
internal fun getFileContentFromCommitBytes(
    repository: org.eclipse.jgit.lib.Repository,
    commit: org.eclipse.jgit.revwalk.RevCommit,
    filePath: String
): ByteArray? {
    return try {
        org.eclipse.jgit.treewalk.TreeWalk(repository).use { treeWalk ->
            treeWalk.addTree(commit.tree)
            treeWalk.isRecursive = true
            treeWalk.filter = org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath)
            if (treeWalk.next()) {
                repository.newObjectReader().use { reader -> reader.open(treeWalk.getObjectId(0)).bytes }
            } else null
        }
    } catch (e: Exception) {
        null
    }
}
