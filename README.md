THE IDEA
---
basically a flight system, there will be two parts. the first part is a basic flight booking system. the second (if i finish early) is a flight control and security system like the FAA and service center, etc...

------------------------------------------------------------------------------------------------------------------------

Customer registration endpoints
---

Use these endpoints in order. Send JSON with `Content-Type: application/json`; no login is required.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/auth/users/register/email` | Checks email and CPR availability, saves a pending registration, and requests a verification email. | `email`, `cpr` | `{"email":"you@example.com","cpr":"123456789"}` |
| POST | `/auth/users/verification` | Verifies the code and returns the pending registration ID (for example, `12`). | `email`, `code` | `{"email":"you@example.com","code":"482193"}` |
| POST | `/auth/users/register` | Creates the customer and user using the pending email and CPR, then deletes pending after saving successfully. | `pendingRegistrationId`, `code`, `password`, `fname`, `lname`, `phoneNumber`, `phoneNumberOpeningCode`, `securityQuestion`, `securityQuestionAnswer` | See the complete JSON example below. |

Use the code received by email and the ID returned by verification in the final request. Codes expire after 10 minutes and allow three incorrect attempts. Request a new code after expiry if needed.

All fields in this final registration example are required, including `phoneNumber`, `phoneNumberOpeningCode`, `securityQuestion`, and `securityQuestionAnswer`:

```json
{
  "pendingRegistrationId": 12,
  "code": "482193",
  "password": "your-password",
  "fname": "Saud",
  "lname": "Example",
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
