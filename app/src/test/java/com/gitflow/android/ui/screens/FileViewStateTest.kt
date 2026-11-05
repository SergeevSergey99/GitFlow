package com.gitflow.android.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.ChangeStatus
import com.gitflow.android.data.models.FileTreeNode
import com.gitflow.android.data.models.FileTreeNodeType
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.data.repository.RepositorySession
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.screens.main.ChangesUiState
import com.gitflow.android.ui.screens.main.ChangesViewModel
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
import org.junit.Assert.assertNull
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
class FileViewStateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val settingsManager = mockk<AppSettingsManager>(relaxed = true)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var application: Application
    private lateinit var repoDir: File
    private lateinit var git: Git
    private lateinit var gitRepository: GitRepository
    private lateinit var model: Repository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settingsManager.getLastRepositoryId() } returns null
        application = ApplicationProvider.getApplicationContext()
        gitRepository = GitRepository(application, mockk<AuthManager>(relaxed = true), settingsManager)

        repoDir = tempFolder.newFolder("repo")
        git = Git.init().setDirectory(repoDir).call()
        File(repoDir, "notes.txt").writeText("first\n")
        git.add().addFilepattern("notes.txt").call()
        git.commit().setMessage("add notes")
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()
        model = Repository(
            id = "file-view-test", name = "repo", path = repoDir.absolutePath,
            lastUpdated = 0L, currentBranch = git.repository.branch
        )
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        git.close()
        Dispatchers.resetMain()
    }

    private fun changesViewModel() =
        ChangesViewModel(application, gitRepository, RepositorySession(gitRepository, settingsManager, sessionScope), model)

    private suspend fun ChangesViewModel.awaitState(predicate: (ChangesUiState) -> Boolean): ChangesUiState =
        withTimeout(10_000) { uiState.first(predicate) }

    @Test
    fun openDiff_loadsWorkingTreeDiffAndCloseClearsIt() = runBlocking {
        File(repoDir, "notes.txt").writeText("first\nsecond\n")
        val viewModel = changesViewModel()

        viewModel.openDiff(FileChange("notes.txt", ChangeStatus.MODIFIED, ChangeStage.UNSTAGED))
        val openedDiff = viewModel.awaitState { it.openedDiff != null }.openedDiff!!

        assertEquals("notes.txt", openedDiff.path)
        assertEquals(1, openedDiff.additions)
        viewModel.closeDiff()
        assertNull(viewModel.uiState.value.openedDiff)
    }

    @Test
    fun openFileHistory_listsCommitsOrExplainsWhyNone() = runBlocking {
        val viewModel = changesViewModel()

        viewModel.openFileHistory("notes.txt")
        val history = viewModel.awaitState { it.historyPath == "notes.txt" && !it.isHistoryLoading }
        assertEquals(listOf("add notes"), history.fileHistory.map { it.message })

        viewModel.openFileHistory("missing.txt")
        val empty = viewModel.awaitState { it.historyPath == "missing.txt" && !it.isHistoryLoading }
        assertEquals(application.getString(R.string.commit_detail_history_no_changes), empty.historyError)
    }

    @Test
    fun commitFileViewer_loadsContentFromTheCommit() = runBlocking {
        val head = git.log().setMaxCount(1).call().first()
        val commit = Commit(
            hash = head.name, message = head.shortMessage, author = "Test User", email = "test@example.com",
            timestamp = 0L, parents = emptyList()
        )
        File(repoDir, "notes.txt").writeText("changed in working tree\n")
        val viewModel = CommitDetailViewModel(gitRepository, commit, model)

        viewModel.openFileViewer(FileTreeNode("notes.txt", "notes.txt", FileTreeNodeType.FILE))
        val loaded = withTimeout(10_000) { viewModel.uiState.first { !it.isViewedFileLoading && it.viewedFile != null } }

        assertEquals("first\n", loaded.viewedFileContent)
        viewModel.closeFileViewer()
        assertNull(viewModel.uiState.value.viewedFile)
    }
}
