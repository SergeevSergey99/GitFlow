package com.gitflow.android.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.ChangeStatus
import com.gitflow.android.data.models.ConflictResolutionStrategy
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.LineType
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.screens.main.buildResolvedContent
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
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

/**
 * Regression tests for the working-tree / index / commit git operations in
 * [GitRepositoryIndexTest]-covered files. Each test runs against a real JGit repository
 * created in a temp folder. Commit identity is taken from the repo's git config, so the
 * mocked [AuthManager] / [AppSettingsManager] are never consulted for these flows.
 */
@RunWith(RobolectricTestRunner::class)
// Force a stock Application: the real GitFlowApplication.onCreate() calls startKoin() and
// WorkManager.getInstance(), which throw under Robolectric (Koin already started across tests,
// WorkManager not initialized). These tests don't need either.
@Config(sdk = [34], application = Application::class)
class GitRepositoryIndexTest {

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
        val authManager = mockk<AuthManager>(relaxed = true)
        val settingsManager = mockk<AppSettingsManager>(relaxed = true)
        repo = GitRepository(context, authManager, settingsManager)

        repoDir = tempFolder.newFolder("repo")
        git = Git.init().setDirectory(repoDir).call()
        git.repository.config.apply {
            setString("user", null, "name", "Test User")
            setString("user", null, "email", "test@example.com")
            save()
        }

        // Seed an initial commit so HEAD exists (unstage/discard/diff-against-HEAD need it).
        writeFile("initial.txt", "init")
        git.add().addFilepattern("initial.txt").call()
        git.commit().setMessage("init")
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()
        mainBranch = git.repository.branch

        model = Repository(
            id = "test-repo",
            name = "repo",
            path = repoDir.absolutePath,
            lastUpdated = 0L,
            currentBranch = mainBranch
        )
    }

    @After
    fun tearDown() {
        git.close()
    }

    private fun writeFile(name: String, content: String) {
        File(repoDir, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    private fun <T> GitResult<T>.value(): T = (this as GitResult.Success).data

    private fun commitAll(message: String) {
        git.add().addFilepattern(".").call()
        git.commit().setMessage(message)
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()
    }

    // ---------- staging ----------

    @Test
    fun stageFile_addsUntrackedFileToIndex() = runBlocking {
        writeFile("a.txt", "hello")

        val result = repo.stageFile(model, FileChange("a.txt", ChangeStatus.UNTRACKED, ChangeStage.UNSTAGED))

        assertTrue(result is GitResult.Success)
        assertTrue("a.txt" in git.status().call().added)
    }

    @Test
    fun stageAll_stagesEveryPendingFile() = runBlocking {
        writeFile("a.txt", "1")
        writeFile("nested/b.txt", "2")

        val result = repo.stageAll(model)

        assertTrue(result is GitResult.Success)
        val added = git.status().call().added
        assertTrue(added.contains("a.txt"))
        assertTrue(added.contains("nested/b.txt"))
    }

    @Test
    fun unstageFile_returnsFileToUntracked() = runBlocking {
        writeFile("a.txt", "1")
        git.add().addFilepattern("a.txt").call()
        assertTrue("a.txt" in git.status().call().added)

        val result = repo.unstageFile(model, "a.txt")

        assertTrue(result is GitResult.Success)
        val status = git.status().call()
        assertFalse("a.txt" in status.added)
        assertTrue("a.txt" in status.untracked)
    }

    // ---------- discard ----------

    @Test
    fun discardFileChanges_revertsTrackedModification() = runBlocking {
        writeFile("initial.txt", "modified content")

        val result = repo.discardFileChanges(model, "initial.txt")

        assertTrue(result is GitResult.Success)
        assertEquals("init", File(repoDir, "initial.txt").readText())
    }

    @Test
    fun discardFileChanges_deletesUntrackedFile() = runBlocking {
        writeFile("new.txt", "junk")

        val result = repo.discardFileChanges(model, "new.txt")

        assertTrue(result is GitResult.Success)
        assertFalse(File(repoDir, "new.txt").exists())
    }

    @Test
    fun getCommits_reusesOrderUntilBranchesMoveAndPagesThroughIt() = runBlocking {
        writeFile("second.txt", "2")
        commitAll("second")
        val first = repo.getCommits(model).value().map { it.message }
        assertEquals(listOf("second", "init"), first)
        assertEquals(first, repo.getCommits(model).value().map { it.message })

        writeFile("third.txt", "3")
        commitAll("third")
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("feature.txt", "f")
        commitAll("feature work")

        assertEquals(
            listOf("feature work", "third", "second", "init"),
            repo.getCommits(model).value().map { it.message }
        )
        assertEquals(listOf("third"), repo.getCommits(model, page = 1, pageSize = 1).value().map { it.message })
    }

    @Test
    fun getChangedFiles_countsLinesForEveryKindOfChange() = runBlocking {
        writeFile("tracked.txt", "a\nb\nc\n")
        writeFile("doomed.txt", "x\ny\n")
        commitAll("base")
        writeFile("staged-new.txt", "1\n2\n")
        git.add().addFilepattern("staged-new.txt").call()
        writeFile("tracked.txt", "a\nB\nc\nd\n")
        File(repoDir, "doomed.txt").delete()
        writeFile("untracked.txt", "u\n")

        val stats = repo.getChangedFiles(model).value().associate { it.path to (it.additions to it.deletions) }

        assertEquals(2 to 0, stats["staged-new.txt"])
        assertEquals(2 to 1, stats["tracked.txt"])
        assertEquals(0 to 2, stats["doomed.txt"])
        assertEquals(1 to 0, stats["untracked.txt"])
    }

    @Test
    fun stashSave_withUntracked_stashesNewFilesAndApplyBringsThemBack() = runBlocking {
        writeFile("new.txt", "fresh")

        assertTrue(repo.stashSave(model, includeUntracked = false) is GitResult.Failure)
        assertTrue(repo.stashSave(model, includeUntracked = true) is GitResult.Success)
        assertFalse(File(repoDir, "new.txt").exists())

        assertTrue(repo.stashApply(model, 0) is GitResult.Success)
        assertEquals("fresh", File(repoDir, "new.txt").readText())
    }

    @Test
    fun readsFromMissingRepository_reportFailureInsteadOfEmptyResults() = runBlocking {
        val missing = model.copy(path = tempFolder.newFolder("not-a-repo").absolutePath)

        assertTrue(repo.getCommits(missing) is GitResult.Failure)
        assertTrue(repo.getChangedFiles(missing) is GitResult.Failure)
        assertTrue(repo.getBranches(missing) is GitResult.Failure)
        assertTrue(repo.stashList(missing) is GitResult.Failure)
    }

    @Test
    fun discardFileChanges_removesStagedNewFile() = runBlocking {
        writeFile("new.txt", "staged")
        git.add().addFilepattern("new.txt").call()

        val result = repo.discardFileChanges(model, "new.txt")

        assertTrue(result is GitResult.Success)
        assertFalse(File(repoDir, "new.txt").exists())
        assertTrue(git.status().call().isClean)
    }

    @Test
    fun workingDiff_keepsLinesThatStartWithDoubledMarkers() = runBlocking {
        writeFile("query.sql", "select 1;\n-- old comment\n")
        commitAll("add query")
        writeFile("query.sql", "select 1;\n++counter\n")

        val diff = repo.getWorkingFileDiff(model, "query.sql", ChangeStage.UNSTAGED).value()!!
        val changedLines = diff.hunks.flatMap { it.lines }.filter { it.type != LineType.CONTEXT }
        val listed = repo.getChangedFiles(model).value().single { it.path == "query.sql" }

        assertEquals(
            listOf(LineType.DELETED to "-- old comment", LineType.ADDED to "++counter"),
            changedLines.map { it.type to it.content }
        )
        assertEquals(1 to 1, diff.additions to diff.deletions)
        assertEquals(1 to 1, listed.additions to listed.deletions)
    }

    @Test
    fun commitDiff_parsesHunkHeaderWithoutLineCounts() = runBlocking {
        writeFile("initial.txt", "changed")
        commitAll("change initial")
        val head = repo.getCommitByHash(git.repository.resolve("HEAD").name, model).value()

        val hunk = repo.getCommitDiffs(head, model).value().single { it.path == "initial.txt" }.hunks.single()

        assertEquals(listOf(1, 1, 1, 1), listOf(hunk.oldStart, hunk.oldLines, hunk.newStart, hunk.newLines))
        assertEquals(1, hunk.lines.single { it.type == LineType.DELETED }.oldLineNumber)
        assertEquals(1, hunk.lines.single { it.type == LineType.ADDED }.newLineNumber)
    }

    // ---------- commit / amend ----------

    @Test
    fun commit_createsCommitFromStagedChangesWithConfigIdentity() = runBlocking {
        writeFile("a.txt", "1")
        git.add().addFilepattern("a.txt").call()

        val result = repo.commit(model, "add a")

        assertTrue(result is GitResult.Success)
        val head = git.log().setMaxCount(1).call().first()
        assertEquals("add a", head.fullMessage)
        assertEquals("Test User", head.authorIdent.name)
        assertEquals("test@example.com", head.authorIdent.emailAddress)
    }

    @Test
    fun commit_withNothingStaged_returnsNoStagedChanges() = runBlocking {
        val result = repo.commit(model, "empty")

        assertTrue(result is GitResult.Failure.NoStagedChanges)
    }

    @Test
    fun amendLastCommit_rewritesMessageWithoutAddingCommit() = runBlocking {
        val before = git.log().call().toList().size

        val result = repo.amendLastCommit(model, "amended message")

        assertTrue(result is GitResult.Success)
        assertEquals("amended message", repo.getLastCommitMessage(model))
        assertEquals(before, git.log().call().toList().size)
    }

    @Test
    fun amendLastCommit_keepsOriginalAuthorAndRecordsNewCommitter() = runBlocking {
        writeFile("a.txt", "1")
        git.add().addFilepattern("a.txt").call()
        git.commit().setMessage("by someone else")
            .setAuthor("Original Author", "original@example.com")
            .setCommitter("Original Author", "original@example.com")
            .call()
        val originalWhen = git.log().setMaxCount(1).call().first().authorIdent.`when`

        assertTrue(repo.amendLastCommit(model, "reworded") is GitResult.Success)

        val amended = git.log().setMaxCount(1).call().first()
        assertEquals("Original Author", amended.authorIdent.name)
        assertEquals(originalWhen, amended.authorIdent.`when`)
        assertEquals("Test User", amended.committerIdent.name)
    }

    // ---------- status ----------

    @Test
    fun getChangedFiles_classifiesStagedAndUnstagedEntries() = runBlocking {
        writeFile("staged.txt", "s")
        git.add().addFilepattern("staged.txt").call()
        writeFile("untracked.txt", "u")
        writeFile("initial.txt", "changed")

        val byPath = repo.getChangedFiles(model).value().associateBy { it.path }

        assertEquals(ChangeStage.STAGED, byPath.getValue("staged.txt").stage)
        assertEquals(ChangeStatus.ADDED, byPath.getValue("staged.txt").status)
        assertEquals(ChangeStage.UNSTAGED, byPath.getValue("untracked.txt").stage)
        assertEquals(ChangeStatus.UNTRACKED, byPath.getValue("untracked.txt").status)
        assertEquals(ChangeStage.UNSTAGED, byPath.getValue("initial.txt").stage)
        assertEquals(ChangeStatus.MODIFIED, byPath.getValue("initial.txt").status)
    }

    // ---------- merge conflict flow: detect -> resolve -> commit ----------

    @Test
    fun mergeConflict_isDetectedResolvedAndCommitted() = runBlocking {
        // Divergent change on a feature branch.
        git.checkout().setCreateBranch(true).setName("feature").call()
        writeFile("initial.txt", "theirs")
        git.add().addFilepattern("initial.txt").call()
        git.commit().setMessage("feature change")
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()

        // Conflicting change on the main branch.
        git.checkout().setName(mainBranch).call()
        writeFile("initial.txt", "ours")
        git.add().addFilepattern("initial.txt").call()
        git.commit().setMessage("main change")
            .setAuthor("Test User", "test@example.com")
            .setCommitter("Test User", "test@example.com")
            .call()

        val mergeResult = git.merge().include(git.repository.resolve("feature")).call()
        assertEquals(MergeResult.MergeStatus.CONFLICTING, mergeResult.mergeStatus)

        // The repository layer surfaces the conflict.
        val conflicts = repo.getMergeConflicts(model).value()
        assertEquals(1, conflicts.size)
        assertEquals("initial.txt", conflicts.first().path)

        // Resolve by taking the incoming (THEIRS) version and stage it. Note: resolving to
        // THEIRS makes the tree differ from HEAD (which holds "ours"), so there is a staged
        // change to commit. Resolving to OURS here would leave the tree identical to HEAD and
        // commitImpl's "nothing staged" guard would (currently) block the merge commit —
        // tracked as a separate app edge case, not exercised here.
        val resolve = repo.resolveConflict(model, "initial.txt", ConflictResolutionStrategy.THEIRS)
        assertTrue(resolve is GitResult.Success)
        assertEquals("theirs", File(repoDir, "initial.txt").readText().trim())

        // The merge can now be committed and no conflicts remain.
        assertTrue(repo.commit(model, "merge feature") is GitResult.Success)
        assertTrue(repo.getMergeConflicts(model).value().isEmpty())
    }

    @Test
    fun resolvedConflictContent_keepsCrlfAndTrailingNewline() {
        writeFile("crlf.txt", "head\r\n<<<<<<< HEAD\r\nours\r\n=======\r\ntheirs\r\n>>>>>>> feature\r\ntail\r\n")
        val conflict = parseConflictFile(repoDir.absolutePath, "crlf.txt")!!

        val resolved = buildResolvedContent(conflict, listOf("theirs"))

        assertEquals("head\r\ntheirs\r\ntail\r\n", resolved)
    }
}
