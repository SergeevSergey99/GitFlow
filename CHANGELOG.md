# CHANGELOG

Все заметные изменения проекта фиксируются здесь.

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
