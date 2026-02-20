# GitFlowAndroid - Анализ проекта

> **Дата анализа:** 2026-02-16 | **Последнее обновление:** 2026-02-20
> **Ветка:** master
> **Версия:** 1.0.0 (pre-release)

---

## Содержание

1. [Обзор проекта](#1-обзор-проекта)
2. [Критические проблемы безопасности](#2-критические-проблемы-безопасности)
3. [Архитектурные проблемы](#3-архитектурные-проблемы)
4. [Проблемы производительности](#4-проблемы-производительности)
5. [Качество кода](#5-качество-кода)
6. [Конфигурация сборки](#6-конфигурация-сборки)
7. [Тестирование](#7-тестирование)
8. [UI/UX проблемы](#8-uiux-проблемы)
9. [Отсутствующие возможности](#9-отсутствующие-возможности)
10. [Дорожная карта улучшений](#10-дорожная-карта-улучшений)

---

## 1. Обзор проекта

**GitFlowAndroid** - Android Git-клиент с OAuth авторизацией (GitHub/GitLab), визуализацией коммитов, фоновым клонированием и полным набором Git-операций.

| Параметр | Значение |
|----------|----------|
| Язык | Kotlin 1.9.0 |
| UI | Jetpack Compose (Material 3) |
| Архитектура | MVVM (без чистой архитектуры) |
| Git-библиотека | JGit 5.13.3 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Файлов .kt | ~30 |
| Общий объём кода | ~10,000+ строк |

### Текущие возможности
- Создание/открытие/клонирование/удаление репозиториев
- OAuth авторизация GitHub и GitLab
- Stage/unstage/commit/pull/push/fetch
- Merge, cherry-pick, rebase
- Создание/удаление веток и тегов
- Визуализация графа коммитов
- Просмотр diff с hunks
- Фоновое клонирование с уведомлениями
- Разрешение конфликтов слияния
- Локализация (EN/RU)

---

## 2. Критические проблемы безопасности

### 2.1 Хранение токенов в открытом SharedPreferences
- **Файл:** `AuthManager.kt:16`
- **Серьёзность:** КРИТИЧЕСКАЯ
- **Описание:** OAuth-токены хранятся в plain-text SharedPreferences. На rooted-устройствах или при backup-атаках токены легко извлекаются.

```kotlin
// Текущий код (НЕБЕЗОПАСНО):
private val preferences: SharedPreferences =
    context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

// Исправление:
private val preferences = EncryptedSharedPreferences.create(
    "auth_prefs_encrypted",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### 2.2 Логирование токенов и секретов
- **Файл:** `AuthManager.kt:146-148, 434`
- **Серьёзность:** КРИТИЧЕСКАЯ
- **Описание:** Access-токены логируются в logcat, доступный другим приложениям.

```kotlin
// НЕДОПУСТИМО (AuthManager.kt:146):
android.util.Log.d("AuthManager", "Access Token: ${token.accessToken}")
android.util.Log.d("AuthManager", "Token Type: ${token.tokenType}")

// Также (AuthManager.kt:434):
android.util.Log.d("AuthManager", "Токен найден: ${token.accessToken.take(7)}...")
```

**Рекомендация:** Удалить ВСЕ логирование секретов. Использовать Timber с кастомным Tree, который strip-ает в release-сборках.

### 2.3 Токены встраиваются в URL
- **Файл:** `AuthManager.kt:440-444`
- **Серьёзность:** ВЫСОКАЯ
- **Описание:** При клонировании токен вставляется прямо в URL (`https://TOKEN@github.com/...`), который затем логируется.

```kotlin
// Текущий код (AuthManager.kt:440):
repository.cloneUrl.replace("https://", "https://${token.accessToken}@")

// Лог (AuthManager.kt:447):
android.util.Log.d("AuthManager", "Итоговый clone URL: $cloneUrl") // Полный токен в логе!
```

**Рекомендация:** Использовать `CredentialsProvider` в JGit вместо встраивания токена в URL. В `GitRepository.kt:355-363` этот подход уже частично реализован - нужно использовать его повсеместно.

### 2.4 Отсутствие PKCE в OAuth
- **Файл:** `AuthManager.kt:89-103`
- **Серьёзность:** ВЫСОКАЯ
- **Описание:** OAuth-поток не использует PKCE (Proof Key for Code Exchange). Мобильные приложения уязвимы к перехвату authorization code.

**Рекомендация:** Добавить генерацию `code_verifier` и `code_challenge` при формировании auth URL, передавать `code_verifier` при обмене кода на токен.

### 2.5 Client Secret в APK
- **Файл:** `OAuthConfig.kt:40-43`, `oauth.properties`
- **Серьёзность:** ВЫСОКАЯ
- **Описание:** Client secrets хранятся в assets APK и могут быть извлечены декомпиляцией.

**Рекомендации:**
- Для GitHub: использовать Device Flow (не требует client_secret)
- Для GitLab: использовать backend-прокси для обмена кодов
- Минимум: задокументировать риск, не давать приложению прав выше необходимых

### 2.6 Небезопасный WebView
- **Файл:** `OAuthActivity.kt:152-157`
- **Серьёзность:** СРЕДНЯЯ
- **Описание:** JavaScript включён без ограничения доменов. Нет валидации URL.

```kotlin
// Текущий код:
settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
}
```

**Рекомендация:** Валидировать `authUrl` перед загрузкой (только `github.com` и `gitlab.com`). Использовать Chrome Custom Tabs вместо встроенного WebView.

### 2.7 Force Unwrap (!!) в обработке OAuth-ответов
- **Файл:** `AuthManager.kt:138, 170, 208, 222`
- **Серьёзность:** СРЕДНЯЯ
- **Описание:** Использование `!!` (force unwrap) при разборе OAuth-ответов может вызвать crash при unexpected null.

```kotlin
// Пример (AuthManager.kt:138):
val oauthResponse = tokenResponse.body()!!  // Crash при null body
```

---

## 3. Архитектурные проблемы

### 3.1 Отсутствие Dependency Injection
- **Файлы:** `MainScreen.kt:31`, `SettingsScreen.kt:51-52`, `ChangesScreen.kt:36`
- **Серьёзность:** ВЫСОКАЯ
- **Описание:** Все зависимости создаются вручную через `remember { }` прямо в Composable-функциях.

```kotlin
// MainScreen.kt:31 - антипаттерн:
val gitRepository = remember { GitRepository(context) }

// SettingsScreen.kt:51-52 - то же самое:
val authManager = remember { AuthManager(context) }
val settingsManager = remember { AppSettingsManager(context) }
```

**Рекомендация:** Внедрить Hilt (или Koin):
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val gitRepository: GitRepository
) : ViewModel()
```

### 3.2 God-классы
| Файл | Строк (было) | Строк (сейчас) | Статус |
|------|-------------|----------------|--------|
| `CommitDetailDialog.kt` | **2895** | **2895** | ⏳ Логика → CommitDetailViewModel ✅ |
| `GitRepository.kt` | **2199** | **~250** | ✅ Разбит на 6 файлов |
| `SettingsScreen.kt` | **1125** | **1125** | Открытая задача |
| `AuthManager.kt` | **~600** | **~600** | Открытая задача |

**✅ `GitRepository.kt` разбит:**
- `GitRepository.kt` — ~250 строк: скелет класса + override-делегаты + shared helpers
- `GitRepositoryMeta.kt` — addRepository, createRepository, clone, removeWithFiles, refresh
- `GitRepositoryIndex.kt` — getChangedFiles, staging, commit, merge conflicts
- `GitRepositoryBranches.kt` — branches, pull, push, tags, cherry-pick, merge
- `GitRepositoryCommits.kt` — getCommits (paginated), getCommitDiffs
- `GitRepositoryFiles.kt` — fileTree, fileContent, restore, fileHistory

**✅ `CommitDetailDialog.kt`:** `CommitDetailViewModel` создан, 18+ `remember { mutableStateOf }` перенесены.

### 3.3 Отсутствие интерфейсов/абстракций ✅ РЕШЕНО
- ~~**Серьёзность:** ВЫСОКАЯ~~
- Создан `IGitRepository` с 42 методами. `GitRepository : IGitRepository`.
- Все UI-файлы используют тип `IGitRepository`, не конкретный класс.
- Единственный экземпляр создаётся в `MainScreen` и передаётся вниз.
- Появилась возможность подмены для тестирования (mock/fake).

### 3.4 Отсутствие доменного слоя
- **Серьёзность:** СРЕДНЯЯ
- **Описание:** ViewModels/Composables напрямую вызывают Repository. Бизнес-логика размазана по слоям.

**Рекомендация:** Добавить Use Cases:
```kotlin
class CloneRepositoryUseCase(
    private val gitRepository: GitRepository,
    private val networkChecker: NetworkChecker
) {
    suspend operator fun invoke(params: CloneParams): Result<Repository>
}
```

### 3.5 Нет ViewModel для основных экранов ✅ ЧАСТИЧНО РЕШЕНО
- ~~**Серьёзность:** СРЕДНЯЯ~~
- **✅ Решено:** `CommitDetailViewModel` + `ChangesViewModel` созданы. Состояние переживает ротацию.
- `ChangesViewModel` использует `AndroidViewModel` для доступа к строковым ресурсам без утечки Context.
- **Открытая задача:** `MainScreen` (список репозиториев наблюдает Flow ✓, но сам экран без ViewModel).

### 3.6 Дублирование кода ✅ РЕШЕНО
- ~~**Серьёзность:** СРЕДНЯЯ~~
- При разбивке GitRepository все auto-find overloads (`getCommitDiffs(commit)`, `getFileContent(commit, path)` и др.) теперь однострочные делегаты к primary overload. Логика поиска репозитория вынесена в `findRepositoryForCommit()`.

---

## 4. Проблемы производительности

### 4.1 Загрузка всех коммитов без пагинации ✅ РЕШЕНО
- ~~**Серьёзность:** КРИТИЧЕСКАЯ~~
- `getCommits(repository, page: Int = 0, pageSize: Int = 50)` — пагинация через offset/limit в RevWalk-цикле.
- EnhancedGraphScreen показывает кнопку "Load more" когда `commits.size >= pageSize`.
- `currentPageSize` сбрасывается при смене репозитория.

### 4.2 Повторное открытие репозитория
- **Файл:** `GitRepository.kt` (множество мест)
- **Серьёзность:** ВЫСОКАЯ
- **Описание:** Каждый вызов метода вызывает `openRepository()` заново. Нет кэширования `Git` объектов.

```kotlin
// Паттерн, повторяющийся ~30 раз:
val git = openRepository(repository.path) ?: return ...
// ... использование ...
git.close()
```

**Рекомендация:** Создать пул/кэш Git-объектов с LRU-политикой.

### 4.3 Поиск репозитория для коммита (brute force)
- **Файл:** `GitRepository.kt:1109-1133, 1445-1458, 1496-1508, 1585-1597`
- **Серьёзность:** СРЕДНЯЯ
- **Описание:** Метод `findRepositoryContainingCommit` перебирает ВСЕ репозитории, открывая каждый, чтобы найти нужный коммит. При 20+ репозиториях это ощутимо тормозит.

### 4.4 Теги считываются для каждого коммита ✅ РЕШЕНО
- ~~**Серьёзность:** СРЕДНЯЯ~~
- В `getCommitsImpl()` теги загружаются один раз через `tagList().call()` и хранятся в `tagsByCommit: Map<String, List<String>>`. Поиск тегов для коммита — O(1) вместо O(N * T).

### 4.5 Отсутствие кэширования
- **Серьёзность:** СРЕДНЯЯ
- **Описание:** Нет кэша для: данных коммитов, diff'ов, результатов API-запросов, информации о репозиториях.

**Рекомендация:** Внедрить Room для кэширования часто используемых данных.

### 4.6 Утечки ресурсов ✅ РЕШЕНО
- ~~**Серьёзность:** СРЕДНЯЯ~~
- Все `RevWalk`, `TreeWalk`, `DiffFormatter`, `Git` объекты обёрнуты в `.use {}`.
- `PullResult`/`PushResult` удалены — `pull()` и `push()` возвращают `GitResult<Unit>`.

---

## 5. Качество кода

### 5.1 Избыточное логирование
- **Всего:** 179 вызовов `android.util.Log` в 8 файлах
- **Распределение по файлам:**

| Файл | Количество Log вызовов |
|------|----------------------|
| `AuthManager.kt` | 58 |
| `GitRepository.kt` | 49 |
| `OAuthConfig.kt` | 19 |
| `RemoteRepositoriesScreen.kt` | 16 |
| `RemoteRepositoriesViewModel.kt` | 15 |
| `CommitDetailDialog.kt` | 11 |
| `CloneRepositoryService.kt` | 6 |
| `FormUrlEncodedConverterFactory.kt` | 5 |

**Рекомендация:**
- Заменить `android.util.Log` на Timber
- Настроить release-Tree для продакшен-логирования (Crashlytics и т.д.)
- Удалить все логи с чувствительными данными

### 5.2 Незавершённые TODO
```
MainScreen.kt:179      - TODO: Implement GitOperationsSheet
CommitDetailDialog.kt:1814 - TODO: Добавить копирование в буфер обмена
GitRepository.kt:1262  - TODO: Подсчитать количество новых коммитов (pull)
GitRepository.kt:1289  - TODO: Подсчитать количество отправленных коммитов (push)
```

### 5.3 Закомментированный код
- **Файл:** `MainScreen.kt:70-73` - закомментированный IconButton для operations
```kotlin
/*IconButton(onClick = { showOperationsSheet = true }) {
    Icon(Icons.Default.MoreVert, ...)
}*/
```

### 5.4 Несогласованная обработка ошибок ✅ РЕШЕНО
- ~~**Описание:** Использовался микс подходов: `Result<T>`, null, emptyList(), Boolean~~
- Введён `sealed class GitResult<out T>` в `data/models/GitResult.kt`:
  - `GitResult.Success<T>(data: T)`
  - `GitResult.Failure.Generic(message, cause?)`
  - `GitResult.Failure.Conflict(message, paths)` — для cherry-pick/merge конфликтов
  - `GitResult.Failure.NoStagedChanges` — для commit без staged файлов
  - `GitResult.Failure.TagAlreadyExists(tagName)` — для createTag
  - `GitResult.Failure.Cancelled(message)` — для clone cancellation
- Все методы с `Result<T>`, `PullResult`, `PushResult`, `Boolean` переведены на `GitResult<T>`.
- `PullResult` и `PushResult` удалены из Models.kt.
- UI-слой использует `when (result) { is GitResult.Success → ; is GitResult.Failure → }`.

### 5.5 Магические строки
- Хардкод URL'ов (`"https://github.com/"`, `"https://gitlab.com/"`)
- Хардкод ключей SharedPreferences
- Хардкод имён notification-каналов
- Хардкод автора коммита (`"GitFlow Android"`, `"gitflow@android.local"`)

### 5.6 ProGuard - пустые правила
- **Файл:** `proguard-rules.pro`
- **Описание:** Файл содержит только стандартные комментарии и одну строку `keepattributes`. Отсутствуют необходимые правила для:
  - JGit (reflection, service loader)
  - Retrofit (models, API interfaces)
  - Gson (data classes)
  - Kotlin serialization

---

## 6. Конфигурация сборки

### 6.1 Минификация отключена
- **Файл:** `app/build.gradle.kts:26`
- `isMinifyEnabled = false` для release-сборки
- Без минификации APK значительно больше, а код не обфусцирован

### 6.2 Устаревшие зависимости

| Зависимость | Текущая | Рекомендуемая |
|-------------|---------|---------------|
| Kotlin | 1.9.0 | 2.0+ (K2 compiler) |
| Compose BOM | 2023.10.01 | 2024.12+ |
| JGit | 5.13.3 | 6.10+ |
| Coroutines | 1.7.3 | 1.9+ |
| Core KTX | 1.12.0 | 1.15+ |
| Lifecycle | 2.7.0 | 2.8+ |
| Navigation | 2.7.6 | 2.8+ |
| Retrofit | 2.9.0 | 2.11+ |

### 6.3 Java 1.8 target
- **Файл:** `app/build.gradle.kts:33-36`
- `jvmTarget = "1.8"` - можно обновить до `"11"` (min SDK 26 поддерживает)

### 6.4 Отсутствует signing config
- Нет конфигурации подписи для release-сборки
- Нет разделения debug/release OAuth credentials

### 6.5 Нет CI/CD
- Отсутствует `.github/workflows/`
- Нет автоматической сборки и тестирования

---

## 7. Тестирование

### 7.1 Текущее покрытие
- **Unit-тесты:** 1 файл, 3 теста (`CommitDetailDialogTest.kt`)
- **Android-тесты:** 0 файлов
- **UI-тесты:** 0 файлов
- **Покрытие:** ~0.1%

### 7.2 Что необходимо покрыть тестами

**Приоритет 1 (критично):**
- `AuthManager` - OAuth flow, хранение/получение токенов
- `GitRepository` - все Git-операции
- `RepositoryDataStore` - персистентность данных

**Приоритет 2 (важно):**
- ViewModels - управление состоянием
- `OAuthConfig` - загрузка конфигурации
- `CloneProgressCallback` - расчёт прогресса

**Приоритет 3 (желательно):**
- UI-тесты основных экранов (Compose Testing)
- Интеграционные тесты OAuth-флоу
- E2E тесты: клонирование -> коммит -> push

---

## 8. UI/UX проблемы

### 8.1 GitOperationsSheet - заглушка
- **Файл:** `MainScreen.kt:172-195`
- Кнопка операций закомментирована (строки 70-73), а сам sheet содержит только placeholder-текст

### 8.2 Нет pull-to-refresh
- Экран списка репозиториев не поддерживает жест обновления
- Экран изменений не обновляется автоматически

### 8.3 Нет индикатора загрузки при push/pull
- Пользователь не видит прогресс длительных сетевых операций

### 8.4 Отсутствие аватаров пользователей
- `GitUser.avatarUrl` не используется для отображения (нет Coil/Glide)
- Вместо аватара - иконки Material Icons

### 8.5 Нет подтверждения опасных операций
- `hardResetToCommit` выполняется без подтверждения
- Удаление ветки без подтверждения
- Force push без предупреждения

### 8.6 Нет офлайн-индикатора
- Приложение не показывает состояние сети
- Push/pull молча падают без сети

### 8.7 WebView не уничтожается
- **Файл:** `OAuthActivity.kt`
- WebView создаётся в `AndroidView` factory, но не уничтожается при закрытии Activity -> потенциальная утечка памяти

---

## 9. Отсутствующие возможности

### 9.1 Высокий приоритет

| Функция | Описание |
|---------|----------|
| **Git Stash** | Сохранение/восстановление незавершённых изменений |
| **Поиск коммитов** | Поиск по сообщению, автору, дате |
| **Blame View** | Просмотр автора каждой строки файла |
| **SSH-ключи** | Генерация и управление SSH-ключами для Git |
| **Token Refresh** | Обновление истёкших GitLab-токенов (`refreshToken` не используется) |
| **SAF-поддержка** | Полноценная работа с Storage Access Framework (сейчас - заглушка, `GitRepository.kt:215`) |

### 9.2 Средний приоритет

| Функция | Описание |
|---------|----------|
| **Bitbucket** | Поддержка третьего крупного Git-хостинга |
| **Мульти-аккаунт** | Несколько аккаунтов одного провайдера |
| **Interactive Rebase** | Визуальный интерактивный rebase |
| **Git LFS** | Поддержка больших файлов |
| **Submodules** | Работа с подмодулями |
| **Commit Amend** | Редактирование последнего коммита |
| **Diff - word-level** | Подсветка изменений на уровне слов |
| **Undo/Redo** | Отмена последней Git-операции |

### 9.3 Низкий приоритет (Nice to Have)

| Функция | Описание |
|---------|----------|
| **Биометрическая защита** | Защита приложения отпечатком/лицом |
| **Виджеты** | Виджет с статусом репозитория |
| **Deep Links** | Открытие репозитория по ссылке |
| **Gitignore Templates** | Шаблоны .gitignore при создании репозитория |
| **Syntax Highlighting** | Подсветка синтаксиса в diff-просмотрщике |
| **GPG Signing** | Подписание коммитов |
| **Patch Apply** | Применение .patch файлов |
| **Custom Themes** | Пользовательские цветовые схемы |

---

## 10. Дорожная карта улучшений

### Фаза 1: Безопасность и стабильность ✅ ЗАВЕРШЕНА

- [x] Заменить SharedPreferences на EncryptedSharedPreferences
- [x] Удалить/скрыть критические логи с токенами (частично)
- [x] Заменить встраивание токенов в URL на CredentialsProvider
- [x] Добавить PKCE в OAuth-поток
- [x] Добавить валидацию URL в WebView / Chrome Custom Tabs
- [x] Исправить force unwrap (`!!`) на безопасные вызовы
- [x] Добавить ProGuard-правила для JGit, Retrofit, Gson
- [x] Включить `isMinifyEnabled = true` для release

### Фаза 2: Архитектура и производительность ✅ ЗАВЕРШЕНА

- [ ] Внедрить Hilt для DI (отложено — после ViewModels для всех экранов)
- [x] Разделить `GitRepository` на 6 специализированных файлов
- [x] `CommitDetailViewModel` — 18+ remember → ViewModel, rotation-safe
- [x] `ChangesViewModel` — staging, commit, conflicts, push — rotation-safe
- [x] Добавить пагинацию коммитов (`page`/`pageSize`, "Load more" UI)
- [ ] LRU-кэш Git-объектов (отложено)
- [x] Оптимизировать загрузку тегов (однократная Map до цикла, O(1))
- [x] Исправить утечки ресурсов (`.use {}` для всех JGit-объектов)
- [x] `IGitRepository` — интерфейс, единый экземпляр
- [x] Стандартизировать обработку ошибок (`sealed class GitResult<T>`)

### Фаза 3: Тестирование и CI/CD

- [ ] Добавить unit-тесты для AuthManager (теперь testable через IGitRepository)
- [ ] Добавить unit-тесты для Git-операций (mock IGitRepository)
- [ ] Добавить UI-тесты для основных экранов
- [ ] Настроить GitHub Actions CI/CD
- [ ] Добавить линтеры (detekt, ktlint)
- [ ] Добавить leak canary для debug-сборок

### Фаза 4: Функциональные улучшения

- [ ] Git Stash (stash/pop/apply/drop)
- [ ] Поиск и фильтрация коммитов
- [ ] Blame View
- [ ] Token refresh для GitLab
- [ ] Поддержка SSH-ключей
- [ ] Pull-to-refresh на экранах
- [ ] Индикатор сетевых операций
- [ ] Подтверждение опасных операций

### Фаза 5: Полировка

- [ ] Загрузка аватаров пользователей (Coil)
- [ ] Syntax highlighting в diff
- [ ] Обновление зависимостей до актуальных версий
- [ ] Подписание release-сборки
- [ ] Подготовка к публикации в Google Play

---

## Статистика проекта

```
Файлов Kotlin:               ~40 (было ~30; +IGitRepository, +GitResult, +GitRepository*5, +ViewModels*2)
Общий объём кода:            ~10,500 строк
Вызовов Log:                 179 (не изменилось)
Тестов:                      3 (не изменилось)
TODO/FIXME:                  4 (не изменилось)
Файлов без интерфейсов:      Нет (IGitRepository покрывает все Git-операции)
Dependency Injection:        Нет (запланировано Hilt)
CI/CD:                       Нет
ProGuard-правила:            Полные (JGit, Retrofit, Gson, Serialization)
Минификация release:         Включена
Самый большой файл:          CommitDetailDialog.kt (~2895 строк) — данные в ViewModel
GitRepository.kt:            ~250 строк (было 2199; разбит на 6 файлов)
GitResult<T>:                5 типов ошибок (Generic, Conflict, NoStagedChanges, TagAlreadyExists, Cancelled)
IGitRepository:              42 метода, все UI-файлы используют только интерфейс
```
