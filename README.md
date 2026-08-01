# AVAS Backend

Spring Boot system of record for AVAS identities, roles, projects, planning artifacts, governed pricing, commerce and audit history.

## Requirements

- Java 21 or newer
- Maven 3.9 or newer
- MySQL 8
- MongoDB 7

## Run locally

Copy `.env.example` to `.env`, replace every placeholder, export it into your shell, start MySQL and MongoDB, then run:

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

Startup fails unless `JWT_SECRET` contains at least 32 UTF-8 bytes.

Health is available at <http://localhost:8080/actuator/health> and API discovery at <http://localhost:8080/api/v1>.

## Verify

```bash
mvn test
mvn -DskipTests package
docker build -t avas-backend .
```

## First administrator

No shared accounts or passwords are included. For a new database only, set `AVAS_BOOTSTRAP_ADMIN_ENABLED=true` and provide the remaining `AVAS_BOOTSTRAP_ADMIN_*` settings with a unique 12–72 character password containing uppercase, lowercase, number and symbol. Start once, verify access, then remove the password and disable bootstrap.

## Security model

Every protected request is authenticated and reduced to the selected `X-Active-Role`. The selected role must be active, assigned to the account and hold the required permission. Project access is owner-scoped unless the active role is Administrator; professional and site access requires an explicit assignment source.

See `docs/api.md` for endpoints and `docs/architecture.md` for data ownership.
