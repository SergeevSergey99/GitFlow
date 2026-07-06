# Рекомендации и ближайший backlog

> Обновлено: 2026-07-04
> История изменений: `CHANGELOG.md`
> Детали по auth-слою: `AUTH_ISSUES.md`

## Выполнено

- [x] Разделён `GitRepository` по тематическим файлам + общий интерфейс `IGitRepository`.
- [x] Исправлен crash при выборе активного репозитория.
- [x] Исправлен reset-конфликт при unstage (`<paths> + --mixed/--soft/--hard`).
- [x] Улучшен `ChangesScreen`: полноэкранный diff, long-press actions, tree/list режим, сворачивание панели commit.
- [x] Добавлены действия: история файла, копирование имени/пути, reset changes.
- [x] Реализован `BranchManagementDialog` + `BranchesViewModel`: переключение/создание/удаление веток, remote tracking checkout, поиск, ahead/behind chips.
- [x] Внедрён Koin 4.0 DI: `di/AppModule.kt`, синглтоны + все ViewModels; удалены все Factory-классы; `koinViewModel()` / `koinInject()` на всех экранах.
- [x] Исправлены compiler warnings: AutoMirrored icons, Compose API (`HorizontalDivider`, `PrimaryTabRow`), OAuthActivity deprecated `onReceivedError`, Koin DSL import.
- [x] Allowlist хостов в OAuth WebView (`OAuthActivity.isHostAllowed`), state-проверка (CSRF), PKCE.
- [x] **P0.1** R8 keep-правила для всех auth-DTO (`data.auth.**`) — release больше не ломает Bitbucket/Gitea/Azure. _(2026-07-04)_
- [x] **P0.2** Backup/data-extraction rules + fallback пересоздания `EncryptedSharedPreferences` — нет краш-лупа после restore. _(2026-07-04)_
- [x] **P0.3** Строгий `hostMatches` в `GitRepository`/`AuthManager` — нет утечки токена на look-alike хост. _(2026-07-04)_
- [x] **P0.5** OAuth WebView: перехват redirect в `shouldOverrideUrlLoading`, `isForMainFrame`-guard в `onReceivedError`, идемпотентные callback'и. _(2026-07-04)_
- [x] **P1** `TokenRefreshWorker` — сетевой constraint + `UPDATE`. _(2026-07-04)_
- [x] **P1** `CloneRepositoryService` — не прерывает клон без `POST_NOTIFICATIONS`. _(2026-07-04)_
- [x] **P2** Merge/rebase из `BranchManagementDialog` + конфликтный флоу в «Изменениях»: refresh при входе во вкладку, баннер операции (Continue/Abort), секция «Конфликты», префилл MERGE_MSG, флип ours/theirs при rebase. _(2026-07-04)_
- [x] **P1.5 (частично)** Компактная панель коммита в `ChangesScreen` (одна строка + иконки с бейджами, детали за тумблером). _(2026-07-04)_

---

## P0 — критичное (ломает release-сборку, данные или безопасность) — ✅ ВЫПОЛНЕНО 2026-07-04

> Все три пункта внесены. Оставлены здесь как справка по сделанному; проверка — smoke-тест
> `assembleRelease` (login PAT Gitea/Azure, список Bitbucket) + restore-из-бэкапа на втором устройстве.

### P0.1 Release-сборка ломает Bitbucket / Gitea / Azure DevOps (R8 + Gson)

**Файл:** `app/proguard-rules.pro`

**Проблема.** Keep-правила явно сохраняют только `GitHub*` и `GitLab*` DTO. Модели
`Bitbucket*` (`BitbucketApi.kt`), `Gitea*` (`GiteaApi.kt`), `Azure*` (`AzureDevOpsApi.kt`),
а также `GitHubEmail` и `BitbucketEmail` лежат в пакете `data.auth` и под правила не
попадают. R8 обфусцирует имена полей → Gson перестаёт их маппить → в release-сборке
эти провайдеры получают пустые объекты/NPE. В debug всё работает, поэтому баг незаметен.

**Как исправить.**
1. Заменить точечные keep-правила блоком (секция `Data models`):
   ```proguard
   # Все Gson DTO auth-слоя (GitHub/GitLab/Bitbucket/Gitea/Azure)
   -keep class com.gitflow.android.data.auth.** { *; }
   ```
   Retrofit-интерфейсы уже покрыты общими правилами Retrofit — отдельные `-keep interface` можно убрать.
2. Альтернатива (чище, но дольше): проставить `@SerializedName` на все поля DTO и оставить
   только `-keepclassmembers` для аннотированных полей.
3. **Проверка:** собрать `assembleRelease`, установить и прогнать вручную: login PAT Gitea/Azure,
   список репозиториев Bitbucket. Добавить этот smoke-тест в чеклист релиза.

### P0.2 Восстановление из бэкапа = краш-луп при запуске

**Файлы:** `app/src/main/AndroidManifest.xml`, `data/auth/AuthManager.kt`

**Проблема.** `android:allowBackup="true"` без `dataExtractionRules`/`fullBackupContent`.
Файл `auth_prefs_encrypted` попадает в облачный бэкап, но ключи Android Keystore не
переносятся. После восстановления на новом устройстве `EncryptedSharedPreferences.create()`
в `init` у `AuthManager` бросает `AEADBadTagException` → Koin не собирает граф →
приложение падает на старте бесконечно.

**Как исправить.**
1. Создать `res/xml/backup_rules.xml` (API ≤ 30) и `res/xml/data_extraction_rules.xml` (API 31+),
   исключающие shared prefs `auth_prefs_encrypted`:
   ```xml
   <!-- backup_rules.xml -->
   <full-backup-content>
       <exclude domain="sharedpref" path="auth_prefs_encrypted.xml"/>
   </full-backup-content>
   ```
   ```xml
   <!-- data_extraction_rules.xml -->
   <data-extraction-rules>
       <cloud-backup>
           <exclude domain="sharedpref" path="auth_prefs_encrypted.xml"/>
       </cloud-backup>
       <device-transfer>
           <exclude domain="sharedpref" path="auth_prefs_encrypted.xml"/>
       </device-transfer>
   </data-extraction-rules>
   ```
2. Подключить в манифесте: `android:fullBackupContent="@xml/backup_rules"`,
   `android:dataExtractionRules="@xml/data_extraction_rules"`.
3. Страховка в `AuthManager.createEncryptedPreferences()`: обернуть в `try/catch`;
   при `GeneralSecurityException`/`AEADBadTagException` удалить повреждённый файл
   (`context.deleteSharedPreferences("auth_prefs_encrypted")`) и создать заново.
   Потеря токенов (пользователь перелогинится) лучше краш-лупа.

### P0.3 Утечка токена через поддельный remote URL

**Файл:** `data/repository/GitRepository.kt` → `resolveCredentialsProvider`

**Проблема.** Провайдер определяется через `host.contains("github.com")`. Хост
`github.com.attacker.com` проходит проверку — при clone/push такого URL GitHub-токен
уйдёт злоумышленнику как credentials. То же для веток gitlab/bitbucket/azure.

**Как исправить.**
1. Добавить helper (по образцу `OAuthActivity.isHostAllowed`):
   ```kotlin
   private fun hostMatches(host: String, domain: String): Boolean =
       host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)
   ```
2. Заменить все `host.contains(...)` в `resolveCredentialsProvider` на `hostMatches(host, "github.com")` и т.д.
3. Тем же способом поправить `host.contains` в `AuthManager.getRepositoryApproximateSize`
   и `uri.contains` в `resolveCommitIdentity` (там риск ниже — только подпись коммита, — но логика должна быть единой).
4. **Тест:** unit-тест на `resolveCredentialsProvider` с URL `https://github.com.evil.example/repo.git` → должен вернуть `null`.

---

## P0.5 — OAuth WebView hardening (уточнённый план)

**Файл:** `ui/auth/OAuthActivity.kt`

- [x] **Перехватывать redirect в `shouldOverrideUrlLoading`, а не в `onPageStarted`.** _(2026-07-04, fallback в `onPageStarted` оставлен, callback'и идемпотентны)_
  Сейчас для `gitflow://oauth/...` возвращается `false` («пусть WebView грузит»), а код
  ловится в `onPageStarted`. Поведение для кастомных схем различается между версиями
  WebView: на части устройств сначала прилетает `ERR_UNKNOWN_URL_SCHEME` в
  `onReceivedError` → авторизация падает. Правильно:
  ```kotlin
  if (url.startsWith(redirectUri)) {
      checkForAuthCode(url, redirectUri, expectedState, onCodeReceived, onError)
      return true   // не давать WebView грузить кастомную схему
  }
  ```
  Вызов `checkForAuthCode` из `onPageStarted` можно оставить как fallback, но добавить
  guard-флаг, чтобы callback не сработал дважды.
- [x] **`onReceivedError`: игнорировать ошибки субресурсов.** _(2026-07-04)_ Упавший favicon или
  заблокированный DNS-фильтром домен аналитики закрывает весь auth flow. Первой строкой:
  ```kotlin
  if (request?.isForMainFrame != true) return
  ```
- [ ] **Средний срок — миграция на Custom Tabs.** `androidx.browser` уже в зависимостях,
  `activity-alias` с `gitflow://oauth` уже экспортирован в манифесте — инфраструктура готова.
  Custom Tabs снимает и вопрос доверия WebView, и риск блокировки embedded-браузеров провайдерами.
- [ ] **`client_secret` в APK.** Секрет из `assets/oauth.properties` извлекается из любой сборки.
  PKCE есть, но GitHub/GitLab при наличии секрета опираются на него. Долгосрочные варианты:
  GitHub Device Flow (без секрета) либо микро-бэкенд для обмена `code` → `token`.
  Минимум сейчас: не публиковать APK с боевым секретом; `oauth.properties` в `.gitignore` (уже сделано).
- [ ] Проверить отсутствие секретов в логах auth/network и в crash-репортах.

---

## P1 — надёжность и поддерживаемость

### Тесты (regression, без эмулятора) — ЧАСТИЧНО ВЫПОЛНЕНО 2026-07-04

Подход: Robolectric даёт реальный `Context` (временная ФС для DataStore) + MockK для
`AuthManager`/`AppSettingsManager` (final-классы, которые нельзя сконструировать в тесте
из-за Keystore). Git-операции гоняются на реальном JGit-репозитории во временной папке
(`TemporaryFolder`); идентичность коммита берётся из git config, поэтому моки в git-сценариях
не участвуют. **Важно:** `@Config(application = Application::class)` — иначе Robolectric поднимет
реальный `GitFlowApplication.onCreate` (startKoin + WorkManager), и тесты падают с
`KoinApplicationAlreadyStartedException` / `WorkManager is not initialized`. (`manifest = Config.NONE`
эту проблему НЕ решает — с ресурсами от AGP кастомный Application всё равно используется.)
Зависимости добавлены в `build.gradle.kts`.

- [x] single/batch stage/unstage (`GitRepositoryIndexTest`); _(2026-07-04)_
- [x] reset/discard file changes (tracked → revert, untracked → delete); _(2026-07-04)_
- [x] commit (+ NoStagedChanges) / amend; `getChangedFiles` классификация; _(2026-07-04)_
- [x] merge с конфликтом → `getMergeConflicts` → `resolveConflict(OURS)` → commit; _(2026-07-04)_
- [x] `resolveCredentialsProvider`: точный хост, субдомен, **поддельный хост → null** (P0.3), userInfo, blank; _(2026-07-04, `GitRepositoryCredentialsTest`)_
- [ ] `RepositoryDataStore`: битый JSON → backup + пустой список. **Отложено:** `preferencesDataStore`
  — приватный top-level singleton-делегат, кэшируется на весь JVM-процесс и привязывается к
  filesDir первого теста → под Robolectric флаки. Нужен небольшой рефактор для тестируемости
  (принимать `DataStore<Preferences>` или его директорию через конструктор), после чего тест
  на `decodeRepositoriesOrNull` + backup-ключ пишется тривиально.

> **Прогнать у себя:** `./gradlew testDebugUnitTest` (первый запуск Robolectric скачает
> `android-all` для SDK 34). Проверить также, что версии `robolectric:4.14.1` / `mockk:1.13.13`
> резолвятся в вашем окружении — при необходимости обновить.

### Прочее

- [x] `TokenRefreshWorker`: добавлен constraint `NetworkType.CONNECTED` + политика `UPDATE`. _(2026-07-04)_
- [x] `CloneRepositoryService`: не завершает сервис при отсутствии `POST_NOTIFICATIONS`. _(2026-07-04)_
- [ ] Пагинация списков репозиториев: GitHub — жёстко 2 страницы личных (200 шт.) и 3 на
  организацию, Gitea — 4 страницы. Либо крутить цикл «пока страница полная» с разумным
  потолком, либо показывать в UI «показаны первые N».
- [ ] Bitbucket/Azure ID: в `AuthManager` конвертеры используют `uuid.hashCode()`, а auth-код —
  CRC32 (`toDeterministicId`). Привести к одному способу (CRC32) — иначе один и тот же
  репозиторий получает разные ID в разных местах (см. `AUTH_ISSUES.md` #10).
- [ ] Разгрузить `ChangesScreen.kt` (1707 строк) на компоненты: tree panel, file actions menu, dialogs.
- [ ] Разгрузить `CommitDetailDialog.kt` (3349 строк) на независимые секции.
- [ ] CI pipeline: compile + lint + unit-тесты (+ сборка `assembleRelease`, чтобы ловить R8-регрессии из P0.1).

---

## P1.5 — UI-консистентность (похожие места, реализованные по-разному)

Аудит от 2026-07-04. Каждый пункт — самостоятельная небольшая задача.

- [ ] **Показ ошибок/сообщений — три разных механизма.**
  - Toast: `CommitDetailDialog.kt` (8 мест), `ChangesScreen.kt` (копирование), `CloneRepositoryService`;
  - Snackbar: `ChangesScreen.kt`, `EnhancedGraphScreen.kt`;
  - inline `Text` из `errorMessage`: `AuthScreen`, `RemoteRepositoriesScreen`.

  **Целевой паттерн:** транзиентные результаты операций → `message`/`errorMessage` в UiState
  + `SnackbarHost` на экране; inline-ошибки — только для валидации форм; Toast — только из
  Service (там нет Compose-хоста).
- [ ] **Форма состояния ViewModel — два стиля.** `ChangesViewModel`, `BranchesViewModel`,
  `RepositoryListViewModel`, `SettingsViewModel`, `CommitDetailViewModel` — единый
  `data class XxxUiState`; `AuthViewModel` и `RemoteRepositoriesViewModel` — россыпь
  отдельных `StateFlow`. Привести два последних к единому `UiState`-классу.
- [x] **Локализация auth-флоу** _(2026-07-04)_ Вынесены в `values/` + `values-ru/` все русские
  хардкоды: `AuthScreen` (~24), `OAuthActivity` (7), `AuthViewModel` (5),
  `RemoteRepositoriesViewModel` (6), `AuthManager.normalizeAndValidateInstanceUrl` (5),
  `SettingsScreen` (1). Composable → `stringResource`; ViewModels/AuthManager → `getString`
  (через `authManager.getContext()` / поле `context`). 6 строк в `CloneRepositoryService` —
  это Timber-логи (не UI), намеренно не тронуты. Проверено: нет дублей ключей, наборы en/ru совпадают.
- [x] **Форматирование дат/размеров — единый `ui/util/Formatters.kt`** _(2026-07-04)_
  `timeAgo` (был копипаст в `RepositoryCard` и `EnhancedGraphScreen`, причём в графе —
  нелокализованный английский), `formatDate`/`formatHistoryDate` (из `CommitDetailDialog`),
  `formatBytes` (свёл 4 реализации: `formatSize`, `formatSizeForNotification`, `formatFileSize`,
  `formatRepositorySize` — три из них хардкодили русские «МБ/ГБ»), `isoToShortDate`.
  **Фикс бага таймзоны:** `RemoteRepositoriesScreen.formatDate` парсил ISO `'Z'` как литерал в
  локальной TZ → дата съезжала; теперь `Instant.parse` (UTC) + fallback на `OffsetDateTime`.
  Остались 2 inline-формата (`CommitDetailDialog:1474`, `ChangesScreen:~1593`) — одноразовые, не дубли.
- [ ] **Clipboard — два подхода + копипаст.** Системный `ClipboardManager` c дублированными
  блоками «copy file name / copy file path» в `CommitDetailDialog.kt:1208` и
  `ChangesScreen.kt:1018`; Compose `LocalClipboardManager` в `AddRepositoryDialog.kt`.
  Один helper (`copyToClipboard(context, label, text)` + общий пункт меню), подход — единый.
- [ ] **`collectAsState()` без lifecycle-awareness во всех 10 файлах.** Единообразно, но flow
  продолжают собираться в фоне. Добавить `androidx.lifecycle:lifecycle-runtime-compose` и
  перейти на `collectAsStateWithLifecycle()` одним проходом.
- [x] **`LaunchedEffect(uiState.message)` — авто-закрытие диалога** _(2026-07-04)_ В
  `BranchManagementDialog` заменено на сигнал `mutationSignal`: успех обновляет состояние
  репозитория без закрытия диалога, диалог закрывается только явно. Разблокировало сценарий
  «merge/rebase с конфликтом». (В `ChangesScreen.kt:93` snackbar-логика не трогалась.)

---

## P2 — фичи (по убыванию ценности)

### 1. Merge / rebase веток из `BranchManagementDialog` — ✅ ВЫПОЛНЕНО 2026-07-04

> Реализовано: `mergeBranch`/`rebaseCurrentOnto`/`abortMerge`/`rebaseAbort`/`rebaseContinue`/
> `getRepositoryState` в репозитории (+ тесты `GitRepositoryMergeRebaseTest`), меню действий и
> баннер операции в диалоге, фикс авто-закрытия и `commitImpl` (MERGE_HEAD). Конфликты
> переиспользуют резолвер в `ChangesScreen`. Оставшиеся доработки ветки — rename и push
> отдельной ветки (см. «Мелочи» ниже). Историческое описание плана — ниже.

- `IGitRepository`: `suspend fun mergeBranch(repository, branchName): GitResult<Unit>`,
  `suspend fun rebaseOntoBranch(repository, branchName): GitResult<Unit>`,
  `suspend fun abortRebase(repository): GitResult<Unit>`.
- Реализация в `GitRepositoryBranches.kt`:
  - merge: `git.merge().include(git.repository.resolve(branchName)).call()`;
    по `MergeResult.mergeStatus` различать FAST_FORWARD/MERGED (успех), CONFLICTING
    (вернуть спец-результат «конфликты — открой Changes»), FAILED;
  - rebase: `git.rebase().setUpstream(...).call()`; статус STOPPED = конфликт →
    предлагать continue (после резолва в ChangesScreen) или abort.
- Конфликт-резолвер уже есть (`getMergeConflicts`/`resolveConflict` + UI в ChangesScreen) — переиспользовать.
- UI: два пункта в bottom sheet ветки + confirm-диалог. Обязательно сначала починить
  авто-закрытие диалога (см. P1.5, последний пункт).
- `mergeCommitIntoCurrentBranch` уже существует — merge ветки делается поверх него
  (ветка = её HEAD-коммит), но статусы конфликтов нужно прокинуть наружу.
- **[x] Баг, найденный тестом (2026-07-04, исправлено):** `commitImpl` определял «нечего
  коммитить» через `status.added/changed/removed` и блокировал merge-коммит, разрешённый
  целиком в OURS (дерево == HEAD). Исправлено: при наличии `MERGE_HEAD`
  (`repository.readMergeHeads()?.isNotEmpty()`) коммит разрешён. Покрыто тестом
  `commit_afterMergeResolvedToOurs_succeeds`.

### 2. Клонирование одной ветки — ✅ ВЫПОЛНЕНО 2026-07-04

> `cloneRepositoryImpl` получил параметр `singleBranch: String?`; при непустом значении —
> `setCloneAllBranches(false).setBranchesToClone(listOf("refs/heads/$b")).setBranch(...)`.
> Проброшено через всю цепочку: `IGitRepository` → `CloneRepositoryService` (новый intent-extra
> `EXTRA_SINGLE_BRANCH`) → `RemoteRepositoriesViewModel.startCloneInBackground`. В `CloneDialog`
> добавлен чекбокс «Клонировать только основную ветку (%branch)» (передаётся `repository.defaultBranch`).
> Полноценный shallow clone (`--depth`) — только после апгрейда JGit (см. Технические заметки).

### 3. Просмотр Pull Requests / Merge Requests — ✅ ВЫПОЛНЕНО 2026-07-04

> Endpoints добавлены во все 5 API; единая модель `GitPullRequest`;
> `AuthManager.getPullRequests(repo)` с refresh токенов; `PullRequestsViewModel` +
> `PullRequestsDialog` (список открытых PR, тап → браузер); кнопка на карточке репозитория
> в Remote Repositories. DTO в `data.auth` — под keep-правилом R8 из P0.1 автоматически.
> Возможные доработки: список PR для текущего локального репо (маппинг remote URL → провайдер),
> фильтр по состоянию (open/merged/closed), детали PR внутри приложения.

### 4. SSH-ключи

- Зависимость `org.eclipse.jgit:org.eclipse.jgit.ssh.apache:5.13.3...` (Apache MINA sshd, та же версия).
- Генерация ключа (Ed25519), приватный ключ — в EncryptedSharedPreferences,
  публичный — экран «скопируй в настройки провайдера».
- `SshdSessionFactory` с кастомным `KeyPasswordProvider`; подключить в `TransportConfigCallback`
  для clone/fetch/push, когда URL начинается с `ssh://`/`git@`.
- Большая фича — делать после merge/rebase и PR-списка.

### 5. Поиск по коммитам + blame

- Поиск: `getCommits` уже пагинирован; фильтрация по message/author на стороне клиента
  (JGit `LogCommand` не умеет grep) — поле поиска над списком в `EnhancedGraphScreen`.
- Blame: `git.blame().setFilePath(path).call()` → аннотированный просмотр из
  `CommitDetailDialog` (по кнопке в просмотре файла).

### 6. Мелочи

- [x] Rename ветки — ✅ 2026-07-04. `renameBranch` в data-слое + пункт меню в `BranchManagementDialog` (работает и для текущей, и для других локальных веток; недоступно для remote).
- [x] Push отдельной ветки — ✅ 2026-07-04. `pushBranch` в data-слое (`RefSpec`) + пункт меню в `BranchManagementDialog`, доступен и для текущей ветки, без диалога подтверждения.
- [ ] Per-app language: `android:localeConfig` в манифесте + `LocaleManager` на API 33+,
  текущий `attachBaseContext`-механизм оставить для API < 33. Fallback неизвестного
  значения языка сменить с русского на системный.
- [ ] Предупреждение о submodules при клоне (`.gitmodules` в корне → баннер «submodules не поддерживаются»).

---

## P3 — производительность и UX

- [ ] Профилировать работу на больших репозиториях (graph/diff/history).
- [ ] Кэшировать дорогие вычисления (diff/history при повторных открытиях).
- [ ] Унифицировать tree/list UX между `ChangesScreen` и `CommitDetailDialog`.
- [ ] Проверить edge-to-edge на Android 15 (targetSdk 35 включает его принудительно):
  экраны без `Scaffold` — в первую очередь `OAuthActivity` (нижняя кромка WebView под
  системной навигацией).

## Технические заметки

- **JGit 5.13.3** — осознанный пин (6.x требует `java.nio.file`). Путь апгрейда, когда
  понадобится shallow clone: core library desugaring с `desugar_jdk_libs_nio:2.x`
  (поддерживает `java.nio.file` на minSdk 26) + JGit 6.10.x + прогон всех git-сценариев.
- **`security-crypto 1.1.0-alpha06`** — библиотека deprecated и заморожена Google.
  Работает, но при следующем большом рефакторинге auth-слоя мигрировать на Tink напрямую
  или свой Keystore-обёртку. До миграции обязателен fallback из P0.2.
- **`navigation-compose 2.8.5`** отстаёт от Compose BOM 2026.01 — обновить до 2.9.x при
  ближайшем обновлении зависимостей.
- Для воспроизводимых локальных сборок нужен стабильный `gradlew` (wrapper-скрипты) в репозитории.
- Чеклист релиза: `assembleRelease` + smoke-тест OAuth/PAT всех провайдеров на minified-сборке (см. P0.1).
