# Existing integration fixtures

These existing fixtures combine the applications in a Spring test context. They are kept here so an application's build does not compile another application's source code. This is a test project, not a service. Maintenance fixtures use small in-process client adapters and do not establish HTTP authentication, timeout, or failure-recovery behavior.

They depend on the four applications' ordinary JARs. Each application also produces a runnable `-app.jar`. No application depends on this project or another application. The existing SSE fixtures use Aviation's real after-commit service and an in-process Kafka producer substitute calling Notification's delivery code. A mocked Kafka admin prevents broker connections during these fixtures. Their assertions still cover transaction timing and scoped delivery, but do not prove real Kafka transport or consumer processing. The old SSE Feign fixture now fails if called, so it cannot hide a regression to HTTP delivery.

Compile without executing tests, from the repository root:

```powershell
.\mvnw.cmd -f common/pom.xml -DskipTests install
.\mvnw.cmd -DskipTests install
.\identity-service\mvnw.cmd -f identity-service/pom.xml -DskipTests install
.\mvnw.cmd -f maintenance-service/pom.xml -DskipTests install
.\mvnw.cmd -f notification-service/pom.xml -DskipTests install
.\mvnw.cmd -f integration-tests/pom.xml -DskipTests test-compile
```

These are the existing in-process fixtures, not proof of HTTP or Kafka failure recovery. Database-dependent tests require a configured test database before execution; the focused checks below do not.

## Kafka producer checks

After installing the updated Aviation JAR, run:

```powershell
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=KafkaProducerTest,NotificationKafkaTest,KafkaConfigurationTest,EmailExtractionTest,EmailSenderTest,CrewEmailExtractionTest'
```

The Kafka checks cover after-commit versus rollback behavior, immediate publication without a transaction, distinct event IDs, user/role keys, string and dated DTO JSON payloads, success logging after acknowledgment, serialization/send failures, and Boot's mapper/template/topic wiring. The context checks disable topic creation and do not contact Kafka. Existing email checks verify that SMTP handoffs stay on Feign and crew SSE handoffs use the producer.

These checks use mocks or a small non-web application context: no database, real broker, SMTP server, or application processes. Consumer delivery, actual topic creation/retention, broker recovery, and browser fan-out remain for the next stage.

On 13 November 2026, all 20 selected checks passed (10 Kafka checks and 10 existing email checks), and all 25 integration-test source files compiled. Aviation also built and installed successfully. The database-dependent suite and live Kafka delivery were not run.

Local fixture settings belong in `src/test/resources/application.properties` (ignored by Git). Use `application-example.properties` as a starting point for another checkout and configure `jwt-secret`/`jwt-expiration-ms` for the Identity fixtures. Select a test database before executing these tests.

## Focused email extraction checks

The email stage added three small test classes with 10 checks. They require no Spring application context, database, running services or SMTP provider:

```powershell
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=EmailExtractionTest,EmailSenderTest,CrewEmailExtractionTest'
```

They verify commit/rollback handoff timing, crew employee-to-user resolution, SMTP content/failure handling, retained background failures and executor rejection. All passed on 12 November 2026. The real HTTP/SMTP boundary was checked separately using a temporary local capture server; see Notification's README. Existing combined-context fixtures substitute both mail clients, preventing SMTP calls through these paths. The broader database-dependent suite was compiled but not executed in this stage.
