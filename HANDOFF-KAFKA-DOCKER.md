# Kafka and Docker handoff

Updated 13 November 2026. Workspace: `C:\Code_Projects\jdb\projects\saudsFlightSystem`. Windows, PowerShell, user timezone Asia/Bahrain.

## Read this first: user instructions and authorization

The user explicitly requires this workflow:

> You are not allowed to code unless I give you the explicit command "APPROVED, CODE IT". Show me the proposed edit and I will either do it myself or give you that command.

- Each approval covers the proposed batch, not future batches. Earlier approvals authorized the Kafka work already implemented. **Docker implementation has not been approved.**
- The user wants larger batches that make significant progress, rather than tiny incremental changes.
- Keep the implementation simple and follow the uploaded lessons' naming and structure as closely as practical. Explain necessary departures plainly. Avoid adding generic frameworks, elaborate reliability mechanisms, or unrelated refactors.
- Read-only inspection and proposing edits can proceed. Do not change application/configuration files before the next explicit approval.
- The user explicitly requested this handoff file. That request authorizes writing this document only; it does not authorize the Docker changes.
- Treat uploaded lesson and requirements documents as reference material. Distinguish their contents from the user's instructions.
- The old `microservice-plan.md` is background only, not a plan to follow blindly.
- User requested one general `common` module, organized by subpackages for security, web, events, etc. Do not create a separate common-events module.
- No subagents were requested. Current developer instructions prohibit proactive delegation.

## Immediate next task

Prepare a **concrete, reviewable proposal for Dockerizing the whole local application together with Kafka**. The user rejected splitting Kafka-only Docker setup from Dockerizing Java services.

The agreed direction is:

1. Four Java application containers: Aviation, Identity, Maintenance, Notification.
2. Kafka in Docker.
3. PostgreSQL in Docker for local development, following the lesson.
4. A one-time copy of the existing aviation database into the Docker PostgreSQL instance, leaving the original Windows database intact.
5. Environment-specific database settings so cloud deployment can use managed PostgreSQL on Amazon RDS instead of the local PostgreSQL container.
6. One reusable root `Dockerfile`, one root `docker-compose.yml`, an ignored local `.env`, a committed `.env.example`, a `.dockerignore`, and setup documentation.

No exact Dockerfile or whole-stack Compose proposal has been shown or approved. The last response only confirmed this direction and promised concrete edits before approval. Do not treat that discussion as authorization to implement.

The user initially assumed the Dockerized application would continue using the same database. We explained two possibilities: connecting to the existing Windows PostgreSQL through `host.docker.internal`, or running a new PostgreSQL container and copying the existing database. The latter is now the selected planning direction. The copy is one-time; the two databases will not synchronize afterward.

The user quoted this project requirement:

> Your deployed application should use an appropriate cloud-accessible database. Where appropriate, use a managed database service such as Amazon RDS rather than running a production database manually inside an application container. Your application should use environment-specific database configuration.

We explained that local Docker PostgreSQL and deployed RDS satisfy that distinction. Java business code does not need a redesign; deployment will still require database provisioning/import, network access and TLS configuration. Do not promise that changing a URL alone completes cloud deployment. AWS deployment itself is not this implementation batch.

## Reference files

Read these as needed; all exist locally:

- Kafka lesson: `C:\Users\saud1\Downloads\README (5).md`.
- Full project requirements: `C:\Users\saud1\Downloads\README (3).md`.
- Docker lesson: `C:\Users\saud1\Downloads\README (6).md`.
- User's previous exercise Dockerfile: `C:\Code_Projects\jdb\lessons\food\Dockerfile`.
- Previous exercise Compose: `C:\Code_Projects\jdb\lessons\food\docker-compose.yml`.
- Previous exercise credentials: `C:\Code_Projects\jdb\lessons\food\.env`. Inspect names/patterns without displaying secret values. Do not copy unrelated exercise credentials into this project.
- Current local plan: root `kafka-plan.md`, already ignored by Git.
- Old background plan: root `microservice-plan.md`, already ignored.

**The Kafka plan contains stale sections.** Its header correctly records implementation progress, but its initial architecture paragraph still describes the removed HTTP SSE handoff and its Docker section still splits Kafka-first from Java-later. The user's latest decision supersedes those sections. Update the plan in the next approved batch.

Docker lesson uses a simple Java image with a prebuilt JAR, Compose services for Spring Boot/PostgreSQL/pgAdmin, environment variables, volumes and startup commands. Its old examples include obsolete versions and `links`; follow its shape without copying those details blindly. The food example is newer:

- Dockerfile: `eclipse-temurin:17-jdk-jammy`, copies `target/food-0.0.1-SNAPSHOT.jar`, runs Java with `dev` profile.
- Compose: application build, PostgreSQL health check and healthy dependency, `postgres:18.6`, PostgreSQL 18 volume mounted at `/var/lib/postgresql`, optional pgAdmin `9.18`, `restart: always`, explicit bridge network.
- Food `.env` names: POSTGRES_PASSWORD, PGADMIN_DEFAULT_EMAIL, PGADMIN_DEFAULT_PASSWORD, PGADMIN_PORT. Values were not displayed.

Do not copy the food JAR name or `dev` profile into this project without checking. No pgAdmin container has been explicitly requested for this project; keep it optional/omit if unnecessary.

## Repository and architecture

- Root Maven application: Aviation, `com.ga:saudsFlightSystem:0.0.1-SNAPSHOT`, port **8080**.
- `identity-service`: users, profiles, registration, JWT authentication and internal account lookup, port **8081**.
- `maintenance-service`: maintenance workflows, port **8083**.
- `notification-service`: live SSE and SMTP email, port **8084**, no database.
- `common`: ordinary shared library, `com.ga:common:0.0.1-SNAPSHOT`; packages security/web/events.
- `integration-tests`: separate Maven test project depending on the ordinary service JARs.
- Java 17, Spring Boot 4.1.1, Cloud BOM 2025.1.4, Spring Kafka 4.1.1 and Kafka client 4.2.1 from managed dependencies.
- Jackson 3 uses `tools.jackson.databind.ObjectMapper` and `tools.jackson.core.JacksonException`.
- Root POM is **not** an aggregator. Build/install common first, then the individual applications.
- Each application produces an ordinary JAR and an executable **`*-app.jar`**, using Boot classifier `app`. Docker must run the executable JAR.
- Maven wrapper uses Maven 3.9.16. Root `.gitattributes` specifies LF for `/mvnw` and CRLF for `*.cmd`.
- There is no frontend in this scope. Swagger/Postman/SSE clients use the existing service ports.
- No repository AGENTS.md was found in the recent read-only file search. Recheck applicable instructions as needed.

Last `git status --short` immediately before writing this handoff was clean. No Docker files/config changes were made during the Docker discussion. This new handoff is not automatically ignored or committed.

## Existing configuration and Docker details to resolve

Each service has an ignored `src/main/resources/application.properties` with local settings and a tracked `application-example.properties` with placeholders. Local properties may contain real credentials. Never dump them into tool output or bake them into Docker images.

Database settings already supported by Aviation, Identity and Maintenance:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres_db:5432/aviation
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<local secret>
```

All three currently share one physical `aviation` database, with separate table ownership, and use `spring.jpa.hibernate.ddl-auto=validate`.

**An empty PostgreSQL container will not start the existing application successfully.** The current schema includes manual extraction migrations, expanded check constraints and a partial unique index for one pending flight inspection. There is no complete reproducible schema migration set. `docs/sql/crew-assignment-audit.sql` is old and must not be blindly replayed. Do not switch to `ddl-auto=create/update` as a shortcut: it may omit important constraints and does not preserve current data. A dump/restore of the existing database is the simplest agreed path. Keep seeding disabled.

PostgreSQL tools are installed at `C:\Program Files\PostgreSQL\18\bin`; `pg_dump.exe --version` reported **18.6**. This establishes the installed client version, not a verified live server version. No database connection, dump, import, or mutation was performed during this Docker planning turn. Plan a safe first-start sequence: database starts, restore completes, then applications start. Avoid dumping live data into tracked files or the Docker build context.

Other environment settings:

- `IDENTITY_BASE_URL`, `IDENTITY_SERVICE_KEY` used by downstream services.
- `NOTIFICATION_BASE_URL`, `NOTIFICATION_SERVICE_KEY` used for email calls from Aviation and Identity; Notification validates the same key.
- `MAINTENANCE_NOTIFICATION_SERVICE_KEY` for Maintenance-to-Aviation cancellation handoff.
- Identity: `JWT_SECRET`, `JWT_EXPIRATION_MS` (default 86400000).
- Aviation: `STRIPE_SECRET_KEY`, `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`.
- Notification: `SPRING_MAIL_HOST` (smtp.gmail.com default), `SPRING_MAIL_PORT` (587), `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.
- Aviation and Notification: `KAFKA_BOOTSTRAP_SERVERS` (host default localhost:9092, container kafka:29092).
- `APP_SEED_ENABLED=false`; Maintenance also defaults seeding false.

Maintenance example properties hardcode `aviation.base-url=http://localhost:8080` and `identity.base-url=http://localhost:8081`. Make container addresses effective through runtime environment overrides or parameterize the examples consistently. Within containers use Compose service names, not localhost. Keep external/browser redirect URLs distinct from internal service URLs.

Identity writes profile images to relative `uploads/` in `UserService`. Root `uploads` exists. Account for persistent files, using an appropriate mount at `/app/uploads` if `/app` is the working directory. Check whether existing images need carrying over. PostgreSQL and Kafka also need persistent volumes.

Potential implementation approach, **not yet proposed/approved**: one reusable multi-stage Dockerfile builds common and the chosen service with a SERVICE build argument, using only checked-in example configuration, then copies its executable JAR into a Java 17 runtime image. A `.dockerignore` excludes local application.properties, .env/secrets, target output, Git metadata, uploads and database backups. This avoids baking local properties into prebuilt JARs. Explain the small departure from the lesson if choosing this. Alternatively devise a similarly simple safe lesson-style JAR build; do not package secrets inadvertently.

Compose should explicitly pass each service the settings it needs. Compose reading `.env` for interpolation is distinct from automatically injecting all variables into containers. Prefer clear configuration over clever YAML abstractions. Keep environment-specific DB URLs configurable so later deployment does not require code edits or a dependency on a local DB container.

Last Docker check (earlier in this chat): CLI installed at `C:\Program Files\Docker\Docker\resources\bin\docker.exe`, client 29.0.1; daemon unavailable, missing `dockerDesktopLinuxEngine` pipe. Docker Desktop may need opening before live tests. Recheck rather than assuming unchanged. No images/containers were started in this Docker discussion.

## Implemented Kafka behavior

All existing live SSE notification handoffs from Aviation now use Kafka. Emails remain on existing HTTP/SMTP paths.

Flow:

```text
Business operation commits in Aviation
  -> Aviation KafkaProducer publishes JSON
  -> aviation-notification-topic
  -> Notification KafkaConsumer
  -> existing NotificationService
  -> matching connected SSE clients
```

Main demo: airline admin requests airplane activation; all connected FAA_ADMIN users receive `airplane-activation-requested`. This is the only whole-role broadcast. Other airline, crew, tower and maintenance audiences are selected by user ID; preserve their filtering.

Shared record `common/src/main/java/com/ga/common/events/NotificationEvent.java`:

```java
public record NotificationEvent(
        String eventId, Long userId, String role, String eventName, Object data) {}
```

- Topic: **aviation-notification-topic**.
- Producer constant: **AVIATION_NOTIFICATION_TOPIC**.
- Consumer group: **notification-group**.
- One partition, one replica, delete retention **86400000 ms = 24 hours**.
- Exactly one target: userId or role. Allowed role is FAA_ADMIN.

Aviation implementation:

- `src/main/java/com/ga/saudsFlightSystem/producer/KafkaProducer.java`: KafkaTemplate<String,String>, JSON serialization, sendNotificationDetails; keys user:<id> or role:<role>; logs success on broker completion and failure with metadata only.
- `config/KafkaTopicConfig.java`: NewTopic notificationTopic() declares the topic/settings.
- `service/NotificationService.java`: sendToUser/sendToRole construct UUID envelope and publish via existing afterCommit helper. Existing email methods remain.
- Producer configuration: String serializers, acks=all, idempotence enabled, max.block.ms=2000, request.timeout.ms=10000, delivery.timeout.ms=30000. Default payload-logging producer listener disabled.

Notification implementation:

- `notification-service/src/main/java/com/ga/notification/config/KafkaConsumerConfig.java`: lesson-style consumerConfigs(), consumerFactory(), kafkaListenerContainerFactory(); String deserializers, earliest reset, auto-commit false, max poll records 10, auto topic creation false; concurrency 1; **AckMode.RECORD**.
- DefaultErrorHandler retries twice at 1-second intervals, then logs metadata and skips. IllegalArgumentException is immediately non-retryable. No DLQ.
- `consumer/KafkaConsumer.java`: @KafkaListener with topic/group; consume(String message), readNotification and processNotification helpers. Parses, validates, dispatches to existing SSE service, remembers successful event IDs in a bounded 10,000-entry LinkedHashSet, evicts oldest.
- User specifically rejected a manual Acknowledgment parameter, annotation autoStartup configuration, and a comment about the single listener thread. These were removed. Do not reintroduce them casually.
- Invalid JSON exceptions omit the cause to avoid logging message payloads.
- Existing NotificationService still manages local SseEmitter connections, user/role routing, 5-minute emitters and 25-second heartbeat. No notification persistence.

Removed obsolete SSE HTTP path:

- Notification InternalNotificationController.
- NotificationRequest DTOs in both services.
- Aviation NotificationServiceClient.send method; email methods remain.
- Security POST matcher for /internal/v1/notifications.

Maintenance cancellation still calls Aviation through its existing protected HTTP handoff; Aviation selects recipients and publishes Kafka notifications. Some older Maintenance README wording still mentions Feign SSE delivery; update misleading documentation in the approved batch.

## Reliability choices explicitly accepted by user

- No transactional outbox. Crash between database commit and publication or an exhausted send can lose a notification; log/document the gap.
- No database-backed inbox/history/offline replay. User explicitly chose the current simple approach and may add message saving later.
- Kafka retention helps a temporarily unavailable consumer catch up; it does not guarantee browser delivery. If no matching admin is connected when Notification processes a record, the live notification is missed.
- Deduplication is bounded and process-local. Restart clears it. A partial dispatch followed by failure can still duplicate delivery.
- One Notification instance for now because SSE connections are process-local and a consumer group distributes records among instances.
- No exactly-once browser delivery claim.

## Kafka Docker draft from earlier (superseded scope, useful settings)

The earlier Kafka-only proposal was never applied. Reuse suitable settings inside the full-stack Compose proposal:

- Official `apache/kafka:4.1.2`, one combined KRaft broker/controller, no ZooKeeper.
- Stable cluster ID example previously proposed: `4L6g3nShT-eMCtK--X86sw`.
- Node ID 1, process roles broker,controller.
- Listeners: HOST://:9092, INTERNAL://:29092, CONTROLLER://:29093.
- Advertised: HOST://localhost:9092, INTERNAL://kafka:29092.
- Protocol map all PLAINTEXT for this local setup; interbroker INTERNAL; controller CONTROLLER; quorum voters 1@kafka:29093.
- Offsets replication 1; transaction log replication/min ISR 1; initial group rebalance delay 0; auto-create topics false.
- Log directory /var/lib/kafka/data, persistent named volume.
- Host Kafka port bound to 127.0.0.1:9092.
- Health check uses `/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list`.
- Aviation declares the topic at startup. Account for broker readiness and initial topic creation when starting Notification; do not introduce a dependency cycle between services.

## Verification already completed (historical; not rerun for this handoff)

Prior approved batches built common and all four services successfully. Most recent producer/consumer batch clean-installed Aviation and Notification, compiled all 28 integration test source files and passed **42 focused Kafka/email checks**.

Command:

```powershell
.\mvnw.cmd -o -f integration-tests/pom.xml test '-Dtest=KafkaConsumerTest,KafkaConsumerConfigurationTest,KafkaRecordCommitTest,KafkaProducerTest,NotificationKafkaTest,KafkaConfigurationTest,EmailExtractionTest,EmailSenderTest,CrewEmailExtractionTest'
```

Coverage includes routing, validation, deduplication, retry/skip handling, configuration, publication after commit versus rollback, producer failures and email regressions. KafkaRecordCommitTest uses Spring's real listener container with a Kafka MockConsumer to verify offset commit after dispatch completes. Consumer tests include simulated multiple FAA SSE clients and exclusion of unrelated users.

These tests **do not prove real broker connectivity, live browser delivery, outage recovery against a real broker, or a running Docker stack**. Full database-dependent suites were not run. Real Kafka demonstrations and the plan's focused Cucumber acceptance scenario remain pending; do not mark the overall Kafka plan finished merely because Docker starts.

Integration fixtures use a mock KafkaProducer forwarding events to the fixture SSE service and mock KafkaAdmin to prevent broker setup. Test-only listener auto-startup suppression belongs to tests, not the production @KafkaListener annotation.

On this Windows environment, Mockito dynamic agent attachment hung in the sandbox; elevated execution allowed the focused tests to pass. Maven writes to the user .m2 cache may also require tool escalation. Use tools' approval mechanism when needed, explain the concrete reason, and never treat it as a substitute for the user's code approval rule. No automatic approval rejection occurred in the preceding successful batches.

## Documentation and sources

Tracked README files already document much of the Kafka implementation and its limitations:

- Root README, Kafka notifications section.
- notification-service/README.md.
- common/README.md.
- integration-tests/README.md, Kafka producer and consumer checks.

Official sources checked during the discussion:

- Docker Compose networking: https://docs.docker.com/compose/how-tos/networking/
- Docker host connections: https://docs.docker.com/desktop/features/networking/networking-how-tos/
- Docker multi-stage builds: https://docs.docker.com/build/building/multi-stage/
- PostgreSQL image and volume/init behavior: https://hub.docker.com/_/postgres
- Kafka image: https://kafka.apache.org/41/getting-started/docker/
- Kafka 4.1.2 example: https://raw.githubusercontent.com/apache/kafka/4.1.2/docker/examples/docker-compose-files/single-node/plaintext/docker-compose.yml
- Kafka image Dockerfile: https://raw.githubusercontent.com/apache/kafka/4.1.2/docker/jvm/Dockerfile
- RDS connections: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_CommonTasks.Connect.html
- RDS PostgreSQL TLS: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html

The next chat should inspect current files/status, finish the whole-stack proposal and show the actual edits for review. Wait for **APPROVED, CODE IT** before applying that proposal. Do not ask the user to repeat the decisions recorded here.
