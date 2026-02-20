package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import kotlinx.coroutines.flow.Flow

interface IGitRepository {
    fun getRepositoriesFlow(): Flow<List<Repository>>
    suspend fun getRepositories(): List<Repository>
    suspend fun addRepository(path: String): Result<Repository>
    suspend fun createRepository(name: String, localPath: String): Result<Repository>
    suspend fun cloneRepository(
        url: String,
        localPath: String,
        customDestination: String? = null,
        progressCallback: CloneProgressCallback? = null
    ): Result<Repository>
    suspend fun removeRepository(repositoryId: String)
    suspend fun removeRepositoryWithFiles(repositoryId: String): Result<Unit>
    suspend fun updateRepository(repository: Repository)
    suspend fun refreshRepository(repository: Repository): Repository?
    suspend fun getCommits(repository: Repository, page: Int = 0, pageSize: Int = 50): List<Commit>
    suspend fun getChangedFiles(repository: Repository): List<FileChange>
    suspend fun stageFile(repository: Repository, file: FileChange): Result<Unit>
    suspend fun unstageFile(repository: Repository, filePath: String): Result<Unit>
    suspend fun stageAll(repository: Repository): Result<Unit>
    suspend fun getMergeConflicts(repository: Repository): List<MergeConflict>
    suspend fun getMergeConflict(repository: Repository, path: String): MergeConflict?
    suspend fun resolveConflict(repository: Repository, path: String, strategy: ConflictResolutionStrategy): Result<Unit>
    suspend fun resolveConflictWithContent(repository: Repository, path: String, resolvedContent: String): Result<Unit>
    suspend fun hardResetToCommit(repository: Repository, commitHash: String): Result<Unit>
    suspend fun createTag(repository: Repository, tagName: String, commitHash: String, force: Boolean = false): Result<Unit>
    suspend fun deleteTag(repository: Repository, tagName: String): Result<Unit>
    suspend fun getTagsForCommit(repository: Repository, commitHash: String): List<String>
    suspend fun cherryPickCommit(repository: Repository, commitHash: String): Result<Unit>
    suspend fun mergeCommitIntoCurrentBranch(repository: Repository, commitHash: String): Result<Unit>
    suspend fun commit(repository: Repository, message: String): Result<Unit>
    suspend fun getCommitDiffs(commit: Commit, repository: Repository): List<FileDiff>
    suspend fun getCommitDiffs(commit: Commit): List<FileDiff>
    suspend fun getBranches(repository: Repository): List<Branch>
    suspend fun pull(repository: Repository): PullResult
    suspend fun push(repository: Repository): PushResult
    suspend fun getCommitFileTree(commit: Commit, repository: Repository): FileTreeNode
    suspend fun getCommitFileTree(commit: Commit): FileTreeNode
    suspend fun getFileContent(commit: Commit, filePath: String, repository: Repository): String?
    suspend fun getFileContent(commit: Commit, filePath: String): String?
    suspend fun getFileContentBytes(commit: Commit, filePath: String, repository: Repository): ByteArray?
    suspend fun getFileContentBytes(commit: Commit, filePath: String): ByteArray?
    suspend fun restoreFileToCommit(commit: Commit, filePath: String, repository: Repository): Boolean
    suspend fun restoreFileToCommit(commit: Commit, filePath: String): Boolean
    suspend fun restoreFileToParentCommit(commit: Commit, filePath: String, repository: Repository): Boolean
    suspend fun restoreFileToParentCommit(commit: Commit, filePath: String): Boolean
    suspend fun getFileHistory(commit: Commit, filePath: String, repository: Repository): List<Commit>
    suspend fun getFileHistory(commit: Commit, filePath: String): List<Commit>
}
