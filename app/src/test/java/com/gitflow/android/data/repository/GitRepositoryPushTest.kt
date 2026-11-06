package com.gitflow.android.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.settings.AppSettingsManager
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.util.SystemReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class GitRepositoryPushTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repoDir: File
    private lateinit var git: Git
    private lateinit var repo: GitRepository
    private lateinit var model: Repository

    @Before
    fun setUp() {
        SystemReader.setInstance(IsolatedConfigReader(SystemReader.getInstance(), tempFolder.newFolder("config")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = GitRepository(context, mockk<AuthManager>(relaxed = true), mockk<AppSettingsManager>(relaxed = true))

        val remoteDir = tempFolder.newFolder("remote.git")
        Git.init().setBare(true).setDirectory(remoteDir).call().close()

        repoDir = tempFolder.newFolder("repo")
        git = Git.init().setDirectory(repoDir).call()
        git.remoteAdd().setName("origin").setUri(URIish(remoteDir.toURI().toURL())).call()
        commitFile("initial.txt", "init")
        git.checkout().setCreateBranch(true).setName("feature").call()

        model = Repository(
            id = "test-repo", name = "repo", path = repoDir.absolutePath,
            lastUpdated = 0L, currentBranch = "feature", hasRemoteOrigin = true
        )
    }

    @After
    fun tearDown() {
        git.close()
        SystemReader.setInstance(null)
    }

    private fun commitFile(name: String, message: String) {
        File(repoDir, name).writeText(message)
        git.add().addFilepattern(name).call()
        git.commit().setMessage(message)
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()
    }

    @Test
    fun pushOfNewBranch_remembersUpstreamSoLaterCommitsArePending() = runBlocking<Unit> {
        assertTrue(repo.isBranchUnpublished(git))

        assertTrue(repo.push(model) is GitResult.Success)

        assertEquals("refs/heads/feature", git.repository.config.getString("branch", "feature", "merge"))
        assertEquals("origin", git.repository.config.getString("branch", "feature", "remote"))
        assertFalse(repo.isBranchUnpublished(git))

        commitFile("next.txt", "next")
        assertEquals(1, repo.calculateAheadBehind(git).first)
    }

    @Test
    fun pushBranchOfAnotherBranch_remembersItsUpstream() = runBlocking<Unit> {
        git.branchCreate().setName("other").call()

        assertTrue(repo.pushBranch(model, "other") is GitResult.Success)

        assertEquals("refs/heads/other", git.repository.config.getString("branch", "other", "merge"))
    }

    @Test
    fun detachedHead_isNotReportedAsUnpublished() {
        git.checkout().setName(git.repository.resolve("HEAD").name).call()

        assertFalse(repo.isBranchUnpublished(git))
    }
}
