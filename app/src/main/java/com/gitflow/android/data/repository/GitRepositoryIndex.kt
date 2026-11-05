package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import com.gitflow.android.data.models.GitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
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
            g.reset().addPath(filePath).call()
        }
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

internal suspend fun GitRepository.discardFileChangesImpl(repository: Repository, filePath: String): GitResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext GitResult.Failure.Generic("Repository not found")
        git.use { g ->
            val status = g.status().call()
            if (status.untracked.contains(filePath)) {
                File(repository.path, filePath).deleteRecursively()
            } else if (status.added.contains(filePath)) {
                // The file is absent from HEAD, so there is no version to check out.
                g.rm().setCached(true).addFilepattern(filePath).call()
                File(repository.path, filePath).delete()
            } else {
                g.reset().addPath(filePath).call()
                g.checkout().addPath(filePath).call()
            }
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

internal suspend fun GitRepository.getWorkingFileDiffImpl(
    repository: Repository,
    filePath: String,
    stage: ChangeStage
): FileDiff? = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext null
        git.use { workingFileDiff(it.repository, filePath, stage) }
    } catch (_: Exception) {
        null
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
            // A merge in progress (MERGE_HEAD present) can be committed even when the index
            // matches HEAD — e.g. a conflict resolved entirely to "ours". Only block the
            // "nothing staged" case for a normal, non-merge commit.
            val mergeInProgress = g.repository.readMergeHeads()?.isNotEmpty() == true
            if (!mergeInProgress && status.added.isEmpty() && status.changed.isEmpty() && status.removed.isEmpty()) {
                // inline use{} allows non-local return to withContext
                return@withContext GitResult.Failure.NoStagedChanges()
            }
            val (name, email) = resolveCommitIdentity(g)
                ?: return@withContext GitResult.Failure.IdentityRequired()
            g.commit().setMessage(message)
                .setAuthor(name, email)
                .setCommitter(name, email)
                .call()
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
            val (name, email) = resolveCommitIdentity(g)
                ?: return@withContext GitResult.Failure.IdentityRequired()
            g.commit().setAmend(true).setMessage(message)
                .setAuthor(name, email)
                .setCommitter(name, email)
                .call()
        }
        refreshRepository(repository)
        GitResult.Success(Unit)
    } catch (e: Exception) {
        GitResult.Failure.Generic(e.message ?: "Unknown error", e)
    }
}

/** Reads .git/MERGE_MSG — the message git prepared for the in-progress merge commit. */
internal suspend fun GitRepository.getMergeMessageImpl(repository: Repository): String? = withContext(Dispatchers.IO) {
    try {
        val git = openRepository(repository.path) ?: return@withContext null
        git.use { g -> g.repository.readMergeCommitMsg()?.takeIf { it.isNotBlank() } }
    } catch (e: Exception) {
        null
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
    val fileDiff = runCatching { workingFileDiff(git.repository, filePath, stage) }.getOrNull()
    return (fileDiff?.additions ?: 0) to (fileDiff?.deletions ?: 0)
}

private fun workingFileDiff(repository: org.eclipse.jgit.lib.Repository, filePath: String, stage: ChangeStage): FileDiff? {
    val (oldTree, newTree) = when (stage) {
        ChangeStage.STAGED -> {
            val headTree = repository.newObjectReader().use { reader ->
                val headTreeId = repository.resolve("HEAD^{tree}")
                if (headTreeId != null) CanonicalTreeParser().apply { reset(reader, headTreeId) }
                else EmptyTreeIterator()
            }
            headTree to DirCacheIterator(repository.readDirCache())
        }
        ChangeStage.UNSTAGED -> DirCacheIterator(repository.readDirCache()) to FileTreeIterator(repository)
    }

    val output = ByteArrayOutputStream()
    DiffFormatter(output).use { formatter ->
        formatter.setRepository(repository)
        formatter.pathFilter = PathFilter.create(filePath)
        // format() has to run on the formatter that did scan(): scan() is what lets it read
        // working-tree content, which is not in the object database.
        val entry = formatter.scan(oldTree, newTree).firstOrNull() ?: return null
        formatter.format(entry)
        formatter.flush()
        return parseDiffText(entry, output.toString())
    }
}

internal fun parseConflictFile(repositoryPath: String, path: String): MergeConflict? {
    val file = File(repositoryPath, path)
    if (!file.exists() || !file.isFile) return null

    val text = runCatching { file.readText() }.getOrElse { return null }
    val lineSeparator = if (text.contains("\r\n")) "\r\n" else "\n"
    val lines = text.removeSuffix("\n").removeSuffix("\r").split("\r\n", "\n")
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
        originalLines = lines,
        lineSeparator = lineSeparator,
        endsWithNewline = text.endsWith("\n")
    )
}
