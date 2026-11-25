# Binance Price Tracker

Сервис по расписанию запрашивает среднюю цену торговой пары из Binance API (`GET /api/v3/avgPrice`), сохраняет историю в PostgreSQL и отдаёт аналитику через REST API.

## Стек и особенности реализации

- Java 21, Spring Boot 3.4, PostgreSQL 16, Flyway
- Все денежные значения и расчёты выполняются через `BigDecimal` (точность 8 знаков после запятой, округление `HALF_UP`)
- Защита от дублей на уровне БД по уникальному ключу `(symbol, close_time)`
- Ошибки сети, `5xx` и rate limits (`429` с учётом `Retry-After`, `418`) обрабатываются без остановки планировщика
- Тесты: JUnit 5, Mockito, Testcontainers (PostgreSQL), WireMock

## Запуск

### Через Docker Compose

Поднять PostgreSQL и сервис одной командой из корня проекта:

```bash
docker compose up --build -d
```

При необходимости можно переопределить переменные окружения (например, торговую пару, интервал опроса или зеркало Binance, если `api.binance.com` возвращает HTTP 451):

```bash
BINANCE_SYMBOL=BTCUSDT BINANCE_POLL_INTERVAL=PT15S BINANCE_BASE_URL=[https://data-api.binance.vision](https://data-api.binance.vision) docker compose up --build -d
```

Остановка:

```bash
docker compose down
```

### Локально (без контейнера для приложения)

1. Запустить PostgreSQL:
   ```bash
   docker compose up -d postgres
   ```
2. Запустить приложение через Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

### Запуск тестов

Для прогона интеграционного теста нужен запущенный Docker:

```bash
./mvnw test
```

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `BINANCE_SYMBOL` | `BNBUSDT` | Торговая пара по умолчанию |
| `BINANCE_POLL_INTERVAL` | `PT30S` | Интервал опроса Binance в формате ISO-8601 |
| `BINANCE_BASE_URL` | `https://api.binance.com` | Базовый URL Binance API |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/binance_prices` | JDBC URL базы данных |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Пользователь PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Пароль PostgreSQL |

## REST API

Во всех эндпоинтах параметр `symbol` необязательный (если не передан, берётся значение `BINANCE_SYMBOL`). Границы периода `from` и `to` передаются в формате ISO-8601.

### 1. Последняя сохранённая цена

```bash
curl "http://localhost:8080/api/v1/prices/latest?symbol=BNBUSDT"
```

```json
{
  "symbol": "BNBUSDT",
  "price": 612.45000000,
  "fetchedAt": "2026-09-30T12:30:00Z"
}
```

### 2. Средняя цена за период

```bash
curl "http://localhost:8080/api/v1/prices/average?symbol=BNBUSDT&from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z"
```

```json
{
  "averagePrice": 615.25
}
```

### 3. История цен с пагинацией

Параметры пагинации: `page` (по умолчанию `0`, `>= 0`) и `size` (по умолчанию `20`, от `1` до `100`).

```bash
curl "http://localhost:8080/api/v1/prices?symbol=BNBUSDT&from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z&page=0&size=20"
```

```json
{
  "items": [
    {
      "symbol": "BNBUSDT",
      "price": 612.45000000,
      "fetchedAt": "2026-09-01T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "hasNext": true
}
```

### 4. Тренд за период

Значения `trend`: `UPWARD`, `DOWNWARD`, `FLAT` (сравниваются первая и последняя зафиксированные цены в интервале).

```bash
curl "http://localhost:8080/api/v1/prices/trend?symbol=BNBUSDT&from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z"
```

```json
{
  "symbol": "BNBUSDT",
  "start_date": "2026-09-01",
  "end_date": "2026-09-30",
  "trend": "UPWARD"
}
```

### Коды ошибок

- `400 Bad Request` — невалидный формат даты, отсутствует обязательный параметр, `from > to` или некорректные `page` / `size`.
- `404 Not Found` — по указанной паре или за выбранный интервал нет сохранённых записей.