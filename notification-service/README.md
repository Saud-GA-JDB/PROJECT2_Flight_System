# Notification service — SSE stage

Notification is a separate Spring Boot application on **8084**. It owns the existing live SSE connections, heartbeat, connection cleanup, and delivery code. Aviation remains on **8080**, Identity on **8081**, and Maintenance on **8083**.

This stage uses a controller, a concrete service, a request DTO and a Feign client in Aviation. It has no database entities or repositories: Identity, Aviation and Maintenance continue using the one existing PostgreSQL database. Email stays in Aviation and Identity for a later approved stage.

## Setup

Link `notification-service/pom.xml` in IntelliJ. Keep local `src/main/resources/application.properties` ignored; use `application-example.properties` for a new checkout.

Configure these values in the run configurations:

| Setting | Where | Purpose |
| --- | --- | --- |
| `NOTIFICATION_SERVICE_KEY` | Aviation and Notification, same nonempty private value | Authorizes Aviation's delivery requests |
| `IDENTITY_SERVICE_KEY` | Notification, matching Identity's existing key | Authenticates SSE subscribers through Identity |
| `IDENTITY_BASE_URL` | Notification, default `http://localhost:8081` | Identity address |
| `NOTIFICATION_BASE_URL` | Aviation, default `http://localhost:8084` | Delivery address |

This checkout's local Notification properties reuse Aviation's existing Identity connection settings. The example file uses environment variables. The new notification key is separate from `MAINTENANCE_NOTIFICATION_SERVICE_KEY`, which still protects Maintenance -> Aviation. Neither key is sent by normal SSE subscribers. A missing/wrong delivery key rejects delivery; it does not undo a committed business action.

No SQL or seeding is needed for Notification. The existing Maintenance audit-table setup and runtime checks remain separate prerequisites for Maintenance workflows.

From the repository root, build without executing tests:

```powershell
.\mvnw.cmd -f common-web/pom.xml -DskipTests install
.\mvnw.cmd -f common-security/pom.xml -DskipTests install
.\mvnw.cmd -DskipTests install
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
- Delivery is best effort. Disconnected users, crashes and outages can lose messages. There is no notification table, queue, replay, retry scheduler, Kafka or gateway.
- Email delivery and existing business locks, audits and authorization are unchanged by this stage.

## Manual checks

These checks are user-run; the extraction is compile-verified only.

1. Login on 8081. Subscribe on 8084 with that token and confirm `connected` and heartbeat comments. Without a token expect 401; an invalid token must also be rejected.
2. Trigger an airplane, crew or flight operation on Aviation. Confirm the same event and payload reach the intended user. Open an unrelated user's stream and confirm scoped events do not reach it. For activation requests, check the FAA admin audience.
3. Cancel a pending maintenance request with the correct permissions. Confirm the cancellation, audit and `Maintenance_Cancelled` event; unrelated users should receive nothing.
4. Send a direct internal notification without `X-Service-Key`, with a wrong key, and with the configured key. Expect 401 for the first two; with the correct key, malformed recipient/event input returns 400 and a valid request delivers only to its target. A normal bearer token alone cannot authorize delivery.
5. Stop Notification and perform an otherwise valid flight operation. The operation and audit should commit, with a delivery warning in Aviation. Restart Notification and reconnect: missed events are not replayed.
6. Stop Identity and attempt a new subscription with a bearer token: expect 503. Check that a normal five-minute stream reconnect reruns authentication when Identity is available.

Existing transaction/recipient fixtures were adapted without adding or executing tests. Their in-process client substitute does not verify the HTTP boundary. No application startup, database mutation, or seeder execution is part of build verification.
