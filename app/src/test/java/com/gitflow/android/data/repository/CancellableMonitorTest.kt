package com.gitflow.android.data.repository

import kotlinx.coroutines.Job
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CancellableMonitorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun serverWithCommit(): File {
        val serverDir = tempFolder.newFolder("server")
        Git.init().setDirectory(serverDir).call().use { server ->
            File(serverDir, "file.txt").writeText("content")
            server.add().addFilepattern("file.txt").call()
            server.commit().setMessage("first")
                .setAuthor("Test User", "test@example.com")
                .setCommitter("Test User", "test@example.com")
                .call()
        }
        return serverDir
    }

    private fun fetchWith(job: Job): Pair<Git, Result<Unit>> {
        val serverDir = serverWithCommit()
        val git = Git.init().setDirectory(tempFolder.newFolder("client")).call()
        git.remoteAdd().setName("origin").setUri(URIish(serverDir.toURI().toURL())).call()
        val outcome = runCatching {
            git.fetch().setRemote("origin").setProgressMonitor(JGitProgressMonitor(job)).call()
            Unit
        }
        return git to outcome
    }

    @Test
    fun cancelledJob_stopsTheFetch() {
        val (git, outcome) = fetchWith(Job().apply { cancel() })

        git.use {
            assertTrue(outcome.isFailure)
            assertNull(it.repository.findRef("refs/remotes/origin/master") ?: it.repository.findRef("refs/remotes/origin/main"))
        }
    }

    @Test
    fun activeJob_letsTheFetchFinish() {
        val (git, outcome) = fetchWith(Job())

        git.use {
            assertTrue(outcome.isSuccess)
            assertNotNull(it.repository.findRef("refs/remotes/origin/master") ?: it.repository.findRef("refs/remotes/origin/main"))
        }
    }
}
