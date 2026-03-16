# Request Bot

Telegram-бот для подачи и согласования заявок сотрудников с руководством.

Проект реализован на `Java 17`, `Spring Boot`, `PostgreSQL` и `Telegram Bot API`. Бот позволяет сотрудникам пройти регистрацию, создать заявку прямо в Telegram, отправить её руководителю и получить уведомление о результате. Руководитель получает заявку с кнопками действий и может одобрить, отклонить или прокомментировать её в одном чате.
`Request Bot` решает простую прикладную задачу: переводит внутренний процесс подачи заявок в удобный Telegram-интерфейс.

Сотрудник может:
- зарегистрироваться в системе
- создать заявку за несколько шагов
- посмотреть свои заявки и их статус
- получить уведомление после решения руководителя

Руководитель может:
- получать новые заявки автоматически
- одобрять или отклонять их
- отправлять комментарий сотруднику

Все заявки и данные пользователей сохраняются в базе данных PostgreSQL.

## Ключевые возможности

- [x] регистрация сотрудника через `/start`
- [x] создание заявки через `/new_request`
- [x] выбор типа заявки через inline-кнопки
- [x] выбор срочности через inline-кнопки
- [x] подтверждение отправки заявки
- [x] отправка заявки руководителю
- [x] кнопки руководителя `Одобрить`, `Отклонить`, `Комментарий`
- [x] обновление статуса заявки
- [x] уведомление сотрудника о решении
- [x] просмотр своих заявок через `/my_requests`
- [x] хранение данных в PostgreSQL
- [x] конфигурация через `application.yml`
- [x] базовое логирование и обработка ошибок

## Команды бота

| Команда | Назначение |
| --- | --- |
| `/start` | Запуск бота и регистрация пользователя |
| `/new_request` | Создание новой заявки |
| `/my_requests` | Просмотр своих заявок |
| `/help` | Справка по командам |

## Как работает бот

### Сценарий сотрудника

1. Пользователь запускает бота командой `/start`.
2. Бот запрашивает:
   - ФИО
   - отдел
   - должность
3. После регистрации сотрудник использует `/new_request`.
4. Бот по шагам собирает данные:
   - тип заявки
   - описание
   - срочность
5. Бот показывает итоговую сводку и предлагает:
   - `Отправить`
   - `Отменить`
6. После подтверждения заявка сохраняется в базе и отправляется руководителю.

### Сценарий руководителя

1. Руководитель получает сообщение с информацией по новой заявке.
2. В сообщении доступны кнопки:
   - `Одобрить`
   - `Отклонить`
   - `Комментарий`
3. После действия руководителя:
   - статус заявки меняется
   - сотруднику отправляется уведомление

## Технологии

- `Java 17`
- `Spring Boot 3.5`
- `Spring Data JPA`
- `PostgreSQL`
- `TelegramBots 9.0.0`
- `Maven`
- `H2 Database` для тестового профиля

## Архитектура

Проект построен по слоистой архитектуре.

| Слой | Назначение |
| --- | --- |
| `controller` | Принимает Telegram updates и направляет их в бизнес-логику |
| `service` | Содержит сценарии диалога, работу с заявками и уведомлениями |
| `repository` | Обеспечивает доступ к данным через Spring Data JPA |
| `model` | Сущности и enum-типы |
| `session` | Хранение текущего состояния диалога пользователя |
| `config` | Настройки Telegram-бота и конфигурация Spring |
| `telegram` | Интеграция с Telegram Bot API |

Ключевые классы проекта:

- [RequestBotApplication.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\RequestBotApplication.java)
- [ConversationService.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\service\ConversationService.java)
- [RequestTelegramBot.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\telegram\RequestTelegramBot.java)
- [RequestService.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\service\RequestService.java)
- [UserService.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\service\UserService.java)
- [NotificationService.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\service\NotificationService.java)

## Структура проекта

```text
src
├─ main
│  ├─ java/com/example/request_bot
│  │  ├─ config
│  │  ├─ controller
│  │  ├─ dto
│  │  ├─ model
│  │  ├─ repository
│  │  ├─ service
│  │  ├─ session
│  │  └─ telegram
│  └─ resources
│     ├─ application.yml
│     └─ schema.sql
└─ test
   ├─ java/com/example/request_bot
   └─ resources
```

## Быстрый старт

### 1. Клонирование репозитория

```bash
git clone https://github.com/nuraiymkadyralieva/request-bot.git
cd request-bot
```

### 2. Создание базы данных

Создайте PostgreSQL базу данных:

```sql
CREATE DATABASE requestbot_db;
```

### 3. Создание Telegram-бота

1. Откройте [@BotFather](https://t.me/BotFather)
2. Выполните `/newbot`
3. Укажите имя и `username`
4. Получите `token`
5. Добавьте данные бота в конфигурацию

Для получения `manager-chat-id` можно использовать [@userinfobot](https://t.me/userinfobot).

### 4. Настройка приложения

Откройте [application.yml](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\resources\application.yml) и заполните настройки базы данных и Telegram.

### 5. Запуск

Через IntelliJ IDEA:

- открыть проект
- дождаться загрузки Maven-зависимостей
- запустить [RequestBotApplication.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\RequestBotApplication.java)

Через терминал:

```powershell
.\mvnw.cmd spring-boot:run
```

## Конфигурация

Пример `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/requestbot_db
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

telegram:
  bot:
    username: your_bot_username
    token: your_bot_token
    manager-chat-id: 123456789
```

Важно:
- не коммитьте реальные токены и пароли в репозиторий
- для публичного репозитория лучше использовать переменные окружения

## Тестирование

Для тестов используется отдельный профиль на H2, поэтому запуск тестов не требует живой PostgreSQL базы и настоящего Telegram API.

Запуск тестов:

```powershell
.\mvnw.cmd test
```

Тестовый конфиг:
- [application-test.yml](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\test\resources\application-test.yml)

## Текущее состояние проекта

Сейчас проект представляет собой полноценный MVP, который уже закрывает основное техническое задание:

- регистрация сотрудников
- создание заявок
- отправка заявок руководителю
- согласование заявки в Telegram
- уведомления пользователю
- хранение данных в PostgreSQL

## Возможные улучшения

- ролевая модель для нескольких руководителей
- редактирование уже отправленных сообщений вместо отправки новых
- Docker и `docker-compose` для развёртывания
- вынос секретов в переменные окружения

