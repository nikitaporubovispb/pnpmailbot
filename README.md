# pnpmailbot

**Обучающий проект**

Бот для Telegram для отправки(smtp)/приема(imap) электронных писем.
В конфигурации задаются настройки общей учетной записи, но пользователи могут указать свои smtp/imap учетные записи 

Основные команды:

- /start - получить основные команды
- /get_mails - получить непрочитанные письма
- /send_mail - отправить письмо

## Стек: 

- scala 3
- cats-effect
- canoe
- pureconfig
- jakarta.mail
- jsoup
- skunk-core

## База:

docker-compose.yml
```yml
version: '3.9'

services:

  db:
    image: postgres
    restart: always
    shm_size: 128mb
    ports:
      - "5433:5432"
    environment:
      POSTGRES_PASSWORD: example

  adminer:
    image: adminer
    restart: always
    ports:
      - 8080:8080
```

##  Настройки 

src/mail/resources/application.conf

```conf
bot = {
  api-key = "???"
}

database = {
  host = "localhost",
  port = 5433,
  database = "database1",
  user = "postgres",
  password: "example"
}

smtp = {
  host = "localhost",
  port = 1025
  user = "user",
  pass = "password"
}

imap {
  host = "localhost",
  port = 993
  user = "user",
  pass = "password"
}
```