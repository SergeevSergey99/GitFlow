package com.gitflow.android.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.auth.RefreshResult
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.MainActivity
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

        if (authManager.isLoggedIn(GitProvider.GITLAB)) {
            val result = runCatching { authManager.refreshGitLabTokenIfNeeded() }
                .getOrElse { RefreshResult.NETWORK_ERROR }
            when (result) {
                RefreshResult.OK -> Unit
                RefreshResult.AUTH_EXPIRED -> {
                    Timber.w("TokenRefreshWorker: GitLab refresh token expired")
                    showSessionExpiredNotification(GitProvider.GITLAB)
                }
                RefreshResult.NETWORK_ERROR -> {
                    Timber.w("TokenRefreshWorker: GitLab token refresh failed (network)")
                    allSucceeded = false
                }
            }
        }

        if (authManager.isLoggedIn(GitProvider.BITBUCKET)) {
            val result = runCatching { authManager.refreshBitbucketTokenIfNeeded() }
                .getOrElse { RefreshResult.NETWORK_ERROR }
            when (result) {
                RefreshResult.OK -> Unit
                RefreshResult.AUTH_EXPIRED -> {
                    Timber.w("TokenRefreshWorker: Bitbucket refresh token expired")
                    showSessionExpiredNotification(GitProvider.BITBUCKET)
                }
                RefreshResult.NETWORK_ERROR -> {
                    Timber.w("TokenRefreshWorker: Bitbucket token refresh failed (network)")
                    allSucceeded = false
                }
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }

    private fun showSessionExpiredNotification(provider: GitProvider) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        ensureAuthChannel()

        val contentText = when (provider) {
            GitProvider.GITLAB -> applicationContext.getString(R.string.auth_notification_gitlab_expired)
            GitProvider.BITBUCKET -> applicationContext.getString(R.string.auth_notification_bitbucket_expired)
            else -> return
        }
        val notificationId = when (provider) {
            GitProvider.GITLAB -> 2001
            GitProvider.BITBUCKET -> 2002
            else -> return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, AUTH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clone_notification)
            .setContentTitle(applicationContext.getString(R.string.auth_notification_session_expired_title))
            .setContentText(contentText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot show session-expired notification")
        }
    }

    private fun ensureAuthChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AUTH_CHANNEL_ID,
                applicationContext.getString(R.string.auth_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.auth_notification_channel_description)
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val AUTH_CHANNEL_ID = "auth_alerts"
    }
}
