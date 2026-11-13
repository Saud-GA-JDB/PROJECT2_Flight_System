# Common library

One shared Maven library for the four applications. It replaces the former `common-security` and `common-web` modules; their Java package names and behavior are unchanged.

| Package | Responsibility |
| --- | --- |
| `com.ga.common.security` | Current actor, JWT filter, shared security configuration and Identity client |
| `com.ga.common.web` | Shared exceptions, exception handling and error responses |
| `com.ga.common.events` | Event DTOs, starting with `NotificationEvent` |

This produces an ordinary JAR, not a runnable Spring Boot service. Business entities and repositories remain in their owning services. No Kafka dependency is needed just to define the event DTO.

From the repository root, build/install this library before building the applications:

```powershell
.\mvnw.cmd -f common/pom.xml install
```

Each application depends on `com.ga:common:0.0.1-SNAPSHOT`. After changing shared code, reinstall the library and rebuild the affected applications. In IntelliJ, unlink the old common modules, link `common/pom.xml`, and reload Maven projects.

`NotificationEvent` contains `eventId`, `userId`, `role`, `eventName`, and `data`. Supply either a user ID or a role, not both. The planned Kafka consumer will validate this rule. Adding this record alone does not change notification delivery; the producer and consumer are a later implementation step.
