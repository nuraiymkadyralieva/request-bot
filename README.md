# Request Bot

Telegram-бот для подачи заявок руководству. Проект реализован на Java 17, Spring Boot, PostgreSQL и Telegram Bot API.

Бот позволяет сотрудникам зарегистрироваться, отправлять заявки через Telegram, отслеживать их статус и получать уведомления о решении руководителя. Руководитель, в свою очередь, получает заявку с кнопками действий и может одобрить, отклонить или прокомментировать её прямо в чате.

## Возможности

- регистрация сотрудника через `/start`
- создание новой заявки через `/new_request`
- выбор типа заявки и срочности через inline-кнопки
- подтверждение отправки заявки
- отправка заявки руководителю
- обработка заявки руководителем через кнопки `Одобрить`, `Отклонить`, `Комментарий`
- уведомление сотрудника о решении руководителя
- просмотр собственных заявок через `/my_requests`
- хранение пользователей и заявок в PostgreSQL
- базовое логирование и обработка ошибок

## Бизнес-сценарий

1. Сотрудник запускает бота командой `/start`.
2. Бот запрашивает ФИО, отдел и должность.
3. После регистрации сотрудник создаёт заявку через `/new_request`.
4. Бот последовательно запрашивает:
   - тип заявки
   - описание
   - срочность
5. Бот показывает итоговую сводку и предлагает:
   - `Отправить`
   - `Отменить`
6. После подтверждения заявка сохраняется в базе данных и отправляется руководителю.
7. Руководитель получает сообщение с кнопками:
   - `Одобрить`
   - `Отклонить`
   - `Комментарий`
8. Статус заявки обновляется, а сотрудник получает уведомление о результате.

## Поддерживаемые команды

- `/start` — запуск бота и регистрация
- `/new_request` — создать новую заявку
- `/my_requests` — посмотреть свои заявки
- `/help` — список доступных команд

## Статусы заявок

- `NEW`
- `IN_REVIEW`
- `APPROVED`
- `REJECTED`

В интерфейсе Telegram эти статусы отображаются в человекочитаемом виде на русском языке.

## Технологии

- Java 17
- Spring Boot 3.5
- Spring Data JPA
- PostgreSQL
- TelegramBots 9.0.0
- Maven
- H2 Database для тестов

## Архитектура проекта

Проект построен по слоистой архитектуре:

- `controller` — принимает Telegram updates и направляет их в бизнес-логику
- `service` — содержит сценарии диалога, работу с заявками и уведомлениями
- `repository` — доступ к базе данных через Spring Data JPA
- `model` — сущности и enum-типизации
- `session` — хранение текущего состояния диалога пользователя
- `config` — Telegram-конфигурация и бин настроек
- `telegram` — интеграция с Telegram Bot API

Основные классы:

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
   └─ java/com/example/request_bot
```

## Настройка проекта

### 1. Клонирование

```bash
git clone https://github.com/nuraiymkadyralieva/request-bot.git
cd request-bot
```

### 2. Создание базы данных

Создайте PostgreSQL базу данных, например:

```sql
CREATE DATABASE requestbot_db;
```

### 3. Настройка `application.yml`

Укажите свои параметры подключения к базе данных и настройки Telegram-бота в [application.yml](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\resources\application.yml).

Пример:

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

### 4. Создание Telegram-бота

1. Откройте [@BotFather](https://t.me/BotFather)
2. Выполните команду `/newbot`
3. Укажите имя и `username` бота
4. Получите `token`
5. Вставьте `username` и `token` в `application.yml`

Чтобы получить `manager-chat-id`, можно использовать [@userinfobot](https://t.me/userinfobot).

### 5. Запуск приложения

Через IntelliJ IDEA:

- откройте проект
- дождитесь загрузки Maven-зависимостей
- запустите [RequestBotApplication.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\RequestBotApplication.java)

Через терминал:

```bash
./mvnw spring-boot:run
```

Для Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

## Тестирование

В проекте настроен тестовый профиль на H2, поэтому тесты не требуют реального подключения к PostgreSQL или Telegram API.

Запуск тестов:

```powershell
.\mvnw.cmd test
```

Тестовый конфиг расположен в [application-test.yml](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\test\resources\application-test.yml).

## Схема базы данных

### Таблица `users`

- `id` — идентификатор пользователя
- `telegram_id` — Telegram ID пользователя
- `chat_id` — чат для отправки уведомлений
- `name` — ФИО
- `department` — отдел
- `position` — должность

### Таблица `requests`

- `id` — идентификатор заявки
- `user_id` — ссылка на сотрудника
- `type` — тип заявки
- `description` — описание
- `priority` — срочность
- `status` — статус
- `manager_comment` — комментарий руководителя
- `created_at` — дата создания

SQL-схема находится в [schema.sql](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\resources\schema.sql).

## Пример пользовательского сценария

### Сотрудник

```text
/start
-> вводит ФИО
-> вводит отдел
-> вводит должность

/new_request
-> выбирает тип заявки
-> вводит описание
-> выбирает срочность
-> подтверждает отправку
```

### Руководитель

```text
Получает новую заявку
-> Одобрить
или
-> Отклонить
или
-> Комментарий
```

### Сотрудник

```text
Получает уведомление:
- заявка одобрена
- заявка отклонена
- получен комментарий руководителя
```

## Особенности реализации

- сценарий диалога построен на пользовательских состояниях (`UserState`)
- промежуточные данные новой заявки хранятся в `RequestDraft`
- для диалога используются inline-кнопки Telegram
- реализована защита от повторной обработки уже завершённых заявок
- бот обновляет `chat_id`, чтобы уведомления приходили в корректный чат пользователя
- логика обработки сообщений и callback-действий сосредоточена в [ConversationService.java](C:\JB\JB projects\IntelliJ IDEA Ultimate Projects\request_bot\src\main\java\com\example\request_bot\service\ConversationService.java)

## Что реализовано в текущей версии

- полноценный MVP по техническому заданию
- русскоязычный интерфейс бота
- регистрация сотрудников
- создание и согласование заявок
- уведомления после решения руководителя
- хранение данных в PostgreSQL
- тестовый контур на H2

## Возможные улучшения

- расширение тестового покрытия
- более детальная ролевая модель для руководителей
- редактирование уже отправленных сообщений в Telegram
- Docker-конфигурация для развёртывания
- вынос секретов в переменные окружения

## Автор

Проект разработан как учебная система заявок на базе Telegram Bot API, Spring Boot и PostgreSQL.
