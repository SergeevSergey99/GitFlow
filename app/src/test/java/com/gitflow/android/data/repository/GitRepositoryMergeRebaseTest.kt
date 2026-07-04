package com.gitflow.android.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.ConflictResolutionStrategy
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.settings.AppSettingsManager
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression tests for merge/rebase and their abort paths (GitRepositoryBranches). Runs against
 * a real JGit repository; commit identity comes from the repo config (mocks never consulted).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GitRepositoryMergeRebaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repoDir: File
    private lateinit var git: Git
    private lateinit var repo: GitRepository
    private lateinit var model: Repository
    private lateinit var mainBranch: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = GitRepository(context, mockk<AuthManager>(relaxed = true), mockk<AppSettingsManager>(relaxed = true))

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
            id = "test-repo", name = "repo", path = repoDir.absolutePath,
            lastUpdated = 0L, currentBranch = mainBranch
        )
    }

    @After
    fun tearDown() = git.close()

    private fun writeFile(name: String, content: String) {
        File(repoDir, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    private fun commit(path: String, message: String) {
        git.add().addFilepattern(path).call()
        git.commit().setMessage(message)
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()
    }

    // ---------- merge ----------

    @Test
    fun mergeBranch_fastForwardSucceeds() = runBlocking {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("feature.txt", "f")
        commit("feature.txt", "add feature file")
        git.checkout().setName(mainBranch).call()

        val result = repo.mergeBranch(model, "feature")

        assertTrue(result is GitResult.Success)
        assertTrue(File(repoDir, "feature.txt").exists())
        assertEquals(RepoOperationState.NONE, repo.getRepositoryState(model))
    }

    @Test
    fun mergeBranch_conflictReportsConflictAndLeavesMergingState() = runBlocking {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        commit("initial.txt", "feature change")
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        commit("initial.txt", "main change")

        val result = repo.mergeBranch(model, "feature")

        assertTrue(result is GitResult.Failure.Conflict)
        assertTrue((result as GitResult.Failure.Conflict).paths.contains("initial.txt"))
        assertEquals(RepoOperationState.MERGING, repo.getRepositoryState(model))
    }

    @Test
    fun abortMerge_restoresCleanState() = runBlocking {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        commit("initial.txt", "feature change")
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        commit("initial.txt", "main change")
        repo.mergeBranch(model, "feature")
        assertEquals(RepoOperationState.MERGING, repo.getRepositoryState(model))

        val result = repo.abortMerge(model)

        assertTrue(result is GitResult.Success)
        assertEquals(RepoOperationState.NONE, repo.getRepositoryState(model))
        assertEquals("ours", File(repoDir, "initial.txt").readText().trim())
    }

    @Test
    fun commit_afterMergeResolvedToOurs_succeeds() = runBlocking {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        commit("initial.txt", "feature change")
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        commit("initial.txt", "main change")

        // Resolving the conflict entirely to OURS leaves the tree identical to HEAD.
        assertTrue(repo.mergeBranch(model, "feature") is GitResult.Failure.Conflict)
        assertTrue(repo.resolveConflict(model, "initial.txt", ConflictResolutionStrategy.OURS) is GitResult.Success)

        // The merge commit must still be allowed (MERGE_HEAD present), not blocked as
        // "nothing staged". This is the commitImpl guard fix.
        assertTrue(repo.commit(model, "merge feature (ours)") is GitResult.Success)
        assertEquals(RepoOperationState.NONE, repo.getRepositoryState(model))
    }

    // ---------- rebase ----------

    @Test
    fun rebaseCurrentOnto_cleanReplaySucceeds() = runBlocking {
        // feature diverges by adding feature.txt; main diverges by adding other.txt (no overlap).
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("feature.txt", "f")
        commit("feature.txt", "add feature file")
        git.checkout().setName(mainBranch).call()
        writeFile("other.txt", "o")
        commit("other.txt", "add other file")
        git.checkout().setName("feature").call()

        val result = repo.rebaseCurrentOnto(model, mainBranch)

        assertTrue(result is GitResult.Success)
        assertTrue(File(repoDir, "feature.txt").exists())
        assertTrue(File(repoDir, "other.txt").exists())
        assertEquals(RepoOperationState.NONE, repo.getRepositoryState(model))
    }

    @Test
    fun rebaseCurrentOnto_conflictLeavesRebasingState() = runBlocking {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        commit("initial.txt", "feature change")
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        commit("initial.txt", "main change")
        git.checkout().setName("feature").call()

        val result = repo.rebaseCurrentOnto(model, mainBranch)

        assertTrue(result is GitResult.Failure.Conflict)
        assertEquals(RepoOperationState.REBASING, repo.getRepositoryState(model))
    }

    @Test
    fun rebaseAbort_restoresCleanState() = runBlocking {
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        commit("initial.txt", "feature change")
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        commit("initial.txt", "main change")
        git.checkout().setName("feature").call()
        repo.rebaseCurrentOnto(model, mainBranch)
        assertEquals(RepoOperationState.REBASING, repo.getRepositoryState(model))

        val result = repo.rebaseAbort(model)

        assertTrue(result is GitResult.Success)
        assertEquals(RepoOperationState.NONE, repo.getRepositoryState(model))
        // After abort, feature's own version is back.
        assertEquals("theirs", File(repoDir, "initial.txt").readText().trim())
    }
}
