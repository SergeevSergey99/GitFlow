package com.gitflow.android.data.models

// Existing models
data class Repository(
    val id: String,
    val name: String,
    val path: String,
    val lastUpdated: Long,
    val currentBranch: String
)

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

data class Branch(
    val name: String,
    val isLocal: Boolean,
    val lastCommitHash: String,
    val ahead: Int = 0,
    val behind: Int = 0
)

data class FileChange(
    val path: String,
    val status: ChangeStatus,
    val additions: Int = 0,
    val deletions: Int = 0
)

enum class ChangeStatus {
    ADDED, MODIFIED, DELETED, RENAMED, COPIED, UNTRACKED
}

// New models for diff viewer
data class FileDiff(
    val path: String,
    val oldPath: String? = null,
    val status: FileStatus,
    val additions: Int,
    val deletions: Int,
    val hunks: List<DiffHunk>
)

data class DiffHunk(
    val header: String,
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<DiffLine>
)

data class DiffLine(
    val type: LineType,
    val content: String,
    val lineNumber: Int? = null,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null
)

enum class FileStatus {
    ADDED, MODIFIED, DELETED, RENAMED
}

enum class LineType {
    ADDED, DELETED, CONTEXT
}

data class User(
    val name: String,
    val email: String,
    val username: String
)