package com.gitflow.android.data.models

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
    val branch: String? = null
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

data class User(
    val name: String,
    val email: String,
    val username: String
)