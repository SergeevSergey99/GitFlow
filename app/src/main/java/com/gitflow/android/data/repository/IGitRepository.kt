package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import kotlinx.coroutines.flow.Flow

interface IGitRepository {
    fun getRepositoriesFlow(): Flow<List<Repository>>
    suspend fun getRepositories(): List<Repository>
    suspend fun addRepository(path: String): GitResult<Repository>
    suspend fun createRepository(name: String, localPath: String): GitResult<Repository>
    suspend fun cloneRepository(
        url: String,
        localPath: String,
        customDestination: String? = null,
        progressCallback: CloneProgressCallback? = null
    ): GitResult<Repository>
    suspend fun removeRepository(repositoryId: String)
    suspend fun removeRepositoryWithFiles(repositoryId: String): GitResult<Unit>
    suspend fun updateRepository(repository: Repository)
    suspend fun refreshRepository(repository: Repository): Repository?
    suspend fun getCommits(repository: Repository, page: Int = 0, pageSize: Int = 50): List<Commit>
    suspend fun getChangedFiles(repository: Repository): List<FileChange>
    suspend fun getWorkingFileDiff(repository: Repository, filePath: String, stage: ChangeStage): FileDiff?
    suspend fun stageFile(repository: Repository, file: FileChange): GitResult<Unit>
    suspend fun unstageFile(repository: Repository, filePath: String): GitResult<Unit>
    suspend fun discardFileChanges(repository: Repository, filePath: String): GitResult<Unit>
    suspend fun stageAll(repository: Repository): GitResult<Unit>
    suspend fun getMergeConflicts(repository: Repository): List<MergeConflict>
    suspend fun getMergeConflict(repository: Repository, path: String): MergeConflict?
    suspend fun resolveConflict(repository: Repository, path: String, strategy: ConflictResolutionStrategy): GitResult<Unit>
    suspend fun resolveConflictWithContent(repository: Repository, path: String, resolvedContent: String): GitResult<Unit>
    suspend fun hardResetToCommit(repository: Repository, commitHash: String): GitResult<Unit>
    suspend fun createTag(repository: Repository, tagName: String, commitHash: String, force: Boolean = false): GitResult<Unit>
    suspend fun deleteTag(repository: Repository, tagName: String): GitResult<Unit>
    suspend fun getTagsForCommit(repository: Repository, commitHash: String): List<String>
    suspend fun cherryPickCommit(repository: Repository, commitHash: String): GitResult<Unit>
    suspend fun mergeCommitIntoCurrentBranch(repository: Repository, commitHash: String): GitResult<Unit>
    suspend fun commit(repository: Repository, message: String): GitResult<Unit>
    suspend fun amendLastCommit(repository: Repository, message: String): GitResult<Unit>
    suspend fun getLastCommitMessage(repository: Repository): String?
    suspend fun getCommitDiffs(commit: Commit, repository: Repository): List<FileDiff>
    suspend fun getCommitDiffs(commit: Commit): List<FileDiff>
    suspend fun getBranches(repository: Repository): List<Branch>
    suspend fun fetch(repository: Repository): GitResult<Unit>
    suspend fun pull(repository: Repository): GitResult<Unit>
    suspend fun push(repository: Repository): GitResult<Unit>
    suspend fun pushWithProgress(repository: Repository, onProgress: (SyncProgress) -> Unit): GitResult<Unit>
    suspend fun getCommitFileTree(commit: Commit, repository: Repository): FileTreeNode
    suspend fun getCommitFileTree(commit: Commit): FileTreeNode
    suspend fun getFileContent(commit: Commit, filePath: String, repository: Repository): String?
    suspend fun getFileContent(commit: Commit, filePath: String): String?
    suspend fun getFileContentBytes(commit: Commit, filePath: String, repository: Repository): ByteArray?
    suspend fun getFileContentBytes(commit: Commit, filePath: String): ByteArray?
    suspend fun restoreFileToCommit(commit: Commit, filePath: String, repository: Repository): GitResult<Unit>
    suspend fun restoreFileToCommit(commit: Commit, filePath: String): GitResult<Unit>
    suspend fun restoreFileToParentCommit(commit: Commit, filePath: String, repository: Repository): GitResult<Unit>
    suspend fun restoreFileToParentCommit(commit: Commit, filePath: String): GitResult<Unit>
    suspend fun getFileHistory(commit: Commit, filePath: String, repository: Repository): List<Commit>
    suspend fun getFileHistory(commit: Commit, filePath: String): List<Commit>
    suspend fun getFileHistoryForPath(repository: Repository, filePath: String): List<Commit>
    suspend fun stashSave(repository: Repository, message: String = ""): GitResult<Unit>
    suspend fun stashList(repository: Repository): List<StashEntry>
    suspend fun stashApply(repository: Repository, stashIndex: Int): GitResult<Unit>
    suspend fun stashPop(repository: Repository, stashIndex: Int): GitResult<Unit>
    suspend fun stashDrop(repository: Repository, stashIndex: Int): GitResult<Unit>
}
