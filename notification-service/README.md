# Notification service - SSE and email

Notification is a separate Spring Boot application on **8084**. It owns the existing live SSE connections, heartbeat, connection cleanup, and SMTP email delivery. Aviation remains on **8080**, Identity on **8081**, and Maintenance on **8083**.

This stage uses a controller, a concrete service, a request DTO and Feign clients in Aviation and Identity. It has no database entities or repositories: Identity, Aviation and Maintenance continue using the one existing PostgreSQL database. SMTP is configured only here; callers keep their recipient lookup and message wording.

## Setup

Link `notification-service/pom.xml` in IntelliJ. Keep local `src/main/resources/application.properties` ignored; use `application-example.properties` for a new checkout.

Configure these values in the run configurations:

| Setting | Where | Purpose |
| --- | --- | --- |
| `NOTIFICATION_SERVICE_KEY` | Aviation, Identity and Notification, same nonempty private value | Authorizes internal delivery requests |
| `IDENTITY_SERVICE_KEY` | Notification, matching Identity's existing key | Authenticates SSE subscribers through Identity |
| `IDENTITY_BASE_URL` | Notification, default `http://localhost:8081` | Identity address |
| `NOTIFICATION_BASE_URL` | Aviation and Identity, default `http://localhost:8084` | Delivery address |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Notification only | SMTP connection and sender |

This checkout's local Notification properties reuse Aviation's existing Identity connection settings. The example file uses environment variables. The new notification key is separate from `MAINTENANCE_NOTIFICATION_SERVICE_KEY`, which still protects Maintenance -> Aviation. Neither key is sent by normal SSE subscribers. A missing/wrong delivery key rejects delivery. Aviation logs the failure after commit; Identity returns 503 and rolls back verification/reset changes.

No SQL or seeding is needed for Notification. The existing Maintenance audit table and cancellation handoff were verified locally on 12 November 2026; a fresh checkout still needs the documented Maintenance setup.

From the repository root, build without executing tests:

```powershell
.\mvnw.cmd -f common/pom.xml -DskipTests install
.\mvnw.cmd -DskipTests install
.\mvnw.cmd -f identity-service/pom.xml -DskipTests install
.\mvnw.cmd -f notification-service/pom.xml -DskipTests install
```

Run Identity, Aviation, and `com.ga.notification.NotificationApplication` in separate IntelliJ run configurations. Run Maintenance too when checking cancellations. Alternatively, with the required environment configured:

```powershell
java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT-app.jar
```

Swagger: [Notification API](http://localhost:8084/swagger-ui/index.html).

## Request flow

1. Aviation performs its existing business validation, writes and audits, and selects recipients.
2. Aviation waits for its transaction to commit, then `NotificationServiceClient` sends JSON to `POST /internal/v1/notifications` with `X-Service-Key`.
3. Notification validates the key and request, then delivers the unchanged event name and payload to matching SSE connections.

The small local Aviation `NotificationService` owns the after-commit callback and failure logging. An HTTP call cannot see another application's transaction, so this callback must stay in Aviation. If there is no active transaction (the Maintenance handoff has already committed), it sends immediately. Requests are synchronous and bounded by Feign timeouts, with no automatic delivery retry; a slow dependency can delay the response after commit.

The internal DTO contains `userId`, `role`, `eventName` and `data`. Supply exactly one positive `userId` or the `FAA_ADMIN` role. FAA airplane activation requests are the existing role-wide event; other audiences are selected by user ID. Business DTOs remain in Aviation and are sent as JSON, not imported into Notification.

Maintenance keeps its existing after-commit call to Aviation. Aviation resolves airline admins, filters current active recipients through Identity, and forwards `Maintenance_Cancelled` to Notification. Notification does not read airline, flight, user or maintenance tables.

## SSE behavior and limits

- Subscribe with `GET http://localhost:8084/notifications`, `Accept: text/event-stream` and `Authorization: Bearer <token-from-Identity>`. The old Aviation SSE route has been removed.
- Shared authentication requires an active account. Missing/invalid tokens return 401; non-active accounts are denied; an Identity outage during authentication returns 503.
- Existing event names/payloads, the `connected` event, 25-second heartbeat and five-minute connection timeout are retained. Reconnecting repeats authentication. Already-open streams are not immediately revoked when account state changes.
- Run one Notification instance: connection state is process-local. Restarting it closes streams. Clients must reconnect.
- Delivery is best effort. Disconnected users, crashes and outages can lose messages. There is no SSE table, queue, replay or SSE retry scheduler.
- Existing business locks, audits and authorization remain in their owning applications.

## Manual checks

These checks were exercised locally on 12 November 2026. Repeat them when changing the runtime configuration.

1. Login on 8081. Subscribe on 8084 with that token and confirm `connected` and heartbeat comments. Without a token expect 401; an invalid token must also be rejected.
2. Trigger an airplane, crew or flight operation on Aviation. Confirm the same event and payload reach the intended user. Open an unrelated user's stream and confirm scoped events do not reach it. For activation requests, check the FAA admin audience.
3. Cancel a pending maintenance request with the correct permissions. Confirm the cancellation, audit and `Maintenance_Cancelled` event; unrelated users should receive nothing.
4. Send a direct internal notification without `X-Service-Key`, with a wrong key, and with the configured key. Expect 401 for the first two; with the correct key, malformed recipient/event input returns 400 and a valid request delivers only to its target. A normal bearer token alone cannot authorize delivery.
5. Stop Notification and perform an otherwise valid flight operation. The operation and audit should commit, with a delivery warning in Aviation. Restart Notification and reconnect: missed events are not replayed.
6. Stop Identity and attempt a new subscription with a bearer token: expect 503. Check that a normal five-minute stream reconnect reruns authentication when Identity is available.

Existing SSE fixtures use an in-process client substitute and do not verify the HTTP boundary. Separate manual checks exercised running applications and the existing PostgreSQL database; seeders were disabled.

## Email flow

The DTO is `EmailRequest(recipient, subject, body)`. Each caller has its own copy. Notification owns no business entities and does not call Identity when handling email. These internal routes require `X-Service-Key`, not a user bearer token:

| Route | Success | Use |
| --- | --- | --- |
| `POST /internal/v1/emails` | 200 after SMTP acceptance | Identity verification/reset; Aviation booking and airplane emails |
| `POST /internal/v1/emails/async` | 202 after task acceptance | Crew assignment emails |

Missing/wrong keys return 401; missing fields or an invalid recipient return 400. Synchronous SMTP failures return 503. SMTP acceptance does not guarantee final inbox delivery.

Identity calls Feign directly inside its existing transaction. A failed handoff or SMTP send propagates as 503 and rolls back local registration/reset changes. Do not automatically retry verification/reset messages. Email cannot be rolled back if SMTP accepted it before a later timeout or database failure.

Aviation uses its existing concrete NotificationService to call Feign after commit. Ordinary email failure now logs a warning without undoing the booking/airplane operation. These calls still wait for the bounded HTTP response after commit. Crew assignment replaces the old local application event with an after-commit call to the async endpoint.

Notification keeps the existing five-thread executor, locked in-memory failed-email list, and three-hour retry schedule for background emails only. A failed HTTP handoff is not queued. Restart can lose pending emails; uncertain delivery can produce duplicates. No persistent queue, new tables, Kafka, Docker or gateway were added.

Both callers default to a 2-second connect timeout and 15-second read timeout. SMTP socket connection/read/write timeouts are 2/3/3 seconds. Keep Feign logging disabled so message bodies and verification codes are not logged.

## Email verification

From the repository root, after installing the application JARs:

```powershell
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=EmailExtractionTest,EmailSenderTest,CrewEmailExtractionTest'
```

All 10 focused tests passed on 12 November 2026. They use mocks, without a database or external SMTP: commit/rollback timing, crew user-ID resolution and message construction, handoff failures, sender content, background retries and rejected tasks. Retry is invoked directly in the test; no three-hour wait was performed.

Manual checks used a temporary local SMTP capture server and captured 10 emails, with no external delivery. They covered both HTTP endpoints, service keys, invalid input, all ordinary business email callers, verification/code use and password reset, SMTP rejection, Notification outage, Identity rollback, Aviation commit/audit persistence, and SSE reconnect/delivery. Email still worked while Identity was stopped. A live crew assignment was not exercised because the current database has only airline admins; its caller was checked by the focused fixture and the actual async HTTP endpoint was checked separately.

Temporary processes were stopped. Test records were retained and are listed in the local migration handoff plan. Real SMTP-provider delivery and the broader integration suite were not tested in this stage.
