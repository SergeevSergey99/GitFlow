package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import com.gitflow.android.data.models.GitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File

// ---------------------------------------------------------------------------
// getChangedFiles
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getChangedFilesImpl(repository: Repository): List<FileChange> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyList()
        git.use { g ->
            val status = g.status().call()
            val conflictPaths = status.conflicting.toSet()
            val changes = mutableListOf<FileChange>()

            fun diffFor(path: String, stage: ChangeStage): Pair<Int, Int> = getFileDiffInfo(g, path, stage)

            fun conflictMeta(path: String): Pair<Boolean, Int> {
                if (!conflictPaths.contains(path)) return false to 0
                return true to (parseConflictFile(repository.path, path)?.sections?.size ?: 0)
            }

            status.added.forEach { path ->
                if (conflictPaths.contains(path)) return@forEach
                val (add, del) = diffFor(path, ChangeStage.STAGED)
                changes.add(FileChange(path = path, status = ChangeStatus.ADDED, stage = ChangeStage.STAGED, additions = add, deletions = del))
            }
            status.changed.forEach { path ->
                if (conflictPaths.contains(path)) return@forEach
                val (add, del) = diffFor(path, ChangeStage.STAGED)
                changes.add(FileChange(path = path, status = ChangeStatus.MODIFIED, stage = ChangeStage.STAGED, additions = add, deletions = del))
            }
            status.removed.forEach { path ->
                if (conflictPaths.contains(path)) return@forEach
                val (add, del) = diffFor(path, ChangeStage.STAGED)
                changes.add(FileChange(path = path, status = ChangeStatus.DELETED, stage = ChangeStage.STAGED, additions = add, deletions = del))
            }
            status.modified.forEach { path ->
                if (conflictPaths.contains(path)) return@forEach
                val (add, del) = diffFor(path, ChangeStage.UNSTAGED)
                changes.add(FileChange(path = path, status = ChangeStatus.MODIFIED, stage = ChangeStage.UNSTAGED, additions = add, deletions = del))
            }
            status.missing.forEach { path ->
                if (conflictPaths.contains(path)) return@forEach
                val (add, del) = diffFor(path, ChangeStage.UNSTAGED)
                changes.add(FileChange(path = path, status = ChangeStatus.DELETED, stage = ChangeStage.UNSTAGED, additions = add, deletions = del))
            }
            status.untracked.forEach { path ->
                if (conflictPaths.contains(path)) return@forEach
                val (add, del) = diffFor(path, ChangeStage.UNSTAGED)
                changes.add(FileChange(path = path, status = ChangeStatus.UNTRACKED, stage = ChangeStage.UNSTAGED, additions = add, deletions = del))
            }
            status.conflicting.forEach { path ->
                val (add, del) = diffFor(path, ChangeStage.UNSTAGED)
                val (hasConflicts, conflictCount) = conflictMeta(path)
                changes.add(FileChange(path = path, status = ChangeStatus.MODIFIED, stage = ChangeStage.UNSTAGED,
                    additions = add, deletions = del, hasConflicts = hasConflicts, conflictSections = conflictCount))
            }

            changes.sortedWith(compareBy({ it.stage }, { it.path }))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// ---------------------------------------------------------------------------
// Staging
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.stageFileImpl(repository: Repository, file: FileChange): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            when (file.status) {
                ChangeStatus.DELETED -> g.add().setUpdate(true).addFilepattern(file.path).call()
                else -> g.add().addFilepattern(file.path).call()
            }
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.unstageFileImpl(repository: Repository, filePath: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            g.reset().setMode(ResetCommand.ResetType.MIXED).addPath(filePath).call()
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.stageAllImpl(repository: Repository): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            g.add().addFilepattern(".").call()
            g.add().setUpdate(true).addFilepattern(".").call()
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

// ---------------------------------------------------------------------------
// Conflicts
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.getMergeConflictsImpl(repository: Repository): List<MergeConflict> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext emptyList()
        git.use { g ->
            g.status().call().conflicting.mapNotNull { path ->
                parseConflictFile(repository.path, path)
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

internal suspend fun GitRepository.resolveConflictImpl(
    repository: Repository, path: String, strategy: ConflictResolutionStrategy
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val stage = when (strategy) {
                ConflictResolutionStrategy.OURS -> CheckoutCommand.Stage.OURS
                ConflictResolutionStrategy.THEIRS -> CheckoutCommand.Stage.THEIRS
            }
            g.checkout().setStage(stage).addPath(path).call()
            g.add().addFilepattern(path).call()
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.resolveConflictWithContentImpl(
    repository: Repository, path: String, resolvedContent: String
): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val file = File(repository.path, path)
        file.parentFile?.mkdirs()
        file.writeText(resolvedContent)

        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g -> g.add().addFilepattern(path).call() }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

// ---------------------------------------------------------------------------
// Commit
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.commitImpl(repository: Repository, message: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val status = g.status().call()
            if (status.added.isEmpty() && status.changed.isEmpty() && status.removed.isEmpty()) {
                // inline use{} allows non-local return to withContext
                return@withContext GitResult.Failure.NoStagedChanges()
            }
            val commitCommand = g.commit().setMessage(message)
            resolveCommitIdentity(g)?.let { (name, email) ->
                commitCommand.setAuthor(name, email)
                commitCommand.setCommitter(name, email)
            }
            commitCommand.call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

// ---------------------------------------------------------------------------
// Amend last commit
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.amendLastCommitImpl(repository: Repository, message: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val commitCommand = g.commit().setAmend(true).setMessage(message)
            resolveCommitIdentity(g)?.let { (name, email) ->
                commitCommand.setAuthor(name, email)
                commitCommand.setCommitter(name, email)
            }
            commitCommand.call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.getLastCommitMessageImpl(repository: Repository): String? = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext null
        git.use { g ->
            val head = g.repository.resolve("HEAD") ?: return@use null
            g.repository.parseCommit(head).fullMessage
        }
    } catch (e: Exception) {
        null
    }
}

// ---------------------------------------------------------------------------
// Private helpers (file-local)
// ---------------------------------------------------------------------------

private fun getFileDiffInfo(git: Git, filePath: String, stage: ChangeStage): Pair<Int, Int> {
    return try {
        val repository = git.repository
        val diffCommand = git.diff().setPathFilter(PathFilter.create(filePath))

        when (stage) {
            ChangeStage.STAGED -> {
                val oldTreeIterator = repository.newObjectReader().use { reader ->
                    val headTreeId = repository.resolve("HEAD^{tree}")
                    if (headTreeId != null) CanonicalTreeParser().apply { reset(reader, headTreeId) }
                    else EmptyTreeIterator()
                }
                diffCommand.setOldTree(oldTreeIterator)
                diffCommand.setNewTree(DirCacheIterator(repository.readDirCache()))
            }
            ChangeStage.UNSTAGED -> {
                diffCommand.setOldTree(DirCacheIterator(repository.readDirCache()))
                diffCommand.setNewTree(FileTreeIterator(repository))
            }
        }

        val diffs = diffCommand.call()
        if (diffs.isEmpty()) return 0 to 0

        ByteArrayOutputStream().use { out ->
            DiffFormatter(out).use { fmt ->
                fmt.setRepository(repository)
                diffs.forEach { fmt.format(it) }
            }
            parseDiffStats(out.toString())
        }
    } catch (e: Exception) {
        0 to 0
    }
}

private fun parseDiffStats(diffText: String): Pair<Int, Int> {
    var additions = 0
    var deletions = 0
    for (line in diffText.split('\n')) {
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions++
            line.startsWith("-") && !line.startsWith("---") -> deletions++
        }
    }
    return additions to deletions
}

internal fun parseConflictFile(repositoryPath: String, path: String): MergeConflict? {
    val file = File(repositoryPath, path)
    if (!file.exists() || !file.isFile) return null

    val lines = runCatching { file.readLines() }.getOrElse { return null }
    if (lines.none { it.startsWith("<<<<<<<") }) return null

    val sections = mutableListOf<MergeConflictSection>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (!line.startsWith("<<<<<<<")) { index++; continue }

        val oursLabel = line.removePrefix("<<<<<<<").trim().ifBlank { "HEAD" }
        val startLineIndex = index++

        val oursContent = mutableListOf<String>()
        val baseContent = mutableListOf<String>()
        var theirsContent = mutableListOf<String>()
        var baseLabel: String? = null

        while (index < lines.size && !lines[index].startsWith("=======") && !lines[index].startsWith("|||||||")) {
            oursContent.add(lines[index++])
        }

        if (index < lines.size && lines[index].startsWith("|||||||")) {
            baseLabel = lines[index].removePrefix("|||||||").trim().ifBlank { "BASE" }
            index++
            while (index < lines.size && !lines[index].startsWith("=======")) {
                baseContent.add(lines[index++])
            }
        }

        if (index < lines.size && lines[index].startsWith("=======")) index++

        val theirs = mutableListOf<String>()
        while (index < lines.size && !lines[index].startsWith(">>>>>>>")) {
            theirs.add(lines[index++])
        }
        theirsContent = theirs

        val theirsLabel = if (index < lines.size && lines[index].startsWith(">>>>>>>"))
            lines[index].removePrefix(">>>>>>>").trim().ifBlank { "incoming" }
        else "incoming"

        if (index < lines.size && lines[index].startsWith(">>>>>>>")) index++

        sections.add(MergeConflictSection(
            oursLabel = oursLabel,
            theirsLabel = theirsLabel,
            baseLabel = baseLabel,
            oursContent = oursContent.joinToString("\n"),
            theirsContent = theirsContent.joinToString("\n"),
            baseContent = if (baseContent.isEmpty()) null else baseContent.joinToString("\n"),
            startLineIndex = startLineIndex,
            endLineIndex = index
        ))
    }

    if (sections.isEmpty()) return null

    return MergeConflict(
        path = path,
        oursLabel = sections.first().oursLabel,
        theirsLabel = sections.first().theirsLabel,
        sections = sections,
        originalLines = lines
    )
}
