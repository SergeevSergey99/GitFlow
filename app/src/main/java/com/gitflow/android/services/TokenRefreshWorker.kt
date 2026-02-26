package com.gitflow.android.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Periodic background worker that proactively refreshes OAuth tokens before they expire.
 * Runs every 30 minutes; skips providers whose tokens are not expired or don't need refresh.
 */
class TokenRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val authManager: AuthManager by inject()

    override suspend fun doWork(): Result {
        var allSucceeded = true

        if (authManager.isAuthenticated(GitProvider.GITLAB)) {
            val ok = runCatching { authManager.refreshGitLabTokenIfNeeded() }.getOrElse { false }
            if (!ok) {
                Timber.w("TokenRefreshWorker: GitLab token refresh failed")
                allSucceeded = false
            }
        }

        if (authManager.isAuthenticated(GitProvider.BITBUCKET)) {
            val ok = runCatching { authManager.refreshBitbucketTokenIfNeeded() }.getOrElse { false }
            if (!ok) {
                Timber.w("TokenRefreshWorker: Bitbucket token refresh failed")
                allSucceeded = false
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }
}
