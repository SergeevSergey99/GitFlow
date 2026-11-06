# GitFlow for Android

Мобильный Git-клиент для Android на базе Jetpack Compose и JGit.

---

## Настройка OAuth (`oauth.properties`)

Приложение поддерживает пять провайдеров:

| Провайдер     | Тип авторизации | Требует `oauth.properties` |
|---------------|-----------------|----------------------------|
| GitHub        | OAuth 2.0 + PKCE | Да |
| GitLab        | OAuth 2.0 + PKCE | Да |
| Bitbucket     | OAuth 2.0 + PKCE | Да |
| Gitea/Forgejo | Personal Access Token | Нет |
| Azure DevOps  | Personal Access Token | Нет |

Для **Gitea** и **Azure DevOps** никакая конфигурация сборки не нужна — пользователь вводит URL и PAT прямо в приложении.

---

### Шаг 1 — Создать файл `oauth.properties`

Скопируйте пример и заполните нужные провайдеры:

```bash
cp app/src/main/assets/oauth.properties.example \
   app/src/main/assets/oauth.properties
```

> **Важно:** `oauth.properties` уже добавлен в `.gitignore`. Никогда не коммитьте этот файл — он содержит секреты приложения.

---

### Шаг 2 — Зарегистрировать OAuth-приложение

#### GitHub

1. Откройте **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**
2. Заполните поля:
   - **Application name:** GitFlow Android (любое)
   - **Homepage URL:** любой URL
   - **Authorization callback URL:** `gitflow://oauth/callback`
3. Нажмите **Register application**
4. Скопируйте **Client ID** и сгенерируйте **Client Secret**

```properties
github.client.id=Ov23liXXXXXXXXXXXXXX
github.client.secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

---

#### GitLab

1. Откройте **GitLab → Preferences → Applications** (или для группы: **Group → Settings → Applications**)
2. Заполните поля:
   - **Name:** GitFlow Android
   - **Redirect URI:** `gitflow://oauth/callback`
   - **Scopes:** отметьте `read_user`, `read_repository`, `write_repository`
3. Нажмите **Save application**
4. Скопируйте **Application ID** и **Secret**

```properties
gitlab.client.id=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
gitlab.client.secret=gloas-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

---

#### Bitbucket

1. Откройте **Bitbucket → Personal settings → OAuth consumers → Add consumer**
2. Заполните поля:
   - **Name:** GitFlow Android
   - **Callback URL:** `gitflow://oauth/callback`
   - **Permissions:** отметьте `Account: Read`, `Repositories: Read`, `Repositories: Write`
3. Нажмите **Save**
4. Раскройте созданный consumer — скопируйте **Key** (→ client.id) и **Secret** (→ client.secret)

```properties
bitbucket.client.id=XXXXXXXXXXXXXXXXXXXX
bitbucket.client.secret=XXXXXXXXXXXXXXXXXXXXXXXXXXXXxxxx
```

---

### Шаг 3 — Итоговый файл

Заполните только те провайдеры, которые нужны. Незаполненные провайдеры просто не будут доступны в приложении.

```properties
# app/src/main/assets/oauth.properties

github.client.id=Ov23liXXXXXXXXXXXXXX
github.client.secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

gitlab.client.id=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
gitlab.client.secret=gloas-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

bitbucket.client.id=XXXXXXXXXXXXXXXXXXXX
bitbucket.client.secret=XXXXXXXXXXXXXXXXXXXXXXXXXXXXxxxx
```

---

### Gitea / Forgejo и Azure DevOps

Эти провайдеры **не требуют регистрации приложения** и не используют `oauth.properties`.

Пользователь подключает аккаунт прямо в приложении:
**Настройки → Управление аккаунтами → [провайдер] → Подключить**

**Gitea/Forgejo:**
- URL экземпляра: `https://gitea.example.com`
- Имя пользователя: логин аккаунта
- PAT: создаётся в **User Settings → Applications → Generate Token** на вашем экземпляре

**Azure DevOps:**
- URL организации: `https://dev.azure.com/myorg`
- PAT: создаётся в **User Settings → Personal access tokens → New Token** (права: `Code: Read`)

---

## Порядок загрузки `oauth.properties`

`OAuthConfig` ищет учётные данные в следующем порядке:

1. `app/src/main/assets/oauth.properties` — основной способ для разработчиков
2. Переменные окружения (`GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, и т.д.) — для CI/CD
3. `context.filesDir/oauth.properties` — для production-сборок без встроенных секретов

Переменные окружения для CI:

```
GITHUB_CLIENT_ID
GITHUB_CLIENT_SECRET
GITLAB_CLIENT_ID
GITLAB_CLIENT_SECRET
BITBUCKET_CLIENT_ID
BITBUCKET_CLIENT_SECRET
```

---

## Сборка

Проект собирается через **Android Studio**. Скриптов `gradlew` в репозитории нет — есть только
`gradle/wrapper/gradle-wrapper.properties` (Gradle 9.6.0, AGP 9.5.1). Android Studio скачивает
этот Gradle при синхронизации.

Минимальные требования: Android 8.0 (API 26), compileSdk 36, targetSdk 35.

### Тесты из консоли

Юнит-тесты (Robolectric + реальный JGit во временных папках) запускаются Gradle'ом, который
скачала Studio, и JDK из её комплекта:

```bash
JAVA_HOME="<Android Studio>/jbr" "<GRADLE_USER_HOME>/wrapper/dists/gradle-9.6.0-bin/<hash>/gradle-9.6.0/bin/gradle" :app:testDebugUnitTest
```

Первый запуск Robolectric скачивает `android-all` для SDK 34.

### Разрешение «Доступ ко всем файлам»

JGit работает с обычными путями к файлам, поэтому на Android 11+ для репозиториев вне папки
приложения (своя папка хранения, добавление существующего репозитория) нужно разрешение
`MANAGE_EXTERNAL_STORAGE`. Оно выдаётся в «Настройки → Разрешения → Доступ к файлам».
Публикация в Google Play с этим разрешением требует декларации.
