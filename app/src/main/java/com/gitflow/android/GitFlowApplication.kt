package com.gitflow.android

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
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
        // Schedule proactive token refresh every 30 minutes
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "token_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TokenRefreshWorker>(30, TimeUnit.MINUTES).build()
        )
    }
}
