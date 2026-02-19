# Отложенные задачи

> Обновлено: 2026-02-19. Фазы 1 + мелкие правки вне фаз — завершены.
> Полный анализ проблем — см. PROJECT_ANALYSIS.md

---

## Перед началом Фазы 2

Мелкие правки, которые не вошли в предыдущие сессии:

- [ ] **Обновить зависимости** (отдельный шаг с тестированием):
  - Compose BOM: `2023.10.01` → `2024.12+`
  - Kotlin: `1.9.20` → `2.0+` (K2 compiler)
  - Coroutines: `1.7.3` → `1.9+`
  - Core KTX: `1.12.0` → `1.15+`
  - Lifecycle/Navigation/Retrofit — до актуальных версий
  - **JGit: `5.13.3` → `6.10+`** — мажорный апгрейд, другой group ID, требует отдельного тестирования

- [ ] **`logging-interceptor`** добавлен в зависимости (`build.gradle.kts:98`), но не подключён ни к одному OkHttp-клиенту в `AuthManager`. Либо подключить (полезно для отладки сети), либо удалить как мёртвую зависимость. Удобнее сделать при рефакторинге `AuthManager` в Фазе 2.

- [ ] **`refreshGitLabTokenIfNeeded()`** сейчас вызывается только перед `getRepositories()`. При рефакторинге в Phase 2 (разделение GitRepository) нужно также вызывать перед clone/push/pull для GitLab. Удобно будет сделать через `getValidToken(provider)` в отдельном методе.

---

## Фаза 2: Архитектура и производительность

### Блок 2a — быстрые победы (низкий риск)

- [ ] Исправить утечки ресурсов: `RevWalk`, `TreeWalk`, `DiffFormatter` без `use {}` в `GitRepository.kt`
- [ ] Оптимизировать загрузку тегов: убрать `getTagsForCommit()` из цикла коммитов, загрузить все теги один раз → `HashMap<commitHash, List<String>>`
- [ ] Стандартизировать обработку ошибок: `sealed class GitResult<T>` вместо смеси `null` / `emptyList()` / `Boolean` / исключений
- [ ] Добавить `Log.e` в ProGuard `-assumenosideeffects` — **уже сделано ✓** (на случай если придётся перечитывать)

### Блок 2b — архитектурный рефакторинг (высокий риск)

- [ ] Разделить `GitRepository.kt` (2199 строк) на:
  - `CommitOperations` — getCommits, diff, fileHistory
  - `BranchOperations` — ветки, checkout, merge, rebase, cherryPick
  - `RemoteOperations` — clone, pull, push, fetch, credentialsProvider
  - `FileOperations` — stage, unstage, restore, status
  - `TagOperations` — теги
  - Создать `interface IGitRepository`
- [ ] Разделить `CommitDetailDialog.kt` (2895 строк):
  - Вынести `CommitDetailViewModel`
  - Вынести `DiffViewer`, `FileTreePanel`, `CommitInfoHeader` как отдельные компоненты
- [ ] Добавить пагинацию коммитов: `getCommits(page, pageSize)` + lazy loading в `EnhancedGraphScreen`
- [ ] Кэш Git-объектов: `GitRepositoryCache` (LRU по path) вместо пересоздания `Git` на каждый вызов
- [ ] Внедрить Hilt (делать последним после разделения классов):
  - `@HiltAndroidApp` для Application
  - `@HiltViewModel` для ViewModels
  - Убрать `remember { SomeClass(context) }` из Composables

---

## Фаза 3: Тестирование и CI/CD

- [ ] Unit-тесты: `AuthManager`, `GitRepository` (Git-операции), ViewModels
- [ ] UI-тесты основных экранов (Compose Testing)
- [ ] Настроить GitHub Actions (lint + build + test)
- [ ] Добавить Detekt / Ktlint
- [ ] LeakCanary для debug-сборок

---

## Фаза 4: Функциональные улучшения

- [ ] **Git Stash** — stash/pop/apply/drop
- [ ] **Поиск коммитов** — по сообщению, автору, дате
- [ ] **Blame View** — автор каждой строки файла
- [ ] **Token refresh для GitLab** — реализован `refreshGitLabTokenIfNeeded()`, но нужно интегрировать в clone/push/pull в JGit-операциях
- [ ] **SSH-ключи** — генерация и управление
- [ ] **Pull-to-refresh** на экранах списка репозиториев и изменений
- [ ] **Индикатор прогресса** для push/pull операций
- [ ] **Подтверждение опасных операций**: `hardResetToCommit`, удаление ветки, force push
- [ ] **`GitOperationsSheet`** — раскомментировать кнопку и реализовать (`MainScreen.kt:36, 150`)
- [ ] **Commit Amend** — редактирование последнего коммита

---

## Фаза 5: Полировка

- [ ] Загрузка аватаров пользователей (Coil)
- [ ] Syntax highlighting в diff-просмотрщике
- [ ] Подписание release-сборки (signing config)
- [ ] Подготовка к публикации в Google Play
