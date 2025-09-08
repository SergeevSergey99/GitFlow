package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import java.util.UUID
import kotlin.random.Random

class MockGitRepository {

    private val mockRepositories = mutableListOf<Repository>()
    private val mockCommits = mutableMapOf<String, List<Commit>>()
    private val mockChanges = mutableMapOf<String, List<FileChange>>()

    init {
        // Initialize with some mock data
        createMockData()
    }

    fun getRepositories(): List<Repository> {
        return mockRepositories
    }

    fun addRepository(repository: Repository) {
        mockRepositories.add(repository)
        // Generate mock commits for new repository
        mockCommits[repository.id] = generateCommitsForRepo(repository.name)
        mockChanges[repository.id] = generateChangesForRepo()
    }

    fun getCommits(repository: Repository): List<Commit> {
        return mockCommits[repository.id] ?: emptyList()
    }

    fun getChangedFiles(repository: Repository): List<FileChange> {
        return mockChanges[repository.id] ?: emptyList()
    }

    fun getCommitDiffs(commit: Commit): List<FileDiff> {
        // Generate mock diffs for the commit
        val files = listOf(
            "app/src/main/java/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml",
            "app/build.gradle.kts",
            "README.md",
            "app/src/main/java/ui/HomeScreen.kt"
        )

        return files.shuffled().take(Random.nextInt(2, 5)).map { file ->
            generateFileDiff(file)
        }
    }

    fun getBranches(repository: Repository): List<Branch> {
        return listOf(
            Branch(
                name = "main",
                isLocal = true,
                lastCommitHash = "abc123",
                ahead = 0,
                behind = 0
            ),
            Branch(
                name = "develop",
                isLocal = true,
                lastCommitHash = "def456",
                ahead = 3,
                behind = 1
            ),
            Branch(
                name = "feature/new-ui",
                isLocal = true,
                lastCommitHash = "ghi789",
                ahead = 5,
                behind = 2
            ),
            Branch(
                name = "origin/main",
                isLocal = false,
                lastCommitHash = "abc123",
                ahead = 0,
                behind = 0
            )
        )
    }

    fun createCommit(repository: Repository, message: String, files: List<FileChange>): Commit {
        val newCommit = Commit(
            hash = generateHash(),
            message = message,
            author = "Current User",
            email = "user@example.com",
            timestamp = System.currentTimeMillis(),
            parents = listOf(mockCommits[repository.id]?.firstOrNull()?.hash ?: ""),
            branch = repository.currentBranch
        )

        // Add to beginning of commits list
        val currentCommits = mockCommits[repository.id] ?: emptyList()
        mockCommits[repository.id] = listOf(newCommit) + currentCommits

        // Clear changes after commit
        mockChanges[repository.id] = emptyList()

        return newCommit
    }

    fun pull(repository: Repository): PullResult {
        // Simulate pulling new commits
        val newCommits = (1..Random.nextInt(1, 5)).map {
            Commit(
                hash = generateHash(),
                message = "Remote commit #$it",
                author = "Remote User",
                email = "remote@example.com",
                timestamp = System.currentTimeMillis() - (it * 3600000),
                parents = listOf(generateHash()),
                branch = repository.currentBranch
            )
        }

        val currentCommits = mockCommits[repository.id] ?: emptyList()
        mockCommits[repository.id] = newCommits + currentCommits

        return PullResult(
            success = true,
            newCommits = newCommits.size,
            conflicts = emptyList()
        )
    }

    fun push(repository: Repository): PushResult {
        return PushResult(
            success = true,
            pushedCommits = Random.nextInt(1, 5),
            message = "Successfully pushed to origin/${repository.currentBranch}"
        )
    }
    private fun generateFileDiff(filePath: String): FileDiff {
        val status = FileStatus.values().random()
        val hunks = when (status) {
            FileStatus.ADDED -> generateAddedFileHunks()
            FileStatus.DELETED -> generateDeletedFileHunks()
            FileStatus.MODIFIED -> generateModifiedFileHunks()
            FileStatus.RENAMED -> generateModifiedFileHunks()
        }

        return FileDiff(
            path = filePath,
            oldPath = if (status == FileStatus.RENAMED) "old/$filePath" else null,
            status = status,
            additions = hunks.sumOf { hunk -> hunk.lines.count { it.type == LineType.ADDED } },
            deletions = hunks.sumOf { hunk -> hunk.lines.count { it.type == LineType.DELETED } },
            hunks = hunks
        )
    }

    private fun generateDeletedFileHunks(): List<DiffHunk> {
        return listOf(
            DiffHunk(
                header = "@@ -1,15 +0,0 @@",
                oldStart = 1,
                oldLines = 15,
                newStart = 0,
                newLines = 0,
                lines = listOf(
                    DiffLine(LineType.DELETED, "// This file is deprecated", null, 1),
                    DiffLine(LineType.DELETED, "class OldClass {", null, 2),
                    DiffLine(LineType.DELETED, "    fun oldMethod() {", null, 3),
                    DiffLine(LineType.DELETED, "        println(\"This is old\")", null, 4),
                    DiffLine(LineType.DELETED, "    }", null, 5),
                    DiffLine(LineType.DELETED, "}", null, 6)
                )
            )
        )
    }

    private fun generateModifiedFileHunks(): List<DiffHunk> {
        return listOf(
            DiffHunk(
                header = "@@ -10,7 +10,9 @@ class MainActivity : ComponentActivity() {",
                oldStart = 10,
                oldLines = 7,
                newStart = 10,
                newLines = 9,
                lines = listOf(
                    DiffLine(LineType.CONTEXT, "class MainActivity : ComponentActivity() {", 10, 10, 10),
                    DiffLine(LineType.CONTEXT, "    override fun onCreate(savedInstanceState: Bundle?) {", 11, 11, 11),
                    DiffLine(LineType.DELETED, "        setContent {", null, 12, null),
                    DiffLine(LineType.DELETED, "            MyApp()", null, 13, null),
                    DiffLine(LineType.ADDED, "        super.onCreate(savedInstanceState)", 12, null, 12),
                    DiffLine(LineType.ADDED, "        setContent {", 13, null, 13),
                    DiffLine(LineType.ADDED, "            GitFlowTheme {", 14, null, 14),
                    DiffLine(LineType.ADDED, "                MyApp()", 15, null, 15),
                    DiffLine(LineType.ADDED, "            }", 16, null, 16),
                    DiffLine(LineType.CONTEXT, "        }", 14, 17, 17),
                    DiffLine(LineType.CONTEXT, "    }", 15, 18, 18)
                )
            ),
            DiffHunk(
                header = "@@ -25,5 +27,8 @@ fun MainScreen() {",
                oldStart = 25,
                oldLines = 5,
                newStart = 27,
                newLines = 8,
                lines = listOf(
                    DiffLine(LineType.CONTEXT, "fun MainScreen() {", 25, 25, 27),
                    DiffLine(LineType.DELETED, "    Text(\"Hello World\")", null, 26, null),
                    DiffLine(LineType.ADDED, "    Column {", 28, null, 28),
                    DiffLine(LineType.ADDED, "        Text(\"Welcome to GitFlow\")", 29, null, 29),
                    DiffLine(LineType.ADDED, "        Button(onClick = { }) {", 30, null, 30),
                    DiffLine(LineType.ADDED, "            Text(\"Get Started\")", 31, null, 31),
                    DiffLine(LineType.ADDED, "        }", 32, null, 32),
                    DiffLine(LineType.ADDED, "    }", 33, null, 33),
                    DiffLine(LineType.CONTEXT, "}", 27, 27, 34)
                )
            )
        )
    }
    private fun generateAddedFileHunks(): List<DiffHunk> {
        return listOf(
            DiffHunk(
                header = "@@ -0,0 +1,20 @@",
                oldStart = 0,
                oldLines = 0,
                newStart = 1,
                newLines = 20,
                lines = listOf(
                    DiffLine(LineType.ADDED, "package com.example.app", 1),
                    DiffLine(LineType.ADDED, "", 2),
                    DiffLine(LineType.ADDED, "import androidx.compose.runtime.*", 3),
                    DiffLine(LineType.ADDED, "import androidx.compose.material3.*", 4),
                    DiffLine(LineType.ADDED, "", 5),
                    DiffLine(LineType.ADDED, "@Composable", 6),
                    DiffLine(LineType.ADDED, "fun NewComponent() {", 7),
                    DiffLine(LineType.ADDED, "    var state by remember { mutableStateOf(0) }", 8),
                    DiffLine(LineType.ADDED, "    ", 9),
                    DiffLine(LineType.ADDED, "    Column {", 10),
                    DiffLine(LineType.ADDED, "        Text(\"Counter: \$state\")", 11),
                    DiffLine(LineType.ADDED, "        Button(onClick = { state++ }) {", 12),
                    DiffLine(LineType.ADDED, "            Text(\"Increment\")", 13),
                    DiffLine(LineType.ADDED, "        }", 14),
                    DiffLine(LineType.ADDED, "    }", 15),
                    DiffLine(LineType.ADDED, "}", 16)
                )
            )
        )
    }

    private fun createMockData() {
        // Create sample repositories
        val repo1 = Repository(
            id = "1",
            name = "sample-android-app",
            path = "/storage/emulated/0/GitFlow/sample-android-app",
            lastUpdated = System.currentTimeMillis() - 3600000,
            currentBranch = "main"
        )

        val repo2 = Repository(
            id = "2",
            name = "web-project",
            path = "/storage/emulated/0/GitFlow/web-project",
            lastUpdated = System.currentTimeMillis() - 86400000,
            currentBranch = "develop"
        )

        mockRepositories.add(repo1)
        mockRepositories.add(repo2)

        // Generate commits for each repository
        mockCommits[repo1.id] = generateCommitsForRepo("sample-android-app")
        mockCommits[repo2.id] = generateCommitsForRepo("web-project")

        // Generate changes for each repository
        mockChanges[repo1.id] = generateChangesForRepo()
        mockChanges[repo2.id] = generateChangesForRepo()
    }
    private fun generateCommitsForRepo(repoName: String): List<Commit> {
        fun h() = generateHash()
        val now = System.currentTimeMillis()
        var t = now - 15 * 3600_000L
        fun tick() = t.also { t += 3600_000L }

        val m1 = Commit(h(), "Initial commit","John Doe","john@example.com", tick(), emptyList(), "main")
        val m2 = Commit(h(), "Setup CI","Jane Smith","jane@example.com", tick(), listOf(m1.hash), "main")
        val m3 = Commit(h(), "Core module","Bob Johnson","bob@example.com", tick(), listOf(m2.hash), "main")

        val ui1 = Commit(h(), "feat(ui): scaffold","Alice Brown","alice@example.com", tick(), listOf(m2.hash), "feature/ui")
        val ui2 = Commit(h(), "feat(ui): list","Alice Brown","alice@example.com", tick(), listOf(ui1.hash), "feature/ui")
        val ui3 = Commit(h(), "fix(ui): padding","Alice Brown","alice@example.com", tick(), listOf(ui2.hash), "feature/ui")

        val d1 = Commit(h(), "chore(dev): configs","Charlie Wilson","charlie@example.com", tick(), listOf(m3.hash), "develop")
        val d2 = Commit(h(), "refactor(dev): io","Charlie Wilson","charlie@example.com", tick(), listOf(d1.hash), "develop")
        val d3 = Commit(h(), "merge(ui → develop)","Jane Smith","jane@example.com", tick(), listOf(d2.hash, ui3.hash), "develop")

        val au1 = Commit(h(), "feat(auth): base","John Doe","john@example.com", tick(), listOf(m3.hash), "feature/auth")
        val au2 = Commit(h(), "feat(auth): oauth","John Doe","john@example.com", tick(), listOf(au1.hash), "feature/auth")
        val d4 = Commit(h(), "merge(auth → develop)","Jane Smith","jane@example.com", tick(), listOf(d3.hash, au2.hash), "develop")

        val m4 = Commit(h(), "docs: README","Bob Johnson","bob@example.com", tick(), listOf(m3.hash), "main")
        val m5 = Commit(h(), "merge(develop → main)","Jane Smith","jane@example.com", tick(), listOf(m4.hash, d4.hash), "main")
        val m6 = Commit(h(), "release: 1.0","John Doe","john@example.com", tick(), listOf(m5.hash), "main")

        return listOf(m1,m2,m3,ui1,ui2,ui3,d1,d2,d3,au1,au2,d4,m4,m5,m6).sortedByDescending { it.timestamp }
    }


    private fun generateChangesForRepo(): List<FileChange> {
        val files = listOf(
            "app/src/main/java/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml",
            "app/build.gradle.kts",
            "README.md",
            ".gitignore",
            "app/src/main/java/ui/HomeScreen.kt",
            "app/src/main/java/data/Repository.kt",
            "app/src/test/java/MainActivityTest.kt"
        )

        return files.shuffled().take(Random.nextInt(2, 6)).map { file ->
            FileChange(
                path = file,
                status = ChangeStatus.values().random(),
                additions = Random.nextInt(1, 100),
                deletions = Random.nextInt(0, 50)
            )
        }
    }

    private fun generateHash(): String {
        val chars = "0123456789abcdef"
        return (1..12).map { chars.random() }.joinToString("")
    }
}

// Result classes
data class PullResult(
    val success: Boolean,
    val newCommits: Int,
    val conflicts: List<String>
)

data class PushResult(
    val success: Boolean,
    val pushedCommits: Int,
    val message: String
)