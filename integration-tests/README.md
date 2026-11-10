# Existing integration fixtures

These tests exercise both applications in one Spring test context. They are kept here so neither application's build compiles the other application's source code. This is a test project, not a service.

They depend on the two applications' ordinary JARs. Each application also produces a runnable `-app.jar`. Neither application depends on this project or on the other application.

Compile without executing tests, from the repository root:

```powershell
.\mvnw.cmd -DskipTests install
.\identity-service\mvnw.cmd -f identity-service/pom.xml -DskipTests install
.\mvnw.cmd -f integration-tests/pom.xml -DskipTests test-compile
```

These are the existing in-process fixtures, not proof of HTTP failure recovery. They require a configured test database before execution. No tests were added or run as part of the structure correction.

Local fixture settings belong in `src/test/resources/application.properties` (ignored by Git). Use `application-example.properties` as a starting point for another checkout and configure `jwt-secret`/`jwt-expiration-ms` for the Identity fixtures. Select a test database before executing these tests.
