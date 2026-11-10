# Identity service

Identity runs on **8081**. The flight application stays on **8080**. Both use the same existing PostgreSQL database, with separate table ownership and direct HTTP calls through Feign.

| Identity tables | Flight application tables |
| --- | --- |
| `users`, `user_profiles`, `pending_registrations`, `identity_audit_logs` | Operational tables, including employees and `audit_logs` |

Identity does not import employee entities. The flight application does not import Identity entities or repositories in production. Customer and FAA identity come from the account role; employee permissions still require membership in the operational employee tables.

## Configure and run

The extraction SQL has been applied to the current `aviation` database. Neither application performs schema changes on startup (`ddl-auto=validate`). Do not rerun that migration on this database.

Open this folder as its own Maven/Spring Boot project. It has its own `pom.xml`, Maven wrapper, application class, and configuration.

Local settings are in `src/main/resources/application.properties`, just like the flight application. The local file is ignored by Git; `application-example.properties` is the checked-in template for another checkout. The local database and mail settings match the existing application. The shared service key and Identity's JWT signing key are configured locally, so this checkout does not require new environment variables to start.

The example template supports these environment variables if you prefer that configuration style:

| Variable | Used by |
| --- | --- |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Both; use the same database |
| `IDENTITY_SERVICE_KEY` | Both; use the same nonempty private value |
| `JWT_SECRET` | Identity only; a Base64 signing key of at least 32 random bytes |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Both, for their existing email features |
| `STRIPE_SECRET_KEY`, `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL` | Flight application only |

Keep the same `identity.service-key` in both local properties files. Identity owns `jwt-secret`; the flight application does not need that signing key.

From this `identity-service` directory, start Identity:

```powershell
.\mvnw.cmd spring-boot:run
```

From the flight application's root directory, start it in another terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

Leave `APP_SEED_ENABLED=false` for normal use. To seed sample data, first start Identity with `APP_SEED_ENABLED=true`, then start the flight application with the same option. Identity creates accounts; the flight seeder reads their IDs over HTTP and creates operational records. Seeding is optional and was not run during extraction.

Login, registration, setup, password changes and profile updates use `http://localhost:8081/auth/users/...`. All other existing routes remain on `http://localhost:8080`. Use the token from Identity on both applications. Swagger is available at `/swagger-ui/index.html` on each port. Internal endpoints require `X-Service-Key` and are for the flight application, not normal users.

## Employee creation

1. The flight service validates the caller and saves an employee in its local transaction.
2. Identity commits an inactive `PENDING_EMPLOYEE` account, keyed by account type and employee ID. Repeating that provisioning call returns the same account.
3. The flight service stores the returned user ID and commits its employee transaction.
4. After commit, it asks Identity to activate the account. A job retries linked pending accounts every 30 seconds, including after an application restart. Activation is idempotent and never reactivates a deactivated account.

On a known local rollback, the flight service asks Identity to delete the pending account. If Identity is unavailable, or the process stops before rollback cleanup, an inactive orphan can remain and reserve its email/CPR. It cannot log in. Recovery is deliberately manual for that case: check that no employee link committed, then use the service-protected `DELETE /internal/identity/employees/{accountType}:{employeeId}` endpoint. The recovery key is logged when cleanup fails. Do not cancel a committed employee's pending account; its activation will retry.

Employee creation can therefore return successfully while activation is temporarily pending. Wait for Identity recovery before logging in as that employee. There is no distributed database transaction or messaging infrastructure.

## Build verification

Build Identity independently from this directory, without executing tests:

```powershell
.\mvnw.cmd -DskipTests clean package
```

Use the same command from the flight application's directory to build that application independently. The runnable JAR for each app ends in `-app.jar`; its ordinary JAR is also available for integration fixtures.

Authentication/password tests belong to Identity. Existing tests that involve both applications are in the separate [integration-tests project](../integration-tests/README.md). They depend on the built JARs; neither application imports the other application's sources or depends on the integration-test project. These existing in-process fixtures do not verify HTTP outages or provisioning recovery. No new tests were added or executed.

Two-process runtime checks are separate from compilation. After starting both applications, verify login on 8081, an authorized operational request on 8080, and employee creation followed by login. Stopping Identity should make protected flight requests fail with 503 instead of accepting stale account state.
