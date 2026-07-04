# CHANGELOG

Все заметные изменения проекта фиксируются здесь.

## 2026-07-04 (4)

### Changed — конфликт-индикатор переехал в хэдер (экономия места)
- Большой баннер операции убран из вкладки «Изменения». Вместо него — `TopAppBar` в MainScreen становится красным (`errorContainer`) при merge/rebase: показывает «Merge/Rebase · N конфликтов» (или «готов — закоммитьте/продолжите»), иконка ветвления заменяется на «Отменить» (+ «Продолжить» для rebase, активна когда конфликтов не осталось). Тап по заголовку → попап со списком конфликтующих файлов и кнопкой «Открыть Изменения».
- Состояние операции поднято в `MainViewModel` (`operationState`, `conflictPaths`, `refreshOperationState`, `abortMergeOrRebase`, `continueRebase`). Двусторонняя синхронизация с вкладкой «Изменения»: резолвы там обновляют счётчик в хэдере (`onRepoStateChanged`), abort/continue из хэдера перезагружают вкладку (`operationSignal`). Хэдер виден на всех вкладках — постоянное напоминание о незавершённой операции; переживает перезапуск (merge, оставленный в прошлой сессии, виден на старте).
- `ConflictOperationBanner` и `ChangesViewModel.continueRebase/abortOperation` удалены как дублирующие.

### Changed — панель коммита
- Компактный режим: действия — иконки без подписей (Stage All, Stash, Fetch, Pull ↓N, Push ↑N со счётчиками-бейджами), равномерно распределены (`SpaceEvenly`). В режиме «Детали» те же действия показываются табличкой «иконка + подпись» по 3 в ряд + описание/amend. Тумблер «Детали» — снизу, low-emphasis.
- Хэдер конфликта теперь обновляется сразу после merge/rebase из диалога веток (`onBranchChanged` дополнительно дёргает `refreshOperationState` — merge не меняет полей Repository, поэтому раньше индикатор ждал переключения вкладки).

## 2026-07-04 (3)

### Fixed — конфликты merge/rebase не резолвились во вкладке «Изменения»
- **Корневая причина:** `ChangesViewModel` переживает переключение вкладок и грузил список один раз в `init` — конфликт, созданный merge/rebase из диалога веток, не появлялся в списке без pull-to-refresh. Добавлен `refresh()` (перечитывает состояние, НЕ затирая набранное сообщение коммита) + `LaunchedEffect(Unit)` на входе во вкладку; pull-to-refresh тоже переведён на `refresh()` (раньше стирал сообщение).
- Кнопка Commit блокировалась при merge, разрешённом целиком в OURS (`stagedFiles.isEmpty()`), — добавлен escape hatch `isMerging`; при неразрешённых конфликтах Commit заблокирован всегда.
- **ours/theirs при rebase:** git инвертирует стороны во время rebase (stage 2 = ветка, НА которую перебазируемся). Кнопки «оставить текущую/взять входящую» теперь флипают стратегию при `REBASING`, честно выполняя то, что обещает надпись.
- Префилл сообщения merge-коммита из `.git/MERGE_MSG` (новый метод `getMergeMessage` в `IGitRepository`).

### Added — UX конфликтов в «Изменениях»
- Баннер операции: при MERGING — «разрешите конфликты и закоммитьте» + Отменить; при REBASING — + «Продолжить» (активна только когда конфликтов не осталось; сам коммит делает rebase continue, панель коммита на время rebase скрывается).
- `continueRebase()` / `abortOperation()` прямо из вкладки «Изменения».
- Отдельная секция «Конфликты (N)» первой в списке файлов (не тонет среди unstaged, всегда карточками с кнопками резолва даже в tree-режиме).

### Changed — компактная панель коммита (перевёрстка)
- `CommitSection`: одна строка «сообщение + Commit», тонкая полоска иконок-действий (Stage All, Stash, Fetch, Pull ↓N, Push ↑N — с бейджами счётчиков) и редко используемое (описание, amend) убрано за тумблер «Детали». Высота панели сократилась примерно втрое; список файлов получил освободившееся место.

## 2026-07-04 (2)

### Added — Merge / rebase веток (P2)
- `IGitRepository` + `GitRepository`: `mergeBranch`, `abortMerge`, `rebaseCurrentOnto`, `rebaseContinue`, `rebaseAbort`, `getRepositoryState`.
- `GitRepositoryBranches.kt`: реализации через JGit (`merge().include(ref)`, `rebase().setUpstream/CONTINUE/ABORT`); конфликты возвращаются как `GitResult.Failure.Conflict` с путями; abort merge — hard-reset к HEAD; `getRepositoryState` мапит `RepositoryState` в `RepoOperationState` (NONE/MERGING/REBASING/OTHER).
- Новый enum `RepoOperationState` в `Models.kt`.
- `BranchesViewModel`: `mergeBranch`/`rebaseOnto`/`continueRebase`/`abortOperation`, поле `operationState`, сигнал `mutationSignal`. Единый `runOp` с guard + перезагрузкой состояния.
- `BranchManagementDialog`: меню действий на ветке (Merge/Rebase) с диалогами подтверждения; баннер при незавершённой операции (Continue для rebase / Abort) с указанием разрешить конфликты в Changes; inline-карточка успеха.
- **Убрано авто-закрытие диалога** (P1.5): вместо `LaunchedEffect(uiState.message)`, закрывавшего диалог на любое сообщение, — сигнал `mutationSignal` обновляет состояние репозитория в `MainScreen` без закрытия. Диалог закрывается только явно. Это необходимо для сценария «merge/rebase с конфликтом».

### Fixed
- `commitImpl` (найдено тестом): merge-коммит больше не блокируется как `NoStagedChanges`, если конфликт разрешён целиком в OURS (дерево совпадает с HEAD) — при наличии `MERGE_HEAD` коммит разрешён.

### Added — тесты
- `GitRepositoryMergeRebaseTest`: fast-forward merge, конфликт merge → `Conflict` + состояние MERGING, abort merge; rebase clean replay, rebase-конфликт → REBASING, rebase abort; commit после merge-резолва в OURS (регресс на фикс `commitImpl`).

## 2026-07-04

### Fixed — P0 критичные (release / данные / безопасность)
- **R8 + Gson (release-сборка ломала Bitbucket/Gitea/Azure):** `proguard-rules.pro` — точечные keep-правила заменены на `-keep class com.gitflow.android.data.auth.** { *; }`. Обфускация полей DTO больше не рушит десериализацию Gson в minified-сборке.
- **Краш-луп после restore из бэкапа:** добавлены `res/xml/backup_rules.xml` и `res/xml/data_extraction_rules.xml`, исключающие `auth_prefs_encrypted` из cloud-backup и device-transfer; подключены в манифесте (`fullBackupContent`, `dataExtractionRules`). В `AuthManager` создание `EncryptedSharedPreferences` обёрнуто в try/catch: при `AEADBadTagException`/повреждении файл сбрасывается и пересоздаётся вместо падения на старте.
- **Утечка токена через look-alike host:** `GitRepository.resolveCredentialsProvider` и `resolveCommitIdentity`, `AuthManager.getRepositoryApproximateSize` — `host.contains("github.com")` заменён на строгий `hostMatches` (equals или `.endsWith(".domain")`). `github.com.attacker.com` больше не принимается за GitHub. `resolveCommitIdentity` теперь матчит по `URIish.host`, а не по подстроке всего URL.

### Fixed — OAuth WebView (P0.5)
- `OAuthActivity`: redirect (`gitflow://`) перехватывается в `shouldOverrideUrlLoading` с `return true` — WebView больше не пытается грузить кастомную схему (устраняет `ERR_UNKNOWN_URL_SCHEME` на части устройств). Обработка в `onPageStarted` оставлена как fallback.
- `onReceivedError` игнорирует ошибки суб-ресурсов (`isForMainFrame != true`) — сбой favicon/аналитики/заблокированного DNS-фильтром домена больше не срывает авторизацию.
- Терминальные callback'и (`onCodeReceived`/`onError`) сделаны идемпотентными через `AtomicBoolean` — код не срабатывает дважды.

### Fixed — надёжность (P1)
- `TokenRefreshWorker`: добавлен constraint `NetworkType.CONNECTED`; политика `KEEP` → `UPDATE`, чтобы ограничение применилось и на уже установленных копиях. Больше не крутит retry в офлайне.
- `CloneRepositoryService`: клонирование больше не прерывается при отсутствии `POST_NOTIFICATIONS`. `startForeground()` не требует этого разрешения; прогресс остаётся виден в приложении через `CloneProgressTracker`.

### Added — Regression-тесты git-слоя (P1)
- Зависимости для JVM-тестов: `robolectric:4.14.1`, `androidx.test:core:1.6.1`, `mockk:1.13.13`, `kotlinx-coroutines-test:1.10.2`; `testOptions.unitTests` в `build.gradle.kts`.
- `GitRepositoryIndexTest` — stage (одиночный/`stageAll`), unstage, discard (tracked revert + untracked delete), commit (+ `NoStagedChanges`), amend, `getChangedFiles`, полный сценарий merge-конфликта (`getMergeConflicts` → `resolveConflict(OURS)` → commit). Реальный JGit-репозиторий во временной папке под Robolectric; идентичность коммита — из git config.
- `GitRepositoryCredentialsTest` — host matching в `resolveCredentialsProvider`: точный хост и субдомен → credentials, **look-alike `github.com.attacker.com` → null** (регресс-тест на фикс P0.3), embedded userInfo, blank/unknown → null.
- `RepositoryDataStore` corrupt-JSON тест отложен: `preferencesDataStore` — глобальный singleton-делегат, флаки под Robolectric без рефактора на инъекцию DataStore (см. `RECOMMENDATIONS.md`).

### Docs
- `RECOMMENDATIONS.md` переписан: детальный план P0–P3 с пошаговыми инструкциями, аудит UI-консистентности (P1.5), план фич (merge/rebase, single-branch clone, PR/MR, SSH, blame); отмечены выполненные тесты.
- `AUTH_ISSUES.md` — добавлены issue #13–#15 (утечка токена, R8, бэкап).

## 2026-02-25

### Added — Koin 4.0 DI
- `di/AppModule.kt` — Koin-модуль: синглтоны `AuthManager`, `AppSettingsManager`, `IGitRepository`; все 8 ViewModels зарегистрированы (`koinViewModel` / `parametersOf` для runtime-параметров).
- `koin-android`, `koin-compose`, `koin-androidx-compose` добавлены в `build.gradle.kts`.
- `GitFlowApplication` — `startKoin { androidContext; modules(appModule) }`.
- ProGuard-правила для Koin добавлены в `proguard-rules.pro`.

### Changed — DI migration
- `GitRepository` — конструктор принимает `AuthManager` напрямую (инжектируется Koin); `by lazy { AuthManager(context) }` удалён.
- `MainViewModel`, `SettingsViewModel`, `RepositoryListViewModel` — конструкторы получают зависимости через DI.
- `CloneRepositoryService` — реализует `KoinComponent`; `gitRepository: IGitRepository by inject()` вместо ручного создания.
- Все экраны (`MainScreen`, `RepositoryListScreen`, `ChangesScreen`, `CommitDetailDialog`, `BranchManagementDialog`, `SettingsScreen`, `AuthScreen`, `RemoteRepositoriesScreen`) — `koinViewModel()` / `koinInject()` вместо `viewModel(factory = ...)` и `remember { }`.
- Удалены все вложенные `Factory`-классы из ViewModels (`RepositoryListViewModel`, `BranchesViewModel`, `ChangesViewModel`, `CommitDetailViewModel`).

### Fixed — Compiler warnings
- AutoMirrored Material Icons: `ArrowBack`, `ArrowForward`, `Sort`, `List`, `Send` и 8 других мигрированы на `Icons.AutoMirrored.Filled.*`.
- Compose API: `Divider` → `HorizontalDivider`, `TabRow` → `PrimaryTabRow`, `ScrollableTabRow` → `PrimaryScrollableTabRow`, `LinearProgressIndicator` — lambda-форма.
- `OAuthActivity`: deprecated `onReceivedError(WebView, Int, String, String)` → `onReceivedError(WebView, WebResourceRequest, WebResourceError)`.
- Koin DSL: импорт `viewModel` DSL перенесён на `org.koin.core.module.dsl.viewModel`.
- `SettingsScreen`: `LocalLifecycleOwner` → `androidx.lifecycle.compose`.

### Fixed — IDE
- Явная зависимость `koin-compose` добавлена в `build.gradle.kts` для корректной индексации в IDE (транзитивная зависимость не всегда разрешается).

### Docs
- Актуализированы `PROJECT_ANALYSIS.md`, `RECOMMENDATIONS.md`.
- Актуализированы `.claude/skills/gitflow-android-context/{SKILL.md, ARCHITECTURE.md, QUICK_REFERENCE.md}`.

## 2026-02-22 (2)

### Added — Branch Management UI
- `BranchManagementDialog.kt` — диалог управления ветками: список локальных/удалённых, поиск, ahead/behind chips, переключение, удаление, создание новой ветки с опцией checkout.
- `BranchesViewModel.kt` — ViewModel с guard-mutex, loadBranches/checkoutBranch/createBranch/deleteBranch, Factory per repository.
- `IGitRepository` — добавлены `checkoutBranch`, `createBranch`, `deleteBranch`.
- `GitRepositoryBranches.kt` — реализации: remote checkout с `RefAlreadyExistsException` fallback и `TRACK`-режимом.
- `MainScreen.kt` — кнопка `CallSplit` в `TopAppBar`; `showBranchDialog` state; вызов `viewModel.refreshRepositories()` после операции.
- Строки EN/RU: 15 новых ключей `branches_*`.

## 2026-02-22

### Added
- `ChangesScreen`: добавлен tree/list режим для изменённых файлов.
- `ChangesScreen`: добавлены long-press actions для файлов (история, копирование имени/пути, reset changes).
- `ChangesScreen`: добавлен просмотр diff по клику на файл (unified / side-by-side).
- `ChangesScreen`: добавлен полноэкранный diff dialog.
- `ChangesViewModel`: добавлена пакетная операция toggle для файлов/папок.
- `IGitRepository`: добавлены методы `getWorkingFileDiff`, `discardFileChanges`, `getFileHistoryForPath`.
- `GitRepositoryIndex`: добавлена реализация `discardFileChangesImpl` и `getWorkingFileDiffImpl`.
- `GitRepositoryFiles`: добавлена реализация `getFileHistoryForPathImpl`.

### Changed
- `ChangesScreen`: кнопка сворачивания области commit/fetch/pull теперь рисуется поверх контента.
- `ChangesScreen`: кнопка сворачивания выровнена по центру и поднята выше.
- `ChangesScreen`: список файлов занимает максимум доступной высоты.
- `FileChangeCard`: разделены действия — клик по карточке открывает diff, checkbox отвечает только за stage/unstage.
- Path truncation в UI теперь рассчитывается по фактической ширине текста (а не по фиксированному числу символов).

### Fixed
- Исправлено падение при выборе активного репозитория (`removeLast()` на `List` в рантайме Android).
- Исправлена ошибка при unstage: `The combination of arguments <paths>... and [--mixed] | --soft | --hard] is not allowed`.
- Исправлена недостающая иконка `Cloud` (compile error в `EnhancedGraphScreen`).

### Docs
- Актуализированы `PROJECT_ANALYSIS.md`, `RECOMMENDATIONS.md`.
- Актуализированы `.claude/skills/gitflow-android-context/{SKILL.md, ARCHITECTURE.md, QUICK_REFERENCE.md}`.
