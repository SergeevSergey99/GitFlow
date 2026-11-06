package com.gitflow.android.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.ResetMode
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.data.settings.AppSettingsManager
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class GraphViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var application: Application
    private lateinit var repoDir: File
    private lateinit var git: Git
    private lateinit var gitRepository: GitRepository
    private lateinit var model: Repository
    private lateinit var mainBranch: String

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        gitRepository = GitRepository(application, mockk<AuthManager>(relaxed = true), mockk<AppSettingsManager>(relaxed = true))

        repoDir = tempFolder.newFolder("repo")
        git = Git.init().setDirectory(repoDir).call()
        git.repository.config.apply {
            setString("user", null, "name", "Test User")
            setString("user", null, "email", "test@example.com")
            save()
        }
        writeFile("initial.txt", "base")
        commit("initial.txt", "init")
        mainBranch = git.repository.branch
        model = Repository(
            id = "graph-test", name = "repo", path = repoDir.absolutePath,
            lastUpdated = 0L, currentBranch = mainBranch
        )
    }

    @After
    fun tearDown() {
        git.close()
        Dispatchers.resetMain()
    }

    private fun writeFile(name: String, content: String) {
        File(repoDir, name).writeText(content)
    }

    private fun commit(path: String, message: String) {
        git.add().addFilepattern(path).call()
        git.commit().setMessage(message)
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()
    }

    private suspend fun GraphViewModel.awaitState(predicate: (GraphUiState) -> Boolean): GraphUiState =
        withTimeout(10_000) { uiState.first(predicate) }

    @Test
    fun reset_reportsSuccessAndReloadsHistoryFromChangeEvent() = runBlocking {
        writeFile("second.txt", "2")
        commit("second.txt", "second")
        val viewModel = GraphViewModel(application, gitRepository, model)
        val loaded = viewModel.awaitState { !it.isLoading && it.commits.size == 2 }
        viewModel.selectActionCommit(loaded.commits.last())

        viewModel.resetTo(loaded.commits.last(), ResetMode.HARD, "reset done")

        val reset = viewModel.awaitState { it.operationResult != null && it.commits.size == 1 }
        assertEquals(GraphOperationResult("reset done", closeDialogs = true), reset.operationResult)
        assertNull(reset.actionCommit)
    }

    @Test
    fun searchCommits_findsCommitsBeyondTheFirstPageAndRespectsLimit() = runBlocking<Unit> {
        repeat(60) { index ->
            writeFile("file$index.txt", "$index")
            commit("file$index.txt", "change $index")
        }

        val byMessage = (gitRepository.searchCommits(model, "CHANGE 0", limit = 10) as GitResult.Success).data
        assertEquals(listOf("change 0"), byMessage.map { it.message })

        val byAuthor = (gitRepository.searchCommits(model, "test user", limit = 5) as GitResult.Success).data
        assertEquals(5, byAuthor.size)

        val initHash = git.log().all().call().last().name
        val byHash = (gitRepository.searchCommits(model, initHash.take(8), limit = 10) as GitResult.Success).data
        assertEquals(listOf(initHash), byHash.map { it.hash })
    }

    @Test
    fun conflictingCherryPick_isReportedAsConflictAndClosesDialogs() = runBlocking {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        commit("initial.txt", "feature change")
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        commit("initial.txt", "main change")
        val viewModel = GraphViewModel(application, gitRepository, model)
        val featureCommit = viewModel.awaitState { !it.isLoading }.commits.first { it.message == "feature change" }

        viewModel.cherryPick(featureCommit, "picked")

        val result = viewModel.awaitState { it.operationResult != null }.operationResult!!
        assertEquals(application.getString(R.string.graph_commit_operation_conflicts), result.message)
        assertTrue(result.closeDialogs)
    }

    @Test
    fun unreadableHistory_showsErrorAndRetryLoadsIt() = runBlocking {
        val brokenDir = tempFolder.newFolder("broken")
        val viewModel = GraphViewModel(application, gitRepository, model.copy(id = "broken", path = brokenDir.absolutePath))

        val failed = viewModel.awaitState { !it.isLoading }
        assertTrue(failed.loadError != null)
        assertTrue(failed.commits.isEmpty())

        Git.init().setDirectory(brokenDir).call().use { repaired ->
            File(brokenDir, "a.txt").writeText("a")
            repaired.add().addFilepattern("a.txt").call()
            repaired.commit().setMessage("first")
                .setAuthor("Test User", "test@example.com")
                .setCommitter("Test User", "test@example.com")
                .call()
        }
        viewModel.retry()

        val loaded = viewModel.awaitState { it.commits.size == 1 }
        assertNull(loaded.loadError)
    }

    @Test
    fun failedOperation_keepsDialogsOpenForRetry() = runBlocking {
        val viewModel = GraphViewModel(application, gitRepository, model)
        val head = viewModel.awaitState { !it.isLoading }.commits.first()
        git.tag().setName("v1").call()

        viewModel.createTag(head, "v1", "tagged")

        val result = viewModel.awaitState { it.operationResult != null }.operationResult!!
        assertFalse(result.closeDialogs)
        assertFalse(viewModel.uiState.value.isOperationRunning)
    }
}
