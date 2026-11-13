# Existing integration fixtures

These existing fixtures combine the applications in a Spring test context. They are kept here so an application's build does not compile another application's source code. This is a test project, not a service. Maintenance fixtures use small in-process client adapters and do not establish HTTP authentication, timeout, or failure-recovery behavior.

They depend on the four applications' ordinary JARs. Each application also produces a runnable `-app.jar`. No application depends on this project or another application. The existing SSE fixtures use Aviation's real after-commit service and an in-process Feign substitute calling Notification's moved delivery code. Their assertions still cover transaction timing and scoped delivery, but do not prove HTTP serialization, service-key authentication, or timeouts.

Compile without executing tests, from the repository root:

```powershell
.\mvnw.cmd -f common/pom.xml -DskipTests install
.\mvnw.cmd -DskipTests install
.\identity-service\mvnw.cmd -f identity-service/pom.xml -DskipTests install
.\mvnw.cmd -f maintenance-service/pom.xml -DskipTests install
.\mvnw.cmd -f notification-service/pom.xml -DskipTests install
.\mvnw.cmd -f integration-tests/pom.xml -DskipTests test-compile
```

These are the existing in-process fixtures, not proof of HTTP failure recovery. They require a configured test database before execution. No tests were added or run as part of the structure correction.

Local fixture settings belong in `src/test/resources/application.properties` (ignored by Git). Use `application-example.properties` as a starting point for another checkout and configure `jwt-secret`/`jwt-expiration-ms` for the Identity fixtures. Select a test database before executing these tests.

## Focused email extraction checks

The email stage added three small test classes with 10 checks. They require no Spring application context, database, running services or SMTP provider:

```powershell
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=EmailExtractionTest,EmailSenderTest,CrewEmailExtractionTest'
```

They verify commit/rollback handoff timing, crew employee-to-user resolution, SMTP content/failure handling, retained background failures and executor rejection. All passed on 12 November 2026. The real HTTP/SMTP boundary was checked separately using a temporary local capture server; see Notification's README. Existing combined-context fixtures substitute both mail clients, preventing SMTP calls through these paths. The broader database-dependent suite was compiled but not executed in this stage.
