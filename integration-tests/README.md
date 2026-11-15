# Existing integration fixtures

## Cucumber API tests

The Cucumber tests are organized into `config/CucumberSpringConfiguration`,
`runners/TestRunner`, `definitions/*APIStepDefinitions`, and `resources/features/*API.feature`.
They use JUnit 4 `@RunWith(Cucumber.class)`, REST Assured requests, `Assert` assertions,
and Spring Boot on a random port. The dependency versions support this project's Java 17
and Spring Boot setup, using the existing Maven Surefire configuration.

The 14 scenarios focus on the main workflows:

| Feature | Coverage |
| --- | --- |
| BookingAPI | Search, book, list, cancel, occupied-seat rejection, customer ownership |
| AirlineAPI | FAA creates an airline/admin; airline admin schedules, assigns crew and activates a flight |
| AirplaneAPI | Airline registers and requests activation; FAA accepts or denies |
| FlightOperationAPI | Pilot requests clearance and takes off/lands; responsible towers approve/reject; take-off needs approval |
| ATCAPI | Tower admin creates and lists own-tower controllers |
| InspectionAPI | Airport admin assigns; inspector lists and passes/fails the inspection |
| MaintenanceAPI | Airline requests; provider admin assigns; technician lists and completes maintenance |

Install the application JARs with the commands below after changing application code.
Then run only the Cucumber suite from the repository root:

```powershell
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=TestRunner'
```

You can also run the suite directly in IntelliJ IDEA:

1. Load `integration-tests/pom.xml` as a Maven project if it is not already linked, then reload Maven projects.
2. Open `src/test/java/runners/TestRunner.java` in this module and click the green Run arrow beside the class.
3. In **Run > Edit Configurations**, check that the JUnit configuration uses the integration-test module's classpath and Java 17. Set its working directory to the `integration-tests` folder, because the runner's feature path is relative to that folder.
4. Run `TestRunner`. Keep PostgreSQL running; the tests start their own application server.

The same setup and cleanup hooks run through IntelliJ. The application JARs must be installed
as described below after application changes. See [IntelliJ's Maven testing guide](https://www.jetbrains.com/help/idea/work-with-tests-in-maven.html).

The suite uses the **existing PostgreSQL connection** in the local
`src/test/resources/application.properties`. It validates the existing schema and never
creates, drops or upgrades it. A `@Before` hook creates unique scenario records, and an
`@After` hook removes those records and their bookings, crew, inspections, requests,
control events and audit rows in a committed transaction, even after a failed step.
Cleanup uses the scenario's exact emails, airline codes and generated IDs. It does not
truncate tables, reset sequences or rely on rollback of HTTP requests. Each scenario logs
its `cucumber-<UUID>` identifier for locating records if the JVM is forcibly stopped before
cleanup. Keep this suite sequential and let it finish so its cleanup hook can run.

These are HTTP API tests against a combined test context. They reuse the existing Identity
and Maintenance adapters: role accounts log in through the real login endpoint and send
bearer tokens, but cross-service calls stay in process. Email delivery and Kafka publishing
use the existing test substitutes. Application seeders and scheduled jobs are disabled in
the Cucumber context so they do not update unrelated database records. Separate application
processes, real Feign transport, SMTP and Kafka delivery are outside this suite's coverage.

Verified on 14 November 2026: all **14 scenarios / 109 steps passed** against the existing
local PostgreSQL database. All 14 cleanup hooks completed. A separate read-only database
check found no remaining Cucumber accounts, airlines, airplanes, airports, operators,
providers or towers, and no audit rows for the scenario actors in the three audit tables.

Re-run on 15 November 2026 after adding the separate Docker runner: all 14 scenarios / 109 steps passed again, with all scenario cleanup hooks completed.

## Docker Cucumber notification test

`dockerbdd/runners/DockerTestRunner` runs one scenario from `resources/docker-features/NotificationAPI.feature`: an airline admin requests airplane activation and an FAA admin receives the matching SSE notification through real Kafka. It follows the existing JUnit 4 runner, Gherkin, REST Assured and `Assert` style. Java's HTTP client reads the SSE stream.

Start the local stack using the [Docker guide](../docs/DOCKER.md), and install the application JARs using the commands below before compiling the tests. The fixture database must be the same local test database used by those containers. Run from the repository root:

```powershell
$env:DOCKER_TEST_DATABASE_PASSWORD = 'YOUR_DOCKER_DATABASE_PASSWORD'
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=dockerbdd.runners.DockerTestRunner'
```

Supply the password through the shell or IDE environment; do not commit it. This runner does not load the root `.env` automatically.

| Environment variable | Default |
| --- | --- |
| `DOCKER_TEST_DATABASE_URL` | `jdbc:postgresql://localhost:5433/aviation` |
| `DOCKER_TEST_DATABASE_USERNAME` | `postgres` |
| `DOCKER_TEST_DATABASE_PASSWORD` | Required: local Docker database password |
| `DOCKER_TEST_IDENTITY_URL` | `http://localhost:8081` |
| `DOCKER_TEST_AVIATION_URL` | `http://localhost:8080` |
| `DOCKER_TEST_NOTIFICATION_URL` | `http://localhost:8084` |

The small Spring configuration supplies only JDBC fixture setup/cleanup; it does not start an application or substitute Kafka/Feign. A setup transaction creates two active test accounts, their profiles, an airline/admin membership and a grounded airplane. Requests log in through Identity and request activation through Aviation. The test waits for SSE connection confirmation before submitting, then checks HTTP 201, the `airplane-activation-requested` event, matching request/airplane IDs, registration number and `PENDING` status. Event waiting is bounded to 15 seconds after the HTTP assertions.

The `@After` hooks close the SSE stream and remove only the scenario's database records, including audits and the activation request, even after a failed step. Setup failures roll back. Forced process termination can prevent cleanup; the unique `docker-cucumber-<UUID>` marker is printed for recovery. No schema changes or general seeding occur. The normal activation email path still runs, using a temporary `.invalid` address; SMTP delivery is not asserted. Kafka retains the test event according to topic retention, and connected FAA admins can receive this test notification.

Run this only against local test services and data. The runner and glue are separate from the original 14 scenarios; `DockerTestRunner` is explicitly selected and is not included by Maven's default test-name patterns. Missing configuration or unavailable services fail the test rather than silently skipping it.

Verified on 15 November 2026: the scenario passed against the running Docker services with real Identity login, HTTP service communication, Kafka publication/consumption and SSE delivery. Producer and consumer logs contained the same event ID. Cleanup completed, and an independent database check found no remaining test accounts, airline or airplane. This covers the successful workflow, not broker outages, overload, browser rendering, SMTP delivery or AWS deployment.

## Existing service fixtures

These existing fixtures combine the applications in a Spring test context. They are kept here so an application's build does not compile another application's source code. This is a test project, not a service. Maintenance fixtures use small in-process client adapters and do not establish HTTP authentication, timeout, or failure-recovery behavior.

They depend on the four applications' ordinary JARs. Each application also produces a runnable `-app.jar`. No application depends on this project or another application. The existing SSE fixtures use Aviation's real after-commit service and an in-process Kafka producer substitute calling Notification's delivery code. A mocked Kafka admin prevents broker connections during these fixtures. Their assertions still cover transaction timing and scoped delivery, but do not prove real Kafka transport. The old SSE Feign method and its fixture override have been removed; consumer processing has separate focused tests below.

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

## Kafka producer and consumer checks

After installing the updated Aviation and Notification JARs, run:

```powershell
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=KafkaConsumerTest,KafkaConsumerConfigurationTest,KafkaRecordCommitTest,KafkaProducerTest,NotificationKafkaTest,KafkaConfigurationTest,EmailExtractionTest,EmailSenderTest,CrewEmailExtractionTest'
```

The Kafka checks cover after-commit versus rollback behavior, immediate publication without a transaction, distinct event IDs, user/role keys, string and dated DTO JSON payloads, success logging after acknowledgment, serialization/send failures, and Boot's mapper/template/topic wiring. The context checks disable topic creation and do not contact Kafka. Existing email checks verify that SMTP handoffs stay on Feign and crew SSE handoffs use the producer.

Consumer checks cover string/structured payload routing, validation, duplicate suppression, retry after a failed dispatch, retry exhaustion, logged skipping, and fan-out to two mock FAA admin emitters while excluding an unrelated user. A test-only bean post-processor prevents annotation listeners from starting in context checks; the production listener needs no `autoStartup` option. `KafkaRecordCommitTest` runs the real listener container against Kafka's `MockConsumer`, holding dispatch with a latch and checking that the record offset commits only after dispatch returns.

On 13 November 2026, all 42 selected checks passed (32 Kafka checks and 10 existing email checks), and all 28 integration-test source files compiled. Aviation and Notification clean builds and installs passed. These tests use mocks, a small non-web context, or a simulated consumer: no database, real broker, SMTP server, or application processes.

Separate Docker checks on 14 November 2026 verified topic creation and retention settings, synthetic real-broker delivery to authenticated HTTP SSE streams, user/role filtering, and duplicate suppression. See the [Docker verification record](../docs/DOCKER.md#verification-on-14-november-2026). Browser delivery, broker-outage recovery, and an actual activation-request workflow were not exercised by those checks. The database-backed Cucumber results are recorded above; they use in-process service adapters and substitute Kafka delivery.

Local fixture settings belong in `src/test/resources/application.properties` (ignored by Git). Use `application-example.properties` as a starting point for another checkout and configure `jwt-secret`/`jwt-expiration-ms` for the Identity fixtures. Select a test database before executing these tests.

## Focused email extraction checks

The email stage added three small test classes with 10 checks. They require no Spring application context, database, running services or SMTP provider:

```powershell
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=EmailExtractionTest,EmailSenderTest,CrewEmailExtractionTest'
```

They verify commit/rollback handoff timing, crew employee-to-user resolution, SMTP content/failure handling, retained background failures and executor rejection. All passed on 12 November 2026. The real HTTP/SMTP boundary was checked separately using a temporary local capture server; see Notification's README. Existing combined-context fixtures substitute both mail clients, preventing SMTP calls through these paths. The broader database-dependent suite was compiled but not executed in this stage.
