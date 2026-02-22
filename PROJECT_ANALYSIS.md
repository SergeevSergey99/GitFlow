# GitFlowAndroid — актуальный анализ проекта

> Обновлено: 2026-02-22

## 1) Текущее состояние

`GitFlowAndroid` — Android Git-клиент на Kotlin/Compose с поддержкой OAuth (GitHub/GitLab), локальных/remote операций и просмотром commit/file diff.

### Актуальный стек
- Kotlin: `2.3.10`
- AGP: `8.13.2`
- Compile SDK / Target SDK / Min SDK: `36 / 35 / 26`
- Compose BOM: `2026.01.01`
- JGit: `5.13.3.202401111512-r`
- Coroutines: `1.10.2`
- DataStore Preferences: `1.1.2`

### Архитектура (фактически)
- MVVM + `StateFlow`
- Единый контракт `IGitRepository`
- Реализация разбита по модулям:
  - `GitRepositoryMeta.kt`
  - `GitRepositoryIndex.kt`
  - `GitRepositoryBranches.kt`
  - `GitRepositoryCommits.kt`
  - `GitRepositoryFiles.kt`
  - `GitRepositoryStash.kt`

## 2) Что уже исправлено относительно ранних ревизий

- Падение при выборе активного репозитория (Android API несовместимый вызов `removeLast()` на `List`) устранено.
- Staging/unstaging в Changes: исправлена ошибка reset (`The combination of arguments <paths>...`).
- В `ChangesScreen` добавлены:
  - сворачиваемая панель commit/fetch/pull;
  - tree/list режимы для изменённых файлов;
  - массовое переключение stage по папке в tree-режиме;
  - long-press actions (история, копирование имени/пути, reset changes);
  - открытие diff по клику на файл;
  - полноэкранный diff dialog.
- В `CommitDetailDialog` улучшены path-truncation/действия по long-press/режимы отображения.
- Fetch/Pull/Push UX и счётчики подтянуты в UI.

## 3) Критические проблемы (ещё актуальны)

### Безопасность
1. **WebView OAuth всё ещё с `javaScriptEnabled = true`** и без жёсткого allowlist-перехвата доменов в рантайме.
2. **Client secrets в клиенте** (модель угрозы сохраняется; APK можно декомпилировать).
3. Нужна единая ревизия логирования в auth/network на предмет утечек чувствительных данных.

### Архитектура и поддерживаемость
1. `CommitDetailDialog.kt` и `ChangesScreen.kt` остаются перегруженными по UI-логике.
2. Часть UI state по-прежнему локально в composable, не в ViewModel (не везде нужно, но есть перегруз).
3. Отсутствует полноценный DI-контейнер (Hilt/Koin).

### Качество и стабильность
1. Нет стабильного набора unit/UI тестов для regression-critical сценариев (stage/unstage/diff/history).
2. Нет CI-пайплайна для обязательного compile/lint/test перед merge.
3. В репозитории может отсутствовать `gradlew` в отдельных локальных состояниях — это мешает воспроизводимой проверке.

## 4) Приоритетный план работ

### P0 (в ближайшие сессии)
- Добавить smoke/regression тесты для:
  - stage/unstage (single + batch);
  - discard/reset changes;
  - open diff + open history.
- Закрыть WebView OAuth hardening (allowlist host/scheme, блок редиректов вне провайдера).
- Финализировать UX tree/list в Changes (если нужны мелкие доработки).

### P1
- Вынести крупные блоки из `ChangesScreen` и `CommitDetailDialog` в отдельные компоненты + ViewModel-слой.
- Ввести DI (минимально — для repository/auth/settings).
- Добавить CI: `assembleDebug`, `compileDebugKotlin`, базовые тесты.

### P2
- Докрутить производительность на больших репозиториях (кэширование diff/истории, lazy-подгрузка где нужно).
- Подготовить релизные quality gates (crash-free, ANR, логирование, proguard review).

## 5) Общая оценка

Проект в рабочем состоянии и заметно зрелее ранней версии: ключевые crash/UX проблемы на основных экранах закрыты, репозиторный слой структурирован. Главные риски сейчас — безопасность OAuth/WebView, тестовое покрытие и высокая сложность крупных UI-файлов.
