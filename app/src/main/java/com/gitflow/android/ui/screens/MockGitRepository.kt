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

    // ---------- Mock seed ----------

    private fun createMockData() {
        val now = System.currentTimeMillis()
        val repos = listOf(
            Repository("1", "sample-android-app", "/storage/emulated/0/GitFlow/sample-android-app", now - 3_600_000, "main"),
            Repository("2", "web-project",        "/storage/emulated/0/GitFlow/web-project",        now - 86_400_000, "develop"),

            // Новые сценарии
            Repository("3", "linear-main",              "/mock/linear-main",              now - 2_000_000, "main"),
            Repository("4", "feature-merge",            "/mock/feature-merge",            now - 1_900_000, "main"),
            Repository("5", "two-features-interleaved", "/mock/two-features",             now - 1_800_000, "main"),
            Repository("6", "long-lived-release-hotfix","/mock/release-hotfix",          now - 1_700_000, "main"),
            Repository("7", "criss-cross-merge",        "/mock/criss-cross",              now - 1_600_000, "main"),
            Repository("8", "octopus-merge",            "/mock/octopus",                  now - 1_500_000, "main"),
            Repository("9", "orphan-branch",            "/mock/orphan",                   now - 1_400_000, "main"),
            Repository("10","rebase-like",              "/mock/rebase-like",              now - 1_300_000, "main"),
            Repository("11","sparse-history-missing",   "/mock/sparse",                   now - 1_200_000, "main"),
            Repository("12","tags-and-annotated",       "/mock/tags",                     now - 1_100_000, "main")
        )

        mockRepositories.addAll(repos)
        repos.forEach { repo ->
            mockCommits[repo.id] = generateCommitsForRepo(repo.name)
            mockChanges[repo.id] = generateChangesForRepo()
        }
    }

    // ---------- Scenarios ----------

    private fun generateCommitsForRepo(repoName: String): List<Commit> {
        return when (repoName) {
            "sample-android-app", "web-project" -> scenarioSample()
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
            else -> scenarioSample()
        }.sortedByDescending { it.timestamp }
    }

    // helper for time ticks
    private class Ticker(start: Long, private val stepMin: Int) {
        private var t = start
        fun next(): Long = t.also { t += stepMin * 60_000L }
    }

    private fun scenarioSample(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 15 * 60_60_1000L, 60)
        fun h() = generateHash()
        val m1 = Commit(h(), "Initial commit", "John Doe", "john@example.com", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "Setup CI", "Jane Smith", "jane@example.com", tick.next(), listOf(m1.hash), "main")
        val m3 = Commit(h(), "Core module", "Bob Johnson", "bob@example.com", tick.next(), listOf(m2.hash), "main")

        val ui1 = Commit(h(), "feat(ui): scaffold", "Alice Brown", "alice@example.com", tick.next(), listOf(m2.hash), "feature/ui")
        val ui2 = Commit(h(), "feat(ui): list", "Alice Brown", "alice@example.com", tick.next(), listOf(ui1.hash), "feature/ui")
        val ui3 = Commit(h(), "fix(ui): padding", "Alice Brown", "alice@example.com", tick.next(), listOf(ui2.hash), "feature/ui")

        val d1 = Commit(h(), "chore(dev): configs", "Charlie Wilson", "charlie@example.com", tick.next(), listOf(m3.hash), "develop")
        val d2 = Commit(h(), "refactor(dev): io", "Charlie Wilson", "charlie@example.com", tick.next(), listOf(d1.hash), "develop")
        val d3 = Commit(h(), "merge(ui → develop)", "Jane Smith", "jane@example.com", tick.next(), listOf(d2.hash, ui3.hash), "develop")

        val au1 = Commit(h(), "feat(auth): base", "John Doe", "john@example.com", tick.next(), listOf(m3.hash), "feature/auth")
        val au2 = Commit(h(), "feat(auth): oauth", "John Doe", "john@example.com", tick.next(), listOf(au1.hash), "feature/auth")
        val d4 = Commit(h(), "merge(auth → develop)", "Jane Smith", "jane@example.com", tick.next(), listOf(d3.hash, au2.hash), "develop")

        val m4 = Commit(h(), "docs: README", "Bob Johnson", "bob@example.com", tick.next(), listOf(m3.hash), "main")
        val m5 = Commit(h(), "merge(develop → main)", "Jane Smith", "jane@example.com", tick.next(), listOf(m4.hash, d4.hash), "main")
        val m6 = Commit(h(), "release: 1.0", "John Doe", "john@example.com", tick.next(), listOf(m5.hash), "main", tags = listOf("v1.0.0"))

        return listOf(m1, m2, m3, ui1, ui2, ui3, d1, d2, d3, au1, au2, d4, m4, m5, m6)
    }

    private fun scenarioLinear(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 12 * 60_60_1000L, 45)
        fun h() = generateHash()
        var prev: Commit? = null
        val list = mutableListOf<Commit>()
        repeat(12) { i ->
            val c = Commit(
                h(), "linear: step ${i + 1}", "Dev", "dev@example.com",
                tick.next(), prev?.let { listOf(it.hash) } ?: emptyList(), "main"
            )
            list += c
            prev = c
        }
        return list
    }

    private fun scenarioFeatureMerge(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 10 * 60_60_1000L, 30)
        fun h() = generateHash()
        val m1 = Commit(h(), "init", "Dev", "dev@x", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "main work 1", "Dev", "dev@x", tick.next(), listOf(m1.hash), "main")
        val m3 = Commit(h(), "main work 2", "Dev", "dev@x", tick.next(), listOf(m2.hash), "main")

        val f1 = Commit(h(), "feature: A1", "Alice", "a@x", tick.next(), listOf(m2.hash), "feature/A")
        val f2 = Commit(h(), "feature: A2", "Alice", "a@x", tick.next(), listOf(f1.hash), "feature/A")

        val m4 = Commit(h(), "merge A → main", "Maint", "m@x", tick.next(), listOf(m3.hash, f2.hash), "main")
        val m5 = Commit(h(), "post-merge", "Dev", "dev@x", tick.next(), listOf(m4.hash), "main")
        return listOf(m1, m2, m3, f1, f2, m4, m5)
    }

    private fun scenarioTwoFeaturesInterleaved(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 9 * 60_60_1000L, 20)
        fun h() = generateHash()
        val m1 = Commit(h(), "base", "Dev", "d@x", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "prep", "Dev", "d@x", tick.next(), listOf(m1.hash), "main")

        val a1 = Commit(h(), "A1", "A", "a@x", tick.next(), listOf(m2.hash), "feature/A")
        val b1 = Commit(h(), "B1", "B", "b@x", tick.next(), listOf(m2.hash), "feature/B")
        val a2 = Commit(h(), "A2", "A", "a@x", tick.next(), listOf(a1.hash), "feature/A")
        val b2 = Commit(h(), "B2", "B", "b@x", tick.next(), listOf(b1.hash), "feature/B")

        val m3 = Commit(h(), "merge B", "Maint", "m@x", tick.next(), listOf(m2.hash, b2.hash), "main")
        val m4 = Commit(h(), "merge A", "Maint", "m@x", tick.next(), listOf(m3.hash, a2.hash), "main")
        return listOf(m1, m2, a1, b1, a2, b2, m3, m4)
    }

    private fun scenarioReleaseHotfix(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 14 * 60_60_1000L, 25)
        fun h() = generateHash()
        val m1 = Commit(h(), "init", "Dev", "d@x", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "feat: core", "Dev", "d@x", tick.next(), listOf(m1.hash), "main")
        val m3 = Commit(h(), "feat: ui", "Dev", "d@x", tick.next(), listOf(m2.hash), "main")

        // release/1.0 от m3
        val r1 = Commit(h(), "release: prep", "Rel", "r@x", tick.next(), listOf(m3.hash), "release/1.0")
        val r2 = Commit(h(), "release: docs", "Rel", "r@x", tick.next(), listOf(r1.hash), "release/1.0")

        // main продолжает жить
        val m4 = Commit(h(), "main: perf", "Dev", "d@x", tick.next(), listOf(m3.hash), "main")

        // hotfix от r2
        val h1 = Commit(h(), "hotfix: critical", "Hot", "h@x", tick.next(), listOf(r2.hash), "hotfix/1.0.1")
        // слияние hotfix в release и main
        val r3 = Commit(h(), "merge hotfix → release", "Rel", "r@x", tick.next(), listOf(r2.hash, h1.hash), "release/1.0")
        val m5 = Commit(h(), "merge hotfix → main", "Maint", "m@x", tick.next(), listOf(m4.hash, h1.hash), "main", tags = listOf("v1.0.1"))

        val m6 = Commit(h(), "post-release", "Dev", "d@x", tick.next(), listOf(m5.hash), "main")
        return listOf(m1, m2, m3, r1, r2, m4, h1, r3, m5, m6)
    }

    private fun scenarioCrissCross(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 8 * 60_60_1000L, 18)
        fun h() = generateHash()
        val b = Commit(h(), "base", "Dev", "d@x", tick.next(), emptyList(), "main")
        val a1 = Commit(h(), "A1", "A", "a@x", tick.next(), listOf(b.hash), "branch/A")
        val c1 = Commit(h(), "C1", "C", "c@x", tick.next(), listOf(b.hash), "branch/C")

        val a2 = Commit(h(), "A2", "A", "a@x", tick.next(), listOf(a1.hash), "branch/A")
        val c2 = Commit(h(), "C2", "C", "c@x", tick.next(), listOf(c1.hash), "branch/C")

        // A ← merge C
        val a3 = Commit(h(), "merge C → A", "Maint", "m@x", tick.next(), listOf(a2.hash, c2.hash), "branch/A")
        // C ← merge A
        val c3 = Commit(h(), "merge A → C", "Maint", "m@x", tick.next(), listOf(c2.hash, a3.hash), "branch/C")

        // main берёт C
        val m1 = Commit(h(), "merge C → main", "Maint", "m@x", tick.next(), listOf(b.hash, c3.hash), "main")
        return listOf(b, a1, c1, a2, c2, a3, c3, m1)
    }

    private fun scenarioOctopus(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 7 * 60_60_1000L, 15)
        fun h() = generateHash()
        val m1 = Commit(h(), "init", "Dev", "d@x", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "work", "Dev", "d@x", tick.next(), listOf(m1.hash), "main")

        val f1 = Commit(h(), "f1", "F", "f@x", tick.next(), listOf(m2.hash), "feature/f1")
        val f2 = Commit(h(), "f2", "F", "f@x", tick.next(), listOf(m2.hash), "feature/f2")
        val f3 = Commit(h(), "f3", "F", "f@x", tick.next(), listOf(m2.hash), "feature/f3")

        // Octopus merge на main: родители [mainTip, f1, f2, f3]
        val m3 = Commit(h(), "octopus merge", "Maint", "m@x", tick.next(), listOf(m2.hash, f1.hash, f2.hash, f3.hash), "main")
        val m4 = Commit(h(), "after octopus", "Dev", "d@x", tick.next(), listOf(m3.hash), "main")
        return listOf(m1, m2, f1, f2, f3, m3, m4)
    }

    private fun scenarioOrphan(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 6 * 60_60_1000L, 12)
        fun h() = generateHash()
        val m1 = Commit(h(), "init", "Dev", "d@x", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "main work", "Dev", "d@x", tick.next(), listOf(m1.hash), "main")

        // Ветка experiment от m1, никогда не мерджится
        val e1 = Commit(h(), "exp: try 1", "Exp", "e@x", tick.next(), listOf(m1.hash), "experiment")
        val e2 = Commit(h(), "exp: try 2", "Exp", "e@x", tick.next(), listOf(e1.hash), "experiment")

        val m3 = Commit(h(), "main continue", "Dev", "d@x", tick.next(), listOf(m2.hash), "main")
        return listOf(m1, m2, e1, e2, m3)
    }

    private fun scenarioRebaseLike(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 5 * 60_60_1000L, 10)
        fun h() = generateHash()
        val m1 = Commit(h(), "base", "Dev", "d@x", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "main 2", "Dev", "d@x", tick.next(), listOf(m1.hash), "main")

        val f1 = Commit(h(), "F1", "A", "a@x", tick.next(), listOf(m2.hash), "feature/rebase")
        val f2 = Commit(h(), "F2", "A", "a@x", tick.next(), listOf(f1.hash), "feature/rebase")

        val m3 = Commit(h(), "main 3", "Dev", "d@x", tick.next(), listOf(m2.hash), "main")

        // «Переписанные» коммиты после rebase на m3
        val rf1 = Commit(h(), "F1'", "A", "a@x", tick.next(), listOf(m3.hash), "feature/rebase")
        val rf2 = Commit(h(), "F2'", "A", "a@x", tick.next(), listOf(rf1.hash), "feature/rebase")

        // Итог: видно старую цепочку F1–F2 и новую F1'–F2' без merge
        val m4 = Commit(h(), "main 4", "Dev", "d@x", tick.next(), listOf(m3.hash), "main")
        return listOf(m1, m2, f1, f2, m3, rf1, rf2, m4)
    }

    private fun scenarioSparse(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 4 * 60_60_1000L, 9)
        fun h() = generateHash()
        val m1 = Commit(h(), "init", "Dev", "d@x", tick.next(), emptyList(), "main")
        val m2 = Commit(h(), "work", "Dev", "d@x", tick.next(), listOf(m1.hash), "main")

        // Коммит, ссылающийся на отсутствующего родителя (суррогатная проверка устойчивости)
        val missingParent = "ffffffffffff"
        val x1 = Commit(h(), "edge: missing parent", "X", "x@x", tick.next(), listOf(missingParent), "edge/missing")

        val m3 = Commit(h(), "continue", "Dev", "d@x", tick.next(), listOf(m2.hash), "main")
        return listOf(m1, m2, x1, m3)
    }

    private fun scenarioTags(): List<Commit> {
        val tick = Ticker(System.currentTimeMillis() - 3 * 60_60_1000L, 8)
        fun h() = generateHash()
        val m1 = Commit(h(), "init", "Dev", "d@x", tick.next(), emptyList(), "main", tags = listOf("v0.1.0"))
        val m2 = Commit(h(), "feat: api", "Dev", "d@x", tick.next(), listOf(m1.hash), "main", tags = listOf("beta"))
        val m3 = Commit(h(), "fix: api", "Dev", "d@x", tick.next(), listOf(m2.hash), "main")
        val f1 = Commit(h(), "feat: ui", "UI", "u@x", tick.next(), listOf(m2.hash), "feature/ui", tags = listOf("WIP"))
        val f2 = Commit(h(), "feat: ui grid", "UI", "u@x", tick.next(), listOf(f1.hash), "feature/ui")
        val m4 = Commit(h(), "merge ui", "Maint", "m@x", tick.next(), listOf(m3.hash, f2.hash), "main", tags = listOf("v0.2.0"))
        return listOf(m1, m2, m3, f1, f2, m4)
    }

    // ---------- File changes (simple) ----------

    private fun generateChangesForRepo(): List<FileChange> {
        val pool = listOf(
            FileChange("app/build.gradle.kts", ChangeStatus.MODIFIED, 12, 3),
            FileChange("README.md", ChangeStatus.MODIFIED, 5, 1),
            FileChange("app/src/main/AndroidManifest.xml", ChangeStatus.MODIFIED, 2, 0),
            FileChange("app/src/main/java/com/example/MainActivity.kt", ChangeStatus.RENAMED, 0, 0),
            FileChange("app/src/main/res/layout/activity_main.xml", ChangeStatus.ADDED, 42, 0),
            FileChange("docs/changelog.md", ChangeStatus.ADDED, 18, 0),
            FileChange("legacy/unused.txt", ChangeStatus.DELETED, 0, 10)
        )
        return pool.shuffled().take(Random.nextInt(3, pool.size))
    }

    // ---------- Utils ----------

    private fun generateHash(): String {
        val chars = "0123456789abcdef"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
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
