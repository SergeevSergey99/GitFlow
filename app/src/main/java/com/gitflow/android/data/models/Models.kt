package com.gitflow.android.data.models

import kotlinx.serialization.Serializable

// Existing models
@Serializable
data class Repository(
    val id: String,
    val name: String,
    val path: String,
    val lastUpdated: Long,
    val currentBranch: String,
    val totalBranches: Int = 1, // Добавляем информацию о количестве веток
    val hasRemoteOrigin: Boolean = false // Добавляем информацию о наличии remote origin
)

@Serializable
data class Commit(
    val hash: String,
    val message: String,
    val author: String,
    val email: String,
    val timestamp: Long,
    val parents: List<String>,
    val branch: String? = null,
    val tags: List<String> = emptyList(),
    val description: String = "",
    val branchHeads: List<String> = emptyList(), // Список веток, где этот коммит является HEAD
    val isMergeCommit: Boolean = false // Является ли коммит merge коммитом
)

@Serializable
data class Branch(
    val name: String,
    val isLocal: Boolean,
    val lastCommitHash: String,
    val ahead: Int = 0,
    val behind: Int = 0
)

@Serializable
data class FileChange(
    val path: String,
    val status: ChangeStatus,
    val stage: ChangeStage,
    val additions: Int = 0,
    val deletions: Int = 0
)

@Serializable
enum class ChangeStatus {
    ADDED, MODIFIED, DELETED, RENAMED, COPIED, UNTRACKED
}

@Serializable
enum class ChangeStage {
    STAGED, UNSTAGED
}

// New models for diff viewer
@Serializable
data class FileDiff(
    val path: String,
    val oldPath: String? = null,
    val status: FileStatus,
    val additions: Int,
    val deletions: Int,
    val hunks: List<DiffHunk>
)

@Serializable
data class DiffHunk(
    val header: String,
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<DiffLine>
)

@Serializable
data class DiffLine(
    val type: LineType,
    val content: String,
    val lineNumber: Int? = null,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null
)

@Serializable
enum class FileStatus {
    ADDED, MODIFIED, DELETED, RENAMED
}

@Serializable
enum class LineType {
    ADDED, DELETED, CONTEXT
}

@Serializable
data class User(
    val name: String,
    val email: String,
    val username: String
)

// Models for commit file tree
@Serializable
data class FileTreeNode(
    val name: String,
    val path: String,
    val type: FileTreeNodeType,
    val size: Long? = null,
    val children: List<FileTreeNode> = emptyList(),
    val lastModified: Long? = null
)

@Serializable
enum class FileTreeNodeType {
    FILE, DIRECTORY
}

@Serializable
data class CommitFileInfo(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val content: String? = null
)

// Результаты Git операций
data class PullResult(
    val success: Boolean,
    val newCommits: Int,
    val conflicts: List<String>
)

data class PushResult(
    val success: Boolean,
    val pushedCommits: Int,
    val message: String
)

// OAuth и авторизация модели
@Serializable
data class OAuthToken(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val scope: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long? = null
)

@Serializable
data class GitUser(
    val id: Long,
    val login: String,
    val name: String?,
    val email: String?,
    val avatarUrl: String?,
    val provider: GitProvider
)

@Serializable
enum class GitProvider {
    GITHUB, GITLAB
}

@Serializable
data class GitRemoteRepository(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val private: Boolean,
    val cloneUrl: String,
    val sshUrl: String,
    val htmlUrl: String,
    val defaultBranch: String,
    val owner: GitUser,
    val provider: GitProvider,
    val updatedAt: String,
    val approximateSizeBytes: Long? = null
)

// Результат авторизации
data class AuthResult(
    val success: Boolean,
    val user: GitUser? = null,
    val token: OAuthToken? = null,
    val error: String? = null
)
