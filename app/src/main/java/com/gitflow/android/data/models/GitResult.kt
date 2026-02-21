package com.gitflow.android.data.models

/**
 * Unified result type for all Git repository operations.
 * Replaces the inconsistent mix of Result<T>, Boolean, PullResult, PushResult.
 *
 * Usage:
 *   when (val result = gitRepository.commit(repo, message)) {
 *       is GitResult.Success  -> showToast("Committed")
 *       is GitResult.Failure.NoStagedChanges -> showToast("Nothing to commit")
 *       is GitResult.Failure.Conflict        -> showConflictList(result.paths)
 *       is GitResult.Failure                 -> showToast(result.message)
 *   }
 */
sealed class GitResult<out T> {

    data class Success<out T>(val data: T) : GitResult<T>()

    sealed class Failure : GitResult<Nothing>() {
        abstract val message: String

        /** General-purpose failure wrapping any exception or plain message. */
        data class Generic(
            override val message: String,
            val cause: Throwable? = null
        ) : Failure()

        /** Operation produced merge/cherry-pick conflicts. */
        data class Conflict(
            override val message: String,
            val paths: List<String> = emptyList()
        ) : Failure()

        /** Commit attempted with nothing staged. */
        data class NoStagedChanges(
            override val message: String = "No staged changes to commit"
        ) : Failure()

        /** Tag creation failed because the tag already exists. */
        data class TagAlreadyExists(val tagName: String) : Failure() {
            override val message: String get() = "Tag '$tagName' already exists"
        }

        /** Operation was cancelled by the user. */
        data class Cancelled(
            override val message: String = "Operation cancelled"
        ) : Failure()
    }
}

// ---------------------------------------------------------------------------
// Progress reporting for long-running network operations (push/pull)
// ---------------------------------------------------------------------------

/**
 * Progress snapshot for a JGit network operation (push or pull).
 * [totalWork] == 0 means the total is unknown (indeterminate progress).
 */
data class SyncProgress(
    val task: String,
    val done: Int,
    val totalWork: Int
) {
    /** 0..1 fraction, or null when progress is indeterminate. */
    val fraction: Float? get() = if (totalWork > 0) done.toFloat() / totalWork else null
}

// ---------------------------------------------------------------------------
// Convenience extensions
// ---------------------------------------------------------------------------

fun <T> GitResult<T>.isSuccess(): Boolean = this is GitResult.Success
fun <T> GitResult<T>.getOrNull(): T? = (this as? GitResult.Success)?.data

fun <T, R> GitResult<T>.map(transform: (T) -> R): GitResult<R> = when (this) {
    is GitResult.Success  -> GitResult.Success(transform(data))
    is GitResult.Failure  -> this
}

fun <T> GitResult<T>.onSuccess(action: (T) -> Unit): GitResult<T> {
    if (this is GitResult.Success) action(data)
    return this
}

fun <T> GitResult<T>.onFailure(action: (GitResult.Failure) -> Unit): GitResult<T> {
    if (this is GitResult.Failure) action(this)
    return this
}

/** Convenience builder — wraps a throwing block into GitResult. */
inline fun <T> gitResultOf(block: () -> T): GitResult<T> = try {
    GitResult.Success(block())
} catch (e: Exception) {
    GitResult.Failure.Generic(e.message ?: "Unknown error", e)
}
