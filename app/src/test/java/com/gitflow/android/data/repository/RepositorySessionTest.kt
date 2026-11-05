package com.gitflow.android.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.settings.AppSettingsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RepositorySessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val settingsManager = mockk<AppSettingsManager>(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repoDir: File
    private lateinit var git: Git
    private lateinit var gitRepository: GitRepository
    private lateinit var session: RepositorySession
    private lateinit var model: Repository
    private lateinit var mainBranch: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        every { settingsManager.getLastRepositoryId() } returns null
        gitRepository = GitRepository(context, mockk<AuthManager>(relaxed = true), settingsManager)

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

        model = (gitRepository.addRepository(repoDir.absolutePath) as GitResult.Success).data
        session = RepositorySession(gitRepository, settingsManager, scope)
    }

    @After
    fun tearDown() = runBlocking {
        gitRepository.removeRepository(model.id)
        scope.cancel()
        git.close()
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

    private suspend fun awaitSnapshot(predicate: (RepositorySnapshot?) -> Boolean): RepositorySnapshot? =
        withTimeout(10_000) { session.snapshot.first(predicate) }

    @Test
    fun select_publishesSnapshotAndRemembersSelection() = runBlocking {
        session.select(model)

        val snapshot = awaitSnapshot { it != null }!!

        assertEquals(model.id, snapshot.repository.id)
        assertEquals(RepoOperationState.NONE, snapshot.conflictInfo.operation)
        verify { settingsManager.setLastRepositoryId(model.id) }
    }

    @Test
    fun conflictingMergeAndAbort_updateSnapshotWithoutManualRefresh() = runBlocking<Unit> {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        commit("initial.txt", "feature change")
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        commit("initial.txt", "main change")
        session.select(model)
        awaitSnapshot { it != null }

        gitRepository.mergeBranch(model, "feature")
        val merging = awaitSnapshot { it?.conflictInfo?.operation == RepoOperationState.MERGING }!!

        assertEquals(listOf("initial.txt"), merging.conflictInfo.conflictPaths)

        gitRepository.abortMerge(model)
        awaitSnapshot { it != null && it.conflictInfo.operation == RepoOperationState.NONE }
    }

    @Test
    fun removingSelectedRepository_clearsSelection() = runBlocking {
        session.select(model)
        awaitSnapshot { it != null }

        gitRepository.removeRepository(model.id)

        awaitSnapshot { it == null }
        verify { settingsManager.setLastRepositoryId(null) }
    }
}
