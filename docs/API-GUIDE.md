# Detailed API and operations guide

Cron Jobs
---

**Flight status update:** `FlightService.updateFlightStatus()` runs every 5 minutes using `@Scheduled(cron = "0 */5 * * * *")`.

- Changes `ACTIVE` to `IN_AIR` when `actualDeparture` is not null and its time has been reached.
- Changes `IN_AIR` to `CLOSED` when `actualArrival` is not null and its time has been reached.

Null actual times leave the status unchanged, allowing for delayed flights. Time checks use the server's local time.

Customer registration endpoints
---

Use these endpoints in order. Send JSON with `Content-Type: application/json`. Only setup requires the Bearer token returned by login.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/auth/users/register/email` | Checks email and CPR availability, saves a pending registration, and requests a verification email. | `email`, `cpr` | `{"email":"you@example.com","cpr":"123456789"}` |
| POST | `/auth/users/verification` | Verifies the code, creates an inactive customer account with status `SETUP_REQUIRED`, and returns a success message instructing the user to log in with CPR as their initial password. | `email`, `code` | `{"email":"you@example.com","code":"482193"}` |
| POST | `/auth/users/login` | Logs in using email and the current password (CPR is the initial password). Returns status 200 with the JWT in the response's `message` field. | `email`, `password` | `{"email":"you@example.com","password":"123456789"}` |
| POST | `/auth/users/setup` | Completes the logged-in customer's details, changes the password, activates the account, then deletes pending after saving successfully. | `password`, `fName`, `lName`, `phoneNumber`, `phoneNumberOpeningCode`, `securityQuestion`, `securityQuestionAnswer` | See the complete JSON example below. |

For `/auth/users/register/email`, use a valid email address and a CPR containing exactly nine digits. Success returns status 200 with `{"message":"Verification email requested."}`. Invalid input or an unexpired pending registration returns 400; an email or CPR already registered to an account returns 409. Email sending failures return 500 with message `Could not send the email.`. These errors use the global response fields `timestamp`, `status`, `error`, `message`, and `path`. The pending registration is saved before sending the email, so a failed send can leave a pending registration that blocks another request until it expires.

Login does not require a Bearer token. Accounts with status `DEACTIVATED` cannot log in; inactive accounts can log in only while their status is `SETUP_REQUIRED`. Authentication failures return status 401 with the global error response fields `timestamp`, `status`, `error`, `message`, and `path`. The error is `UNAUTHORIZED`, and the message is `Error: Email or password is incorrect, or the account is inactive.`

Codes expire after 10 minutes and allow three incorrect attempts. Request a new code after expiry if verification has not been completed. After verification, setup does not require the code or pending ID and is not limited by the code's expiry.

For `/auth/users/verification`, success returns status 200 with a `message` instructing the user to log in and finish setup. Missing email or code, an expired registration, an incorrect code, or exhausted attempts returns 400. No pending registration returns 404; conflicting existing account details return 409. These errors use the global response fields `timestamp`, `status`, `error`, `message`, and `path`. Repeating verification with a valid, unexpired code preserves the existing password when the account still has status `SETUP_REQUIRED` and its customer CPR matches the pending registration.

Setup-required accounts can log in and finish setup but cannot access other protected endpoints. Send `Authorization: Bearer <token>` when calling `/auth/users/setup`. The account and email come from the authenticated user; no email field is required in the request body. The account must have status `SETUP_REQUIRED` and a verified pending registration. Choose a new password different from the CPR. Successful setup sets `isActive` to true and status to `ACTIVE`.

All fields in this setup example are required:

```json
{
  "password": "your-new-password",
  "fName": "Saud",
  "lName": "Example",
  "phoneNumber": "12345678",
  "phoneNumberOpeningCode": "+973",
  "securityQuestion": "Your question",
  "securityQuestionAnswer": "Your answer"
}
```

Password reset endpoint
---

Requires an active account and `Authorization: Bearer <token>`, including for this reset route. Send JSON with `Content-Type: application/json`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/auth/users/forgetPassword` | Checks the customer's security answer, resets their password to CPR, sends an email, and returns a confirmation message. | `email`, `securityQuestionAnswer` | `{"email":"you@example.com","securityQuestionAnswer":"Your answer"}` |

The target must have a customer profile. Unknown email returns 404; an incorrect answer returns 401. These responses and the successful 200 response contain a `message` field. The security answer is trimmed and converted to lowercase before checking it against the stored hash.

The reset email is sent to the target customer's email address with subject `Reset Password`. Email sending failures return 500 through the global error handler, with fields `timestamp`, `status`, `error`, `message`, and `path`, and message `Could not send the email.`. The explicit password save occurs after the email is sent.

Change password endpoint
---

Requires an active account and `Authorization: Bearer <token>`. Send JSON with `Content-Type: application/json`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/auth/users/changePassword` | Changes the logged-in user's password and returns a success message. | `newPassword` | `{"newPassword":"NewPassword123"}` |

The validator requires 8 to 30 characters, at least one uppercase letter and one digit, and no whitespace. Invalid passwords return 422; the current error message incorrectly says the maximum is 20.

Update profile endpoint
---

Requires an active account and `Authorization: Bearer <token>`. Send `multipart/form-data` with a required JSON part named `request` using `Content-Type: application/json`, and an optional file part named `image`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| PUT | `/auth/users/updateProfile` | Updates profile details and returns `Profile updated successfully` with status 200. | `request` (JSON multipart part). Optional: `userId` (query parameter), `image` (file part). | `{"phoneNumber":"36001234","phoneNumberOpeningCode":"+973"}` (content of the `request` part) |

Without `userId`, updates the logged-in user's profile. Allowed JSON fields are `phoneNumber`, `phoneNumberOpeningCode`, `securityQuestion`, and `securityQuestionAnswer`. The security question and answer must be provided together and must not be blank. Either phone field can be supplied on its own; the resulting number and country code must be valid together. Omitted fields remain unchanged.

Supplying `userId`, for example `/auth/users/updateProfile?userId=2`, requires an `FAA_ADMIN` account with a linked FAA admin profile. This allows updates to the target user's phone details, image, `fName`, `lName`, `cpr`, `emailAddress`, and `active`. Security question and answer cannot be changed when `userId` is supplied. Even FAA admins must supply `userId` to change names, CPR, email, or account activation state.

Names must not be blank. CPR must contain exactly nine digits and be available; email must have a valid format and be available. Changing email or CPR requires the target account to have completed setup. Set `active` to `false` to deactivate the account and set its status to `DEACTIVATED`; `true` is rejected. Profile details and image updates require a linked customer, airline employee, or FAA admin profile.

To upload an image, save the JSON example to `profile.json` and send it as the `request` part:

```powershell
curl.exe -X PUT "http://localhost:8080/auth/users/updateProfile" -H "Authorization: Bearer YOUR_TOKEN" -F "request=@profile.json;type=application/json" -F "image=@profile.jpg"
```

Omit the `image` part to keep the current image. A nonempty uploaded file is saved under `uploads/` with a generated filename, and its path is stored in the profile's `imageUrl`.

Add airplane endpoint
---

Requires an active airline employee with airline role `ADMIN` and `Authorization: Bearer <token>`. Send JSON with `Content-Type: application/json`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/airlineAdmin/airplanes` | Adds a grounded airplane to the logged-in admin's airline, sends the admin a confirmation email, and returns an `AddAirplaneResponse` with status 201. | `registrationNumber`, `model`, `standardSeatCapacity`, `firstClassSeatCapacity`, `maxMileage` | `{"registrationNumber":"A9C-ABC","model":"Airbus A320","standardSeatCapacity":150,"firstClassSeatCapacity":12,"maxMileage":6000}` |

Registration number must be unique. Both seat capacities and max mileage must be greater than zero. Missing or invalid fields return 422; a duplicate registration number returns 404. Activation requires FAA approval.

The confirmation email is sent to the logged-in admin's email address with subject `Airplane Added` and includes the airplane's registration number and model.

Request airplane activation endpoint
---

Requires an active airline employee with airline role `ADMIN` and `Authorization: Bearer <token>`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/airlineAdmin/airplanes/{airplaneId}/requestActivation` | Creates a pending activation request, records the logged-in admin as its requester, sends a confirmation email, and returns an `AirplaneRequestResponse` with status 201. | `airplaneId` (path) | No body. |

The airplane must belong to the admin's airline and must not already be active. An existing pending request blocks submission; seven days must pass after the last request before requesting again. Connected FAA admins receive the `airplane-activation-requested` SSE event after the transaction commits. The airplane stays grounded until FAA approval.

The confirmation email is sent to the requesting admin's email address with subject `Activation Request Submitted` and includes the airplane's registration number and model. The requester is taken from the authenticated account, with no additional request fields needed.

Add flight endpoint
---

Requires an active airline employee with airline role `ADMIN` and `Authorization: Bearer <token>`. Send JSON with `Content-Type: application/json`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/airlineAdmin/airplanes/{airplaneId}/addFlight` | Creates an active flight for the admin's airline and returns an `AddFlightResponse` with status 201. | `airplaneId` (path), `originAirportIataCode`, `arrivalAirportIataCode`, `scheduledDeparture`, `scheduledArrival` | `{"originAirportIataCode":"BAH","arrivalAirportIataCode":"DXB","scheduledDeparture":"2027-01-10T10:00:00","scheduledArrival":"2027-01-10T12:00:00"}` |

Use existing, different airport IATA codes and local date-times without an offset. Departure must be in the future and arrival must be later. The airplane must belong to the admin's airline, be active, and have at least two hours between non-cancelled flights. The flight number is generated from the airline code and flight ID, and available seat counts start at the airplane's capacities.

Search flights endpoint
---

Requires an active account and `Authorization: Bearer <token>`. Supply filters as query parameters.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| GET | `/flights/search` | Returns a page of available flights matching the supplied filters. | None. Optional query parameters: `date`, `airlineCode`, `originAirport`, `destinationAirport`, `originCity`, `destinationCity`, `originCountry`, `destinationCountry`, `seatType`, `page`, `size`, `sort`. | No body. |

For example, `/flights/search?date=2027-01-10&originAirport=BAH&destinationAirport=DXB&seatType=standard&page=1&size=10&sort=scheduledDeparture,asc`.

Only flights with status `ACTIVE`, an active airplane, available seats, and scheduled departure more than five minutes away are returned. Filters can be combined; all supplied filters must match. Airline codes, airport IATA codes, cities, and countries use exact matches ignoring case. `date` filters scheduled departure by calendar day in `yyyy-MM-dd` format and must be today or later, using the server's local time. `seatType` must be exactly `standard` or `firstClass` and requires availability in that class; omitting it allows either class.

Pagination starts at `page=1` and defaults to `size=10`. Page must be at least 1, and size must be between 1 and 100. The default sort is `scheduledDeparture,asc`; supported fields are `scheduledDeparture`, `scheduledArrival`, and `flightNumber`, with direction `asc` or `desc`. Ties are ordered by flight ID ascending.

The response contains `content` (flight details), `page`, `size`, `totalElements`, and `totalPages`. Each flight includes its ID, flight number, airline code, origin and destination airport codes, cities and countries, scheduled times, and available seat counts for both classes. A page with no results returns an empty `content` list.

Book flight endpoint
---

Requires an active account and `Authorization: Bearer <token>`. Users can book for themselves; only an airline employee with airline role `ADMIN` can book for another user, and only on their own airline.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/customer/{userId}/flights/{flightId}/{seatType}/{seatId}/book` | Books the selected seat, reduces the available seat count, sends the booking owner a confirmation email, and returns a `BookingResponse` with status 201. | `userId`, `flightId`, `seatType`, `seatId` (all path parameters) | No body. |

For example, `/customer/1/flights/2/standard/3/book`. The user and flight must exist. `seatType` must be exactly `standard` or `firstClass`; `seatId` starts at 1 and must be within that class's capacity. The seat must be available, both flight and airplane must be active, and departure must be more than five minutes away. A successful booking has status `BOOKED` and a generated booking reference.

The email is sent to the booking owner's email address, including when an airline admin books on their behalf. Its subject is `Booking Confirmation`, and it includes the booking reference, flight number, seat, origin and destination airport IATA codes, and scheduled departure.

View bookings endpoint
---

Requires an active account and `Authorization: Bearer <token>`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| GET | `/bookings` | Returns the logged-in user's bookings, or all bookings for the logged-in airline admin's airline ordered by booking time descending. | None. | No body. |

Airline employees must have airline role `ADMIN` to use this endpoint. Other account roles receive their own booking list. The response is a list of booking objects.

Cancel booking endpoint
---

Requires an active account and `Authorization: Bearer <token>`. Only the booking owner or an airline employee with airline role `ADMIN` for the booking's airline can cancel it.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| DELETE | `/bookings/{bookingId}` | Marks a booking as `CANCELLED`, restores the available seat count for its class, emails the booking owner, and returns a `BookingResponse` with status 200. | `bookingId` (path) | No body. |

The booking must exist and have status `BOOKED`. Owners must cancel at least 48 hours before departure or receive 417; an admin of the booking's airline is exempt from this time limit. The booking record is retained.

The cancellation email is sent to the booking owner's email address, including when an airline admin cancels on their behalf. Its subject is `Booking Cancelled`, and it includes the booking reference and flight number.

Search bookings endpoint
---

Requires an active airline employee with airline role `ADMIN` and `Authorization: Bearer <token>`. Results are restricted to the logged-in admin's airline.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| GET | `/bookings/search` | Returns bookings matching the supplied filters, ordered by booking time descending. | None. Optional query parameters: `userId`, `flightId`, `status`. | No body. |

For example, `/bookings/search?userId=1&flightId=2&status=BOOKED`. Status must be `BOOKED`, `CANCELLED`, or `FINISHED`. Filters can be used individually or together; results must match all supplied filters. Omit all filters to retrieve every booking for the admin's airline.

Create airline endpoint
---

Requires an active `FAA_ADMIN` account and `Authorization: Bearer <token>`. Supply query parameters or form fields; this controller does not accept a JSON request body.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/faaadmin/airlines` | Creates and returns an airline. | `name`, `airlineCode`, `headquartersCountry` (query parameters or form fields) | No JSON body. |

For example, `POST /faaadmin/airlines?name=Example%20Air&airlineCode=EA&headquartersCountry=Bahrain`. The name and airline code must be unique. The current service does not validate missing or blank fields.

View airlines endpoint
---

Requires an active `FAA_ADMIN` account and `Authorization: Bearer <token>`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| GET | `/faaadmin/airlines` | Returns the list of all airlines. | None. | No body. |

Add airline admin endpoint
---

Requires an active `FAA_ADMIN` account and `Authorization: Bearer <token>`. Send JSON with `Content-Type: application/json`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/faaadmin/airlines/{airlineId}/addAirlineAdmin` | Creates an airline employee with airline role `ADMIN` and CPR as the initial password. Returns the submitted details with the security answer cleared, with status 201. | `airlineId` (path), `emailAddress`, `cpr`, `fName`, `lName`, `phoneNumberOpeningCode`, `phoneNumber`, `hireDate`, `salary`, `securityQuestion`, `securityQuestionAnswer` | See the complete JSON example below. |

The airline must exist, and email and CPR must be available. CPR must contain exactly nine digits, the email must have a valid format, and the phone number must be valid for its country code. Salary must be greater than zero; hire date uses `yyyy-MM-dd`. Names, security question, and security answer must not be blank.

All fields in this example are required:

```json
{
  "emailAddress": "admin@example.com",
  "cpr": "123456789",
  "fName": "Saud",
  "lName": "Example",
  "phoneNumberOpeningCode": "+973",
  "phoneNumber": "36001234",
  "hireDate": "2026-10-01",
  "salary": 1200,
  "securityQuestion": "Your question",
  "securityQuestionAnswer": "Your answer"
}
```

Pending airplane requests endpoint
---

Requires an active `FAA_ADMIN` account with a linked FAA admin profile and `Authorization: Bearer <token>`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| GET | `/faaadmin/airplaneRequests` | Returns a list of `AirplaneRequestResponse` objects for activation requests with status `PENDING`, with status 200. | None. | No body. |

Requests are returned across all airlines. An empty list is returned when no requests are pending. Fetch this endpoint after connecting or reconnecting to notifications to recover outstanding requests, and deduplicate by `requestId`.

Review airplane request endpoint
---

Requires an active `FAA_ADMIN` account with a linked FAA admin profile and `Authorization: Bearer <token>`. Send JSON with `Content-Type: application/json`.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| PUT | `/faaadmin/airplaneRequests/{requestId}` | Accepts or denies a pending activation request, records the reviewer and review time, emails the original requester, and returns an `AirplaneRequestResponse` with status 200. | `requestId` (path), `status`; `reviewReason` is required when denying. | `{"status":"ACCEPTED","reviewReason":"Safety checks passed"}` |

Status must be `ACCEPTED` or `DENIED`. Accepting sets the airplane to `ACTIVE`; denying sets it to `GROUNDED`. A denial requires a nonblank reason; acceptance defaults to `approve` when the reason is missing or blank. Reasons must not exceed 500 characters. The request must exist and still have status `PENDING`.

The review email is sent to the user stored as the request's `requestedBy`, with subject `Activation Request ACCEPTED` or `Activation Request DENIED`. It includes the airplane's registration number, model, decision, and review reason. The request must have a linked requester for this email step.

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

-------------------------------------------------------------------------------------------
Logging and audit logs
---

Application logs record login attempts and outcomes, verification events, important business operations, rejected requests, and errors. They use normal logger calls: `INFO` for operations, `WARNING` for rejected requests, and `SEVERE` for server errors. Unexpected errors use a generic message without a stack trace.

Audit entries are stored in the `audit_logs` table. Each entry contains `id`, `createdAt` (UTC), `userId`, `userRole`, `action`, `entityType`, `entityId`, and a readable `description`. `userId` identifies who performed the action; `entityId` identifies the affected record. Scheduled system actions have no user ID or role. Registration records the newly verified user as the person registering.

Audited actions include registration, account setup, profile updates, deactivation, password changes/resets, booking creation/cancellation, airline and airline admin creation, airplane creation, activation requests and reviews, flight creation, and automatic flight status changes. Repeating successful verification does not create another registration entry. Profile updates record accepted nonempty submissions, including values that were resubmitted unchanged.

Business database changes and their audit entries share a transaction. Email failures roll back the changes for booking creation/cancellation, password resets, airplane creation, and activation requests/reviews. An email already delivered cannot be rolled back, and uploaded profile images are outside the database transaction. Verification keeps incorrect-attempt counts when validation fails. Requesting a verification email retains its existing behavior: the pending registration can remain if sending fails.

Application success messages are written before the transaction commits, so a later commit failure can leave a success message in the application log. Audit entries roll back with the database changes. A scheduled flight-status run saves all its changes and audit entries in one transaction.

Logs and audit descriptions exclude passwords, JWTs/secrets, verification codes, CPRs, security answers, email bodies, and complete request/entity objects. Application timestamps follow the logging configuration; audit timestamps are explicitly UTC.

The existing `spring.jpa.hibernate.ddl-auto=update` setting creates the audit table when the application starts. There is no audit HTTP endpoint. Inspect recent entries in the database with:

```sql
SELECT * FROM audit_logs ORDER BY created_at DESC, id DESC LIMIT 50;
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
## Database seeding

See [Sample database reference](../SAMPLE-DATABASE.md) for login credentials, current
record IDs, airplane and flight details, bookings, and step-by-step manual tests.

The optional startup seeder supplies demo data and the fixtures required by the service tests.
Create an empty PostgreSQL database, configure the datasource in `application.properties`
or through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`, then run from PowerShell:

```powershell
$env:APP_SEED_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

Hibernate creates/updates the tables before the seeder runs. After the first successful
startup, stop the application and use `$env:APP_SEED_ENABLED = "false"` for normal runs.
Seeding is disabled by default. Use these known credentials only in a local demo/test database.

All seven seeded accounts use password **`TestPassword1`**, encoded by the application's
BCrypt `PasswordEncoder`. They start active, with zero failed login attempts and linked
profiles. The security question is “In which city were you born?” with answer `manama`
(also stored encoded).

| Email | Role | Airline |
| --- | --- | --- |
| flighttest.customer@mailsac.com | Customer | — |
| flighttest.faa@mailsac.com | FAA admin | — |
| flighttest.airline@mailsac.com | Airline admin | Gulf Air |
| sara.customer@mailsac.com | Customer | — |
| omar.customer@mailsac.com | Customer | — |
| layla.customer@mailsac.com | Customer | — |
| noor.airline@mailsac.com | Airline admin | Emirates |

An empty database receives two airlines, three airports (BAH, DXB, DOH), five airplanes,
five future flights, four bookings (three booked, one cancelled), and one pending
airplane activation request. Flight dates are relative to the initial seed run, starting
three days ahead. Available-seat counts exclude active bookings.

Test fixtures include booking **900001** (`TEST-BOOKING-900001`, seat `standard-1`),
pending request **900002** on a separate grounded airplane, and active airplane
**900003** (`TEST-FREE-PLANE`) with no flights. PostgreSQL identity sequences advance
past explicit IDs without moving backwards. `Test Created Airline`, `TCA`, and
`TEST-NEW-PLANE` are left for the tests to create.

Rerunning adds missing records without resetting passwords, bookings, seat counts,
flight dates, or approvals. Reserved IDs belonging to unrelated records cause startup
to fail instead of overwriting them. Unique-value conflicts also fail the transaction.
Seed inserts run in one transaction and send no emails; PostgreSQL sequence advances
are not rolled back. Use a fresh database to restore the original demonstration or
test fixtures after modifying them. Restarting does not refresh old flight dates.

To run the existing tests, configure a dedicated test database and seed it first.
The full service tests require working mail configuration: cancellation, airplane
addition, and approval tests send real emails to the Mailsac accounts, even though
their database transactions roll back. Stop the demo server before running them.

```powershell
$env:APP_SEED_ENABLED = "false"
.\mvnw.cmd "-Dtest=PasswordServiceTest,AuthenticationTest,BookingServiceTest,FAAAdminServiceTest,AirlineEmployeeServiceTest,FlightServiceTest" test
```

Aircraft use Airbus A320-200, Airbus A321neo, Boeing 787-9, and Boeing 777-300ER model names. Registrations, seat layouts, flight numbers, and schedules are illustrative sample data, not a representation of current airline fleets or timetables. The required TEST-FREE-PLANE and TEST-BOOKING-900001 identifiers remain for test compatibility.

