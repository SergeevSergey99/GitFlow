package com.gitflow.android.data.repository

import com.gitflow.android.data.models.*
import kotlin.random.Random

class MockGitRepository {

    private val mockRepositories = mutableListOf<Repository>()
    private val mockCommits = mutableMapOf<String, List<Commit>>()
    private val mockChanges = mutableMapOf<String, List<FileChange>>()

    init {
        createMockData()
    }

    // ---------- Public API ----------

    fun getRepositories(): List<Repository> = mockRepositories

    fun addRepository(repository: Repository) {
        mockRepositories.add(repository)
        mockCommits[repository.id] = generateCommitsForRepo(repository.name)
        mockChanges[repository.id] = generateChangesForRepo()
    }

    fun getCommits(repository: Repository): List<Commit> =
        mockCommits[repository.id] ?: emptyList()

    fun getChangedFiles(repository: Repository): List<FileChange> =
        mockChanges[repository.id] ?: emptyList()

    fun getCommitDiffs(commit: Commit): List<FileDiff> {
        // Простые синтетические диффы под любой коммит
        val base = commit.hash.take(6)
        val statusPool = listOf(FileStatus.MODIFIED, FileStatus.ADDED, FileStatus.DELETED, FileStatus.RENAMED)
        val files = listOf(
            "app/src/main/java/feature/$base/Feature.kt",
            "app/src/main/res/layout/item_$base.xml",
            "README.md"
        )
        return files.mapIndexed { idx, path ->
            val st = statusPool[idx % statusPool.size]
            when (st) {
                FileStatus.ADDED -> FileDiff(
                    path = path,
                    status = st,
                    additions = 8,
                    deletions = 0,
                    hunks = listOf(
                        DiffHunk(
                            header = "@@ -0,0 +1,8 @@",
                            oldStart = 0, oldLines = 0, newStart = 1, newLines = 8,
                            lines = (1..8).map { i ->
                                DiffLine(LineType.ADDED, "+ line $i in $path", lineNumber = i, newLineNumber = i)
                            }
                        )
                    )
                )
                FileStatus.DELETED -> FileDiff(
                    path = path,
                    status = st,
                    additions = 0,
                    deletions = 6,
                    hunks = listOf(
                        DiffHunk(
                            header = "@@ -1,6 +0,0 @@",
                            oldStart = 1, oldLines = 6, newStart = 0, newLines = 0,
                            lines = (1..6).map { i ->
                                DiffLine(LineType.DELETED, "- old $i in $path", oldLineNumber = i)
                            }
                        )
                    )
                )
                FileStatus.RENAMED -> FileDiff(
                    path = path.replace(".kt", "_renamed.kt"),
                    oldPath = path,
                    status = st,
                    additions = 3,
                    deletions = 3,
                    hunks = listOf(
                        DiffHunk(
                            header = "@@ -10,6 +10,6 @@",
                            oldStart = 10, oldLines = 6, newStart = 10, newLines = 6,
                            lines = (0 until 6).map { i ->
                                val t = if (i % 2 == 0) LineType.DELETED else LineType.ADDED
                                val prefix = if (t == LineType.DELETED) "-" else "+"
                                DiffLine(t, "$prefix rename tweak $i", oldLineNumber = 10 + i, newLineNumber = 10 + i)
                            }
                        )
                    )
                )
                else -> FileDiff(
                    path = path,
                    status = st,
                    additions = 5,
                    deletions = 4,
                    hunks = listOf(
                        DiffHunk(
                            header = "@@ -20,9 +20,10 @@",
                            oldStart = 20, oldLines = 9, newStart = 20, newLines = 10,
                            lines = buildList {
                                add(DiffLine(LineType.CONTEXT, "  context before", oldLineNumber = 20, newLineNumber = 20))
                                add(DiffLine(LineType.DELETED, "- remove line", oldLineNumber = 21))
                                add(DiffLine(LineType.ADDED, "+ add line", newLineNumber = 21))
                                add(DiffLine(LineType.CONTEXT, "  context after", oldLineNumber = 22, newLineNumber = 22))
                            }
                        )
                    )
                )
            }
        }
    }

    fun getBranches(repository: Repository): List<Branch> {
        val commits = getCommits(repository)
        val byBranch = commits.groupBy { it.branch }
        val branches = byBranch.map { (name, list) ->
            val tip = list.maxByOrNull { it.timestamp }!!

            Branch(
                name = name ?: "",
                isLocal = !name?.startsWith("origin/")!!, lastCommitHash = tip.hash, ahead = 0, behind = 0)
        }.sortedBy { it.name }
        // Добавим origin/main если отсутствует
        val hasRemoteMain = branches.any { it.name == "origin/main" }
        return if (hasRemoteMain) branches else branches + Branch("origin/main", false, byBranch["main"]?.maxByOrNull { it.timestamp }?.hash ?: "000000", 0, 0)
    }

    // Мелкие моки push/pull
    fun pull(repository: Repository) = PullResult(success = true, newCommits = Random.nextInt(0, 3), conflicts = emptyList())
    fun push(repository: Repository) = PushResult(success = true, pushedCommits = Random.nextInt(0, 3), message = "ok")

    // Get file tree for a specific commit
    fun getCommitFileTree(commit: Commit): FileTreeNode {
        val baseFiles = generateMockFilesForCommit(commit)
        return buildFileTree(baseFiles)
    }

    fun getFileContent(commit: Commit, filePath: String): String? {
        // Mock file content based on commit and path
        return when {
            filePath.endsWith(".kt") -> generateKotlinFileContent(filePath, commit)
            filePath.endsWith(".xml") -> generateXmlFileContent(filePath, commit)
            filePath.endsWith(".md") -> generateMarkdownFileContent(filePath, commit)
            filePath.endsWith(".gradle") -> generateGradleFileContent(filePath, commit)
            filePath.endsWith(".json") -> generateJsonFileContent(filePath, commit)
            else -> "Binary file content for $filePath in commit ${commit.hash.take(7)}"
        }
    }

    private fun generateMockFilesForCommit(commit: Commit): List<CommitFileInfo> {
        val baseTime = commit.timestamp
        val hash = commit.hash.take(6)

        return listOf(
            // Android app structure
            CommitFileInfo("app/build.gradle.kts", 2048, baseTime - 1000, null),
            CommitFileInfo("app/src/main/AndroidManifest.xml", 1024, baseTime - 2000, null),
            CommitFileInfo("app/src/main/java/com/example/MainActivity.kt", 3072, baseTime - 3000, null),
            CommitFileInfo("app/src/main/java/com/example/ui/HomeFragment.kt", 2560, baseTime - 4000, null),
            CommitFileInfo("app/src/main/java/com/example/ui/DetailFragment.kt", 1800, baseTime - 5000, null),
            CommitFileInfo("app/src/main/java/com/example/data/Repository.kt", 4096, baseTime - 6000, null),
            CommitFileInfo("app/src/main/java/com/example/data/model/User.kt", 1200, baseTime - 7000, null),
            CommitFileInfo("app/src/main/java/com/example/data/model/Item.kt", 1500, baseTime - 8000, null),
            CommitFileInfo("app/src/main/java/com/example/utils/Extensions.kt", 800, baseTime - 9000, null),
            CommitFileInfo("app/src/main/res/layout/activity_main.xml", 2200, baseTime - 10000, null),
            CommitFileInfo("app/src/main/res/layout/fragment_home.xml", 1800, baseTime - 11000, null),
            CommitFileInfo("app/src/main/res/layout/fragment_detail.xml", 1600, baseTime - 12000, null),
            CommitFileInfo("app/src/main/res/layout/item_list.xml", 1000, baseTime - 13000, null),
            CommitFileInfo("app/src/main/res/values/strings.xml", 1400, baseTime - 14000, null),
            CommitFileInfo("app/src/main/res/values/colors.xml", 600, baseTime - 15000, null),
            CommitFileInfo("app/src/main/res/values/themes.xml", 2000, baseTime - 16000, null),
            CommitFileInfo("app/src/main/res/drawable/ic_launcher.xml", 800, baseTime - 17000, null),
            CommitFileInfo("app/src/test/java/com/example/ExampleUnitTest.kt", 1000, baseTime - 18000, null),

            // Project level files
            CommitFileInfo("build.gradle.kts", 1500, baseTime - 19000, null),
            CommitFileInfo("settings.gradle.kts", 400, baseTime - 20000, null),
            CommitFileInfo("gradle.properties", 800, baseTime - 21000, null),
            CommitFileInfo("README.md", 3000, baseTime - 22000, null),
            CommitFileInfo(".gitignore", 1200, baseTime - 23000, null),

            // Add some commit-specific files
            CommitFileInfo("feature_${hash}/FeatureActivity.kt", 2800, baseTime, null),
            CommitFileInfo("feature_${hash}/res/layout/activity_feature.xml", 1600, baseTime, null),
            CommitFileInfo("docs/commit_${hash}_notes.md", 500, baseTime, null),
        )
    }

    private fun buildFileTree(files: List<CommitFileInfo>): FileTreeNode {
        val root = mutableMapOf<String, Any>()

        files.forEach { file ->
            val parts = file.path.split("/")
            var current = root

            for (i in parts.indices) {
                val part = parts[i]
                if (i == parts.lastIndex) {
                    // This is a file
                    current[part] = file
                } else {
                    // This is a directory
                    if (current[part] !is MutableMap<*, *>) {
                        current[part] = mutableMapOf<String, Any>()
                    }
                    @Suppress("UNCHECKED_CAST")
                    current = current[part] as MutableMap<String, Any>
                }
            }
        }

        return convertToFileTreeNode("", "", root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertToFileTreeNode(name: String, path: String, node: Any): FileTreeNode {
        return when (node) {
            is CommitFileInfo -> FileTreeNode(
                name = name,
                path = path,
                type = FileTreeNodeType.FILE,
                size = node.size,
                lastModified = node.lastModified
            )
            is Map<*, *> -> {
                val children = (node as Map<String, Any>).map { (childName, childNode) ->
                    val childPath = if (path.isEmpty()) childName else "$path/$childName"
                    convertToFileTreeNode(childName, childPath, childNode)
                }.sortedWith(compareBy<FileTreeNode> { it.type }.thenBy { it.name })

                FileTreeNode(
                    name = name,
                    path = path,
                    type = FileTreeNodeType.DIRECTORY,
                    children = children
                )
            }
            else -> throw IllegalArgumentException("Unknown node type: ${node::class}")
        }
    }

    private fun generateKotlinFileContent(filePath: String, commit: Commit): String {
        val className = filePath.substringAfterLast("/").removeSuffix(".kt")
        val packageName = filePath.substringBeforeLast("/").replace("/", ".").removePrefix("app.src.main.java.")

        return """
            package $packageName
            
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Modifier
            
            /**
             * $className - created in commit ${commit.hash.take(7)}
             * Author: ${commit.author}
             * Date: ${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(commit.timestamp))}
             */
            class $className {
                
                fun example() {
                    println("Generated for commit: ${commit.hash.take(7)}")
                }
                
                @Composable
                fun ExampleComposable() {
                    Column {
                        Text("Hello from $className")
                        Text("Commit: ${commit.hash.take(7)}")
                    }
                }
            }
        """.trimIndent()
    }

    private fun generateXmlFileContent(filePath: String, commit: Commit): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Generated for commit ${commit.hash.take(7)} -->
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Commit: ${commit.hash.take(7)}"
                    android:textSize="16sp" />
                    
            </LinearLayout>
        """.trimIndent()
    }

    private fun generateMarkdownFileContent(filePath: String, commit: Commit): String {
        return """
            # ${filePath.substringAfterLast("/")}
            
            This file was generated for commit `${commit.hash.take(7)}`.
            
            ## Details
            - Author: ${commit.author}
            - Message: ${commit.message}
            - Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(commit.timestamp))}
            
            ## Content
            This is mock content for demonstration purposes.
        """.trimIndent()
    }

    private fun generateGradleFileContent(filePath: String, commit: Commit): String {
        return """
            // Generated for commit ${commit.hash.take(7)}
            
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }
            
            android {
                compileSdk 34
                
                defaultConfig {
                    minSdk 24
                    targetSdk 34
                    versionCode 1
                    versionName "1.0"
                }
            }
            
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.compose.ui:ui:1.5.4")
                // Commit: ${commit.hash.take(7)}
            }
        """.trimIndent()
    }

    private fun generateJsonFileContent(filePath: String, commit: Commit): String {
        return """
            {
              "commit": "${commit.hash.take(7)}",
              "author": "${commit.author}",
              "message": "${commit.message}",
              "timestamp": ${commit.timestamp},
              "file": "$filePath"
            }
        """.trimIndent()
    }

    // ---------- Mock seed ----------

    private fun createMockData() {
        val now = System.currentTimeMillis()
        val repos = listOf(
            Repository("1", "sample-android-app", "/storage/emulated/0/GitFlow/sample-android-app", now - 3_600_000, "main"),
            Repository("3", "linear-main", "/mock/linear-main", now - 2_000_000, "main"),
            Repository("4", "feature-merge", "/mock/feature-merge", now - 1_900_000, "main"),
            Repository("5", "two-features-interleaved", "/mock/two-features", now - 1_800_000, "main"),
            Repository("6", "long-lived-release-hotfix", "/mock/release-hotfix", now - 1_700_000, "main"),
            Repository("7", "criss-cross-merge", "/mock/criss-cross", now - 1_600_000, "main"),
            Repository("8", "octopus-merge", "/mock/octopus", now - 1_500_000, "main"),
            Repository("9", "orphan-branch", "/mock/orphan", now - 1_400_000, "main"),
            Repository("10", "rebase-like", "/mock/rebase-like", now - 1_300_000, "main"),
            Repository("11", "sparse-history-missing", "/mock/sparse", now - 1_200_000, "main"),
            Repository("12", "tags-and-annotated", "/mock/tags", now - 1_100_000, "main"),
            Repository("13", "single-branch-merges", "/mock/single-branch", now - 1_000_000, "main"),
            Repository("14", "ten-branches", "/mock/ten-branches", now - 900_000, "main")
        )

        mockRepositories.addAll(repos)
        repos.forEach { repo ->
            mockCommits[repo.id] = generateCommitsForRepo(repo.name)
            mockChanges[repo.id] = generateChangesForRepo()
        }
    }

    private fun generateCommitsForRepo(repoName: String): List<Commit> {
        return when (repoName) {
            "sample-android-app" -> scenarioSample()
            "linear-main" -> scenarioLinear()
            "feature-merge" -> scenarioFeatureMerge()
            "two-features-interleaved" -> scenarioTwoFeaturesInterleaved()
            "long-lived-release-hotfix" -> scenarioReleaseHotfix()
            "criss-cross-merge" -> scenarioCrissCross()
            "octopus-merge" -> scenarioOctopus()
            "orphan-branch" -> scenarioOrphan()
            "rebase-like" -> scenarioRebaseLike()
            "sparse-history-missing" -> scenarioSparse()
            "tags-and-annotated" -> scenarioTags()
            "single-branch-merges" -> scenarioSingleBranchMerges()
            "ten-branches" -> scenarioTenBranches()
            else -> scenarioSample()
        }.sortedByDescending { it.timestamp }
    }

    private fun generateChangesForRepo(): List<FileChange> {
        return listOf(
            FileChange("app/src/main/java/MainActivity.kt", ChangeStatus.MODIFIED, 5, 2),
            FileChange("app/src/main/res/layout/activity_main.xml", ChangeStatus.MODIFIED, 3, 1),
            FileChange("README.md", ChangeStatus.ADDED, 10, 0),
            FileChange("temp.txt", ChangeStatus.UNTRACKED, 0, 0)
        )
    }

    // Helper methods for scenarios
    private class Ticker(start: Long, private val stepMin: Int) {
        private var t = start
        fun next(): Long = t.also { t += stepMin * 60_000L }
    }

    private fun generateHash(): String =
        (1..40).map { "0123456789abcdef".random() }.joinToString("")

    private fun scenarioSample(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 15 * 60 * 60 * 1000L, 60)
        fun h() = generateHash()

        val m1 = Commit(h(), "Initial commit", "John Doe", "john@example.com", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "Setup CI", "Jane Smith", "jane@example.com", tick.next(), listOf(m1.hash), "main")
        val m3 = Commit(h(), "Core module", "Bob Johnson", "bob@example.com", tick.next(), listOf(m2.hash), "main")

        val ui1 = Commit(h(), "feat(ui): scaffold", "Alice Brown", "alice@example.com", tick.next(), listOf(m2.hash), "feature/ui")
        val ui2 = Commit(h(), "feat(ui): list", "Alice Brown", "alice@example.com", tick.next(), listOf(ui1.hash), "feature/ui")
        val ui3 = Commit(h(), "fix(ui): padding", "Alice Brown", "alice@example.com", tick.next(), listOf(ui2.hash), "feature/ui")

        val d1 = Commit(h(), "chore(dev): configs", "Charlie Wilson", "charlie@example.com", tick.next(), listOf(m3.hash), "develop")
        val merge1 = Commit(h(), "Merge feature/ui into main", "John Doe", "john@example.com", tick.next(), listOf(m3.hash, ui3.hash), "main")
        val m4 = Commit(h(), "Update README", "Jane Smith", "jane@example.com", tick.next(), listOf(merge1.hash), "main")

        return listOf(m1, m2, m3, ui1, ui2, ui3, d1, merge1, m4)
    }

    private fun scenarioLinear(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 10 * 60 * 60 * 1000L, 30)
        fun h() = generateHash()

        return (1..10).fold(emptyList<Commit>()) { acc, i ->
            val parent = acc.lastOrNull()
            val commit = Commit(
                h(),
                "Linear commit $i",
                "Dev $i",
                "dev$i@example.com",
                tick.next(),
                parent?.let { listOf(it.hash) } ?: emptyList(),
                "main"
            )
            acc + commit
        }
    }

    private fun scenarioFeatureMerge(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 8 * 60 * 60 * 1000L, 45)
        fun h() = generateHash()

        val m1 = Commit(h(), "Initial", "Alice", "alice@example.com", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "Base work", "Alice", "alice@example.com", tick.next(), listOf(m1.hash), "main")

        val f1 = Commit(h(), "Feature start", "Bob", "bob@example.com", tick.next(), listOf(m2.hash), "feature/awesome")
        val f2 = Commit(h(), "Feature progress", "Bob", "bob@example.com", tick.next(), listOf(f1.hash), "feature/awesome")
        val f3 = Commit(h(), "Feature complete", "Bob", "bob@example.com", tick.next(), listOf(f2.hash), "feature/awesome")

        val merge = Commit(h(), "Merge feature/awesome", "Alice", "alice@example.com", tick.next(), listOf(m2.hash, f3.hash), "main")
        val m3 = Commit(h(), "Post-merge cleanup", "Alice", "alice@example.com", tick.next(), listOf(merge.hash), "main")

        return listOf(m1, m2, f1, f2, f3, merge, m3)
    }

    // Placeholder methods for other scenarios
    private fun scenarioTwoFeaturesInterleaved(): List<Commit> = scenarioSample()
    private fun scenarioReleaseHotfix(): List<Commit> = scenarioSample()
    private fun scenarioCrissCross(): List<Commit> = scenarioSample()
    private fun scenarioOctopus(): List<Commit> = scenarioSample()
    private fun scenarioOrphan(): List<Commit> = scenarioSample()
    private fun scenarioRebaseLike(): List<Commit> = scenarioSample()
    private fun scenarioSparse(): List<Commit> = scenarioSample()
    private fun scenarioTags(): List<Commit> = scenarioSample()
    private fun scenarioSingleBranchMerges(): List<Commit> = scenarioSample()
    private fun scenarioTenBranches(): List<Commit> = scenarioSample()
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
