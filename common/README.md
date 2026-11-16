# Common library

One shared Maven library for the four applications. It replaces the former `common-security` and `common-web` modules; their Java package names and behavior are unchanged.

| Package | Responsibility |
| --- | --- |
| `com.ga.common.security` | Current actor, JWT filter, shared security configuration and Identity client |
| `com.ga.common.web` | Shared exceptions, error responses, database-conflict classification, and pagination helpers |
| `com.ga.common.contract` | Shared Identity account responses, email requests, and Aviation–Maintenance request/response records |
| `com.ga.common.config` | Conditional background-job scheduling |
| `com.ga.common.events` | Event DTOs and shared notification topic names |
| `com.ga.common.validation` | Email, password, and phone validation using Commons Validator, Passay, and libphonenumber |

This produces an ordinary JAR, not a runnable Spring Boot service. Business entities and repositories remain in their owning services. No Kafka dependency is needed just to define the event DTO.

From the repository root, build/install this library before building the applications:

```powershell
.\mvnw.cmd -f common/pom.xml install
```

The four backend applications depend on `com.ga:common:0.0.1-SNAPSHOT`; Gateway is independent. Aviation, Identity, and Maintenance explicitly import the shared password and phone validator beans. After changing shared code, reinstall the library and rebuild the affected applications. In IntelliJ, unlink the old common modules, link `common/pom.xml`, and reload Maven projects.

`NotificationEvent` contains `eventId`, `userId`, `role`, `eventName`, and `data`. Supply either a user ID or a role, not both. Aviation's Kafka producer serializes this record; Notification's consumer reads and validates it. Service-specific business DTOs travel as JSON inside `data`.

The four backend applications explicitly import `SchedulingConfiguration`. Background jobs are enabled by default. Set `app.scheduling.enabled=false` (or `APP_SCHEDULING_ENABLED=false`) to disable them. Application test contexts set this property explicitly, including the Cucumber suite. This switch controls scheduled execution only; service methods remain callable directly.

Shared contract records keep their existing JSON field names and types. Each service keeps its own clients, entities, and repositories. `Pagination.validateZeroBased` applies to ATC and flight-operation listings; flight search retains its one-based page validation and shares only the total-page calculation.
