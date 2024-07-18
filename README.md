# pnpmailbot

**Обучающий проект**

Бот для Telegram для отправки(smtp)/приема(imap) электронных писем.
В конфигурации задаются настройки общей учетной записи, но пользователи могут указать свои smtp/imap учетные записи 

Основные команды:

- /start - получить основные команды
- /send_mail - отправить письмо
- /get_mails - получить список непрочитанные письма, отобразить короткое инфо
- /show_mail_# - просмотреть письмо
- /as_seen - отметить последнее письмо прочитанным
- /forward - переслать последнее просмотренное письмо
- /reply - ответить на последнее просмотренное письмо
- /config - добавить конфигурацию почтового сервера

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
```

##  Настройки 

src/mail/resources/application.conf

```conf
bot = {
  api-key = "???"
}

encryption-config = {
  key = "0123456789abcdef"
  iv = "abcdef9876543210"
}

database = {
  host = "localhost",
  port = 5433,
  database = "database1",
  user = "postgres",
  password: "example"
}

imap {
  host = "imap.example.com",
  port = 993
  user = "user@example.com",
  pass = "password"
}

smtp = {
  host = "smtp.example.com",
  port = 583
  user = "user@example.com"
  pass = "password"
}


```

### Сборка

#### FatJar

1. sbt assembly
2. рядом с pnpmailbot.jar положить "application.conf" (который при разработке "resource/application.conf")  
3. java -jar pnpmailbot.jar
