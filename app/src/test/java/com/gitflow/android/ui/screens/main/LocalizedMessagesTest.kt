package com.gitflow.android.ui.screens.main

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.data.repository.RepositorySession
import com.gitflow.android.data.settings.AppSettingsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalizedMessagesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val settingsManager = mockk<AppSettingsManager>(relaxed = true)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var application: Application
    private lateinit var git: Git
    private lateinit var gitRepository: GitRepository
    private lateinit var model: Repository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsManager.getLastRepositoryId() } returns null
        application = ApplicationProvider.getApplicationContext()
        gitRepository = GitRepository(application, mockk<AuthManager>(relaxed = true), settingsManager)

        val repoDir = tempFolder.newFolder("repo")
        git = Git.init().setDirectory(repoDir).call()
        git.repository.config.apply {
            setString("user", null, "name", "Test User")
            setString("user", null, "email", "test@example.com")
            save()
        }
        File(repoDir, "a.txt").writeText("a")
        git.add().addFilepattern("a.txt").call()
        git.commit().setMessage("init").call()
        model = Repository(
            id = "messages-test", name = "repo", path = repoDir.absolutePath,
            lastUpdated = 0L, currentBranch = git.repository.branch
        )
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        git.close()
        Dispatchers.resetMain()
    }

    @Test
    fun branchMessages_comeFromResources() = runBlocking {
        val viewModel = BranchesViewModel(
            application, model, gitRepository, RepositorySession(gitRepository, settingsManager, sessionScope)
        )

        viewModel.createBranch(" ", checkout = false)
        assertEquals(application.getString(R.string.branches_name_empty), viewModel.uiState.value.errorMessage)

        viewModel.createBranch("feature", checkout = false)
        val created = withTimeout(10_000) { viewModel.uiState.first { it.message != null } }
        assertEquals(application.getString(R.string.branches_created, "feature"), created.message)
    }

    @Test
    fun repositoryFailures_carryLocalizedMessages() = runBlocking {
        val nothingToStash = gitRepository.stashSave(model)
        val nothingToCommit = gitRepository.commit(model, "empty")

        assertEquals(application.getString(R.string.git_error_nothing_to_stash), (nothingToStash as GitResult.Failure).message)
        assertEquals(application.getString(R.string.git_error_no_staged_changes), (nothingToCommit as GitResult.Failure.NoStagedChanges).message)
    }
}
