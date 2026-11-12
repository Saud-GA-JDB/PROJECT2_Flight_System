# Existing integration fixtures

These existing fixtures combine the applications in a Spring test context. They are kept here so an application's build does not compile another application's source code. This is a test project, not a service. Maintenance fixtures use small in-process client adapters and do not establish HTTP authentication, timeout, or failure-recovery behavior.

They depend on the three applications' ordinary JARs. Each application also produces a runnable `-app.jar`. No application depends on this project or another application.

Compile without executing tests, from the repository root:

```powershell
.\mvnw.cmd -f common-web/pom.xml -DskipTests install
.\mvnw.cmd -f common-security/pom.xml -DskipTests install
.\mvnw.cmd -DskipTests install
.\identity-service\mvnw.cmd -f identity-service/pom.xml -DskipTests install
.\mvnw.cmd -f maintenance-service/pom.xml -DskipTests install
.\mvnw.cmd -f integration-tests/pom.xml -DskipTests test-compile
```

These are the existing in-process fixtures, not proof of HTTP failure recovery. They require a configured test database before execution. No tests were added or run as part of the structure correction.

Local fixture settings belong in `src/test/resources/application.properties` (ignored by Git). Use `application-example.properties` as a starting point for another checkout and configure `jwt-secret`/`jwt-expiration-ms` for the Identity fixtures. Select a test database before executing these tests.
