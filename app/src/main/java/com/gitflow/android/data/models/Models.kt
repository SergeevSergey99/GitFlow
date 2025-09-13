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
    val description: String = ""
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
    val additions: Int = 0,
    val deletions: Int = 0
)

@Serializable
enum class ChangeStatus {
    ADDED, MODIFIED, DELETED, RENAMED, COPIED, UNTRACKED
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
