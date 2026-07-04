# GitFlowAndroid: Guide по доделыванию и полировке

> Актуально по состоянию на 2026-03-09

## Краткая оценка

Проект уже выглядит как рабочий MVP+ / open beta, а не как черновик. Есть нормальный git-слой, Compose UI, DI, auth, branch/stash/diff/history сценарии, фоновое клонирование и сохранение части пользовательского состояния.

Грубая оценка текущего состояния:

- `7/10` как pet/open beta
- `4.5/10` как production-ready приложение

Главный вывод: проблема уже не в нехватке фич, а в переходе от "реализовано" к "стабильно, безопасно и проверяемо".

## Что уже хорошо

- Репозиторный слой оформлен через единый контракт `IGitRepository`.
- `GitRepository` разбит по тематическим файлам, а не превращён в один монолит.
- Внедрён DI через Koin, базовые зависимости и ViewModel'и централизованы.
- Есть восстановление последнего выбранного репозитория.
- Реализованы полезные git-сценарии: branches, stash, diff, history, fetch/pull/push.
- Есть фоновое клонирование через service + progress tracking.
- В проекте уже виден переход от "демо" к реально используемому приложению.

## Ключевые проблемы

### 1. OAuth и auth flow остаются самым рискованным местом

Файл: `app/src/main/java/com/gitflow/android/ui/auth/OAuthActivity.kt`

Что не нравится:

- WebView-based OAuth сам по себе более хрупкий и рискованный путь, чем browser/custom tabs.
- Allowlist хостов для навигации слишком грубый.
- Любая ошибка ресурса сейчас может завершить весь auth flow.
- Валидация callback завязана на `startsWith`, а не на строгий разбор URI.
- В клиенте остаются `client_secret`, что создаёт стандартный риск для mobile OAuth clients.

Практический риск:

- логин может ломаться на MFA/SSO/CDN/redirect-сценариях;
- flow будет нестабилен в реальных сетевых условиях;
- security review такого auth-механизма будет болезненным.

### 2. Deep link callback заявлен, но архитектурно не доведён

Файл: `app/src/main/AndroidManifest.xml`

Что не нравится:

- В манифесте уже есть callback alias для `gitflow://oauth/...`.
- Но `OAuthActivity` реально ожидает запуск через internal extras, а не обработку внешнего callback intent.

Итог:

- если переводить flow на Custom Tabs / внешний браузер, текущий callback pipeline придётся доделывать почти с нуля.

### 3. Слабая проверяемость проекта

Файлы:

- `app/build.gradle.kts`
- `app/src/test/java/com/gitflow/android/ui/screens/CommitDetailDialogTest.kt`

Что видно:

- unit test фактически один;
- `androidTest` отсутствует;
- regression coverage почти нет;
- в репозитории нет `gradlew` / `gradlew.bat`, что мешает воспроизводимой проверке и нормальному CI.

Итог:

- проект уже достаточно сложный, чтобы без тестов часто ломать уже работающие сценарии.

### 4. UI-файлы стали слишком большими

Файлы:

- `app/src/main/java/com/gitflow/android/ui/screens/main/ChangesScreen.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/CommitDetailDialog.kt`
- `app/src/main/java/com/gitflow/android/ui/screens/EnhancedGraphScreen.kt`

Что видно:

- `ChangesScreen.kt` очень крупный;
- `CommitDetailDialog.kt` уже явно вышел за разумный объём;
- крупные composable держат на себе и UI, и orchestration, и часть interaction logic.

Итог:

- любая следующая доработка дорожает;
- regression risk растёт;
- код всё сложнее читать и тестировать.

### 5. Есть инженерные недоделки вокруг репозитория

Что видно:

- в `.gitignore` игнорируется `/*/build/`, но не корневой `build/`;
- в рабочем дереве уже есть untracked `build/`;
- в корне лежит `oauth.properties`, хотя runtime-конфигурация в коде ориентируется на `assets`, env и `filesDir`.

Итог:

- это повышает риск путаницы и случайных утечек секретов;
- ухудшается DX и чистота репозитория.

## Приоритетный план работ

## P0: обязательно перед любым серьёзным релизом

### 1. Сделать проект воспроизводимым

Нужно:

- добавить `gradlew`;
- добавить `gradlew.bat`;
- убедиться, что `gradle-wrapper.jar` и wrapper config в порядке;
- поправить `.gitignore`, чтобы игнорировался и корневой `build/`;
- привести README к одному реальному способу сборки.

Ожидаемый результат:

- любой человек и CI могут собрать проект одинаково;
- меньше ручной магии вокруг Android Studio.

### 2. Закрыть auth hardening

Предпочтительный путь:

- уйти с WebView на Custom Tabs + PKCE + deep link callback.

Если WebView пока остаётся, то минимум:

- проверять только main-frame navigation;
- разделить allowlist для main frame и subresources;
- не завершать auth flow на каждой ошибке ассета или стороннего ресурса;
- валидировать callback через разбор `Uri` (`scheme`, `host`, `path`), а не `startsWith`;
- отдельно пройтись по настройкам `javaScriptEnabled`, `domStorageEnabled`, cookies и redirect behavior;
- сделать аудит логов auth/network на предмет токенов и чувствительных данных.

### 3. Добавить regression tests на git-critical сценарии

Минимальный набор:

- single stage / unstage;
- batch stage / unstage;
- discard/reset file changes;
- commit / amend;
- open diff;
- open history;
- branch checkout / create / delete;
- stash save / apply / pop / drop.

Лучший путь:

- тестировать не Compose-экран напрямую, а ViewModel/use-case/repository logic;
- использовать локальные test repos как фиксированные сценарии.

### 4. Поднять CI

Минимальный pipeline:

- `assembleDebug`
- `lint`
- `test`

Если появятся UI/instrumentation тесты:

- добавить отдельный job для `connectedAndroidTest` или эквивалентного smoke-раннера.

## P1: снижение технического долга

### 1. Разгрузить `ChangesScreen`

Разбить на части:

- toolbar/panel для commit/fetch/pull/push;
- staged/unstaged list sections;
- tree/list presentation layer;
- file actions menu;
- conflict dialog wiring;
- stash dialog wiring;
- diff/history launcher logic.

Цель:

- экран должен координировать, а не содержать в себе всё сразу.

### 2. Разгрузить `CommitDetailDialog`

Разделить отдельно:

- header/meta section;
- file tree section;
- preview/diff section;
- history section;
- action menu / dialog helpers.

Цель:

- упростить чтение и тестирование;
- снизить риск случайных поломок.

### 3. Подчистить строковые ресурсы и локализацию

Что стоит сделать:

- убрать хардкод-строки из ViewModel и UI;
- всё пользовательское сообщение вынести в `strings.xml`;
- проверить русскую и английскую локаль на целостность.

Особенно обратить внимание:

- `BranchesViewModel` сейчас содержит строки прямо в коде;
- auth/UI сообщения частично смешаны между ресурсами и literal strings.

### 4. Выстроить единый паттерн ошибок и transient-сообщений

Нужно унифицировать:

- snackbar messages;
- persistent banners;
- retryable network errors;
- auth-expired errors;
- destructive action confirmations.

Цель:

- чтобы пользователь видел предсказуемое поведение на всех экранах.

## P2: производительность и UX-полировка

### 1. Проверить большие репозитории

Нужно руками прогнать сценарии на:

- длинной истории;
- больших diff;
- большом количестве changed files;
- большом количестве branches/tags.

Смотреть на:

- лаги скролла;
- задержки открытия diff/history;
- повторные тяжёлые вычисления;
- лишние refresh/reload.

### 2. Подумать о кэшировании дорогих операций

Кандидаты:

- commit diff;
- file history;
- graph-derived data;
- вычисления для tree/list представлений.

Важно:

- кэш нужен только там, где он реально улучшает UX и не ломает консистентность.

### 3. Довести UX long-running операций

Проверить:

- clone;
- push;
- fetch/pull;
- conflict resolution;
- stash operations.

Что должно быть:

- понятный progress;
- корректная блокировка повторных запусков;
- retry там, где это уместно;
- нормальное сообщение об ошибке;
- отсутствие зависаний UI.

## Конкретный roadmap на ближайшие 2 недели

### Неделя 1

1. Добавить Gradle wrapper.
2. Починить `.gitignore` и убрать build-мусор из рабочего процесса.
3. Поднять базовый CI.
4. Привести README к воспроизводимой инструкции сборки.

### Неделя 2

1. Переделать или хотя бы захарднить OAuth flow.
2. Добавить regression tests на git-critical операции.
3. Начать декомпозицию `ChangesScreen`.

## Чеклист перед beta/release

- проект собирается одинаково локально и в CI;
- нет секретов в репозитории и логах;
- auth flow стабилен на GitHub/GitLab/Bitbucket;
- есть regression tests на базовые git-сценарии;
- destructive actions подтверждаются;
- network/auth ошибки оформлены единообразно;
- app не разваливается на rotate/process recreation;
- локализация не ломает UI;
- релизная сборка проходит с minify/shrinkResources;
- проект проверен на нескольких реальных репозиториях, а не только на тестовых.

## Итог

Проект жизнеспособный и уже выглядит сильнее типичного pet Android app. Основа хорошая. Следующий качественный скачок даст не добавление новых фич, а стабилизация:

- безопасность auth;
- тесты;
- CI;
- декомпозиция перегруженных экранов;
- общая release discipline.

Если держать фокус именно на этом, из текущего состояния вполне реально дойти до очень приличного beta-качества без полного переписывания архитектуры.
