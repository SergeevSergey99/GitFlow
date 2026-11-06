# Auth Layer — Known Issues

## Критические

- [x] **#1 — Credentials не инжектируются для Bitbucket/Gitea/Azure DevOps**
  `resolveCredentialsProvider` в `GitRepository.kt` распознаёт только `github.com` и `gitlab.com`.
  Клонирование, push и pull приватных репозиториев Bitbucket, Gitea и Azure DevOps всегда завершались
  с 401 без этого исправления. Также исправлен `resolveCommitIdentity`.
  _Исправлено в `GitRepository.kt`._

- [x] **#2 — `isAuthenticated` не проверяет срок жизни токена**
  `fun isAuthenticated(...) = getToken(...) != null` — истёкший токен показывался как активный.
  UI отображал пользователя как подключённого, запросы падали с 401.
  _Исправлено в `AuthManager.kt`._

- [x] **#3 — Сбой рефреша токена не останавливал запрос**
  В `getRepositories` результат `refreshGitLabTokenIfNeeded()` и `refreshBitbucketTokenIfNeeded()`
  игнорировался. Код продолжал с истёкшим токеном → "Ошибка загрузки" вместо "Войдите снова".
  _Исправлено в `AuthManager.kt`._

- [x] **#4 — `pendingAuthStates` — race condition**
  Обычный `mutableMapOf()` (не потокобезопасен). `getAuthUrl` и `handleAuthCallback` вызываются
  из разных корутин — потенциальная гонка при параллельном доступе.
  _Исправлено, затем заменено целиком (2026-11-06): карты в памяти больше нет, ожидающие PKCE-входы
  хранятся в зашифрованных prefs (`pending_auth_<state>`), см. #16._

- [x] **#13 — Утечка токена через поддельный remote URL** *(аудит 2026-07-04)*
  `resolveCredentialsProvider` в `GitRepository.kt` матчил провайдера через
  `host.contains("github.com")`. Хост `github.com.attacker.com` проходил проверку.
  _Исправлено: строгий `hostMatches` в `GitRepository` и `AuthManager`, тест `GitRepositoryCredentialsTest`._

- [x] **#14 — Release-сборка ломает Bitbucket/Gitea/Azure (R8 + Gson)** *(аудит 2026-07-04)*
  _Исправлено: `-keep class com.gitflow.android.data.auth.** { *; }` в `proguard-rules.pro`._

- [x] **#15 — Восстановление из бэкапа = краш-луп** *(аудит 2026-07-04)*
  _Исправлено: `fullBackupContent`/`dataExtractionRules` в манифесте и пересоздание prefs в
  `AuthManager.createEncryptedPreferences()`._

- [x] **#16 — Вход отменялся, если процесс убит, пока открыт браузер** *(2026-11-06)*
  PKCE-verifier жил только в памяти, `AuthViewModel.currentProvider` терялся вместе с ViewModel,
  флаги `OAuthActivity` сбрасывались при пересоздании.
  _Исправлено: verifier в зашифрованных prefs (запись через `commit()`), провайдер возвращается
  в результате `OAuthActivity`, её флаги — в `onSaveInstanceState`._

- [x] **#17 — Уведомление «сессия истекла» каждые 30 минут** *(2026-11-06)*
  _Исправлено: `TokenRefreshWorker` ставит отметку `markSessionExpiryNotified` и пропускает
  провайдера; отметку снимают новый токен (`saveToken`) и `logout`._

---

## Умеренные

- [x] **#5 — Нет email у пользователей Bitbucket**
  `email = null` хардкодом. Bitbucket возвращает email через отдельный endpoint `/user/emails`.
  Без email git-коммиты могут использовать пустой адрес.
  _Исправлено: добавлен `getUserEmails` в `BitbucketApi.kt`, вызов `fetchBitbucketPrimaryEmail` в `handleBitbucketCallback`._

- [x] **#6 — GitHub org repos: только 1 страница на организацию**
  `getOrganizationRepositories` делает один вызов на орг (макс. 100 репо).
  Большие организации (Google, Microsoft и т.п.) будут показаны неполностью.
  _Исправлено: пагинация до 3 страниц на организацию в `getGitHubRepositories`._

- [ ] **#7 — `AuthManager` раздаёт Context UI-слою**
  Поле стало приватным, но `AuthManager.getContext()` по-прежнему используют `AuthViewModel`
  (строки и Intent для `OAuthActivity`) и `RemoteRepositoriesViewModel` (строки).
  Data-слой не должен предоставлять Context UI-слою.

- [x] **#8 — `resolveCommitIdentity` fallback перебирает только GitHub/GitLab**
  Если remote URL не распознан, перебираются только 2 провайдера вместо всех 5.
  Для Bitbucket/Gitea/Azure коммиты могут получить неверную подпись.
  _Исправлено в рамках #1 — теперь перебираются все `GitProvider.entries`._

---

## Незначительные

- [x] **#9 — Двойной `withContext(Dispatchers.IO)` в refresh-методах**
  `refreshGitLabTokenIfNeeded` и `refreshBitbucketTokenIfNeeded` оборачивали себя в
  `withContext(Dispatchers.IO)`, хотя вызываются уже из `withContext(Dispatchers.IO)`.
  _Исправлено: лишний `withContext` удалён, добавлен комментарий о контракте вызова._

- [ ] **#10 — `id` через `hashCode()` на строках (Bitbucket, Azure DevOps)**
  Пользователи уже получают id через CRC32 (`toDeterministicId`), а конвертеры репозиториев —
  всё ещё `uuid.hashCode()`/`account_id.hashCode()`/`id.hashCode()`. Уникальность не гарантирована:
  при коллизии `distinctBy { it.id }` может схлопнуть два репозитория в один.

- [ ] **#11 — Azure DevOps `updatedAt = ""`**
  Azure API не возвращает дату обновления репозитория напрямую. Пустая строка ломает
  сортировку `sortedByDescending { it.updatedAt }` и отображение даты в карточке.

- [ ] **#12 — `OAuthConfig` — глобальное мутабельное состояние**
  `object OAuthConfig` с приватными `var`-полями инициализируется из `AuthManager.init`.
  Делает юнит-тестирование практически невозможным без рефлексии.
