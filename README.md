# Saud's Flight System

Saud's Flight System is a Java REST API for flight booking, airline administration, and FAA-style airplane approval. The idea grew out of an earlier frontend project that used the Amadeus API: this project explores building the backend behind an aviation application. The current backend manages its own PostgreSQL data; it does not integrate with Amadeus.

The purpose is to connect three main users: customers who search and book flights, airline administrators who manage airplanes and schedules, and FAA administrators who review airplane activation requests. It is an educational project, with a wider aviation workflow planned in the ERD.

## Main features

- Customer registration with email verification, account setup, JWT login, password management, and profile updates with optional images.
- Role and ownership checks for customer, airline administrator, and FAA administrator operations; temporary lockout after repeated failed logins.
- Flight search with route, date, airline, and seat-class filters, pagination, and sorting.
- Seat booking and cancellation, seat availability updates, and email confirmations.
- Airline and airline administrator creation, airplane registration, and FAA activation approval or rejection.
- Flight scheduling with airplane availability checks and scheduled flight-status updates.
- Server-Sent Events (SSE) notifying connected FAA administrators about airplane activation requests.
- Database audit records, centralized error handling, demo data seeding, and Postman collections.
- Interactive Swagger UI and a generated OpenAPI specification.

## Technologies

| Technology | Use |
| --- | --- |
| Java 17 and Spring Boot 4.1.1 | Application runtime and REST backend |
| Spring MVC | Controllers, HTTP requests, and SSE through `SseEmitter` |
| Spring Security, JWT (JJWT), BCrypt | Authentication, authorization, password and security-answer hashing |
| Spring Data JPA / Hibernate and PostgreSQL | Entity relationships, repositories, and persistence |
| Maven and Maven Wrapper | Dependencies, compilation, and running tests |
| Spring Mail / SMTP | Verification and transactional email |
| springdoc-openapi 3.1.1 / Swagger UI | Generated API documentation and interactive requests |
| Lombok | Reducing Java boilerplate |
| Apache Commons Validator, Passay, libphonenumber | Email, password, and phone validation |
| JUnit and Spring Boot Test | Automated testing |
| Postman, Git, and GitHub | Manual API testing, version control, and repository hosting |

## Architecture

The application is a single Spring Boot backend organized into layers:

```text
Swagger UI / Postman / API client
              |
     Spring Security + JWT filter
              |
         Controllers
              |
           Services ----> SMTP email / SSE connections
              |
      JPA repositories
              |
          PostgreSQL
```

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP endpoints for accounts, customers, bookings, flights, airlines, FAA administration, and notifications |
| `service` | Business rules, validation, transactions, email, notifications, and audit logging |
| `repository` | Database access through Spring Data JPA |
| `model` | Persistent entities, request objects, and response DTOs |
| `security` | JWT processing, user details, password encoding, and route access |
| `exception` | Application exceptions and centralized HTTP error responses |
| `config` | Optional database seeding and OpenAPI configuration |

Authentication uses a shared `User` entity linked to the relevant customer, airline employee, or FAA administrator profile. Controllers delegate to services, which enforce business rules and access repositories. Business changes and audit entries share transactions. Scheduled jobs update flight statuses, while SSE keeps an HTTP connection open for server-to-client events. The SSE implementation stores active connections in memory and sends transactional events after a successful commit.

## General approach

I began with the broader aviation system idea and designed the entities around the ERD. The early work focused on airlines, airplanes, airports, flights, bookings, and user profiles. As authentication developed, I changed the model to separate shared login details into a `User` entity linked to the different profile types. This let authentication use one account structure while keeping each role's details in its own model.

I then built the application in stages: registration and email verification, validation and password management, airline and FAA workflows, flight scheduling, and booking. I organized the code into controllers, services, and repositories, keeping the main validation and business rules in services. Later work added flight search, SSE notifications, audit logging, account lockout, tests, and sample data. Because the original plan was larger than the available time, I reduced the scope to complete the booking and airplane-approval workflows before the deadline.

## User stories

See [User stories](docs/USER-STORIES.md) for the current customer, airline administrator, and FAA administrator workflows, plus deferred stories from the wider design. These stories were documented retrospectively from the implementation and original scope.

## ERD

See the [implemented system ERD](docs/ERD-IMPLEMENTED.md) for the current entities, fields, and relationships based on the JPA models.

The [original ERD below](#erd-diagram) is preserved unchanged as the broader design. It is not fully accurate and includes planned functionality; use the implemented ERD for the current system.

## Planning

See [Planning and progress](docs/PLANNING.md) for deliverables, scope, a development timeline, and remaining work. This is a retrospective record based on Git history, not a claim that a planning board existed during development.

## API documentation

After starting the application:

- API base URL: `http://localhost:8080`
- [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)
- [OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml)
- [Detailed endpoint guide](docs/API-GUIDE.md), including request examples, business rules, SSE usage, audit logging, and test setup.
- [Postman presentation guide](postman/PRESENTATION.md) and [sample database reference](SAMPLE-DATABASE.md).

Swagger UI is a browser interface for exploring the API. OpenAPI is the structured specification that powers it. The documentation routes are public; business endpoints retain their authentication and role checks. The integration uses [springdoc-openapi](https://springdoc.org/).

In Swagger UI, expand `POST /auth/users/login`, choose **Try it out**, and log in with a seeded account. Copy the JWT from the response's `message` field. Click **Authorize**, paste just the token (without `Bearer `), and authorize. Swagger then includes the bearer header in protected requests. Use the account role appropriate to the endpoint. Login, registration-email requests, and verification do not require a token. Account setup requires login; other protected endpoints require an active account.

The specification is generated from controller signatures. Some endpoints return generic response types, so the detailed guide remains useful for exact response shapes and business validation. For the long-running SSE stream, use the authenticated `curl.exe -N` example in the guide.

## Installation

### 1. Prerequisites and clone

Install JDK 17 or later, PostgreSQL, and Git. A Maven wrapper is included; an installed Maven 3.6.3 or later is an alternative. Use an SMTP account for features that send email.

```powershell
git clone https://github.com/Saud-GA-JDB/PROJECT2_Flight_System.git
cd PROJECT2_Flight_System
```

The following commands use PowerShell. On macOS/Linux, use `./mvnw` and `export NAME=value` for environment variables.

### 2. Configure PostgreSQL

Start PostgreSQL and connect through pgAdmin or `psql` using an account allowed to create databases:

```sql
CREATE DATABASE aviation;
```

The application database user must be able to create and update tables in this database. Hibernate uses `ddl-auto=update` to create/update the schema on startup; there are no migration scripts to run.

### 3. Configure the application

For a fresh clone, copy the checked-in template:

```powershell
Copy-Item src/main/resources/application-example.properties src/main/resources/application.properties
```

If you already have a local `application.properties`, keep it and compare it with the template instead of overwriting it. The local file is ignored by Git. The template contains placeholders rather than account credentials.

### 4. Set environment variables

Set these in the same terminal where you start the application. Replace the placeholder values with your own:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/aviation"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "YOUR_DATABASE_PASSWORD"
$env:SPRING_MAIL_HOST = "smtp.gmail.com"
$env:SPRING_MAIL_PORT = "587"
$env:SPRING_MAIL_USERNAME = "YOUR_SMTP_USERNAME"
$env:SPRING_MAIL_PASSWORD = "YOUR_SMTP_PASSWORD_OR_APP_PASSWORD"
$env:JWT_EXPIRATION_MS = "86400000"

# Generate a Base64-encoded 32-byte signing key for local development.
$jwtKeyBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtKeyBytes)
$jwtRandom.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
```

Keep the same JWT secret across restarts if existing tokens should remain valid. Gmail SMTP requires an appropriate app password; use your provider's settings if using another SMTP service. The app does not automatically load a `.env` file. When launching through an IDE, configure these variables in its run configuration.

### 5. Seed the database and start

```powershell
$env:APP_SEED_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

If the wrapper cannot start, use `mvn spring-boot:run` with installed Maven. The first run downloads dependencies and needs internet access.

Wait for the application to start and report `Database seed completed; existing records were preserved.` The seeder creates seven demo accounts, two airlines, three airports, five airplanes, five flights, four bookings, and an airplane activation request in an empty database. Seeding itself sends no email.

After the first successful seed, stop with **Ctrl+C** and start normally:

```powershell
$env:APP_SEED_ENABLED = "false"
.\mvnw.cmd spring-boot:run
```

Seeding is optional and disabled by default. Rerunning it adds missing data but does not reset existing records or refresh old flight dates. Sample departures start three days after the initial seed. See [Sample database reference](SAMPLE-DATABASE.md) for details; generated IDs may differ between databases.

### 6. Access the API and Swagger

Open [Swagger UI](http://localhost:8080/swagger-ui/index.html), then use one of these local demo accounts:

| Role | Email | Password |
| --- | --- | --- |
| Customer | flighttest.customer@mailsac.com | TestPassword1 |
| Airline administrator | flighttest.airline@mailsac.com | TestPassword1 |
| FAA administrator | flighttest.faa@mailsac.com | TestPassword1 |

These are demonstration credentials. To log in directly from PowerShell:

```powershell
$body = @{ email = "flighttest.customer@mailsac.com"; password = "TestPassword1" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/users/login" -ContentType "application/json" -Body $body
$headers = @{ Authorization = "Bearer $($login.message)" }
Invoke-RestMethod -Uri "http://localhost:8080/flights/search" -Headers $headers
```

Use the same token in Swagger's **Authorize** dialog. Raw documentation is available at [OpenAPI JSON](http://localhost:8080/v3/api-docs). The project is an API backend; it does not include a customer-facing frontend.

### Testing

Existing service tests depend on seeded PostgreSQL fixtures, and some send real email. Use a dedicated seeded test database and working SMTP settings. Follow the [test setup and fixture notes](docs/API-GUIDE.md#database-seeding) before running the service suite. Manual changes to fixtures can affect results.

## Unsolved problems and current limitations

- The full aviation workflow is unfinished. Airport operations, crew assignments, ground services, maintenance, and air traffic control remain outside the implemented scope.
- The original ERD still needs a manual accuracy update; the separate implemented ERD documents the current models.
- SSE is live delivery within one backend instance. It has no persistent inbox, missed-event replay, or shared delivery across servers. Clients must reconnect and fetch outstanding requests.
- Flight times use local date-times without offsets, and time-based checks use server-local time. Full airport time-zone handling remains future work.
- A failed verification email can leave a pending registration until expiry. Email and database transactions are not fully coordinated: a delivered email cannot be rolled back if a later database commit fails.


## Major challenges

**Time management and scope.** The original ERD described a much larger system than I could implement in the available time. I narrowed the delivery to customer booking, airline administration, and FAA airplane approvals. This made it possible to finish the core workflows, but the wider operational system remains unfinished.

**Learning SSE.** SSE was especially difficult because we had not been taught how to implement it, and I did not have enough time for a deep dive. The online examples I found were not very helpful for understanding and applying the concept. The implementation now uses Spring MVC's `SseEmitter`, tracks authenticated connections, removes disconnected clients, sends heartbeats, and delivers airplane-request events to connected FAA administrators after the database transaction commits. This provided a working notification path within the project's limited scope.

**Structuring authentication across roles.** During development, I changed the database design so authentication could use a shared `User` entity, with separate links to customer, airline employee, and FAA administrator profiles. This separated common login information from role-specific details and supported the different application workflows.

## Future improvements

The main priority is completing the wider workflow in the ERD: airport operators and employees, crew and ground-service assignments, maintenance providers and records, and air traffic control facilities and flight events. These would connect booking and scheduling to the operational side of a flight.

Further improvements include a frontend, reliable notification storage and delivery, consistent response DTOs and validation, a reset-token password recovery flow, time-zone-aware scheduling, schema migrations, and tests that run independently of manually seeded data and external email services.

## AI usage

I used AI during the project for code generation, documentation, discussion, and review:

- **Database seeding:** The database seeding implementation was generated by AI.
- **Postman:** The contents of the `postman/` folder were generated by AI.
- **README:** This `README.md` was generated by AI using the project code, Git history, assignment requirements, and my answers about the project.
- **Unit tests:** The unit tests were created by AI.
- **Audit and logging:** I started the audit and logging implementation manually, including the models and services, and applied it to a few operations. I then asked AI to complete the repetitive work by following my existing approach and patterns.
- **Filtering queries:** The custom `@Query` queries used for filtering were generated by AI.
- **SSE:** I implemented Server-Sent Events manually with AI assistance and guidance from [Baeldung](https://www.baeldung.com/spring-server-sent-events), [Alexander Obregon's tutorial on Medium](https://medium.com/@AlexanderObregon/how-to-implement-server-sent-events-sse-in-spring-boot-620024272ccb), and [GeeksforGeeks](https://www.geeksforgeeks.org/advance-java/server-sent-events-in-spring/).
- **Exploring solutions:** I discussed some problems with AI to explore possible approaches and options.
- **Logic review:** I also used AI to check whether the logic was correct. For those reviews, I asked it to identify issues without editing the logic.

## Resources and acknowledgements

- [Baeldung: email validation in Java](https://www.baeldung.com/java-email-validation-regex)
- [GeeksforGeeks: sending email through Spring Boot SMTP](https://www.geeksforgeeks.org/springboot/spring-boot-sending-email-via-smtp/)
- [Stack Overflow: phone-number validation in Spring Boot](https://stackoverflow.com/questions/71654287/how-to-validate-phone-number-using-spring-boot)
- [springdoc-openapi documentation](https://springdoc.org/)
- ChatGPT assistance included the nine-digit CPR validation expression used in `PendingRegistrationService`.

---

ERD Diagram
---
Implemented entities below follow the current JPA models. `Person` is a mapped superclass, so its fields appear in `CUSTOMER`, `AIRLINE_EMPLOYEE`, and `FAA_ADMIN`; it has no separate table. Attribute names use Java field names, with relationship IDs representing join columns. Enum attributes show Java types (not database storage types).

Entities marked `PLANNED` and their relationships are retained from the original design and are not implemented yet. In particular, `AIRPORT.operatorId` is planned. For implemented relationships, an optional parent (`o|`) reflects a nullable join column in the current model.

```mermaid
erDiagram
AIRLINE {
long id PK
string name UK
string airlineCode UK
string headquartersCountry
}

    AIRPLANE {
        long id PK
        long airlineId FK
        string registrationNumber UK
        string model
        int seatCapacity
        long maxMileage
        Status status "ACTIVE or GROUNDED; default GROUNDED"
    }

    AIRPLANE_REQUEST {
        long id PK
        long airplaneId FK "Required"
        ApprovalStatus status "PENDING, ACCEPTED, DENIED; default PENDING"
        string reviewReason "Up to 500 characters"
        datetime requestedAt "Creation timestamp"
        datetime reviewedAt
        long reviewedById FK "Nullable FAA admin reviewer"
    }

    FAA_ADMIN {
        long id PK
        string fName
        string lName
        string cpr UK "Required"
        string phoneNumberOpeningCode
        string phoneNumber
        byte[] imageData
        string imageUrl
        datetime createdAt
        datetime updatedAt
    }

    USER {
        long id PK
        string emailAddress UK "Required"
        string password "Required"
        string securityQuestion
        string securityQuestionAnswer
        Role role "CUSTOMER, AIRPORT_EMPLOYEE, AIRLINE_EMPLOYEE, FAA_ADMIN"
        boolean isActive
        Status status "SETUP_REQUIRED or ACTIVE"
        long customerId FK, UK "Nullable"
        long airlineEmployeeId FK, UK "Nullable"
        long faaAdminId FK, UK "Nullable"
    }

    PENDING_REGISTRATION {
        long id PK
        string emailAddress UK "Required"
        string cpr UK "Required"
        string codeHash "Required"
        datetime expiresAt "Required"
        int failedAttempts "Required"
        boolean verified "Required"
    }

    AIRLINE_EMPLOYEE {
        long id PK
        long airlineId FK
        string fName
        string lName
        string cpr UK "Required"
        string phoneNumberOpeningCode
        string phoneNumber
        byte[] imageData
        string imageUrl
        datetime createdAt
        datetime updatedAt
        AirlineRole airlineRole "PILOT, FLIGHT_ATTENDANT, GATE_RECEPTION"
        datetime hireDate
        long salary
    }

    AIRPORT_OPERATOR["AIRPORT_OPERATOR (PLANNED)"] {
        long id PK
        string name
        string contactEmail
    }

    AIRPORT {
        long id PK
        long operatorId FK "PLANNED; not in Airport model"
        string iataCode UK
        string name
        string city
        string country
        string timeZone
    }

    AIRPORT_EMPLOYEE["AIRPORT_EMPLOYEE (PLANNED)"] {
        long id PK
        long operatorId FK
        long airportId FK
        string firstName
        string lastName
        string role
        string email UK
    }

    FLIGHT {
        long id PK
        long airlineId FK
        long airplaneId FK
        long originAirportId FK
        long destinationAirportId FK
        string flightNumber
        datetime scheduledDeparture
        datetime scheduledArrival
        datetime actualDeparture
        datetime actualArrival
        string status
    }

    CUSTOMER {
        long id PK
        string fName
        string lName
        string cpr UK "Required"
        string phoneNumberOpeningCode
        string phoneNumber
        byte[] imageData
        string imageUrl
        datetime createdAt
        datetime updatedAt
    }

    BOOKING {
        long id PK
        long customerId FK
        long flightId FK
        string bookingRef UK
        string seatNumber
        datetime bookedAt
        datetime updatedAt
        string status
    }

    CREW_ASSIGNMENT["CREW_ASSIGNMENT (PLANNED)"] {
        long id PK
        long flightId FK
        long employeeId FK
        string duty
        datetime assignedAt
    }

    GROUND_SERVICE_ASSIGNMENT["GROUND_SERVICE_ASSIGNMENT (PLANNED)"] {
        long id PK
        long flightId FK
        long airportId FK
        long employeeId FK
        string serviceType
        datetime startedAt
        datetime completedAt
    }

    MAINTENANCE_PROVIDER["MAINTENANCE_PROVIDER (PLANNED)"] {
        long id PK
        string name
        string contactEmail
    }

    MAINTENANCE_EMPLOYEE["MAINTENANCE_EMPLOYEE (PLANNED)"] {
        long id PK
        long providerId FK
        string firstName
        string lastName
        string role
        string licenseNumber
    }

    MAINTENANCE_RECORD["MAINTENANCE_RECORD (PLANNED)"] {
        long id PK
        long airplaneId FK
        long technicianId FK
        string maintenanceType
        string description
        datetime performedAt
        string releaseStatus
    }

    AIR_NAVIGATION_SERVICE_PROVIDER["AIR_NAVIGATION_SERVICE_PROVIDER (PLANNED)"] {
        long id PK
        string name
        string providerType
    }

    CONTROL_FACILITY["CONTROL_FACILITY (PLANNED)"] {
        long id PK
        long providerId FK
        long airportId FK
        string name
        string facilityType
    }

    ATC_EMPLOYEE["ATC_EMPLOYEE (PLANNED)"] {
        long id PK
        long facilityId FK
        string firstName
        string lastName
        string role
        string employeeNumber UK
    }

    FLIGHT_CONTROL_EVENT["FLIGHT_CONTROL_EVENT (PLANNED)"] {
        long id PK
        long flightId FK
        long controllerId FK
        string eventType
        datetime occurredAt
        string notes
    }

    AIRLINE ||--o{ AIRPLANE : manages
    AIRLINE o|--o{ AIRLINE_EMPLOYEE : employs
    AIRLINE o|--o{ FLIGHT : operates
    AIRPLANE o|--o{ FLIGHT : assignedTo
    AIRPLANE ||--o{ AIRPLANE_REQUEST : has
    FAA_ADMIN o|--o{ AIRPLANE_REQUEST : reviews

    CUSTOMER o|--o| USER : linkedTo
    AIRLINE_EMPLOYEE o|--o| USER : linkedTo
    FAA_ADMIN o|--o| USER : linkedTo

    AIRPORT_OPERATOR ||--o{ AIRPORT : operates
    AIRPORT_OPERATOR ||--o{ AIRPORT_EMPLOYEE : employs
    AIRPORT ||--o{ AIRPORT_EMPLOYEE : workplace
    AIRPORT o|--o{ FLIGHT : origin
    AIRPORT o|--o{ FLIGHT : destination

    CUSTOMER o|--o{ BOOKING : makes
    FLIGHT o|--o{ BOOKING : has

    AIRLINE_EMPLOYEE ||--o{ CREW_ASSIGNMENT : receives
    FLIGHT ||--o{ CREW_ASSIGNMENT : has

    AIRPORT_EMPLOYEE ||--o{ GROUND_SERVICE_ASSIGNMENT : performs
    AIRPORT ||--o{ GROUND_SERVICE_ASSIGNMENT : location
    FLIGHT ||--o{ GROUND_SERVICE_ASSIGNMENT : receives

    MAINTENANCE_PROVIDER ||--o{ MAINTENANCE_EMPLOYEE : employs
    MAINTENANCE_EMPLOYEE ||--o{ MAINTENANCE_RECORD : performs
    AIRPLANE ||--o{ MAINTENANCE_RECORD : has

    AIR_NAVIGATION_SERVICE_PROVIDER ||--o{ CONTROL_FACILITY : operates
    AIRPORT o|--o{ CONTROL_FACILITY : hosts
    CONTROL_FACILITY ||--o{ ATC_EMPLOYEE : employs
    ATC_EMPLOYEE ||--o{ FLIGHT_CONTROL_EVENT : handles
    FLIGHT ||--o{ FLIGHT_CONTROL_EVENT : has
```
