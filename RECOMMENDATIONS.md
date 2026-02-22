# Рекомендации и ближайший backlog

> Обновлено: 2026-02-22
> История изменений: `CHANGELOG.md`

## Выполнено

- [x] Разделён `GitRepository` по тематическим файлам + общий интерфейс `IGitRepository`.
- [x] Исправлен crash при выборе активного репозитория.
- [x] Исправлен reset-конфликт при unstage (`<paths> + --mixed/--soft/--hard`).
- [x] Улучшен `ChangesScreen`: полноэкранный diff, long-press actions, tree/list режим, сворачивание панели commit.
- [x] Добавлены действия: история файла, копирование имени/пути, reset changes.

## P0 — следующий шаг (обязательное)

- [ ] Добавить regression tests для сценариев:
  - [ ] single/batch stage/unstage;
  - [ ] reset/discard file changes;
  - [ ] open diff / open history.
- [ ] Harden OAuth WebView:
  - [ ] allowlist хостов (`github.com`, `gitlab.com`, callback host);
  - [ ] блокировка любых внешних редиректов;
  - [ ] аудит JavaScript/DOM storage настроек.
- [ ] Проверить отсутствие секретов в логах auth/network и в crash-репортах.

## P1 — архитектура и поддерживаемость

- [ ] Разгрузить `ChangesScreen.kt` на отдельные компоненты (tree panel, file actions menu, dialogs).
- [ ] Разгрузить `CommitDetailDialog.kt` на независимые секции.
- [ ] Внедрить DI (Hilt/Koin) минимум для `IGitRepository`, `AuthManager`, `AppSettingsManager`.
- [ ] Добавить CI pipeline: compile + lint + тесты.

## P2 — производительность и UX

- [ ] Профилировать работу на больших репозиториях (graph/diff/history).
- [ ] Добавить кэширование дорогих вычислений (diff/history при повторных открытиях).
- [ ] Продумать унификацию tree/list UX между `ChangesScreen` и `CommitDetailDialog`.

## Технические заметки

- JGit остаётся на `5.13.3` из-за совместимости с minSdk 26.
- Для воспроизводимых локальных сборок нужен стабильный `gradlew` в репозитории.
