THE IDEA
---
basically a flight system, there will be two parts. the first part is a basic flight booking system. the second (if i finish early) is a flight control and security system like the FAA and service center, etc...

------------------------------------------------------------------------------------------------------------------------

Customer registration endpoints
---

Use these endpoints in order. Send JSON with `Content-Type: application/json`. Only setup requires the Bearer token returned by login.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/auth/users/register/email` | Checks email and CPR availability, saves a pending registration, and requests a verification email. | `email`, `cpr` | `{"email":"you@example.com","cpr":"123456789"}` |
| POST | `/auth/users/verification` | Verifies the code, creates an inactive customer account with status `SETUP_REQUIRED`, and returns a success message instructing the user to log in with CPR as their initial password. | `email`, `code` | `{"email":"you@example.com","code":"482193"}` |
| POST | `/auth/users/login` | Logs in using email and CPR as the initial password. Returns the JWT in the response's `message` field. | `email`, `password` | `{"email":"you@example.com","password":"123456789"}` |
| POST | `/auth/users/setup` | Completes the logged-in customer's details, changes the password, activates the account, then deletes pending after saving successfully. | `email`, `password`, `fName`, `lName`, `phoneNumber`, `phoneNumberOpeningCode`, `securityQuestion`, `securityQuestionAnswer` | See the complete JSON example below. |

Codes expire after 10 minutes and allow three incorrect attempts. Request a new code after expiry if verification has not been completed. After verification, setup does not require the code or pending ID and is not limited by the code's expiry.

Setup-required accounts can log in and finish setup but cannot access other protected endpoints. Send `Authorization: Bearer <token>` when calling `/auth/users/setup`. The email must match the logged-in user. Choose a new password different from the CPR. Successful setup sets `isActive` to true and status to `ACTIVE`.

All fields in this setup example are required:

```json
{
  "email": "you@example.com",
  "password": "your-new-password",
  "fName": "Saud",
  "lName": "Example",
  "phoneNumber": "12345678",
  "phoneNumberOpeningCode": "+973",
  "securityQuestion": "Your question",
  "securityQuestionAnswer": "Your answer"
}
```

Live notifications (SSE)
---

An active, authenticated user opens `GET /notifications` with `Authorization: Bearer <JWT>`.
The response is `text/event-stream` and stays open. User ID and role come from the JWT-authenticated
account, never from client-supplied parameters. Each tab/device gets its own connection.

Try it in PowerShell (replace the token with an FAA admin token):

```powershell
curl.exe -N -H "Authorization: Bearer YOUR_FAA_TOKEN" http://localhost:8080/notifications
```

The first event is `connected`. While this command is running, use an airline admin token to call
`POST /airlineAdmin/airplanes/{airplaneId}/requestActivation` for a valid airplane. After the request
transaction commits, all connected FAA admins receive `airplane-activation-requested`. Its JSON
payload is the same `AirplaneRequestResponse` DTO returned to the requesting airline admin.
Customers and airline employees do not receive this event. A rejected or rolled-back request sends nothing.

Reuse `NotificationService` from other business services:

```java
notificationService.sendToUser(userId, "booking-updated", bookingResponse);
notificationService.sendToRole(User.Role.FAA_ADMIN, "airplane-activation-requested", requestResponse);
```

Choose recipients on the backend. Use user delivery for private updates; role delivery reaches
every connected member of that role, so it is not suitable for airline-specific private information.
Payloads should be DTO snapshots, not JPA entities. Calls made inside a Spring transaction are
deferred until successful commit; calls outside a transaction send immediately.

The server sends a comment heartbeat every 25 seconds and closes streams after five minutes.
Clients must reconnect with a valid token; each new connection repeats authentication and account
checks. Role/account changes are reflected on reconnection, not immediately within an existing stream.
Disconnect the stream on logout. The SSE `retry` hint is three seconds, but a streaming `fetch`
client must implement its own reconnect loop and event parsing. Native browser `EventSource`
cannot set the required Authorization header; use a header-capable SSE client or streaming `fetch`.
The frontend handles named events to show toasts or update its notification UI.

Delivery is live and best-effort within one backend instance. There is no stored notification inbox,
event replay, or multi-server fan-out. After connecting/reconnecting, FAA clients should also fetch
`GET /faaadmin/airplaneRequests` to recover outstanding work and deduplicate by `requestId`.
Network writes use Spring MVC's blocking emitter API; a slow client can delay broadcasting.
For multiple backend instances or larger traffic, add a shared broker and bounded asynchronous delivery.

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
-------------------------------------------------------------------------------------------
Used Resources
---
Baeldung email varification methods post
https://www.baeldung.com/java-email-validation-regex

GeekForGeeks Spring boot sending email via SMTP tutorial
https://www.geeksforgeeks.org/springboot/spring-boot-sending-email-via-smtp/

chatgpt suggested the code cpr.matches("[0-9]{9} in PendingRegistrationService sendCode() method

StackOverFlow for phone number validation
https://stackoverflow.com/questions/71654287/how-to-validate-phone-number-using-spring-boot
