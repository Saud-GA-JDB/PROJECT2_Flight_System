# Maintenance service

Maintenance runs on port **8083**, Identity on **8081**, and the root Aviation application on **8080**. All use the existing PostgreSQL `aviation` database. There is no gateway.

Maintenance owns providers, contracts, maintenance employees, work records, and `maintenance_audit_logs`. It stores airplane, airline, and user IDs. Aviation owns airplane and airline checks; Identity owns accounts. Communication uses the two Feign interfaces under `client/`. No application depends on another application's JAR.

## Configuration and running

Keep local `src/main/resources/application.properties` ignored. Use `application-example.properties` for a new checkout. Keep `ddl-auto=validate` and seeding disabled. Create `maintenance_audit_logs` using the SQL supplied in chat before starting Maintenance; the earlier contract-table SQL is already confirmed complete.

Set **the same `MAINTENANCE_NOTIFICATION_SERVICE_KEY` environment variable** for the root and Maintenance run configurations. It is a private shared value for the cancellation notification endpoint. Without it, cancellations still commit, but notification delivery fails and is logged. The endpoint rejects a missing or wrong key. Continue using the existing `IDENTITY_SERVICE_KEY` separately.

From the repository root, build without running tests:

```powershell
.\mvnw.cmd -f common/pom.xml -DskipTests install
.\mvnw.cmd -DskipTests install
.\mvnw.cmd -f identity-service/pom.xml -DskipTests install
.\mvnw.cmd -f maintenance-service/pom.xml -DskipTests install
```

Run the three application main classes in IntelliJ, linking their separate POMs if necessary. Stop the previous root application before starting its rebuilt version: the old version still contains Maintenance business operations.

Alternatively, from the repository root in separate terminals:

```powershell
java -jar identity-service/target/identity-service-0.0.1-SNAPSHOT-app.jar
java -jar target/saudsFlightSystem-0.0.1-SNAPSHOT-app.jar
java -jar maintenance-service/target/maintenance-service-0.0.1-SNAPSHOT-app.jar
```

Swagger: [Maintenance](http://localhost:8083/swagger-ui/index.html). Login remains `POST http://localhost:8081/auth/users/login`. Send its bearer token on Maintenance requests.

## Public routes

All paths below now use **port 8083**. Their request bodies and response shapes remain the same.

| Method | Path | Caller |
| --- | --- | --- |
| POST | `/faaadmin/maintenanceProviders` | FAA admin |
| POST | `/faaadmin/maintenanceProviders/{providerId}/addMaintenanceAdmin` | FAA admin |
| POST | `/airlineAdmin/maintenanceProviders/{providerId}/contract` | Airline admin |
| POST | `/airlineAdmin/airplanes/{airplaneId}/maintenanceRequests` | Owning airline admin |
| GET | `/airlineAdmin/airplanes/{airplaneId}/maintenanceRecords` | Owning airline admin |
| POST | `/airlineAdmin/maintenanceRequests/{requestId}/cancel` | Owning airline admin |
| POST | `/maintenanceAdmin/technicians` | Provider admin |
| GET | `/maintenanceAdmin/maintenanceRequests` | Provider admin |
| POST | `/maintenanceAdmin/maintenanceRequests/{requestId}?technicianId={id}` | Provider admin |
| POST | `/maintenanceAdmin/maintenanceRequests/{requestId}/cancel` | Provider admin |
| GET | `/maintenanceTechnician/maintenanceRecords` | Technician |
| PUT | `/maintenanceTechnician/maintenanceRecords/{recordId}/complete?findings=...` | Assigned technician |

History uses `page=0`, `size=20` by default and optional `status`. Cancellation takes `{"reason":"Work is no longer required"}`.

## What remains shared

The `common` library's `security` package supplies authentication and reading the current actor. Each app's small security configuration selects its routes. The local actor provider only asks Identity for fresh account state at existing authorization checkpoints. Its `web` package supplies common exceptions and error responses.

Employee creation preserves Identity's existing pending-account flow. Maintenance saves the employee link, activates the account after commit, and retries pending linked accounts every 30 seconds. This is **account activation recovery**, not a notification retry. It reads only Maintenance employees. Known rollbacks cancel the pending account; the limitations documented in [Identity recovery](../identity-service/README.md) still apply.

## Cancellation notifications

The cancellation and its audit commit together. A short after-commit helper then makes **one Feign call** to Aviation's `/internal/v1/maintenance-notifications/cancellations` endpoint. The controller checks the Maintenance service key, resolves airline admins, and filters active recipients through Identity. Aviation then calls Notification through Feign to deliver `Maintenance_Cancelled` to SSE connections at `http://localhost:8084/notifications`. See [Notification setup](../notification-service/README.md); Aviation and Notification need their matching `NOTIFICATION_SERVICE_KEY` in addition to the existing Maintenance handoff key.

Maintenance supplies provider admins and the assigned technician as candidate user IDs. Aviation resolves current airline membership at delivery time. There is no queue, outbox, notification scheduler, or notification retry. An outage or a crash after commit can lose the notification; it does not undo the cancellation. Provider completion and cancellation do not require Aviation authorization; airline operations do and fail with 503 if Aviation cannot be reached.

## Optional sample data

Keep seeders disabled for normal use. If deliberately seeding a development database, seed Identity and Aviation first. Maintenance's seeder uses existing Identity sample accounts, an existing airplane ID (`app.seed.airplane-id`), and its airline admin's full bearer header (`app.seed.authorization`) to obtain airline scope over Feign. Enable `app.seed.enabled` only for that seed run, then disable it again. It creates only Maintenance-owned rows and preserves existing workflow progress.

## Manual checks

After applying the audit-table SQL and starting the rebuilt apps:

1. Log in through Identity; call Maintenance without a token and confirm 401, then with the correct role.
2. Create a provider and provider admin; create a technician as that admin. Verify the new employee can log in after activation.
3. Contract the provider as an airline admin, request work for an owned airplane, assign the technician, and complete it. Confirm terminal records cannot be assigned or cancelled.
4. Create another request and cancel it. Check its history, audit row, and scoped SSE delivery on port 8084 with Notification running. Wrong-airline and wrong-provider callers must be rejected.
5. Check paginated history and Identity/Aviation outage responses. A provider cancellation should still commit during an Aviation outage, with notification failure logged.

Builds and fixture compilation do not establish runtime behavior. No applications, seeders, live database changes, or automated tests were run during extraction.
