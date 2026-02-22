package com.gitflow.android.di

import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.screens.CommitDetailViewModel
import com.gitflow.android.ui.screens.MainViewModel
import com.gitflow.android.ui.screens.main.BranchesViewModel
import com.gitflow.android.ui.screens.main.ChangesViewModel
import com.gitflow.android.ui.screens.main.RepositoryListViewModel
import com.gitflow.android.ui.screens.main.SettingsViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Singletons
    single { AuthManager(androidContext()) }
    single { AppSettingsManager(androidContext()) }
    single<IGitRepository> { GitRepository(androidContext(), get()) }

    // ViewModels — static dependencies
    viewModel { MainViewModel(androidApplication(), get()) }
    viewModel { RepositoryListViewModel(androidApplication(), get()) }
    viewModel { SettingsViewModel(androidApplication(), get(), get()) }

    // ViewModels — runtime parameters via parametersOf()
    viewModel { params ->
        ChangesViewModel(androidApplication(), get(), params.get<Repository>())
    }
    viewModel { params ->
        CommitDetailViewModel(get(), params.get<Commit>(), params.getOrNull<Repository>())
    }
    viewModel { params ->
        BranchesViewModel(androidApplication(), params.get<Repository>(), get())
    }
}
