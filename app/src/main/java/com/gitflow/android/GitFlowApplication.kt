package com.gitflow.android

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gitflow.android.di.appModule
import com.gitflow.android.services.TokenRefreshWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import java.util.concurrent.TimeUnit

class GitFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GitFlowApplication)
            modules(appModule)
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Schedule proactive token refresh every 30 minutes.
        // Requires network so the worker doesn't wake up and burn retries while offline.
        val refreshConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        // UPDATE (not KEEP) so the network constraint also reaches installs that already
        // scheduled the worker before this constraint existed, without resetting the interval.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "token_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TokenRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(refreshConstraints)
                .build()
        )
    }
}
