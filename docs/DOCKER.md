# Local Docker setup

Run commands from the repository root in PowerShell. Docker Desktop must be running with Linux containers. The first image build needs internet access for Java images, Maven, and dependencies.

## Containers and configuration

| Compose service | Purpose | Host address |
| --- | --- | --- |
| `aviation` | Root Spring Boot application | `http://localhost:8080` |
| `identity` | Accounts, authentication, profiles | `http://localhost:8081` |
| `maintenance` | Maintenance workflows | `http://localhost:8083` |
| `notification` | Kafka consumer, SSE and email | `http://localhost:8084` |
| `postgres_db` | Local copy of the shared aviation database | `localhost:5433` |
| `kafka` | Single KRaft broker/controller | `localhost:9092` |

All published ports bind to loopback. Stop existing applications or other containers using these ports first. The original Windows PostgreSQL can remain on port 5432.

The root Dockerfile builds `common` and the application selected by its `SERVICE` argument. Each final image contains one executable `*-app.jar`; `common` is a library inside that JAR, not another container. Maven tests are skipped during image packaging and are run separately when validating changes.

The build uses the checked-in `application-example.properties` as its application configuration. `.dockerignore` excludes local properties, `.env`, build output, uploads and backups. Runtime credentials are supplied through Compose's explicit `environment` entries. Reading `.env` for interpolation does not automatically inject every variable into every container. Do not put credentials into Docker build arguments or commit the local `.env`.

The containers use Asia/Bahrain as their JVM timezone because existing flight rules use local date/time values. The API-documentation health check establishes that the application responds; it does not prove SMTP, Stripe, or every downstream operation works.

## 1. Configure local settings

For a new checkout, copy the template without overwriting an existing `.env`:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Fill every empty credential field. Use this project's existing JWT key, service keys, Stripe sandbox configuration, and SMTP account where applicable. `JWT_SECRET` must be Base64 encoding of at least 32 random bytes. Set a password for the new Docker PostgreSQL instance; it need not match the original database password. Keep the same service-key value wherever that key is used.

Keep Stripe success/cancel URLs browser-accessible, usually on localhost:8080 for this setup. Internal service calls use Compose names such as `http://identity:8081`, and Kafka clients inside containers use `kafka:29092`. These addresses are distinct from browser/host addresses.

Single-quote `.env` values containing `$` so Compose does not interpolate them. Validate without printing resolved secrets:

```powershell
docker compose config --quiet
```

Avoid sharing unfiltered `docker compose config` or container inspection output: resolved environment values include credentials.

## 2. Copy the existing database once

The applications use `ddl-auto=validate`. They require the existing schema, including extraction changes, check constraints, and partial indexes. Do not replace validation with `create` or `update`, enable seeders, or blindly replay the old SQL scripts.

The commands below assume the source database is `aviation` on Windows localhost:5432 with user `postgres`. Check the actual source settings first. Stop source application writes if you want a clear cutover point; the dump is a consistent snapshot, and later source changes will not reach Docker. Confirm source/server compatibility with the installed PostgreSQL tools before export.

Create a unique ignored backup. PostgreSQL prompts for the source password; do not place it in the command line:

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
& "$pgBin\psql.exe" -X -W -h localhost -p 5432 -U postgres -d aviation -c 'SHOW server_version;'
if ($LASTEXITCODE -ne 0) { throw 'Source database check failed.' }

New-Item -ItemType Directory -Path backups -Force | Out-Null
$backupPath = Join-Path (Get-Location) ('backups/aviation-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump')
& "$pgBin\pg_dump.exe" -W -h localhost -p 5432 -U postgres -d aviation --format=custom --file=$backupPath
if ($LASTEXITCODE -ne 0) { throw 'Database export failed.' }
```

`pg_dump` reads the source; it does not change its schema or data. Keep the backup private. Use `--file`, not a PowerShell text pipeline, for the binary custom-format dump.

Start only the database and broker:

```powershell
docker compose up -d --wait postgres_db kafka
if ($LASTEXITCODE -ne 0) { throw 'Infrastructure startup failed.' }
```

Verify that the destination is empty. If this fails, stop and inspect it; do not overwrite an existing Docker database:

```powershell
$tableCount = docker compose exec -T postgres_db psql -X -U postgres -d aviation -At -c "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema');"
if ($LASTEXITCODE -ne 0 -or "$tableCount".Trim() -ne '0') {
    throw 'Destination is not an empty database; restore stopped.'
}

docker compose cp "$backupPath" postgres_db:/tmp/aviation.dump
if ($LASTEXITCODE -ne 0) { throw 'Backup copy failed.' }
docker compose exec -T postgres_db pg_restore -U postgres -d aviation --exit-on-error --single-transaction --no-owner --no-privileges /tmp/aviation.dump
if ($LASTEXITCODE -ne 0) { throw 'Database restore failed; do not start applications.' }
docker compose exec -T postgres_db rm /tmp/aviation.dump
```

Verify restored tables and important constraints before starting the services. The restore intentionally omits original ownership/ACLs for this local development database; it does not copy global PostgreSQL roles. The applications share the Docker `postgres` user locally.

Identity stores profile images under `/app/uploads`, bind-mounted from root `uploads/`. If the previous Identity run used a different working directory, copy its upload files into root `uploads/` before using the Docker application. Database backups do not contain these files. Existing stored image URL formats are unchanged by this Docker setup.

## 3. Start and check the applications

Only after successful restore:

```powershell
docker compose up -d --build --wait
docker compose ps
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --describe --topic aviation-notification-topic
```

Identity waits for PostgreSQL. Aviation waits for PostgreSQL, Kafka and Identity; its Kafka admin fails startup when broker initialization fails. Aviation's topic bean creates `aviation-notification-topic` with one partition, one replica, delete cleanup and 24-hour retention. Notification waits for Aviation, Identity and Kafka, so it starts after topic initialization. Maintenance waits for PostgreSQL, Identity and Aviation. Identity's email dependency on Notification is used at request time and does not introduce a startup cycle.

Use `/swagger-ui/index.html` on each application's port. Login remains on Identity port 8081; connect SSE clients to Notification port 8084. Check login and an authenticated operational request, then the FAA activation-request workflow with the intended and unrelated audiences. Email and Stripe checks require working external credentials; starting the containers alone does not verify those integrations.

Run exactly one Notification instance. Its SSE connections and deduplication cache are process-local. Kafka retention permits consumer catch-up but does not add offline browser replay, persistent notification history, or exactly-once delivery. See [Notification's checks and limitations](../notification-service/README.md).

For routine operation after initialization:

```powershell
docker compose up -d --build --wait
docker compose logs --tail 100 aviation notification
docker compose stop
```

Inspect logs locally before sharing them; application logs may contain account or operational details.

## Persistence and shutdown

The `postgres_data` and `kafka_data` named volumes survive ordinary container recreation and `docker compose down`. Profile files persist in root `uploads/`. Do not run `docker compose down -v` unless intentionally deleting the Docker database and Kafka data. Changing `POSTGRES_PASSWORD` in `.env` does not change the password of an already initialized PostgreSQL volume.

The PostgreSQL health check only establishes server readiness; it does not establish that the aviation schema has been restored. The restore step is deliberately manual and runs only for a fresh destination. Startup must not automatically replay a dump into an existing database.

## Later managed PostgreSQL deployment

The three database-backed images already accept `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. A deployed environment can point those settings to managed PostgreSQL such as RDS without changing Java business code. Provision/import the database, configure private network access, supply secrets through the deployment environment, and configure certificate-verified TLS as required there.

This Compose file is the local development stack and still starts/depends on its local PostgreSQL service. For cloud deployment, use deployment-specific orchestration that removes those local database dependencies and supplies managed-service endpoints. An RDS URL alone does not perform deployment. Public routing, Kafka security, uploads storage and external browser redirect URLs also need deployment-specific configuration.

## Verification on 14 November 2026

- Compose configuration validated, and all four images built successfully using the reusable Dockerfile.
- The source PostgreSQL server was verified as 18.6. Its database was exported to an ignored custom-format backup, then restored with one transaction into a verified-empty Docker database. The restored schema contains 26 public tables and the `flight_inspections_one_pending_per_flight` partial index. No source database mutation was performed.
- All six containers became healthy on the documented host ports. The Kafka topic has one partition, one replica, delete cleanup and 24-hour retention. Consumer lag was zero after the live checks.
- Each application's OpenAPI endpoint responded. Missing and invalid SSE bearer tokens returned 401. A read-only authenticated Aviation request successfully resolved the account through Identity.
- Synthetic events sent through the real Kafka broker reached two authenticated FAA SSE streams, excluded an unrelated customer's stream, suppressed a duplicate event, and delivered a user-specific event only to that user's stream. The two FAA streams used the same existing FAA account; this did not demonstrate two distinct FAA accounts. Short-lived locally signed tokens exercised JWT/account validation, not the password-login flow. The streaming client was an HTTP test client, not a browser.
- Every packaged application configuration matched its checked-in example template, and each executable JAR included the `common` library. The Identity upload mount was present and writable.
- The existing focused Kafka/email suite passed 42 tests with no failures, errors, or skips.

These checks did not trigger an actual airplane-activation business operation, send SMTP email, call Stripe, verify broker-outage recovery, or execute the full database-dependent/Cucumber suites. Those checks remain separate from container startup and the synthetic Kafka-to-SSE smoke test. The ignored local verification script and test log are under `backups/`; they are not required to run the application.

## References

- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Compose networking](https://docs.docker.com/compose/how-tos/networking/)
- [PostgreSQL official image and PostgreSQL 18 volume layout](https://hub.docker.com/_/postgres)
- [Apache Kafka single-node Docker example](https://raw.githubusercontent.com/apache/kafka/4.1.2/docker/examples/docker-compose-files/single-node/plaintext/docker-compose.yml)
