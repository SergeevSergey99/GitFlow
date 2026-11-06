package com.gitflow.android.data.repository

import com.gitflow.android.R
import com.gitflow.android.data.models.*
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Inspects the exception cause chain and maps network-related exceptions to
 * [GitResult.Failure.NetworkError], everything else to [GitResult.Failure.Generic].
 */
internal fun classifyNetworkException(e: Exception): GitResult.Failure {
    var cause: Throwable? = e
    while (cause != null) {
        when (cause) {
            is UnknownHostException,
            is NoRouteToHostException ->
                return GitResult.Failure.NetworkError(
                    message = cause.message ?: "No internet connection",
                    cause = e,
                    isOffline = true
                )
            is ConnectException,
            is SocketTimeoutException,
            is SocketException,
            is SSLException ->
                return GitResult.Failure.NetworkError(
                    message = cause.message ?: "Server unreachable",
                    cause = e,
                    isOffline = false
                )
        }
        cause = cause.cause
    }
    // JGit TransportException — check for auth failure before treating as generic network error
    val msgLower = e.message?.lowercase() ?: ""
    if (e is TransportException || e is org.eclipse.jgit.errors.TransportException) {
        if (msgLower.contains("not authorized") ||
            msgLower.contains("authentication required") ||
            msgLower.contains("invalid credentials") ||
            msgLower.contains("invalid username or password")) {
            return GitResult.Failure.AuthRequired(
                message = e.message ?: "Authentication required",
                cause = e
            )
        }
        return GitResult.Failure.NetworkError(
            message = e.message ?: "Network error",
            cause = e,
            isOffline = false
        )
    }
    return genericFailure(e)
}

private class JGitProgressMonitor(
    private val callback: (SyncProgress) -> Unit
) : ProgressMonitor {
    private var currentTask = ""
    private var total = 0
    private var done = 0

    override fun start(totalTasks: Int) {}
    override fun beginTask(title: String?, totalWork: Int) {
        currentTask = title ?: ""
        total = totalWork
        done = 0
        callback(SyncProgress(currentTask, 0, total))
    }
    override fun update(completed: Int) {
        done += completed
        callback(SyncProgress(currentTask, done, total))
    }
    override fun endTask() {
        callback(SyncProgress(currentTask, total, total))
    }
    override fun isCancelled(): Boolean = false
}

private fun GitRepository.conflictPathList(paths: List<String>): String =
    paths.joinToString(", ").ifEmpty { text(R.string.git_conflicts_see_working_tree) }

private fun GitRepository.evaluatePushResult(results: Iterable<PushResult>): GitResult<Unit> {
    val failedUpdates = mutableListOf<String>()
    var hasUpdates = false

    for (result in results) {
        for (update in result.remoteUpdates) {
            hasUpdates = true
            when (update.status) {
                RemoteRefUpdate.Status.OK,
                RemoteRefUpdate.Status.UP_TO_DATE -> Unit
                else -> {
                    val remoteName = update.remoteName ?: update.srcRef ?: "<unknown>"
                    val details = update.message?.takeIf { it.isNotBlank() } ?: update.status.name
                    failedUpdates += "$remoteName: $details"
                }
            }
        }
    }

    return when {
        !hasUpdates -> GitResult.Success(Unit)
        failedUpdates.isEmpty() -> GitResult.Success(Unit)
        else -> GitResult.Failure.Generic(text(R.string.git_error_push_failed, failedUpdates.joinToString("; ")))
    }
}

internal suspend fun GitRepository.getBranchesImpl(repository: Repository): GitResult<List<Branch>> =
    withGit(repository) { git -> GitResult.Success(getBranchesInternal(git)) }

internal suspend fun GitRepository.checkoutBranchImpl(
    repository: Repository,
    branchName: String,
    isLocal: Boolean
): GitResult<Unit> = withGit(repository) { git ->
    if (isLocal) {
        git.checkout().setName(branchName).call()
    } else {
        val localName = branchName.removePrefix("origin/")
        try {
            git.checkout()
                .setCreateBranch(true)
                .setName(localName)
                .setStartPoint(branchName)
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .call()
        } catch (_: RefAlreadyExistsException) {
            git.checkout().setName(localName).call()
        }
    }
    refreshRepository(repository)
    GitResult.Success(Unit)
}

internal suspend fun GitRepository.createBranchImpl(
    repository: Repository,
    branchName: String,
    checkout: Boolean
): GitResult<Unit> = withGit(repository) { git ->
    if (checkout) {
        git.checkout().setCreateBranch(true).setName(branchName).call()
    } else {
        git.branchCreate().setName(branchName).call()
    }
    refreshRepository(repository)
    GitResult.Success(Unit)
}

internal suspend fun GitRepository.deleteBranchImpl(
    repository: Repository,
    branchName: String,
    force: Boolean
): GitResult<Unit> = withGit(repository) { git ->
    val deleted = git.branchDelete().setBranchNames(branchName).setForce(force).call()
    if (deleted.isEmpty()) return@withGit GitResult.Failure.Generic(text(R.string.git_error_branch_not_deleted))
    refreshRepository(repository)
    GitResult.Success(Unit)
}

internal suspend fun GitRepository.renameBranchImpl(
    repository: Repository,
    oldName: String,
    newName: String
): GitResult<Unit> = withGit(
    repository,
    onError = { exception ->
        if (exception is RefAlreadyExistsException) GitResult.Failure.Generic(text(R.string.git_error_branch_exists, newName))
        else genericFailure(exception)
    }
) { git ->
    // oldName always refers to a local branch ref — renaming works whether or not
    // it's the currently checked-out one (JGit updates HEAD's symref accordingly).
    git.branchRename().setOldName(oldName).setNewName(newName).call()
    refreshRepository(repository)
    GitResult.Success(Unit)
}

// Resolves credentials (and refreshes an expiring GitLab/Bitbucket token) for the origin remote.
private suspend fun GitRepository.originCredentials(git: Git) = resolveCredentialsProvider(
    git.repository.config.let { it.getString("remote", "origin", "pushurl") ?: it.getString("remote", "origin", "url") }
)

internal suspend fun GitRepository.fetchImpl(repository: Repository): GitResult<Unit> =
    withGit(repository, onError = ::classifyNetworkException) { git ->
        val fetchCommand = git.fetch()
        originCredentials(git)?.let { fetchCommand.setCredentialsProvider(it) }
        fetchCommand.call()
        refreshRepository(repository)
        GitResult.Success(Unit)
    }

internal suspend fun GitRepository.pullImpl(repository: Repository): GitResult<Unit> =
    withGit(repository, onError = ::classifyNetworkException) { git ->
        val credentials = originCredentials(git)
        // Not refused without an identity: most pulls fast-forward and create no commit.
        applyCommitIdentity(git)
        val pullCommand = git.pull()
        credentials?.let { pullCommand.setCredentialsProvider(it) }
        val result = pullCommand.call()
        val mergeConflicts = result.mergeResult
            ?.takeIf { it.mergeStatus == MergeResult.MergeStatus.CONFLICTING }
            ?.conflicts?.keys?.toList()
        val rebaseStopped = result.rebaseResult?.status in listOf(RebaseResult.Status.STOPPED, RebaseResult.Status.CONFLICTS)
        when {
            result.isSuccessful -> GitResult.Success(Unit)
            mergeConflicts != null || rebaseStopped -> {
                val paths = mergeConflicts ?: git.status().call().conflicting.toList()
                GitResult.Failure.Conflict(
                    message = text(R.string.git_conflicts_pull, conflictPathList(paths)),
                    paths = paths
                )
            }
            else -> GitResult.Failure.Generic(text(R.string.git_error_pull_failed))
        }
    }

internal suspend fun GitRepository.pushImpl(repository: Repository): GitResult<Unit> =
    withGit(repository, onError = ::classifyNetworkException) { git ->
        val pushCommand = git.push()
        originCredentials(git)?.let { pushCommand.setCredentialsProvider(it) }
        finishPush(repository, evaluatePushResult(pushCommand.call()))
    }

/**
 * Pushes a specific local branch to its remote, regardless of which branch is currently
 * checked out — unlike [pushImpl], which (via JGit's default push behavior) always targets
 * HEAD. Uses an explicit refspec so a branch other than the current one can be published
 * without first switching to it.
 */
internal suspend fun GitRepository.pushBranchImpl(repository: Repository, branchName: String): GitResult<Unit> =
    withGit(repository, onError = ::classifyNetworkException) { git ->
        val refSpec = RefSpec("refs/heads/$branchName:refs/heads/$branchName")
        val pushCommand = git.push().setRefSpecs(refSpec)
        originCredentials(git)?.let { pushCommand.setCredentialsProvider(it) }
        finishPush(repository, evaluatePushResult(pushCommand.call()))
    }

internal suspend fun GitRepository.pushWithProgressImpl(
    repository: Repository,
    onProgress: (SyncProgress) -> Unit
): GitResult<Unit> = withGit(repository, onError = ::classifyNetworkException) { git ->
    val pushCommand = git.push()
    originCredentials(git)?.let { pushCommand.setCredentialsProvider(it) }
    pushCommand.setProgressMonitor(JGitProgressMonitor(onProgress))
    finishPush(repository, evaluatePushResult(pushCommand.call()))
}

private suspend fun GitRepository.finishPush(repository: Repository, pushResult: GitResult<Unit>): GitResult<Unit> {
    if (pushResult is GitResult.Success) refreshRepository(repository)
    return pushResult
}

internal suspend fun GitRepository.resetToCommitImpl(repository: Repository, commitHash: String, mode: ResetMode): GitResult<Unit> =
    withGit(repository) { git ->
        val jgitMode = when (mode) {
            ResetMode.SOFT -> ResetCommand.ResetType.SOFT
            ResetMode.MIXED -> ResetCommand.ResetType.MIXED
            ResetMode.HARD -> ResetCommand.ResetType.HARD
        }
        git.reset().setRef(commitHash).setMode(jgitMode).call()
        refreshRepository(repository)
        GitResult.Success(Unit)
    }

internal suspend fun GitRepository.createTagImpl(
    repository: Repository, tagName: String, commitHash: String, force: Boolean
): GitResult<Unit> = withGit(repository) { git ->
    val existing = git.tagList().call().any { it.name.removePrefix("refs/tags/") == tagName }
    if (existing && !force) return@withGit GitResult.Failure.TagAlreadyExists(tagName, text(R.string.git_error_tag_exists, tagName))

    val objectId = git.repository.resolve(commitHash)
        ?: return@withGit GitResult.Failure.Generic(text(R.string.git_error_cannot_resolve_commit, commitHash))
    val revObject = RevWalk(git.repository).use { revWalk -> revWalk.parseAny(objectId) }
    git.tag().setName(tagName).setObjectId(revObject).setAnnotated(false).setForceUpdate(force).call()
    refreshRepository(repository)
    GitResult.Success(Unit)
}

internal suspend fun GitRepository.deleteTagImpl(repository: Repository, tagName: String): GitResult<Unit> =
    withGit(repository) { git ->
        git.tagDelete().setTags(tagName).call()
        refreshRepository(repository)
        GitResult.Success(Unit)
    }

internal suspend fun GitRepository.getTagsForCommitImpl(repository: Repository, commitHash: String): GitResult<List<String>> =
    withGit(repository) { git -> GitResult.Success(getTagsForCommit(git, commitHash)) }

internal suspend fun GitRepository.cherryPickCommitImpl(repository: Repository, commitHash: String): GitResult<Unit> =
    withGit(repository) { git ->
        val objectId = git.repository.resolve(commitHash)
            ?: return@withGit GitResult.Failure.Generic(text(R.string.git_error_cannot_resolve_commit, commitHash))
        if (!applyCommitIdentity(git)) return@withGit identityRequired()
        val result = git.cherryPick().include(objectId).call()
        when (result.status) {
            CherryPickResult.CherryPickStatus.OK -> {
                refreshRepository(repository)
                GitResult.Success(Unit)
            }
            CherryPickResult.CherryPickStatus.CONFLICTING -> {
                val paths = result.failingPaths?.keys?.toList() ?: emptyList()
                GitResult.Failure.Conflict(
                    message = text(R.string.git_conflicts_cherry_pick, conflictPathList(paths)),
                    paths = paths
                )
            }
            else -> GitResult.Failure.Generic(text(R.string.git_error_cherry_pick_failed, result.status))
        }
    }

internal suspend fun GitRepository.mergeCommitIntoCurrentBranchImpl(repository: Repository, commitHash: String): GitResult<Unit> =
    withGit(repository) { git ->
        val objectId = git.repository.resolve(commitHash)
            ?: return@withGit GitResult.Failure.Generic(text(R.string.git_error_cannot_resolve_commit, commitHash))
        if (!applyCommitIdentity(git)) return@withGit identityRequired()
        interpretMergeResult(git.merge().include(objectId).setCommit(true).call(), repository)
    }

// ---------------------------------------------------------------------------
// Merge / rebase a branch into the current branch
// ---------------------------------------------------------------------------

internal suspend fun GitRepository.mergeBranchImpl(repository: Repository, branchName: String): GitResult<Unit> =
    withGit(repository) { git ->
        if (!applyCommitIdentity(git)) return@withGit identityRequired()
        val mergeCommand = git.merge().setCommit(true)
        // Prefer a named ref so the merge commit message references the branch.
        val ref = git.repository.findRef(branchName)
        if (ref != null) {
            mergeCommand.include(ref)
        } else {
            val objectId = git.repository.resolve(branchName)
                ?: return@withGit GitResult.Failure.Generic(text(R.string.git_error_cannot_resolve_branch, branchName))
            mergeCommand.include(objectId)
        }
        interpretMergeResult(mergeCommand.call(), repository)
    }

private suspend fun GitRepository.interpretMergeResult(result: MergeResult, repository: Repository): GitResult<Unit> =
    when (result.mergeStatus) {
        MergeResult.MergeStatus.FAST_FORWARD,
        MergeResult.MergeStatus.ALREADY_UP_TO_DATE,
        MergeResult.MergeStatus.MERGED,
        MergeResult.MergeStatus.MERGED_NOT_COMMITTED -> {
            refreshRepository(repository)
            GitResult.Success(Unit)
        }
        MergeResult.MergeStatus.CONFLICTING -> {
            val paths = result.conflicts?.keys?.toList() ?: emptyList()
            GitResult.Failure.Conflict(
                message = text(R.string.git_conflicts_merge, conflictPathList(paths)),
                paths = paths
            )
        }
        else -> GitResult.Failure.Generic(text(R.string.git_error_merge_failed, result.mergeStatus))
    }

internal suspend fun GitRepository.abortMergeImpl(repository: Repository): GitResult<Unit> =
    withGit(repository) { git ->
        val jRepo = git.repository
        val headId = jRepo.resolve(Constants.HEAD)
            ?: return@withGit GitResult.Failure.Generic(text(R.string.git_error_cannot_resolve_head))
        val mergeHeadId = jRepo.readMergeHeads()?.firstOrNull()
        val cherryPickHeadId = jRepo.readCherryPickHead()
        val incomingPaths = when {
            mergeHeadId != null -> pathsChangedSinceMergeBase(jRepo, headId, mergeHeadId)
            cherryPickHeadId != null -> pathsChangedByCommit(jRepo, cherryPickHeadId)
            else -> return@withGit GitResult.Failure.Generic(text(R.string.git_error_nothing_to_abort))
        }

        // JGit has no "merge --abort", and a hard reset would also wipe uncommitted changes
        // in files the merge never touched (JGit allows merging with such files dirty).
        // Only paths the incoming side changed relative to its base (the merge base, or the
        // picked commit's parent) can have been modified, and of those only the ones whose
        // index now differs from HEAD.
        val status = git.status().call()
        val indexChanged = status.added + status.changed + status.removed + status.conflicting
        val mergedPaths = incomingPaths.filter { it in indexChanged }
        val headTree = RevWalk(jRepo).use { walk -> walk.parseCommit(headId).tree }
        val (pathsInHead, pathsAddedByMerge) = mergedPaths.partition { path ->
            TreeWalk.forPath(jRepo, path, headTree)?.use { true } ?: false
        }

        if (pathsInHead.isNotEmpty()) {
            git.reset().apply { pathsInHead.forEach { addPath(it) } }.call()
            git.checkout().apply { pathsInHead.forEach { addPath(it) } }.call()
        }
        if (pathsAddedByMerge.isNotEmpty()) {
            git.rm().setCached(true).apply { pathsAddedByMerge.forEach { addFilepattern(it) } }.call()
            pathsAddedByMerge.forEach { File(jRepo.workTree, it).delete() }
        }
        jRepo.writeMergeHeads(null)
        jRepo.writeCherryPickHead(null)
        jRepo.writeMergeCommitMsg(null)
        refreshRepository(repository)
        GitResult.Success(Unit)
    }

private fun pathsChangedSinceMergeBase(
    repository: org.eclipse.jgit.lib.Repository,
    headId: ObjectId,
    mergeHeadId: ObjectId
): Set<String> {
    val paths = mutableSetOf<String>()
    RevWalk(repository).use { walk ->
        val theirs = walk.parseCommit(mergeHeadId)
        walk.revFilter = RevFilter.MERGE_BASE
        walk.markStart(walk.parseCommit(headId))
        walk.markStart(theirs)
        val baseTrees = walk.map { it.tree }.ifEmpty { listOf(null) }
        for (baseTree in baseTrees) paths += pathsChangedBetween(repository, baseTree, theirs.tree)
    }
    return paths
}

private fun pathsChangedByCommit(repository: org.eclipse.jgit.lib.Repository, commitId: ObjectId): Set<String> =
    RevWalk(repository).use { walk ->
        val commit = walk.parseCommit(commitId)
        val parentTree = commit.parents.firstOrNull()?.let { walk.parseCommit(it).tree }
        pathsChangedBetween(repository, parentTree, commit.tree)
    }

private fun pathsChangedBetween(
    repository: org.eclipse.jgit.lib.Repository,
    fromTree: RevTree?,
    toTree: RevTree
): Set<String> {
    val paths = mutableSetOf<String>()
    TreeWalk(repository).use { treeWalk ->
        if (fromTree != null) treeWalk.addTree(fromTree) else treeWalk.addTree(EmptyTreeIterator())
        treeWalk.addTree(toTree)
        treeWalk.isRecursive = true
        treeWalk.filter = TreeFilter.ANY_DIFF
        while (treeWalk.next()) paths += treeWalk.pathString
    }
    return paths
}

internal suspend fun GitRepository.rebaseCurrentOntoImpl(repository: Repository, branchName: String): GitResult<Unit> =
    withGit(repository) { git ->
        if (!applyCommitIdentity(git)) return@withGit identityRequired()
        interpretRebaseResult(git.rebase().setUpstream(branchName).call(), repository, git)
    }

internal suspend fun GitRepository.rebaseContinueImpl(repository: Repository): GitResult<Unit> =
    withGit(repository) { git ->
        if (!applyCommitIdentity(git)) return@withGit identityRequired()
        interpretRebaseResult(git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call(), repository, git)
    }

internal suspend fun GitRepository.rebaseAbortImpl(repository: Repository): GitResult<Unit> =
    withGit(repository) { git ->
        git.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
        refreshRepository(repository)
        GitResult.Success(Unit)
    }

/** Maps a [RebaseResult] to a [GitResult], refreshing repo metadata on a clean finish. */
private suspend fun GitRepository.interpretRebaseResult(
    result: RebaseResult,
    repository: Repository,
    git: Git
): GitResult<Unit> {
    return when (result.status) {
        RebaseResult.Status.OK,
        RebaseResult.Status.UP_TO_DATE,
        RebaseResult.Status.FAST_FORWARD -> {
            refreshRepository(repository)
            GitResult.Success(Unit)
        }
        RebaseResult.Status.STOPPED,
        RebaseResult.Status.CONFLICTS -> {
            // Read conflicting paths from the working tree rather than RebaseResult, keeping
            // the source of truth consistent with the merge path and ChangesScreen.
            val paths = runCatching { git.status().call().conflicting.toList() }.getOrDefault(emptyList())
            GitResult.Failure.Conflict(
                message = text(R.string.git_conflicts_rebase, conflictPathList(paths)),
                paths = paths
            )
        }
        RebaseResult.Status.UNCOMMITTED_CHANGES ->
            GitResult.Failure.Generic(text(R.string.git_error_rebase_uncommitted))
        else -> GitResult.Failure.Generic(text(R.string.git_error_rebase_failed, result.status))
    }
}

private fun mapRepositoryState(state: RepositoryState): RepoOperationState = when (state) {
    RepositoryState.MERGING, RepositoryState.MERGING_RESOLVED -> RepoOperationState.MERGING
    RepositoryState.REBASING, RepositoryState.REBASING_REBASING,
    RepositoryState.REBASING_MERGE, RepositoryState.REBASING_INTERACTIVE -> RepoOperationState.REBASING
    RepositoryState.SAFE, RepositoryState.BARE -> RepoOperationState.NONE
    else -> RepoOperationState.OTHER
}

internal suspend fun GitRepository.getRepositoryStateImpl(repository: Repository): RepoOperationState =
    withGit(repository) { git -> GitResult.Success(mapRepositoryState(git.repository.repositoryState)) }
        .getOrNull() ?: RepoOperationState.NONE

internal suspend fun GitRepository.getConflictInfoImpl(repository: Repository): GitResult<ConflictInfo> =
    withGit(repository) { git ->
        val jRepo = git.repository
        val state = mapRepositoryState(jRepo.repositoryState)
        if (state != RepoOperationState.MERGING && state != RepoOperationState.REBASING) {
            return@withGit GitResult.Success(ConflictInfo(operation = state))
        }
        val headHash = jRepo.resolve(Constants.HEAD)?.name
        val conflictPaths = git.status().call().conflicting.toList()

        val info = if (state == RepoOperationState.MERGING) {
            val mergeHead = jRepo.readMergeHeads()?.firstOrNull()?.name
            val mergeMessage = jRepo.readMergeCommitMsg()
            val source = mergeMessage
                ?.let { Regex("Merge (?:remote-tracking )?branch '([^']+)'").find(it)?.groupValues?.get(1) }
                ?: mergeHead?.take(7)
            ConflictInfo(
                operation = state,
                sourceLabel = source,
                targetLabel = jRepo.branch,
                sourceHash = mergeHead,
                targetHash = headHash,
                conflictPaths = conflictPaths
            )
        } else {
            // Rebase state lives in .git/rebase-merge/ (interactive/merge) or .git/rebase-apply/.
            val stateDir = listOf("rebase-merge", "rebase-apply")
                .map { File(jRepo.directory, it) }
                .firstOrNull { it.isDirectory }
            val headName = stateDir
                ?.let { File(it, "head-name").takeIf(File::exists)?.readText()?.trim() }
                ?.removePrefix("refs/heads/")
            val ontoHash = stateDir
                ?.let { File(it, "onto").takeIf(File::exists)?.readText()?.trim() }
            ConflictInfo(
                operation = state,
                sourceLabel = headName ?: jRepo.branch,
                targetLabel = ontoHash?.take(7),
                sourceHash = headHash,
                targetHash = ontoHash,
                conflictPaths = conflictPaths
            )
        }
        GitResult.Success(info)
    }
