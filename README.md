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

ERD Diagram
---
```mermaid
erDiagram
AIRLINE {
long id PK
string name
string airlineCode UK
string headquartersCountry
}

    AIRPLANE {
        long id PK
        long airlineId FK
        string registrationNumber UK
        string model
        int seatCapacity
        string status
    }

    AIRLINE_EMPLOYEE {
        long id PK
        long airlineId FK
        string firstName
        string lastName
        string email UK
        string role
        date hireDate
    }

    AIRPORT_OPERATOR {
        long id PK
        string name
        string contactEmail
    }

    AIRPORT {
        long id PK
        long operatorId FK
        string iataCode UK
        string name
        string city
        string country
        string timeZone
    }

    AIRPORT_EMPLOYEE {
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
        string firstName
        string lastName
        string email UK
        string phoneNumber
    }

    BOOKING {
        long id PK
        long customerId FK
        long flightId FK
        string bookingReference UK
        string seatNumber
        datetime bookedAt
        string status
    }

    CREW_ASSIGNMENT {
        long id PK
        long flightId FK
        long employeeId FK
        string duty
        datetime assignedAt
    }

    GROUND_SERVICE_ASSIGNMENT {
        long id PK
        long flightId FK
        long airportId FK
        long employeeId FK
        string serviceType
        datetime startedAt
        datetime completedAt
    }

    MAINTENANCE_PROVIDER {
        long id PK
        string name
        string contactEmail
    }

    MAINTENANCE_EMPLOYEE {
        long id PK
        long providerId FK
        string firstName
        string lastName
        string role
        string licenseNumber
    }

    MAINTENANCE_RECORD {
        long id PK
        long airplaneId FK
        long technicianId FK
        string maintenanceType
        string description
        datetime performedAt
        string releaseStatus
    }

    AIR_NAVIGATION_SERVICE_PROVIDER {
        long id PK
        string name
        string providerType
    }

    CONTROL_FACILITY {
        long id PK
        long providerId FK
        long airportId FK
        string name
        string facilityType
    }

    ATC_EMPLOYEE {
        long id PK
        long facilityId FK
        string firstName
        string lastName
        string role
        string employeeNumber UK
    }

    FLIGHT_CONTROL_EVENT {
        long id PK
        long flightId FK
        long controllerId FK
        string eventType
        datetime occurredAt
        string notes
    }

    AIRLINE ||--o{ AIRPLANE : manages
    AIRLINE ||--o{ AIRLINE_EMPLOYEE : employs
    AIRLINE ||--o{ FLIGHT : operates
    AIRPLANE o|--o{ FLIGHT : assignedTo

    AIRPORT_OPERATOR ||--o{ AIRPORT : operates
    AIRPORT_OPERATOR ||--o{ AIRPORT_EMPLOYEE : employs
    AIRPORT ||--o{ AIRPORT_EMPLOYEE : workplace
    AIRPORT ||--o{ FLIGHT : origin
    AIRPORT ||--o{ FLIGHT : destination

    CUSTOMER ||--o{ BOOKING : makes
    FLIGHT ||--o{ BOOKING : has

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