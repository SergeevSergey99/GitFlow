package com.gitflow.android

import android.app.Application
import timber.log.Timber

class GitFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
