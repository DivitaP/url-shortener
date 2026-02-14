# 🔗 URL Shortener API

A production-style REST API for shortening URLs, built with **Java 17** and **Spring Boot 3**.

## Architecture

```
Client
  │
  ▼
UrlController          (REST layer — routing, HTTP status codes)
  │
  ▼
UrlService             (Business logic — code generation, expiry, click tracking)
  │
  ▼
UrlRepository          (Data access — Spring Data JPA)
  │
  ▼
H2 (dev) / PostgreSQL (prod)
```

## Endpoints

| Method   | Endpoint                    | Description                        | Status |
|----------|-----------------------------|------------------------------------|--------|
| `POST`   | `/api/shorten`              | Shorten a URL                      | 201    |
| `GET`    | `/{shortCode}`              | Redirect to original URL           | 302    |
| `GET`    | `/api/stats/{shortCode}`    | Get click stats for a short URL    | 200    |
| `DELETE` | `/api/urls/{shortCode}`     | Delete a shortened URL             | 204    |
| `GET`    | `/actuator/health`          | Service health check               | 200    |
| `GET`    | `/actuator/metrics`         | App metrics                        | 200    |

## Request & Response Examples

**POST /api/shorten**
```json
// Request
{
  "originalUrl": "https://github.com/your-profile/some-very-long-repo-url",
  "expiryDays": 7
}

// Response 201
{
  "shortCode": "aB3xYz",
  "shortUrl": "http://localhost:8080/aB3xYz",
  "originalUrl": "https://github.com/your-profile/some-very-long-repo-url",
  "expiresAt": "2025-01-21T14:30:00"
}
```

**GET /api/stats/aB3xYz**
```json
{
  "shortCode": "aB3xYz",
  "shortUrl": "http://localhost:8080/aB3xYz",
  "originalUrl": "https://github.com/your-profile/some-very-long-repo-url",
  "clickCount": 14,
  "createdAt": "2025-01-14T14:30:00",
  "expiresAt": "2025-01-21T14:30:00",
  "expired": false
}
```

**Error Response (consistent shape)**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "No URL found for short code: abc123",
  "path": "/abc123",
  "timestamp": "2025-01-14T14:30:00"
}
```

## Running Locally

**With H2 (in-memory, zero setup):**
```bash
./gradlew bootRun
```
Then open: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:urlshortenerdb`)

**With Docker + PostgreSQL:**
```bash
docker-compose up --build
```

## Running Tests
```bash
# All tests
./gradlew test

# With report
./gradlew test jacocoTestReport
```

## Tech Stack

| Layer        | Technology                    |
|--------------|-------------------------------|
| Language     | Java 17                       |
| Framework    | Spring Boot 3.2               |
| Data Access  | Spring Data JPA + Hibernate   |
| Database     | H2 (dev) / PostgreSQL (prod)  |
| Validation   | Jakarta Validation + Hibernate Validator |
| Testing      | JUnit 5, Mockito, MockMvc     |
| Infra        | Docker, Docker Compose        |
| Observability| Spring Actuator               |

## Key Design Decisions

- **Short codes** are 6-char alphanumeric strings generated with `SecureRandom` — collision-safe with 62⁶ (~56B) combinations
- **Expiry** defaults to 30 days; expired URLs return `410 Gone` instead of `404 Not Found` to distinguish "never existed" vs "once existed"
- **Click tracking** is updated on every redirect and exposed via the stats endpoint
- **Scheduled cleanup** runs hourly to purge expired records from the DB
- **Consistent error shape** — all errors return `{ status, error, message, path, timestamp }` via `@RestControllerAdvice`
