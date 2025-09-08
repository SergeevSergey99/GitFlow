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
        val messages = listOf(
            "Initial commit",
            "Add README.md",
            "Update dependencies",
            "Fix critical bug in login flow",
            "Add new feature: dark mode",
            "Refactor database layer",
            "Update UI components",
            "Fix memory leak",
            "Add unit tests",
            "Improve performance",
            "Update documentation",
            "Fix merge conflicts",
            "Add CI/CD pipeline",
            "Update gradle version",
            "Fix crash on Android 12"
        )

        val authors = listOf(
            "John Doe" to "john@example.com",
            "Jane Smith" to "jane@example.com",
            "Bob Johnson" to "bob@example.com",
            "Alice Brown" to "alice@example.com",
            "Charlie Wilson" to "charlie@example.com"
        )

        val branches = listOf("main", "develop", "feature/ui", "feature/auth", "hotfix/crash")

        val now = System.currentTimeMillis()
        val commits = mutableListOf<Commit>()
        var parentHash = ""

        for (i in messages.indices) {
            val author = authors.random()
            val hash = generateHash()

            commits.add(
                Commit(
                    hash = hash,
                    message = messages[messages.size - 1 - i],
                    author = author.first,
                    email = author.second,
                    timestamp = now - (i * 3600000L), // Each commit 1 hour apart
                    parents = if (parentHash.isEmpty()) emptyList() else listOf(parentHash),
                    branch = if (i % 3 == 0) branches.random() else null
                )
            )

            parentHash = hash
        }

        return commits
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