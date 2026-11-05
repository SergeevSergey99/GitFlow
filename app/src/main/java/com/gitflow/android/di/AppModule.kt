package com.gitflow.android.di

import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.repository.RepositorySession
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.screens.CommitDetailViewModel
import com.gitflow.android.ui.screens.GraphViewModel
import com.gitflow.android.ui.screens.MainViewModel
import com.gitflow.android.ui.screens.main.BranchesViewModel
import com.gitflow.android.ui.screens.main.ChangesViewModel
import com.gitflow.android.ui.screens.main.RepositoryListViewModel
import com.gitflow.android.ui.screens.main.SettingsViewModel
import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.ui.auth.AuthViewModel
import com.gitflow.android.ui.repositories.PullRequestsViewModel
import com.gitflow.android.ui.repositories.RemoteRepositoriesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Singletons
    single { AuthManager(androidContext()) }
    single { AppSettingsManager(androidContext()) }
    single<IGitRepository> { GitRepository(androidContext(), get(), get()) }
    single { RepositorySession(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }

    // ViewModels — static dependencies
    viewModel { AuthViewModel(get(), get()) }
    viewModel { RemoteRepositoriesViewModel(get()) }
    viewModel { MainViewModel(androidApplication(), get(), get(), get()) }
    viewModel { RepositoryListViewModel(androidApplication(), get(), get()) }
    viewModel { SettingsViewModel(androidApplication(), get(), get()) }

    // ViewModels — runtime parameters via parametersOf()
    viewModel { params ->
        ChangesViewModel(androidApplication(), get(), get(), params.get<Repository>())
    }
    viewModel { params ->
        CommitDetailViewModel(get(), params.get<Commit>(), params.get<Repository>())
    }
    viewModel { params ->
        GraphViewModel(androidApplication(), get(), params.get<Repository>())
    }
    viewModel { params ->
        BranchesViewModel(androidApplication(), params.get<Repository>(), get(), get())
    }
    viewModel { params ->
        PullRequestsViewModel(get(), params.get<GitRemoteRepository>())
    }
}
