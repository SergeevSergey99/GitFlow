package com.gitflow.android.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gitflow.android.R
import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.data.repository.CloneProgress
import com.gitflow.android.data.repository.CloneProgressCallback
import com.gitflow.android.data.repository.CloneProgressTracker
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.data.settings.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class CloneRepositoryService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var gitRepository: GitRepository

    private var cloneJob: Job? = null
    private var progressCallback: CloneProgressCallback? = null
    private var currentCloneKey: String? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        gitRepository = GitRepository(applicationContext)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CLONE -> handleStartClone(intent)
            ACTION_CANCEL_CLONE -> handleCancelClone()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cloneJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun handleStartClone(intent: Intent) {
        if (cloneJob?.isActive == true) {
            android.util.Log.w(TAG, "Клонирование уже выполняется, новый запрос проигнорирован")
            return
        }

        val cloneUrl = intent.getStringExtra(EXTRA_CLONE_URL)
        val localPath = intent.getStringExtra(EXTRA_LOCAL_PATH)
        val repoName = intent.getStringExtra(EXTRA_REPO_NAME)
        val repoFullName = intent.getStringExtra(EXTRA_REPO_FULL_NAME)
        val customDestination = intent.getStringExtra(EXTRA_CUSTOM_DESTINATION)
        val approximateSize = intent.getLongExtra(EXTRA_REPO_SIZE, -1L).takeIf { intent.hasExtra(EXTRA_REPO_SIZE) && it >= 0 }

        if (cloneUrl.isNullOrBlank() || localPath.isNullOrBlank() || repoName.isNullOrBlank() || repoFullName.isNullOrBlank()) {
            android.util.Log.e(TAG, "Недостаточно данных для запуска клонирования")
            stopSelf()
            return
        }

        val cloneKey = customDestination?.takeIf { it.isNotBlank() } ?: localPath
        currentCloneKey = cloneKey
        CloneProgressTracker.registerClone(
            key = cloneKey,
            repoName = repoName,
            repoFullName = repoFullName,
            approximateSize = approximateSize
        )

        startCloneJob(
            cloneKey = cloneKey,
            cloneUrl = cloneUrl,
            localPath = localPath,
            customDestination = customDestination,
            repoName = repoName,
            repoFullName = repoFullName,
            approximateSize = approximateSize
        )
    }

    private fun startCloneJob(
        cloneKey: String,
        cloneUrl: String,
        localPath: String,
        customDestination: String?,
        repoName: String,
        repoFullName: String,
        approximateSize: Long?
    ) {
        if (!hasNotificationPermission()) {
            android.util.Log.w(TAG, "Нет разрешения POST_NOTIFICATIONS, завершаем сервис")
            stopSelf()
            return
        }

        val progressBuilder = createProgressNotificationBuilder(repoFullName)
        val initialNotification = progressBuilder
            .setContentText(getString(R.string.notification_clone_preparing))
            .setProgress(0, 0, true)
            .build()

        startForeground(NOTIFICATION_ID, initialNotification)

        val callback = CloneProgressCallback(cloneKey)
        progressCallback = callback

        cloneJob = serviceScope.launch {
            val progressUpdates = launch {
                callback.progress.collect { progress ->
                    updateProgressNotification(progressBuilder, repoFullName, progress)
                }
            }

            val result = withContext(Dispatchers.IO) {
                gitRepository.cloneRepository(
                    url = cloneUrl,
                    localPath = localPath,
                    customDestination = customDestination,
                    progressCallback = callback
                )
            }

            progressUpdates.cancelAndJoin()
            progressCallback = null

            result.fold(
                onSuccess = {
                    CloneProgressTracker.markCompleted(cloneKey)
                },
                onFailure = { error ->
                    val message = error.message
                    if (message?.contains("cancel", ignoreCase = true) == true) {
                        CloneProgressTracker.markCancelled(cloneKey, message)
                    } else {
                        CloneProgressTracker.markFailed(cloneKey, message)
                    }
                }
            )

            val completionNotification = buildCompletionNotification(repoFullName, repoName, result, approximateSize)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifySafely(NOTIFICATION_ID, completionNotification)
            currentCloneKey = null
            stopSelf()
        }
    }

    private fun buildCompletionNotification(
        repoFullName: String,
        repoName: String,
        result: Result<com.gitflow.android.data.models.Repository>,
        approximateSize: Long?
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clone_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        result.fold(
            onSuccess = {
                val sizeText = approximateSize?.let { formatSizeForNotification(it) }
                val contentText = if (sizeText != null) {
                    getString(R.string.notification_clone_completed_with_size, repoName, sizeText)
                } else {
                    getString(R.string.notification_clone_completed_detail, repoName)
                }
                builder
                    .setContentTitle(getString(R.string.notification_clone_completed))
                    .setContentText(contentText)
            },
            onFailure = { error ->
                val message = error.message ?: getString(R.string.notification_clone_failed_generic)
                if (message.contains("cancel", ignoreCase = true)) {
                    builder
                        .setContentTitle(getString(R.string.notification_clone_cancelled))
                        .setContentText(getString(R.string.notification_clone_cancelled_detail, repoFullName))
                } else {
                    builder
                        .setContentTitle(getString(R.string.notification_clone_failed))
                        .setContentText(getString(R.string.notification_clone_failed_detail, repoFullName, message))
                }
            }
        )

        return builder.build()
    }

    private fun createProgressNotificationBuilder(repoFullName: String): NotificationCompat.Builder {
        val cancelPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_CANCEL,
            Intent(this, CloneRepositoryService::class.java).apply { action = ACTION_CANCEL_CLONE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clone_notification)
            .setContentTitle(getString(R.string.notification_clone_in_progress, repoFullName))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_clone_notification,
                getString(R.string.notification_clone_cancel),
                cancelPendingIntent
            )
    }

    private fun updateProgressNotification(
        builder: NotificationCompat.Builder,
        repoFullName: String,
        progress: CloneProgress
    ) {
        val percent = (progress.progress * 100).roundToInt().coerceIn(0, 100)
        val isIndeterminate = progress.total <= 0
        val stageText = progress.stage.ifBlank { getString(R.string.notification_clone_in_progress_default) }

        builder
            .setContentTitle(getString(R.string.notification_clone_in_progress, repoFullName))
            .setContentText(stageText)
            .setProgress(100, if (isIndeterminate) 0 else percent, isIndeterminate)
            .setOngoing(true)

        if (progress.estimatedTimeRemaining.isNotEmpty()) {
            builder.setSubText(getString(R.string.notification_clone_eta, progress.estimatedTimeRemaining))
        } else {
            builder.setSubText(null)
        }

        notifySafely(NOTIFICATION_ID, builder.build())
    }

    private fun handleCancelClone() {
        android.util.Log.d(TAG, "Отмена операции клонирования по запросу пользователя")
        progressCallback?.cancel()
        currentCloneKey?.let { CloneProgressTracker.markCancelled(it) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_clone_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_clone_description)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifySafely(id: Int, notification: Notification) {
        if (hasNotificationPermission()) {
            try {
                notificationManager.notify(id, notification)
            } catch (security: SecurityException) {
                android.util.Log.w(TAG, "Не удалось показать уведомление из-за SecurityException", security)
            }
        } else {
            android.util.Log.w(TAG, "Пропускаем уведомление: нет разрешения POST_NOTIFICATIONS")
        }
    }

    companion object {
        private const val TAG = "CloneRepoService"
        private const val ACTION_START_CLONE = "com.gitflow.android.action.START_CLONE"
        private const val ACTION_CANCEL_CLONE = "com.gitflow.android.action.CANCEL_CLONE"
        private const val EXTRA_CLONE_URL = "extra_clone_url"
        private const val EXTRA_LOCAL_PATH = "extra_local_path"
        private const val EXTRA_CUSTOM_DESTINATION = "extra_custom_destination"
        private const val EXTRA_REPO_NAME = "extra_repo_name"
        private const val EXTRA_REPO_FULL_NAME = "extra_repo_full_name"
        private const val EXTRA_REPO_SIZE = "extra_repo_size"
        private const val CHANNEL_ID = "clone_repository_channel"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_CANCEL = 2001

        fun start(
            context: Context,
            repository: GitRemoteRepository,
            cloneUrl: String,
            localPath: String,
            customDestination: String? = null
        ): Boolean {
            return start(
                context = context,
                repoName = repository.name,
                repoFullName = repository.fullName,
                cloneUrl = cloneUrl,
                localPath = localPath,
                approximateSize = repository.approximateSizeBytes,
                customDestination = customDestination
            )
        }

        fun start(
            context: Context,
            repoName: String,
            repoFullName: String,
            cloneUrl: String,
            localPath: String,
            approximateSize: Long? = null,
            customDestination: String? = null
        ): Boolean {
            val appContext = context.applicationContext
            if (!isNetworkAllowed(appContext)) {
                showWifiOnlyRestriction(appContext)
                return false
            }

            val cloneKey = customDestination?.takeIf { it.isNotBlank() } ?: localPath
            CloneProgressTracker.registerClone(
                key = cloneKey,
                repoName = repoName,
                repoFullName = repoFullName,
                approximateSize = approximateSize
            )

            val intent = Intent(context, CloneRepositoryService::class.java).apply {
                action = ACTION_START_CLONE
                putExtra(EXTRA_CLONE_URL, cloneUrl)
                putExtra(EXTRA_LOCAL_PATH, localPath)
                putExtra(EXTRA_REPO_NAME, repoName)
                putExtra(EXTRA_REPO_FULL_NAME, repoFullName)
                approximateSize?.let { putExtra(EXTRA_REPO_SIZE, it) }
                customDestination?.let { putExtra(EXTRA_CUSTOM_DESTINATION, it) }
            }
            ContextCompat.startForegroundService(context, intent)
            return true
        }

        fun cancel(context: Context) {
            val intent = Intent(context, CloneRepositoryService::class.java).apply {
                action = ACTION_CANCEL_CLONE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        private fun isNetworkAllowed(context: Context): Boolean {
            val settingsManager = AppSettingsManager(context)
            if (!settingsManager.isWifiOnlyDownloadsEnabled()) {
                return true
            }
            return isWifiConnection(context)
        }

        private fun isWifiConnection(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                val networkInfo = connectivityManager.activeNetworkInfo ?: return false
                networkInfo.type == ConnectivityManager.TYPE_WIFI ||
                    networkInfo.type == ConnectivityManager.TYPE_ETHERNET
            }
        }

        private fun showWifiOnlyRestriction(context: Context) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    context.getString(R.string.clone_wifi_only_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun formatSizeForNotification(sizeBytes: Long): String {
            val megabytes = sizeBytes / 1024.0 / 1024.0
            return if (megabytes >= 1024) {
                String.format(Locale.getDefault(), "%.1f ГБ", megabytes / 1024.0)
            } else {
                String.format(Locale.getDefault(), "%.1f МБ", megabytes)
            }
        }
    }
}
