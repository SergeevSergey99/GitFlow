package com.gitflow.android.data.repository

import android.content.Context
import com.gitflow.android.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository as JGitRepository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.eclipse.jgit.lib.ProgressMonitor

data class CloneProgress(
    val stage: String = "",
    val progress: Float = 0f,
    val total: Int = 0,
    val completed: Int = 0,
    val logs: List<String> = emptyList(),
    val estimatedTimeRemaining: String = "",
    val isCancellable: Boolean = true
)

class CloneProgressCallback : ProgressMonitor {
    private val _progress = MutableStateFlow(CloneProgress())
    val progress: StateFlow<CloneProgress> = _progress.asStateFlow()

    private val logs = mutableListOf<String>()
    private var currentStage = ""
    private var totalWork = 0
    private var completedWork = 0
    private var cancelled = false
    private var startTime = 0L
    private val progressHistory = mutableListOf<Pair<Long, Int>>() // timestamp, completed work

    override fun start(totalTasks: Int) {
        android.util.Log.d("CloneProgress", "start() called with totalTasks: $totalTasks")
        totalWork = totalTasks
        completedWork = 0
        currentStage = "Starting clone..."
        startTime = System.currentTimeMillis()
        progressHistory.clear()
        addLog("Starting repository clone...")
        updateProgress()
    }

    override fun beginTask(title: String, totalWork: Int) {
        android.util.Log.d("CloneProgress", "beginTask() called: $title, totalWork: $totalWork")
        currentStage = title
        this.totalWork = totalWork
        this.completedWork = 0
        addLog("Starting: $title")
        updateProgress()
    }

    override fun update(completed: Int) {
        completedWork += completed

        // Записываем прогресс для расчета времени
        val now = System.currentTimeMillis()
        progressHistory.add(Pair(now, completedWork))

        // Оставляем только последние 10 записей для расчета средней скорости
        if (progressHistory.size > 10) {
            progressHistory.removeAt(0)
        }

        updateProgress()
    }

    override fun endTask() {
        addLog("Completed: $currentStage")
        updateProgress()
    }

    override fun isCancelled(): Boolean = cancelled

    fun cancel() {
        cancelled = true
        addLog("Clone operation cancelled by user")
        updateProgress()
    }

    private fun addLog(message: String) {
        logs.add("[${System.currentTimeMillis() % 100000}] $message")
        if (logs.size > 50) {
            logs.removeAt(0)
        }
    }

    private fun updateProgress() {
        val progressPercentage = if (totalWork > 0) {
            (completedWork.toFloat() / totalWork.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val estimatedTime = calculateEstimatedTime()

        val newProgress = CloneProgress(
            stage = currentStage,
            progress = progressPercentage,
            total = totalWork,
            completed = completedWork,
            logs = logs.toList(),
            estimatedTimeRemaining = estimatedTime,
            isCancellable = !cancelled && totalWork > 0
        )

        android.util.Log.d("CloneProgress", "updateProgress: $newProgress")
        _progress.value = newProgress
    }

    private fun calculateEstimatedTime(): String {
        if (totalWork <= 0 || completedWork <= 0 || progressHistory.size < 2) {
            return "Calculating..."
        }

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - startTime

        // Используем данные за последние несколько секунд для более точного расчета
        val recentHistory = progressHistory.filter {
            currentTime - it.first <= 5000 // последние 5 секунд
        }

        if (recentHistory.size < 2) {
            return "Calculating..."
        }

        val firstEntry = recentHistory.first()
        val lastEntry = recentHistory.last()

        val timeSpan = lastEntry.first - firstEntry.first
        val workSpan = lastEntry.second - firstEntry.second

        if (timeSpan <= 0 || workSpan <= 0) {
            return "Calculating..."
        }

        val workRemaining = totalWork - completedWork
        val averageSpeed = workSpan.toDouble() / timeSpan.toDouble() // work per millisecond
        val estimatedTimeMs = (workRemaining / averageSpeed).toLong()

        return formatTime(estimatedTimeMs)
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}

class RealGitRepository(private val context: Context) {

    private val dataStore = RepositoryDataStore(context)

    // ---------- Public API ----------

    fun getRepositoriesFlow(): Flow<List<Repository>> = dataStore.repositories

    suspend fun getRepositories(): List<Repository> = withContext(Dispatchers.IO) {
        dataStore.repositories.first()
    }

    suspend fun addRepository(path: String): Result<Repository> = withContext(Dispatchers.IO) {
        try {
            val repoDir = if (path.startsWith("content://")) {
                // Handle URI from Storage Access Framework
                val uri = Uri.parse(path)
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                if (documentFile == null || !documentFile.exists()) {
                    return@withContext Result.failure(Exception("Cannot access directory"))
                }
                // For SAF URIs, we need a different approach
                // This is a simplified implementation - in production you'd need more robust URI handling
                return@withContext Result.failure(Exception("SAF URI support needs additional implementation"))
            } else {
                File(path)
            }
            
            if (!repoDir.exists() || !File(repoDir, ".git").exists()) {
                return@withContext Result.failure(Exception("Directory is not a Git repository: $path"))
            }

            val git = Git.open(repoDir)
            val jgitRepo = git.repository

            val name = repoDir.name
            val currentBranch = jgitRepo.branch
            val lastCommit = getLastCommitTime(git)

            // Получаем информацию о ветках
            val branches = getBranchesInternal(git)
            val totalBranches = branches.size

            // Проверяем наличие remote origin
            val hasRemoteOrigin = git.remoteList().call().any { it.name == "origin" }

            val repository = Repository(
                id = UUID.randomUUID().toString(),
                name = name,
                path = path,
                lastUpdated = lastCommit,
                currentBranch = currentBranch,
                totalBranches = totalBranches,
                hasRemoteOrigin = hasRemoteOrigin
            )

            dataStore.addRepository(repository)
            git.close()

            Result.success(repository)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRepository(name: String, localPath: String): Result<Repository> = withContext(Dispatchers.IO) {
        try {
            val repoDir = File(localPath)
            if (repoDir.exists()) {
                return@withContext Result.failure(Exception("Directory already exists: ${repoDir.absolutePath}"))
            }

            // Создаем директорию для репозитория
            repoDir.mkdirs()

            // Инициализируем новый Git репозиторий
            val git = Git.init()
                .setDirectory(repoDir)
                .call()

            // Создаем начальный коммит с README файлом
            val readmeFile = File(repoDir, "README.md")
            readmeFile.writeText("# $name\n\nThis repository was created with GitFlow Android.")
            
            git.add().addFilepattern("README.md").call()
            git.commit()
                .setMessage("Initial commit")
                .setAuthor("GitFlow Android", "gitflow@android.local")
                .call()

            val currentBranch = git.repository.branch
            val lastCommit = getLastCommitTime(git)

            // Получаем информацию о ветках
            val branches = getBranchesInternal(git)
            val totalBranches = branches.size

            val repository = Repository(
                id = UUID.randomUUID().toString(),
                name = name,
                path = repoDir.absolutePath,
                lastUpdated = lastCommit,
                currentBranch = currentBranch,
                totalBranches = totalBranches,
                hasRemoteOrigin = false
            )

            dataStore.addRepository(repository)
            git.close()

            Result.success(repository)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cloneRepository(
        url: String,
        localPath: String,
        customDestination: String? = null,
        progressCallback: CloneProgressCallback? = null
    ): Result<Repository> = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (!customDestination.isNullOrEmpty()) {
                File(customDestination)
            } else {
                File(localPath)
            }
            
            if (targetDir.exists()) {
                return@withContext Result.failure(Exception("Directory already exists: ${targetDir.absolutePath}"))
            }

            // Создаем родительские директории если они не существуют
            targetDir.parentFile?.mkdirs()

            android.util.Log.d("RealGitRepository", "Начинаем клонирование репозитория")
            android.util.Log.d("RealGitRepository", "URL: $url")
            android.util.Log.d("RealGitRepository", "Директория: ${targetDir.absolutePath}")

            // Проверяем, содержит ли URL токен
            val (cleanUrl, token) = if (url.contains("@")) {
                android.util.Log.d("RealGitRepository", "URL содержит токен, извлекаем его")
                val tokenMatch = Regex("https://([^@]+)@(.+)").find(url)
                if (tokenMatch != null) {
                    val extractedToken = tokenMatch.groupValues[1]
                    val cleanUrlValue = "https://${tokenMatch.groupValues[2]}"
                    android.util.Log.d("RealGitRepository", "Чистый URL: $cleanUrlValue")
                    android.util.Log.d("RealGitRepository", "Токен: ${extractedToken.take(7)}...")
                    Pair(cleanUrlValue, extractedToken)
                } else {
                    Pair(url, null)
                }
            } else {
                android.util.Log.d("RealGitRepository", "URL не содержит токен")
                Pair(url, null)
            }

            val cloneCommand = Git.cloneRepository()
                .setURI(cleanUrl)
                .setDirectory(targetDir)

            // Настраиваем аутентификацию если есть токен
            token?.let {
                android.util.Log.d("RealGitRepository", "Настраиваем CredentialsProvider с токеном")
                val credentialsProvider = org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(it, "")
                cloneCommand.setCredentialsProvider(credentialsProvider)
            }

            // Добавляем прогресс мониторинг если предоставлен
            progressCallback?.let {
                android.util.Log.d("RealGitRepository", "Setting progress monitor: $it")
                cloneCommand.setProgressMonitor(it)
            }

            val git = try {
                cloneCommand.call()
            } catch (e: Exception) {
                // Проверяем, была ли операция отменена
                if (progressCallback?.isCancelled() == true) {
                    android.util.Log.d("RealGitRepository", "Клонирование отменено пользователем")
                    // Очищаем частично загруженные файлы
                    if (targetDir.exists()) {
                        targetDir.deleteRecursively()
                        android.util.Log.d("RealGitRepository", "Удалены частично загруженные файлы: ${targetDir.absolutePath}")
                    }
                    return@withContext Result.failure(Exception("Clone cancelled by user"))
                } else {
                    throw e
                }
            }
            android.util.Log.d("RealGitRepository", "Клонирование завершено успешно")

            val name = targetDir.name
            val currentBranch = git.repository.branch
            val lastCommit = getLastCommitTime(git)

            // Получаем информацию о ветках
            val branches = getBranchesInternal(git)
            val totalBranches = branches.size

            // Проверяем наличие remote origin (обычно есть после клонирования)
            val hasRemoteOrigin = git.remoteList().call().any { it.name == "origin" }

            val repository = Repository(
                id = UUID.randomUUID().toString(),
                name = name,
                path = targetDir.absolutePath,
                lastUpdated = lastCommit,
                currentBranch = currentBranch,
                totalBranches = totalBranches,
                hasRemoteOrigin = hasRemoteOrigin
            )

            dataStore.addRepository(repository)
            git.close()

            Result.success(repository)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeRepository(repositoryId: String) = withContext(Dispatchers.IO) {
        dataStore.removeRepository(repositoryId)
    }

    suspend fun removeRepositoryWithFiles(repositoryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Сначала получаем информацию о репозитории
            val repositories = getRepositories()
            val repository = repositories.find { it.id == repositoryId }
                ?: return@withContext Result.failure(Exception("Repository not found"))

            // Удаляем файлы репозитория
            val repoDir = File(repository.path)
            if (repoDir.exists()) {
                val deleted = repoDir.deleteRecursively()
                if (!deleted) {
                    return@withContext Result.failure(Exception("Failed to delete repository files"))
                }
            }

            // Удаляем из списка
            dataStore.removeRepository(repositoryId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRepository(repository: Repository) = withContext(Dispatchers.IO) {
        dataStore.updateRepository(repository)
    }

    suspend fun refreshRepository(repository: Repository): Repository? = withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext null

            val currentBranch = git.repository.branch
            val lastCommit = getLastCommitTime(git)

            // Получаем обновленную информацию о ветках
            val branches = getBranchesInternal(git)
            val totalBranches = branches.size

            // Проверяем наличие remote origin
            val hasRemoteOrigin = git.remoteList().call().any { it.name == "origin" }

            val updatedRepository = repository.copy(
                currentBranch = currentBranch,
                lastUpdated = lastCommit,
                totalBranches = totalBranches,
                hasRemoteOrigin = hasRemoteOrigin
            )

            updateRepository(updatedRepository)
            git.close()

            updatedRepository
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCommits(repository: com.gitflow.android.data.models.Repository): List<Commit> = withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext emptyList()

            val commits = mutableListOf<Commit>()
            val revWalk = RevWalk(git.repository)

            // Устанавливаем топологическую сортировку
            revWalk.sort(org.eclipse.jgit.revwalk.RevSort.TOPO)

            // Получаем все ветки
            val branches = git.branchList().call()
            val remoteBranches = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call()

            val allRefs = branches + remoteBranches
            val processedCommits = mutableSetOf<String>()

            // Создаем мапу коммит -> список веток, где он является HEAD
            val commitToBranchHeads = mutableMapOf<String, MutableList<String>>()

            // Сначала определяем HEAD коммиты для каждой ветки
            for (ref in allRefs) {
                try {
                    val branchName = getBranchName(ref)
                    val head = git.repository.resolve(ref.objectId.name)

                    if (head != null) {
                        val headCommitHash = head.name
                        commitToBranchHeads.getOrPut(headCommitHash) { mutableListOf() }.add(branchName)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            // Добавляем все ветки как стартовые точки
            for (ref in allRefs) {
                try {
                    val head = git.repository.resolve(ref.objectId.name)
                    if (head != null) {
                        val revCommit = revWalk.parseCommit(head)
                        revWalk.markStart(revCommit)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            // Проходим по коммитам в топологическом порядке
            for (commit in revWalk) {
                if (processedCommits.contains(commit.id.name)) continue
                processedCommits.add(commit.id.name)

                val parents = commit.parents.map { it.id.name }
                val tags = getTagsForCommit(git, commit.id.name)
                val branchHeads = commitToBranchHeads[commit.id.name] ?: emptyList()
                val isMergeCommit = commit.parentCount > 1

                // Определяем основную ветку для коммита
                val mainBranch = branchHeads.firstOrNull() ?: "unknown"

                commits.add(
                    Commit(
                        hash = commit.id.name,
                        message = commit.shortMessage,
                        description = commit.fullMessage.removePrefix(commit.shortMessage).trim(),
                        author = commit.authorIdent.name,
                        email = commit.authorIdent.emailAddress,
                        timestamp = commit.authorIdent.`when`.time,
                        parents = parents,
                        branch = mainBranch,
                        tags = tags,
                        branchHeads = branchHeads,
                        isMergeCommit = isMergeCommit
                    )
                )
            }

            revWalk.close()
            git.close()

            commits
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChangedFiles(repository: com.gitflow.android.data.models.Repository): List<FileChange> = withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext emptyList()

            val status = git.status().call()
            val changes = mutableListOf<FileChange>()

            // Добавленные файлы
            status.added.forEach { path ->
                changes.add(FileChange(path, ChangeStatus.ADDED, 0, 0))
            }

            // Измененные файлы
            status.modified.forEach { path ->
                val diffInfo = getFileDiffInfo(git, path)
                changes.add(FileChange(path, ChangeStatus.MODIFIED, diffInfo.first, diffInfo.second))
            }

            // Удаленные файлы
            status.removed.forEach { path ->
                changes.add(FileChange(path, ChangeStatus.DELETED, 0, 0))
            }

            // Неотслеживаемые файлы
            status.untracked.forEach { path ->
                changes.add(FileChange(path, ChangeStatus.UNTRACKED, 0, 0))
            }

            git.close()
            changes
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCommitDiffs(commit: Commit, repository: com.gitflow.android.data.models.Repository): List<FileDiff> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("RealGitRepository", "Getting diffs for commit: ${commit.hash} in repository: ${repository.name}")

            val git = openRepository(repository.path)
            if (git == null) {
                android.util.Log.e("RealGitRepository", "Failed to open repository: ${repository.path}")
                return@withContext emptyList()
            }

            val jgitRepository = git.repository
            val revWalk = RevWalk(jgitRepository)
            val revCommit = revWalk.parseCommit(jgitRepository.resolve(commit.hash))

            val diffs = mutableListOf<FileDiff>()

            if (revCommit.parentCount > 0) {
                val parent = revWalk.parseCommit(revCommit.getParent(0).id)

                val oldTreeParser = CanonicalTreeParser()
                val newTreeParser = CanonicalTreeParser()

                jgitRepository.newObjectReader().use { reader ->
                    oldTreeParser.reset(reader, parent.tree)
                    newTreeParser.reset(reader, revCommit.tree)
                }

                val diffEntries = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call()

                android.util.Log.d("RealGitRepository", "Found ${diffEntries.size} diff entries")

                for (diffEntry in diffEntries) {
                    val outputStream = ByteArrayOutputStream()
                    val formatter = DiffFormatter(outputStream)
                    formatter.setRepository(jgitRepository)
                    formatter.format(diffEntry)
                    formatter.close()

                    val diffText = outputStream.toString()
                    android.util.Log.d("RealGitRepository", "Processing diff for file: ${diffEntry.newPath ?: diffEntry.oldPath}")
                    val fileDiff = parseDiffText(diffEntry, diffText)
                    diffs.add(fileDiff)
                }
            } else {
                // Первый коммит - показываем все файлы как добавленные
                val treeWalk = TreeWalk(jgitRepository)
                treeWalk.addTree(revCommit.tree)
                treeWalk.isRecursive = true

                while (treeWalk.next()) {
                    val path = treeWalk.pathString
                    val content = getFileContentFromCommit(jgitRepository, revCommit, path)
                    val lines = content?.split('\n')?.size ?: 0

                    diffs.add(
                        FileDiff(
                            path = path,
                            status = FileStatus.ADDED,
                            additions = lines,
                            deletions = 0,
                            hunks = listOf(
                                DiffHunk(
                                    header = "@@ -0,0 +1,$lines @@",
                                    oldStart = 0,
                                    oldLines = 0,
                                    newStart = 1,
                                    newLines = lines,
                                    lines = content?.split('\n')?.mapIndexed { index, line ->
                                        DiffLine(
                                            type = LineType.ADDED,
                                            content = line,
                                            lineNumber = index + 1,
                                            newLineNumber = index + 1
                                        )
                                    } ?: emptyList()
                                )
                            )
                        )
                    )
                }
                treeWalk.close()
            }

            revWalk.close()
            git.close()
            android.util.Log.d("RealGitRepository", "Returning ${diffs.size} diffs")
            diffs
        } catch (e: Exception) {
            android.util.Log.e("RealGitRepository", "Error getting commit diffs", e)
            emptyList()
        }
    }

    suspend fun getCommitDiffs(commit: Commit): List<FileDiff> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("RealGitRepository", "Getting diffs for commit: ${commit.hash}")

            // Находим репозиторий по коммиту
            val repositories = getRepositories()
            android.util.Log.d("RealGitRepository", "Searching through ${repositories.size} repositories for commit")

            val repo = repositories.find { repository ->
                try {
                    android.util.Log.d("RealGitRepository", "Checking repository: ${repository.name} at ${repository.path}")
                    val git = openRepository(repository.path)
                    if (git == null) {
                        android.util.Log.w("RealGitRepository", "Could not open repository at ${repository.path}")
                        return@find false
                    }
                    val hasCommit = git.repository.resolve(commit.hash) != null
                    android.util.Log.d("RealGitRepository", "Repository ${repository.name} has commit ${commit.hash}: $hasCommit")
                    git.close()
                    hasCommit
                } catch (e: Exception) {
                    android.util.Log.e("RealGitRepository", "Error checking repository ${repository.name}", e)
                    false
                }
            }

            if (repo == null) {
                android.util.Log.e("RealGitRepository", "Repository not found for commit: ${commit.hash}")
                return@withContext emptyList()
            }

            android.util.Log.d("RealGitRepository", "Found repository: ${repo.name}")

            val git = openRepository(repo.path)
            if (git == null) {
                android.util.Log.e("RealGitRepository", "Failed to open repository: ${repo.path}")
                return@withContext emptyList()
            }

            val repository = git.repository
            val revWalk = RevWalk(repository)
            val revCommit = revWalk.parseCommit(repository.resolve(commit.hash))

            val diffs = mutableListOf<FileDiff>()

            if (revCommit.parentCount > 0) {
                val parent = revWalk.parseCommit(revCommit.getParent(0).id)

                val oldTreeParser = CanonicalTreeParser()
                val newTreeParser = CanonicalTreeParser()

                repository.newObjectReader().use { reader ->
                    oldTreeParser.reset(reader, parent.tree)
                    newTreeParser.reset(reader, revCommit.tree)
                }

                val diffEntries = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call()

                android.util.Log.d("RealGitRepository", "Found ${diffEntries.size} diff entries")

                for (diffEntry in diffEntries) {
                    val outputStream = ByteArrayOutputStream()
                    val formatter = DiffFormatter(outputStream)
                    formatter.setRepository(repository)
                    formatter.format(diffEntry)
                    formatter.close()

                    val diffText = outputStream.toString()
                    android.util.Log.d("RealGitRepository", "Processing diff for file: ${diffEntry.newPath ?: diffEntry.oldPath}")
                    val fileDiff = parseDiffText(diffEntry, diffText)
                    diffs.add(fileDiff)
                }
            } else {
                // Первый коммит - показываем все файлы как добавленные
                val treeWalk = TreeWalk(repository)
                treeWalk.addTree(revCommit.tree)
                treeWalk.isRecursive = true

                while (treeWalk.next()) {
                    val path = treeWalk.pathString
                    val content = getFileContentFromCommit(repository, revCommit, path)
                    val lines = content?.split('\n')?.size ?: 0

                    diffs.add(
                        FileDiff(
                            path = path,
                            status = FileStatus.ADDED,
                            additions = lines,
                            deletions = 0,
                            hunks = listOf(
                                DiffHunk(
                                    header = "@@ -0,0 +1,$lines @@",
                                    oldStart = 0,
                                    oldLines = 0,
                                    newStart = 1,
                                    newLines = lines,
                                    lines = content?.split('\n')?.mapIndexed { index, line ->
                                        DiffLine(
                                            type = LineType.ADDED,
                                            content = line,
                                            lineNumber = index + 1,
                                            newLineNumber = index + 1
                                        )
                                    } ?: emptyList()
                                )
                            )
                        )
                    )
                }
                treeWalk.close()
            }

            revWalk.close()
            git.close()
            android.util.Log.d("RealGitRepository", "Returning ${diffs.size} diffs")
            diffs
        } catch (e: Exception) {
            android.util.Log.e("RealGitRepository", "Error getting commit diffs", e)
            emptyList()
        }
    }

    suspend fun getBranches(repository: com.gitflow.android.data.models.Repository): List<Branch> = withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext emptyList()

            val branches = mutableListOf<Branch>()

            // Локальные ветки
            val localBranches = git.branchList().call()
            for (ref in localBranches) {
                val branchName = ref.name.removePrefix("refs/heads/")
                val lastCommitHash = ref.objectId.name

                branches.add(
                    Branch(
                        name = branchName,
                        isLocal = true,
                        lastCommitHash = lastCommitHash,
                        ahead = 0, // TODO: Реализовать подсчет ahead/behind
                        behind = 0
                    )
                )
            }

            // Удаленные ветки
            val remoteBranches = git.branchList()
                .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE)
                .call()

            for (ref in remoteBranches) {
                val branchName = ref.name.removePrefix("refs/remotes/")
                val lastCommitHash = ref.objectId.name

                branches.add(
                    Branch(
                        name = branchName,
                        isLocal = false,
                        lastCommitHash = lastCommitHash,
                        ahead = 0,
                        behind = 0
                    )
                )
            }

            git.close()
            branches.sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun pull(repository: com.gitflow.android.data.models.Repository): PullResult = withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext PullResult(false, 0, listOf("Repository not found"))

            val result = git.pull().call()
            git.close()

            if (result.isSuccessful) {
                PullResult(true, 0, emptyList()) // TODO: Подсчитать количество новых коммитов
            } else {
                PullResult(false, 0, listOf("Pull failed"))
            }
        } catch (e: Exception) {
            PullResult(false, 0, listOf(e.message ?: "Unknown error"))
        }
    }

    suspend fun push(repository: com.gitflow.android.data.models.Repository): PushResult = withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext PushResult(false, 0, "Repository not found")

            val result = git.push().call()
            git.close()

            if (result.any { it.messages.isEmpty() }) {
                PushResult(true, 0, "Push successful") // TODO: Подсчитать количество отправленных коммитов
            } else {
                PushResult(false, 0, "Push failed")
            }
        } catch (e: Exception) {
            PushResult(false, 0, e.message ?: "Unknown error")
        }
    }

    suspend fun getCommitFileTree(commit: Commit, repository: com.gitflow.android.data.models.Repository): FileTreeNode = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("RealGitRepository", "Getting file tree for commit: ${commit.hash} in repository: ${repository.name}")

            val git = openRepository(repository.path)
            if (git == null) {
                android.util.Log.e("RealGitRepository", "Failed to open repository: ${repository.path}")
                return@withContext FileTreeNode("", "", FileTreeNodeType.DIRECTORY)
            }

            val jgitRepository = git.repository
            val revWalk = RevWalk(jgitRepository)
            val revCommit = revWalk.parseCommit(jgitRepository.resolve(commit.hash))

            val files = mutableListOf<CommitFileInfo>()
            val treeWalk = TreeWalk(jgitRepository)
            treeWalk.addTree(revCommit.tree)
            treeWalk.isRecursive = true

            while (treeWalk.next()) {
                val path = treeWalk.pathString
                val objectId = treeWalk.getObjectId(0)
                val size = try {
                    jgitRepository.newObjectReader().use { reader ->
                        reader.getObjectSize(objectId, org.eclipse.jgit.lib.Constants.OBJ_BLOB).toLong()
                    }
                } catch (e: Exception) {
                    0L // Если не удается получить размер, используем 0
                }

                files.add(
                    CommitFileInfo(
                        path = path,
                        size = size,
                        lastModified = commit.timestamp
                    )
                )
            }

            android.util.Log.d("RealGitRepository", "Found ${files.size} files in commit")

            treeWalk.close()
            revWalk.close()
            git.close()

            val result = buildFileTree(files)
            android.util.Log.d("RealGitRepository", "Built file tree with ${result.children.size} root items")
            result
        } catch (e: Exception) {
            android.util.Log.e("RealGitRepository", "Error getting commit file tree", e)
            FileTreeNode("", "", FileTreeNodeType.DIRECTORY)
        }
    }

    suspend fun getCommitFileTree(commit: Commit): FileTreeNode = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("RealGitRepository", "Getting file tree for commit: ${commit.hash}")

            // Находим репозиторий по коммиту
            val repositories = getRepositories()
            val repo = repositories.find { repository ->
                try {
                    val git = openRepository(repository.path)
                    val hasCommit = git?.repository?.resolve(commit.hash) != null
                    git?.close()
                    hasCommit
                } catch (e: Exception) {
                    false
                }
            }

            if (repo == null) {
                android.util.Log.e("RealGitRepository", "Repository not found for commit: ${commit.hash}")
                return@withContext FileTreeNode("", "", FileTreeNodeType.DIRECTORY)
            }

            android.util.Log.d("RealGitRepository", "Found repository: ${repo.name}")

            val git = openRepository(repo.path)
            if (git == null) {
                android.util.Log.e("RealGitRepository", "Failed to open repository: ${repo.path}")
                return@withContext FileTreeNode("", "", FileTreeNodeType.DIRECTORY)
            }

            val repository = git.repository
            val revWalk = RevWalk(repository)
            val revCommit = revWalk.parseCommit(repository.resolve(commit.hash))

            val files = mutableListOf<CommitFileInfo>()
            val treeWalk = TreeWalk(repository)
            treeWalk.addTree(revCommit.tree)
            treeWalk.isRecursive = true

            while (treeWalk.next()) {
                val path = treeWalk.pathString
                val objectId = treeWalk.getObjectId(0)
                val size = try {
                    repository.newObjectReader().use { reader ->
                        reader.getObjectSize(objectId, org.eclipse.jgit.lib.Constants.OBJ_BLOB).toLong()
                    }
                } catch (e: Exception) {
                    0L // Если не удается получить размер, используем 0
                }

                files.add(
                    CommitFileInfo(
                        path = path,
                        size = size,
                        lastModified = commit.timestamp
                    )
                )
            }

            android.util.Log.d("RealGitRepository", "Found ${files.size} files in commit")

            treeWalk.close()
            revWalk.close()
            git.close()

            val result = buildFileTree(files)
            android.util.Log.d("RealGitRepository", "Built file tree with ${result.children.size} root items")
            result
        } catch (e: Exception) {
            android.util.Log.e("RealGitRepository", "Error getting commit file tree", e)
            FileTreeNode("", "", FileTreeNodeType.DIRECTORY)
        }
    }

    suspend fun getFileContent(commit: Commit, filePath: String, repository: com.gitflow.android.data.models.Repository): String? = withContext(Dispatchers.IO) {
        try {
            val git = openRepository(repository.path) ?: return@withContext null

            val jgitRepository = git.repository
            val revWalk = RevWalk(jgitRepository)
            val revCommit = revWalk.parseCommit(jgitRepository.resolve(commit.hash))

            val content = getFileContentFromCommit(jgitRepository, revCommit, filePath)

            revWalk.close()
            git.close()

            content
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getFileContent(commit: Commit, filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            // Находим репозиторий по коммиту
            val repositories = getRepositories()
            val repo = repositories.find { repository ->
                try {
                    val git = openRepository(repository.path)
                    val hasCommit = git?.repository?.resolve(commit.hash) != null
                    git?.close()
                    hasCommit
                } catch (e: Exception) {
                    false
                }
            } ?: return@withContext null

            val git = openRepository(repo.path) ?: return@withContext null

            val repository = git.repository
            val revWalk = RevWalk(repository)
            val revCommit = revWalk.parseCommit(repository.resolve(commit.hash))

            val content = getFileContentFromCommit(repository, revCommit, filePath)

            revWalk.close()
            git.close()

            content
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Helper methods ----------

    private fun getBranchesInternal(git: Git): List<Branch> {
        return try {
            val branches = mutableListOf<Branch>()

            // Локальные ветки
            val localBranches = git.branchList().call()
            for (ref in localBranches) {
                val branchName = ref.name.removePrefix("refs/heads/")
                val lastCommitHash = ref.objectId.name

                branches.add(
                    Branch(
                        name = branchName,
                        isLocal = true,
                        lastCommitHash = lastCommitHash,
                        ahead = 0,
                        behind = 0
                    )
                )
            }

            // Удаленные ветки
            val remoteBranches = git.branchList()
                .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE)
                .call()

            for (ref in remoteBranches) {
                val branchName = ref.name.removePrefix("refs/remotes/")
                val lastCommitHash = ref.objectId.name

                branches.add(
                    Branch(
                        name = branchName,
                        isLocal = false,
                        lastCommitHash = lastCommitHash,
                        ahead = 0,
                        behind = 0
                    )
                )
            }

            branches.sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun openRepository(path: String): Git? {
        return try {
            val repoDir = File(path)
            Git.open(repoDir)
        } catch (e: Exception) {
            null
        }
    }

    private fun getLastCommitTime(git: Git): Long {
        return try {
            val commits = git.log().setMaxCount(1).call()
            commits.firstOrNull()?.authorIdent?.`when`?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun getBranchName(ref: Ref): String {
        return when {
            ref.name.startsWith("refs/heads/") -> ref.name.removePrefix("refs/heads/")
            ref.name.startsWith("refs/remotes/") -> ref.name.removePrefix("refs/remotes/")
            else -> ref.name
        }
    }

    private fun getTagsForCommit(git: Git, commitHash: String): List<String> {
        return try {
            val tags = git.tagList().call()
            val repository = git.repository
            val commitId = repository.resolve(commitHash)

            tags.filter { tag ->
                try {
                    val tagCommit = repository.resolve(tag.objectId.name)
                    tagCommit == commitId
                } catch (e: Exception) {
                    false
                }
            }.map { it.name.removePrefix("refs/tags/") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getFileDiffInfo(git: Git, filePath: String): Pair<Int, Int> {
        return try {
            val repository = git.repository
            val head = repository.resolve("HEAD")
            val headCommit = RevWalk(repository).parseCommit(head)

            if (headCommit.parentCount > 0) {
                val parentCommit = RevWalk(repository).parseCommit(headCommit.getParent(0).id)

                val oldTreeParser = CanonicalTreeParser()
                val newTreeParser = CanonicalTreeParser()

                repository.newObjectReader().use { reader ->
                    oldTreeParser.reset(reader, parentCommit.tree)
                    newTreeParser.reset(reader, headCommit.tree)
                }

                val diffs = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .setPathFilter(PathFilter.create(filePath))
                    .call()

                if (diffs.isNotEmpty()) {
                    val outputStream = ByteArrayOutputStream()
                    val formatter = DiffFormatter(outputStream)
                    formatter.setRepository(repository)
                    formatter.format(diffs.first())
                    formatter.close()

                    val diffText = outputStream.toString()
                    parseDiffStats(diffText)
                } else {
                    Pair(0, 0)
                }
            } else {
                Pair(0, 0)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    private fun parseDiffText(diffEntry: org.eclipse.jgit.diff.DiffEntry, diffText: String): FileDiff {
        val lines = diffText.split('\n')
        val hunks = mutableListOf<DiffHunk>()
        var currentHunk: DiffHunk? = null
        val hunkLines = mutableListOf<DiffLine>()

        var additions = 0
        var deletions = 0
        var oldLineNum = 0
        var newLineNum = 0

        for (line in lines) {
            when {
                line.startsWith("@@") -> {
                    // Сохраняем предыдущий hunk
                    currentHunk?.let { hunk ->
                        hunks.add(hunk.copy(lines = hunkLines.toList()))
                        hunkLines.clear()
                    }

                    // Парсим новый hunk header
                    val hunkInfo = parseHunkHeader(line)
                    oldLineNum = hunkInfo.first
                    newLineNum = hunkInfo.third
                    currentHunk = DiffHunk(
                        header = line,
                        oldStart = hunkInfo.first,
                        oldLines = hunkInfo.second,
                        newStart = hunkInfo.third,
                        newLines = hunkInfo.fourth,
                        lines = emptyList()
                    )
                }
                line.startsWith("+") && !line.startsWith("+++") -> {
                    additions++
                    hunkLines.add(DiffLine(
                        type = LineType.ADDED,
                        content = line.drop(1), // Убираем префикс +
                        lineNumber = null,
                        oldLineNumber = null,
                        newLineNumber = newLineNum
                    ))
                    newLineNum++
                }
                line.startsWith("-") && !line.startsWith("---") -> {
                    deletions++
                    hunkLines.add(DiffLine(
                        type = LineType.DELETED,
                        content = line.drop(1), // Убираем префикс -
                        lineNumber = null,
                        oldLineNumber = oldLineNum,
                        newLineNumber = null
                    ))
                    oldLineNum++
                }
                line.startsWith(" ") -> {
                    hunkLines.add(DiffLine(
                        type = LineType.CONTEXT,
                        content = line.drop(1), // Убираем префикс пробела
                        lineNumber = oldLineNum,
                        oldLineNumber = oldLineNum,
                        newLineNumber = newLineNum
                    ))
                    oldLineNum++
                    newLineNum++
                }
            }
        }

        // Добавляем последний hunk
        currentHunk?.let { hunk ->
            hunks.add(hunk.copy(lines = hunkLines.toList()))
        }

        val status = when (diffEntry.changeType) {
            org.eclipse.jgit.diff.DiffEntry.ChangeType.ADD -> FileStatus.ADDED
            org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE -> FileStatus.DELETED
            org.eclipse.jgit.diff.DiffEntry.ChangeType.MODIFY -> FileStatus.MODIFIED
            org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME -> FileStatus.RENAMED
            else -> FileStatus.MODIFIED
        }

        return FileDiff(
            path = diffEntry.newPath ?: diffEntry.oldPath,
            oldPath = if (diffEntry.oldPath != diffEntry.newPath) diffEntry.oldPath else null,
            status = status,
            additions = additions,
            deletions = deletions,
            hunks = hunks
        )
    }

    private fun parseHunkHeader(header: String): Tuple4<Int, Int, Int, Int> {
        // Формат: @@ -oldStart,oldLines +newStart,newLines @@
        val regex = """@@ -(\d+),(\d+) \+(\d+),(\d+) @@""".toRegex()
        val match = regex.find(header)

        return if (match != null) {
            val (oldStart, oldLines, newStart, newLines) = match.destructured
            Tuple4(oldStart.toInt(), oldLines.toInt(), newStart.toInt(), newLines.toInt())
        } else {
            Tuple4(0, 0, 0, 0)
        }
    }

    private fun parseDiffStats(diffText: String): Pair<Int, Int> {
        val lines = diffText.split('\n')
        var additions = 0
        var deletions = 0

        for (line in lines) {
            when {
                line.startsWith("+") && !line.startsWith("+++") -> additions++
                line.startsWith("-") && !line.startsWith("---") -> deletions++
            }
        }

        return Pair(additions, deletions)
    }

    private fun getFileContentFromCommit(repository: JGitRepository, commit: RevCommit, filePath: String): String? {
        return try {
            val treeWalk = TreeWalk(repository)
            treeWalk.addTree(commit.tree)
            treeWalk.isRecursive = true
            treeWalk.filter = PathFilter.create(filePath)

            if (treeWalk.next()) {
                val objectId = treeWalk.getObjectId(0)
                repository.newObjectReader().use { reader ->
                    val loader = reader.open(objectId)
                    String(loader.bytes)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
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

}

// Helper data class
private data class Tuple4<T1, T2, T3, T4>(val first: T1, val second: T2, val third: T3, val fourth: T4)
