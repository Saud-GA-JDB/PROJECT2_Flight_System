> Identity extraction: all /auth/users endpoints now run on port 8081. Maintenance routes use 8083; SSE uses 8084; Aviation routes use 8080. Start/seed Identity first, then the flight application. See the identity-service README for configuration and recovery.

# Detailed API and operations guide

Cron Jobs
---

**Flight status update:** `FlightService.updateFlightStatus()` runs every 5 minutes using `@Scheduled(cron = "0 */5 * * * *")`.

- Changes `ACTIVE` to `IN_AIR` when `actualDeparture` is not null and its time has been reached.
- Changes `IN_AIR` to `CLOSED` when `actualArrival` is not null and its time has been reached.

Pilot actions immediately set status and actual times after locked operational checks. The scheduler invokes FlightStatusService through a separate service transaction per candidate and catches failures so later candidates still run. It locks/rechecks each Flight, skips/logs inconsistent or future actual times, and requires departure <= arrival <= now before closing. Null actual times and schedules alone do not move flights. It writes only FLIGHT_STATUS_CHANGED audits, never physical events, clearance or actual times. Checks use Asia/Bahrain.

Customer registration endpoints
---

Use these endpoints in order. Send JSON with `Content-Type: application/json`. Only setup requires the Bearer token returned by login.

| Method | Endpoint | What it does | Required fields | Sample JSON body |
| --- | --- | --- | --- | --- |
| POST | `/auth/users/register/email` | Checks email and CPR availability, saves a pending registration, and requests a verification email. | `email`, `cpr` | `{"email":"you@example.com","cpr":"123456789"}` |
| POST | `/auth/users/verification` | Verifies the code, creates an inactive customer account with status `SETUP_REQUIRED`, and returns a success message instructing the user to log in with CPR as their initial password. | `email`, `code` | `{"email":"you@example.com","code":"482193"}` |
| POST | `/auth/users/login` | Logs in using email and the current password (CPR is the initial password). Returns status 200 with the JWT in the response's `message` field. | `email`, `password` | `{"email":"you@example.com","password":"123456789"}` |
| POST | `/auth/users/setup` | Completes the logged-in customer's details, changes the password, activates the account, then deletes pending after saving successfully. | `password`, `fName`, `lName`, `phoneNumber`, `phoneNumberOpeningCode`, `securityQuestion`, `securityQuestionAnswer` | See the complete JSON example below. |

For `/auth/users/register/email`, use a valid email address and a CPR containing exactly nine digits. Success returns status 200 with `{"message":"Verification email requested."}`. Invalid input or an unexpired pending registration returns 400; an email or CPR already registered to an account returns 409. Notification/SMTP failures return 503 with message `Could not send the email.`. These errors use the global response fields `timestamp`, `status`, `error`, `message`, and `path`. The pending registration and synchronous Notification call share the Identity transaction, so a send failure rolls back the pending registration. SMTP acceptance cannot be undone after an uncertain timeout or a later database commit failure.

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

The reset email is sent to the target customer's email address with subject `Reset Password`. Notification/SMTP failures return 503 through the global error handler, with fields `timestamp`, `status`, `error`, `message`, and `path`, and message `Could not send the email.`. The explicit password save occurs after the email is sent.

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
curl.exe -X PUT "http://localhost:8081/auth/users/updateProfile" -H "Authorization: Bearer YOUR_TOKEN" -F "request=@profile.json;type=application/json" -F "image=@profile.jpg"
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
| POST | `/airlineAdmin/airplanes/{airplaneId}/addFlight` | Creates a PENDING_CREW flight for the admin's airline and returns an `AddFlightResponse` with status 201. | `airplaneId` (path), `originAirportIataCode`, `arrivalAirportIataCode`, `scheduledDeparture`, `scheduledArrival`, `firstClassPrice`, `standardPrice`; optional `currency` | `{"originAirportIataCode":"BAH","arrivalAirportIataCode":"DXB","scheduledDeparture":"2027-01-10T10:00:00","scheduledArrival":"2027-01-10T12:00:00","firstClassPrice":30000,"standardPrice":15000,"currency":"USD"}` |

Use existing, different airport IATA codes and local date-times without an offset. Departure must be in the future and arrival must be later. The airplane must belong to the admin's airline, be active, and have at least two hours between non-cancelled flights. The flight number is generated from the airline code and flight ID, and available seat counts start at the airplane's capacities. Supply positive firstClassPrice and standardPrice values and BHD or USD currency (omitted currency defaults to BHD). After assigning a pilot, secondary pilot, and flight attendant, call PUT /airlineAdmin/flights/{flightId}/activate for a PENDING_CREW flight owned by your airline with both actual times null, including delayed flights. Activation checks crew; inspection clearance is enforced at departure request/approval/takeOff.

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

An active, authenticated user opens `GET http://localhost:8084/notifications` with `Authorization: Bearer <JWT>` from Identity. This endpoint now belongs to the separate Notification application. See [Notification setup](../notification-service/README.md) for its service key and manual checks. Notification also owns SMTP email delivery; Aviation and Identity call its internal email endpoints through Feign.
The response is `text/event-stream` and stays open. User ID and role come from the JWT-authenticated
account, never from client-supplied parameters. Each tab/device gets its own connection.

Try it in PowerShell (replace the token with an FAA admin token):

```powershell
curl.exe -N -H "Authorization: Bearer YOUR_FAA_TOKEN" http://localhost:8084/notifications
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
.\identity-service\mvnw.cmd -f identity-service/pom.xml '-Dtest=PasswordServiceTest,AuthenticationTest' test
.\mvnw.cmd -f integration-tests/pom.xml '-Dtest=BookingServiceTest,FAAAdminServiceTest,AirlineEmployeeServiceTest,FlightServiceTest' test
```

Aircraft use Airbus A320-200, Airbus A321neo, Boeing 787-9, and Boeing 777-300ER model names. Registrations, seat layouts, flight numbers, and schedules are illustrative sample data, not a representation of current airline fleets or timetables. The required TEST-FREE-PLANE and TEST-BOOKING-900001 identifiers remain for test compatibility.


### Crew and payments

See the README for [crew assignment endpoints](../README.md#crew-assignment-endpoints) and [payment endpoints](../README.md#payment-endpoints), including current state restrictions and concurrency behavior.

### Maintenance endpoints

FAA admins create providers and their admin accounts. Airline admins contract providers and request maintenance for their own airplanes. Provider admins assign their technicians; only the assigned technician can complete a pending record.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/faaadmin/maintenanceProviders` | Create provider; JSON name and contactEmail |
| POST | `/faaadmin/maintenanceProviders/{providerId}/addMaintenanceAdmin` | Create provider admin account |
| POST | `/maintenanceAdmin/technicians` | Create technician within the admin's provider |
| POST | `/airlineAdmin/maintenanceProviders/{providerId}/contract` | Contract provider for the admin's airline |
| POST | `/airlineAdmin/airplanes/{airplaneId}/maintenanceRequests` | Request maintenance; JSON providerId and optional description |
| GET | `/maintenanceAdmin/maintenanceRequests` | List the provider's requests |
| POST | `/maintenanceAdmin/maintenanceRequests/{requestId}?technicianId=...` | Assign a technician |
| GET | `/maintenanceTechnician/maintenanceRecords` | List the technician's assignments |
| PUT | `/maintenanceTechnician/maintenanceRecords/{recordId}/complete?findings=...` | Complete work; findings optional |

A request starts PENDING without a technician. Assignment keeps it PENDING; completion changes it to COMPLETED. Maintenance staff cannot ground or activate airplanes, and completion does not change an inspection result. Owning airline/provider admins can cancel PENDING work with a reason; owning airline admins can read paged airplane history across providers. See Maintenance cancellation and airline history below.

Contract linking and request creation lock the provider before checking airline relationships. Assignment locks the maintenance record before the technician; completion locks the record. State checks and audit writes share the transaction. Multiple pending maintenance requests are allowed.

### Airport accounts and inspection endpoints

FAA admins create operators, link airports, and create airport admin accounts. Each airport admin manages one airport, even though the creation endpoint is named addAirportOperatorAdmin. Airport admins can create INSPECTOR or BAGGAGE_HANDLER employees at their own airport; the baggage workflow itself is deferred.

| Method | Endpoint | Purpose / caller |
| --- | --- | --- |
| POST | `/faaadmin/airportOperators` | FAA admin; JSON name and contactEmail |
| PUT | `/faaadmin/airports/{airportId}/operator?operatorId=...` | FAA admin links an operator |
| POST | `/faaadmin/airports/{airportId}/addAirportOperatorAdmin` | FAA admin creates an airport admin |
| POST | `/airportAdmin/employees?role=INSPECTOR` | Airport admin creates staff; also accepts BAGGAGE_HANDLER |
| POST | `/airportAdmin/flights/{flightId}/inspection?inspectorId=...` | Origin-airport admin assigns an inspector |
| GET | `/airportAdmin/inspections` | Admin lists inspections at their airport |
| GET | `/airportInspector/inspections` | Inspector lists their assignments |
| PUT | `/airportInspector/flights/{flightId}/inspections/{inspectionId}/complete?result=PASSED&reason=...` | Assigned inspector records PASSED or FAILED; reason optional |
| POST | `/airlineAdmin/flights/{flightId}/reinspection` | Owning airline admin requests another attempt after failure |
| GET | `/airlineAdmin/flights/{flightId}/inspections` | Owning airline admin lists the flight's inspection history |

Employee creation uses AddAirportEmployeeRequest JSON: fName, lName, cpr, emailAddress, phoneNumberOpeningCode, phoneNumber, password, securityQuestion, and securityQuestionAnswer. Airport/operator relationships are derived by the service. Airport linking and employee creation lock the airport before checking its operator. An airport with employees cannot change operators; attempting to link its current operator again is also rejected.

#### Inspection workflow

1. The origin-airport admin selects an INSPECTOR at the same airport and operator. The first assignment creates a PENDING inspection.
2. Only the assigned inspector can complete the latest attempt as PASSED or FAILED. The optional reason is stored as supplied, with a 2000-character limit, and completedAt is recorded.
3. After failure, the owning airline admin can request reinspection. This creates a new unassigned PENDING attempt while preserving the previous result and reason.
4. The airport admin assigns an inspector to the new attempt; it need not be the previous inspector.

Assignment cannot overwrite an assigned inspector or reopen a completed attempt. Reinspection is rejected if the latest attempt is pending or passed. Mutations require a flight in PENDING_CREW or ACTIVE status. There is no cross-flight defect carryover, and airport employees do not read maintenance records.

Lists return FlightInspectionResponse objects with id, flightId, airportId, nullable inspectorId, status, reason, createdAt, and completedAt, ordered by descending ID. A flight with no inspections returns an empty list.

#### Inspection concurrency and departure limits

Assignment, completion, and reinspection first lock the flight through findByIdForUpdate(), then read/check the latest attempt. This serializes competing requests, including the first assignment when no inspection row exists. Audit writes share the transaction; listing methods use ordinary reads. The PostgreSQL partial unique index flight_inspections_one_pending_per_flight additionally rejects two PENDING attempts for one flight while allowing completed history.

Inspection completion never changes FAA airplane status. Pilot take-off rechecks current pilot assignment, required crew, latest passed inspection, FAA ACTIVE, occupancy and valid departure approval under resource locks. Inspection mutations require PENDING_CREW or ACTIVE with both actual times null, including delayed flights. The scheduler only corrects status from consistent actual times; it does not create departure permission or actual timestamps.

### Database and integration-test notes

The flight inspection schema requires its foreign keys, status check, and one-pending-per-flight partial unique index. See the [README database setup](../README.md#2-configure-postgresql) for the index and audit-constraint requirements. The old crew-assignment-audit.sql omits newer audit values and must not be rerun unchanged on the current schema.

FlightInspectionServiceTest passed 16 PostgreSQL tests on 28 October 2026 with no failures/errors/skips. It creates its own fixtures with seeding disabled and schema validation enabled. Ordinary tests roll back; committed concurrency fixtures are cleaned up. Tests cover history, permissions, lists, competing assignments/completions/reinspection, and direct unique-index rejection. They do not cover HTTP routing, airport account creation end to end, or the later physical-action workflow.

```powershell
.\mvnw.cmd -f integration-tests/pom.xml -Dtest=FlightInspectionServiceTest test
```

MaintenanceRecordServiceTest passed five PostgreSQL tests on 27 October 2026 and also creates its own fixtures. The older seeded-test instructions above apply to their named suites, not these two classes. No application-wide concurrency-safety claim follows from these results.


## ATC setup and tower administration

All routes below require a bearer JWT, an active account and the stated role/profile. Setup writes and their audits commit together. These routes do not send email or SSE; flight-operation notifications belong to the clearance stage.

| Method | Path | Caller | Success |
| --- | --- | --- | --- |
| POST | `/faaadmin/airNavigationProviders` | FAA admin | 201 ProviderResponse |
| GET | `/faaadmin/airNavigationProviders` | FAA admin | 200 paginated providers |
| POST | `/faaadmin/airNavigationProviders/{providerId}/controlFacilities` | FAA admin | 201 FacilityResponse |
| GET | `/faaadmin/airNavigationProviders/{providerId}/controlFacilities` | FAA admin | 200 paginated provider towers |
| POST | `/faaadmin/controlFacilities/{facilityId}/admins` | FAA admin | 201 ATCEmployeeResponse, ADMIN |
| POST | `/atcAdmin/controllers` | Tower ADMIN | 201 ATCEmployeeResponse, CONTROLLER |
| GET | `/atcAdmin/controllers` | Tower ADMIN | 200 paginated own-tower controllers |
| GET | `/atc/facility` | Tower ADMIN or CONTROLLER | 200 own FacilityResponse |

Provider body:

```json
{"name":"Bahrain Navigation","contactEmail":"operations@example.com"}
```

Tower body:

```json
{"airportId":1,"name":"Bahrain Tower"}
```

Both staff-creation routes use:

```json
{
  "fName":"Ahmed","lName":"Ali","cpr":"123456789",
  "emailAddress":"ahmed@example.com","phoneNumberOpeningCode":"+973",
  "phoneNumber":"33123456","password":"ExamplePass123",
  "securityQuestion":"Example question?","securityQuestionAnswer":"example",
  "employeeNumber":"ATC-001"
}
```

The examples are synthetic. All staff fields are required. CPR is exactly nine digits; email, phone and password follow existing staff validation. Employee numbers are trimmed, uppercased and limited to 50 characters. Passwords and normalized security answers are encoded. Accounts start with role `ATC_EMPLOYEE`, status `ACTIVE` and `isActive=true`. They use the existing `/auth/users/login`, password and profile endpoints. Login returns the JWT in `message`.

Provider/tower names are trimmed, nonblank and at most 150 characters. Provider contact email is valid and at most 254 characters. Provider names/contact emails may repeat. Every tower has an existing provider and airport, with one tower per airport across all providers. FAA may create multiple admins for a tower. There are no transfer or deletion routes.

Validation stays in service code, matching existing DTOs. Unknown JSON fields are ignored by the existing mapper. Client-supplied role, facility, actor, status or timestamps do not override the values determined by the service and route. An admin's controller creation/listing derives the tower from the authenticated account; shared provider membership grants no wider access.

Responses contain only:

- Provider: `id`, `name`, `contactEmail`.
- Facility: `id`, `providerId`, `airportId`, `name`, `facilityType` (`TOWER`).
- Employee: `id`, `userId`, `facilityId`, `employeeNumber`, `fName`, `lName`, `emailAddress`, `atcRole`.

Operational responses contain no CPR, phone, passwords or security answers. Lists accept `page` (default 0, minimum 0) and `size` (default 20, range 1–100), sorted by entity ID ascending. Controller lists include only own-tower controller accounts, including inactive accounts; admins are excluded. Count and paging apply after scope filtering. There is no client-selected sort.

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

Invalid fields, nonpositive IDs and invalid pagination return 400; missing/invalid authentication returns 401; wrong role/profile/tower or inactive status is denied; missing references return 404; duplicate airport tower, email, CPR or employee number returns 409. Database lock conflicts roll back and return 409 with a refresh/retry message. Business errors retain `timestamp`, `status`, `error`, `message`, `path`; the existing security filter can return its own error shape. Cross-profile CPR uniqueness uses service checks, not a globally atomic database identity registry.

Setup audits use `AIR_NAVIGATION_PROVIDER_CREATED`, `CONTROL_FACILITY_CREATED`, and `ATC_EMPLOYEE_CREATED`, recording the actor and created entity. Failed/rolled-back commands leave no success audit. GETs do not create business audits.


## Flight-operation views and readiness

These GET endpoints are available to active accounts. They do not approve a clearance, move a flight, reserve resources, write business audits or send SSE. Clearance commands are documented below. Physical take-off and landing are documented below.

| GET path | Scope |
| --- | --- |
| `/pilot/flights` | Current primary/secondary pilot assignments |
| `/pilot/flights/{flightId}` | Currently assigned pilot |
| `/pilot/flights/{flightId}/readiness` | Currently assigned pilot; operation derives from phase |
| `/pilot/flights/{flightId}/controlEvents` | Currently assigned pilot; all flight events |
| `/atc/flights` | Flight originates or arrives at own tower airport |
| `/atc/flights/{flightId}` | Same airport scope |
| `/atc/flights/{flightId}/readiness?operation=DEPARTURE` | Origin tower for DEPARTURE, destination tower for ARRIVAL |
| `/atc/requests` | PENDING clearance cycles belonging to own tower |
| `/atc/flights/{flightId}/controlEvents` | Flight touches own airport; only own-tower events |
| `/airlineAdmin/flights/{flightId}/operations` | Owning-airline ADMIN |
| `/airlineAdmin/flights/{flightId}/controlEvents` | Owning-airline ADMIN; all flight events |

ATC ADMIN and CONTROLLER have equal read permissions. Shared provider or airline membership alone does not grant pilot/tower access. Removed pilots immediately lose assignment-based access. Other airline roles and FAA accounts have no global access to these operations.

Lists use `page=0`, `size=20` by default; page must be nonnegative and size 1–100. Flight lists accept optional `status`, `fromDate`, `toDate` (`yyyy-MM-dd` inclusive scheduled-departure dates in Asia/Bahrain). ATC flight lists additionally accept `operation=DEPARTURE|ARRIVAL` to select the own-origin/own-destination side. Requests/events accept optional `operation`. Invalid/reversed dates and invalid IDs/enums/paging return 400; missing flight 404; forbidden access 403; missing/invalid authentication 401. Readiness blockers return 200.

Scope filtering happens in the database before count/paging. Flights sort by scheduledDeparture ascending then id; requests by flight id ascending; events by event id descending. There is no client-selected sorting. Responses use the existing `content,page,size,totalElements,totalPages` envelope.

`FlightOperationSummary` includes flight/airline/airplane/airport IDs, flight number, scheduled/actual times, status, clearance, crew (`employeeId,duty`) and latest inspection (`id,status`, or null). It excludes passengers, bookings, payment data and staff identity details. History returns `id,flightId,facilityId,actorUserId,operation,eventType,cycle,revision,occurredAt,reason`.

```json
{
  "flightId":10,"operation":"DEPARTURE","evaluatedAt":"2026-11-07T07:00:00Z",
  "readyForRequest":false,"readyForApproval":false,"readyForAction":false,
  "clearanceRevision":0,
  "blockers":[
    {"code":"INSPECTION_NOT_PASSED","appliesTo":["REQUEST","APPROVAL","ACTION"]},
    {"code":"CLEARANCE_REQUIRED","appliesTo":["ACTION"]}
  ]
}
```

Readiness flags represent lifecycle eligibility, including direct approval eligibility, not permission for a pilot to perform ATC actions. Readiness is a snapshot; clearance commands recheck under their resource locks.

Departure requires ACTIVE with both actual times null, an airplane and both airports, origin tower, all three required crew duties, latest inspection PASSED and FAA airplane ACTIVE. Any airplane/assigned crew (including extras) occupied on another unfinished departed flight blocks readiness, even after its scheduled arrival. IN_AIR conservatively counts as occupied even with inconsistent actual times. A completed landing releases occupancy once the other flight is no longer IN_AIR. There is no scheduled-departure cutoff in these readiness checks; crew, activation and inspection mutations require both actual times null; booking and checkout retain their scheduled cutoffs and also reject actualDeparture.

Arrival requires IN_AIR, a departure time present and no later than the captured Bahrain time, no actual arrival and a configured destination tower. It does not require a fresh inspection, active FAA airplane status or free resources. Terminal pilot readiness has `operation:null` and all flags false.

Clearance starts NONE/cycle 0/revision 0. PENDING never expires and prevents another request; valid APPROVED prevents another request/approval. Action needs a matching tower/operation with unexpired approval. At `now >= expiresAt`, stored APPROVED is returned as effective EXPIRED without any write or event. REJECTED/WITHDRAWN/REVOKED/EXPIRED permit a fresh cycle; consumed DEPARTURE permits starting ARRIVAL while airborne. Readiness changes retain approval and its original expiry. Current clearance times and event times are UTC `Instant` JSON with `Z`; existing flight times remain Bahrain `LocalDateTime`. New seed schedules explicitly use Asia/Bahrain; existing imported schedules are not converted.

Blocker codes: `WRONG_FLIGHT_PHASE`, `ACTUAL_TIME_CONFLICT`, `TOWER_NOT_CONFIGURED`, `CREW_INCOMPLETE`, `INSPECTION_NOT_PASSED`, `AIRPLANE_NOT_ACTIVE`, `AIRPLANE_IN_USE`, `CREW_IN_USE`, `REQUEST_ALREADY_PENDING`, `CLEARANCE_ALREADY_APPROVED`, `CLEARANCE_REQUIRED`, `CLEARANCE_EXPIRED`, `CLEARANCE_OPERATION_MISMATCH`. Missing route references also block the flight phase checks; missing airplane blocks `AIRPLANE_NOT_ACTIVE`.

### Flight-operation schema additions

Stop the application and back up the database before applying these additions once to an existing ATC-enabled schema. Existing flights start with clearance NONE; actual flight times and historical rows are preserved. Apply before Hibernate update creates these columns/table, then verify with `ddl-auto=validate`. The SQL is included here with the API documentation. Do not rerun on an already-updated database.

```sql
BEGIN;
ALTER TABLE flights
    ADD COLUMN clearance_operation varchar(255),
    ADD COLUMN clearance_status varchar(255) NOT NULL DEFAULT 'NONE',
    ADD COLUMN clearance_facility_id bigint REFERENCES control_facilities(id),
    ADD COLUMN clearance_cycle bigint NOT NULL DEFAULT 0,
    ADD COLUMN clearance_revision bigint NOT NULL DEFAULT 0,
    ADD COLUMN clearance_approved_at timestamp(6) with time zone,
    ADD COLUMN clearance_expires_at timestamp(6) with time zone;
ALTER TABLE flights ADD CONSTRAINT flight_clearance_state_check CHECK (
    clearance_cycle >= 0 AND clearance_revision >= 0
    AND (clearance_operation IS NULL OR clearance_operation IN ('DEPARTURE', 'ARRIVAL'))
    AND ((clearance_status = 'NONE' AND clearance_cycle = 0 AND clearance_operation IS NULL
          AND clearance_facility_id IS NULL AND clearance_approved_at IS NULL AND clearance_expires_at IS NULL)
      OR (clearance_cycle > 0 AND clearance_operation IS NOT NULL AND clearance_facility_id IS NOT NULL
          AND ((clearance_status IN ('PENDING', 'REJECTED', 'WITHDRAWN') AND clearance_approved_at IS NULL AND clearance_expires_at IS NULL)
            OR (clearance_status IN ('APPROVED', 'REVOKED', 'CONSUMED') AND clearance_approved_at IS NOT NULL
                AND clearance_expires_at IS NOT NULL AND clearance_expires_at > clearance_approved_at))))
);
CREATE TABLE flight_control_events (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    flight_id bigint NOT NULL REFERENCES flights(id),
    facility_id bigint NOT NULL REFERENCES control_facilities(id),
    actor_user_id bigint NOT NULL REFERENCES users(id),
    operation varchar(255) NOT NULL CHECK (operation IN ('DEPARTURE', 'ARRIVAL')),
    event_type varchar(255) NOT NULL CHECK (event_type IN ('REQUESTED','APPROVED','REJECTED','WITHDRAWN','REVOKED','TAKE_OFF','LANDED')),
    cycle bigint NOT NULL CHECK (cycle > 0),
    revision bigint NOT NULL CHECK (revision > 0),
    occurred_at timestamp(6) with time zone NOT NULL,
    reason varchar(2000),
    UNIQUE (flight_id, revision)
);
CREATE INDEX control_events_flight_idx ON flight_control_events(flight_id,id);
CREATE INDEX control_events_facility_time_idx ON flight_control_events(facility_id,occurred_at);
CREATE INDEX IF NOT EXISTS crew_assignment_employee_idx ON crew_assignments(employee_id);
CREATE INDEX IF NOT EXISTS crew_assignment_flight_idx ON crew_assignments(flight_id);
COMMIT;
```

Control-event columns are not updatable through JPA and no edit/delete routes exist. No relationship cascades delete event history. The new read endpoints leave event and audit counts unchanged. A clean database still needs the existing base schema before these additions.

Verify on a dedicated PostgreSQL database using `mvn '-Dtest=FlightOperationServiceTest,ATCServiceTest,FlightInspectionServiceTest,MaintenanceRecordServiceTest,DatabaseSeederTest' '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/your_test_database' test`. Tests create isolated fixtures and roll back; existing ATC/inspection race tests commit and clean their own fixtures. The operational suite uses fixed time for exact expiry checks and real JWT requests for HTTP coverage.

### Clearance-command audit schema upgrade

After the flight-operation schema above is installed, stop the application and back up the database. Apply this audit-check upgrade before running clearance commands, then start with `ddl-auto=validate`. This preserves existing audit rows and adds the five clearance actions, FLIGHT_TAKE_OFF, FLIGHT_LANDED and event entity type. Reapply this block when upgrading from clearance-only commands; no additional table/column changes are needed for physical actions. Apply it to both existing and freshly initialized schemas; Hibernate validation alone does not verify check-constraint contents.

```sql
BEGIN;
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check CHECK (action IN (
    'BOOKING_CREATED',
    'BOOKING_CANCELLED',
    'USER_REGISTERED',
    'USER_SETUP_COMPLETED',
    'PROFILE_UPDATED',
    'USER_DEACTIVATED',
    'PASSWORD_CHANGED',
    'PASSWORD_RESET',
    'AIRLINE_CREATED',
    'AIRLINE_ADMIN_CREATED',
    'AIRPLANE_CREATED',
    'AIRPLANE_ACTIVATION_REQUESTED',
    'AIRPLANE_ACTIVATION_ACCEPTED',
    'AIRPLANE_ACTIVATION_DENIED',
    'FLIGHT_CREATED',
    'FLIGHT_STATUS_CHANGED',
    'CREW_ASSIGNMENT_CREATED',
    'CREW_ASSIGNMENTS_VIEWED',
    'CREW_ASSIGNMENT_REMOVED',
    'MAINTENANCE_PROVIDER_CREATED',
    'MAINTENANCE_PROVIDER_LINKED',
    'MAINTENANCE_EMPLOYEE_CREATED',
    'MAINTENANCE_RECORD_CREATED',
    'MAINTENANCE_TECHNICIAN_ASSIGNED',
    'MAINTENANCE_RECORD_COMPLETED',
    'AIRPORT_OPERATOR_CREATED',
    'AIRPORT_OPERATOR_LINKED',
    'AIRPORT_EMPLOYEE_CREATED',
    'FLIGHT_INSPECTOR_ASSIGNED',
    'FLIGHT_INSPECTION_COMPLETED',
    'FLIGHT_REINSPECTION_REQUESTED',
    'AIR_NAVIGATION_PROVIDER_CREATED',
    'CONTROL_FACILITY_CREATED',
    'ATC_EMPLOYEE_CREATED',
    'FLIGHT_CLEARANCE_REQUESTED',
    'FLIGHT_CLEARANCE_APPROVED',
    'FLIGHT_CLEARANCE_REJECTED',
    'FLIGHT_CLEARANCE_WITHDRAWN',
    'FLIGHT_CLEARANCE_REVOKED',
    'FLIGHT_TAKE_OFF',
    'FLIGHT_LANDED',
    'MAINTENANCE_CANCELLED'));
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_entity_type_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_entity_type_check CHECK (entity_type IN (
    'BOOKING',
    'USER',
    'AIRLINE',
    'AIRPLANE',
    'AIRPLANE_REQUEST',
    'FLIGHT',
    'CREW_ASSIGNMENT',
    'MAINTENANCE_PROVIDER',
    'MAINTENANCE_RECORD',
    'AIRPORT_OPERATOR',
    'AIRPORT',
    'FLIGHT_INSPECTION',
    'AIR_NAVIGATION_PROVIDER',
    'CONTROL_FACILITY',
    'FLIGHT_CONTROL_EVENT'));
COMMIT;
```

## Clearance commands

All six routes use POST, bearer JWT and active accounts. Pilots must currently hold PILOT or SECONDARY_PILOT duty; both have equal permission. ATC ADMIN and CONTROLLER act only at their own responsible tower: origin for DEPARTURE, destination for ARRIVAL. Sharing a provider grants no additional access.

| Path | Body | Success |
| --- | --- | --- |
| `/pilot/flights/{flightId}/requestTakeOff` | `{"expectedRevision":0}` | 201 |
| `/pilot/flights/{flightId}/requestLanding` | `{"expectedRevision":3}` | 201 |
| `/pilot/flights/{flightId}/clearance/withdraw` | `{"operation":"DEPARTURE","expectedRevision":1}` | 200 |
| `/atc/flights/{flightId}/clearance/decision` | `{"operation":"DEPARTURE","decision":"APPROVED","expectedRevision":1,"reason":null}` | 200 |
| `/atc/flights/{flightId}/clearance/direct` | `{"operation":"DEPARTURE","expectedRevision":0,"reason":null}` | 200 |
| `/atc/flights/{flightId}/clearance/revoke` | `{"operation":"DEPARTURE","expectedRevision":2,"reason":"Traffic conflict"}` | 200 |

IDs must be positive; expectedRevision is required and nonnegative. Decision accepts APPROVED or REJECTED only. Reasons are trimmed, at most 2000 characters, and required for rejection/revocation. Optional blank reasons become null. Actor, tower, state and timestamps come from the server; unknown JSON fields follow the existing ignored-field behavior.

Every success returns `OperationResponse`: flightId, flightNumber, status, actualDeparture, actualArrival, clearance and eventId. For example, after approving a pending departure request:

```json
{"flightId":10,"flightNumber":"SF101","status":"ACTIVE","actualDeparture":null,"actualArrival":null,
 "clearance":{"operation":"DEPARTURE","status":"APPROVED","facilityId":2,"cycle":1,"revision":2,
 "approvedAt":"2026-11-08T07:00:00Z","expiresAt":"2026-11-08T07:20:00Z"},"eventId":103}
```

A request starts a new PENDING cycle. Decisions and withdrawals require matching PENDING state. Direct approval approves a pending cycle or starts a fresh eligible cycle. Valid approval cannot be replaced or extended. Approval expires exactly 20 minutes after the captured action time; equality is expired. Pending requests never time out. Revocation requires an unexpired, unused matching approval and retains its timestamps. A fresh request/direct approval clears the old approval timestamps and increments cycle; every successful command increments revision once. Stale revisions return 409, including retries after a lost response: refresh the scoped GET before deciding what to do next.

Request and approval recheck readiness under locks. Reject/withdraw/revoke enforce phase, actual-time consistency, scope, revision and state without requiring inspection, crew, occupancy or FAA readiness. These commands never change physical flight status or actual times. Airborne arrival requests use the destination tower without fresh inspection, FAA ACTIVE or free-resource requirements. Physical actions and their integration guards are documented below.

Each success writes one append-only control event and one audit referencing its event ID, flight, cycle, revision and actor in the same transaction. Failed commands leave no successful event/audit. SSE uses the existing `/notifications` subscription after commit: `Clearance_Requested`, `Clearance_Approved`, `Clearance_Rejected`, `Clearance_Withdrawn`, `Clearance_Revoked`. Recipients are active User IDs for the responsible tower team, both current pilot duties and owning airline admins, deduplicated. Payload fields are eventId, flightId, operation, cycle, revision, status, occurredAt and expiresAt (approval timestamp only; null otherwise). Reasons remain available through scoped history. Delivery is best effort; reconnect with GET. Expiry sends no timed event.

Errors retain the existing ErrorResponse: 400 invalid input; 401 missing/invalid JWT; 403 role/scope/current assignment; 404 missing flight; 409 stale revision, lifecycle/readiness/expiry conflict, duplicate event revision or lock timeout/deadlock. No automatic command replay.

Demo using a ready flight: read its revision as the assigned pilot, requestTakeOff, read the pending request as the origin controller, approve with the returned revision, then revoke with a reason. Either pilot can request again and withdraw the new pending cycle. Try the destination tower against the departure command to see 403, and replay an old revision to see 409. The full take-off/landing demo below creates an airborne flight through the pilot endpoint.

Run clearance verification against a dedicated database with both schema sections applied:

```powershell
mvn '-Dtest=FlightClearanceServiceTest,FlightClearanceTransactionTest,FlightOperationServiceTest,ATCServiceTest' '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/your_test_database' test
```

Command tests cover service/JWT contracts, lifecycle and scope; transaction tests commit isolated fixtures, use the existing executor with independent PostgreSQL transactions, prove blocking through `pg_blocking_pids`, and clean their fixtures. They cover competing commands, fresh occupancy checks after resource waits, expiry after a lock wait, rollback, scoped after-commit SSE and delivery failure. Physical-action tests also cover revocation, expiry after resource waits, occupied crew, rollback, both-tower SSE and scheduler transaction isolation.

Verified on 8 November 2026: 85 targeted PostgreSQL tests passed with zero failures/errors/skips (29 clearance command/transaction tests plus 56 existing operation, ATC, inspection, maintenance and seeder tests). The test database used schema validation and the documented audit-check upgrade. Development data was not modified.


### Pilot physical actions

| POST path | Body | Success |
| --- | --- | --- |
| `/pilot/flights/{flightId}/takeOff` | `{"expectedRevision":2}` | 200 OperationResponse; IN_AIR, actualDeparture set, clearance CONSUMED |
| `/pilot/flights/{flightId}/land` | `{"expectedRevision":5}` | 200 OperationResponse; CLOSED, actualArrival set, clearance CONSUMED |

An active current primary or secondary pilot may act. Both routes acquire Airplane -> Flight -> all assigned employees ascending ID, then capture server time and recheck revision, phase and matching tower/operation approval. Approval is valid only before expiresAt, never at equality. takeOff repeats departure readiness (crew, latest PASSED inspection, FAA ACTIVE, origin tower and actual airplane/all-crew occupancy). land requires airborne phase, existing departure <= now and destination approval; it does not require a fresh inspection, active FAA state or free resources. Landing releases actual occupancy atomically. Scheduled departure does not limit pilot actions.

The captured Instant supplies UTC event time and Asia/Bahrain actual local time. Client timestamps are ignored. Actual times are never overwritten. Success increments revision once, preserves cycle and approval times, and commits Flight + one TAKE_OFF/LANDED event + one FLIGHT_TAKE_OFF/FLIGHT_LANDED audit together. No additional status-change audit is written for pilot actions. Invalid/missing revision is 400; missing/invalid JWT 401; wrong role/current assignment 403; missing flight 404; stale revision, repeat action, wrong phase/operation/tower, expiry/readiness or lock conflict 409. Refresh GET after a lost response.

After-commit `Flight_Departed` and `Flight_Landed` use the existing clearance notification payload (status CONSUMED, expiresAt null), scoped to deduplicated active User IDs for both airport tower teams, both current pilots and owning airline admins. A tower sharing the provider but neither airport is excluded. Failed writes produce no success SSE; delivery failures cannot undo the action. Reconnect GET is authoritative.

Crew add/remove and activation allow delays with both actual times null; existing qualification, airline, overlap and ACTIVE minimum-crew checks remain. Inspection assignment/completion/reinspection use the same actual-time guard and retain latest-attempt and inspector restrictions. Booking and checkout preparation also reject actualDeparture even if status is still ACTIVE. Existing payment reconciliation is unchanged. Scheduler status correction runs every five minutes with one Flight transaction at a time; a failed flight cannot roll back another flight's correction.

Demo (substitute the returned revision each time):

1. Schedule a flight, assign PILOT + SECONDARY_PILOT + FLIGHT_ATTENDANT, activate it and complete its latest inspection as PASSED. Its airplane must be FAA ACTIVE and all assigned resources free; configure origin and destination towers.
2. Pilot GET readiness, POST requestTakeOff revision 0 -> revision 1; origin ATC approves revision 1 -> revision 2.
3. Pilot POST takeOff revision 2 -> IN_AIR, CONSUMED, revision 3. Replaying revision 2 returns 409.
4. Either pilot POST requestLanding revision 3 -> revision 4; destination ATC approves revision 4 -> revision 5.
5. Either pilot POST land revision 5 -> CLOSED, CONSUMED, revision 6. History has six events with six corresponding audits. The scheduler adds no correction for this completed workflow.
6. On a separate ready flight, revoke approval before takeOff or wait until expiresAt; departure must fail. Use the wrong tower to see 403. Two approved flights sharing an airplane or crew cannot both depart before occupancy is released by landing.

Run the focused verification on the isolated database after applying the audit SQL above:

```powershell
mvn '-Dtest=FlightClearanceServiceTest,FlightClearanceTransactionTest,FlightOperationServiceTest,FlightInspectionServiceTest' '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/your_test_database' '-Dspring.jpa.show-sql=false' test
```

Verified 8 November 2026 on `aviation_operations_verify_20261107` with ddl-auto=validate: 82 tests passed, zero failures/errors/skips (FlightClearanceServiceTest 21, FlightClearanceTransactionTest 22, FlightOperationServiceTest 22, FlightInspectionServiceTest 17). This includes the new physical actions, delayed preparation guards, resource races, rollback/SSE and scheduler isolation. Existing ATC, maintenance and seeder suites were not rerun for this stage. Only the isolated verification database received the audit constraint update; development migration remains the operator's next startup step.


## Maintenance cancellation and airline history

### Maintenance cancellation schema upgrade

Stop the application and back up the database. This stage adds three nullable columns and a cancellation metadata constraint. Apply the audit-check SQL above again to include `MAINTENANCE_CANCELLED`, then run the following transaction before starting with `spring.jpa.hibernate.ddl-auto=validate`.

Inventory on 8 November 2026 found no CANCELLED records in development `aviation` or `aviation_operations_verify_20261107`. Recheck your target before upgrading:

```sql
SELECT id, status, completed_at
FROM maintenance_records WHERE status = 'CANCELLED' ORDER BY id;
```

The strict upgrade below intentionally fails and rolls back if legacy CANCELLED rows lack metadata. If your target has legacy cancellations, record the exact IDs and explicitly grandfather only those rows with all three cancellation fields null in the check; do not fabricate an actor, time or reason or exempt all future rows. The inspected databases need no exception.

```sql
BEGIN;
ALTER TABLE maintenance_records ADD COLUMN IF NOT EXISTS cancellation_reason varchar(2000);
ALTER TABLE maintenance_records ADD COLUMN IF NOT EXISTS cancelled_at timestamp(6) with time zone;
ALTER TABLE maintenance_records ADD COLUMN IF NOT EXISTS cancelled_by_user_id bigint REFERENCES users(id);
ALTER TABLE maintenance_records DROP CONSTRAINT IF EXISTS maintenance_cancellation_check;
ALTER TABLE maintenance_records ADD CONSTRAINT maintenance_cancellation_check CHECK (
    (status = 'CANCELLED'
        AND cancellation_reason IS NOT NULL AND length(btrim(cancellation_reason)) > 0
        AND cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL
        AND completed_at IS NULL)
    OR (status IN ('PENDING', 'COMPLETED')
        AND cancellation_reason IS NULL AND cancelled_at IS NULL AND cancelled_by_user_id IS NULL)
);
COMMIT;
```


### Endpoints and behavior

| Method/path | Caller | Input | Response |
| --- | --- | --- | --- |
| POST `/airlineAdmin/maintenanceRequests/{requestId}/cancel` | Active ADMIN of the airplane's airline | `{"reason":"Work is no longer required"}` | 200 MaintenanceRecordResponse |
| POST `/maintenanceAdmin/maintenanceRequests/{requestId}/cancel` | Active ADMIN of the record's provider | Same body | 200 MaintenanceRecordResponse |
| GET `/airlineAdmin/airplanes/{airplaneId}/maintenanceRecords` | Active ADMIN of the airplane's airline | Optional status PENDING/COMPLETED/CANCELLED; page=0, size=20 | 200 MaintenanceRecordListResponse |

Cancellation accepts assigned or unassigned PENDING work. The reason is trimmed and must contain 1–2000 characters. Actor and UTC cancelledAt come from the server; clients cannot choose them. The record retains provider, airplane, technician, description and findings; completedAt remains null. No FAA aircraft state or flight inspection is changed. Cancellation metadata is null on PENDING/COMPLETED responses. Existing provider and technician list responses include the new fields too.

```json
{
  "id":10,"airplaneId":2,"providerId":3,"technicianId":4,
  "description":"Inspect engine","findings":null,"status":"CANCELLED",
  "createdAt":"2026-11-08T10:00:00","updatedAt":"2026-11-08T11:00:00",
  "completedAt":null,"cancellationReason":"Work is no longer required",
  "cancelledAt":"2026-11-08T08:00:00Z","cancelledByUserId":25
}
```

History includes records from all providers even when the airline no longer has a contract. It uses zero-based pagination (size 1–100), descending createdAt then id, native LIMIT/OFFSET and separately scoped counts. Its concrete response contains content, page, size, totalElements and totalPages. Empty history or a page beyond the end returns empty content; counts still describe all filtered rows. History creates no audit/SSE and takes no write locks.

Cancellation, completion and assignment share the maintenance-record write lock. Each rechecks state after locking. First committed terminal action wins: repeated cancellation/completion, completion after cancellation, and assignment to a terminal record return 409 without changing findings or completion/cancellation metadata. Assignment retains record -> technician order. Cancellation does not require a current provider contract and cannot be performed by a technician. Missing IDs return 404, invalid IDs/reasons/paging/status 400, missing JWT 401, wrong account/role/airline/provider 403, and terminal state or lock conflicts 409.

A successful cancellation and one MAINTENANCE_CANCELLED audit commit together. After commit, `Maintenance_Cancelled` SSE sends `{recordId,status,cancelledAt}` to deduplicated active User IDs for owning airline admins, responsible provider admins and the assigned technician if present. Unrelated/inactive users are excluded. Reasons are retrieved through scoped reads. Rollback sends no success notification; existing SSE delivery remains best effort and reconnecting clients should GET history.

Demo: create two maintenance requests using the existing airline route. Cancel the first without assigning a technician as the airline admin. Assign the second through its provider admin, then cancel it through that admin's cancellation route. Read airline history with status=CANCELLED. Attempt completion as the assigned technician and expect 409; its findings/completedAt remain unchanged. On another request, complete first and then try cancellation to see the reverse 409. Try another provider or airline to see 403.

```powershell
mvn '-Dtest=MaintenanceRecordServiceTest,DatabaseSeederTest' '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/your_test_database' '-Dspring.jpa.show-sql=false' test
```

Verified 8 November 2026 on `aviation_operations_verify_20261107` with schema validation: 16 tests passed, zero failures/errors/skips (MaintenanceRecordServiceTest 15, DatabaseSeederTest 1). Coverage includes both cancellation routes, HTTP errors, cross-provider scoped history, current-account checks, record-lock races, atomic audit rollback and scoped after-commit SSE. Committed fixtures are cleaned up. The physical-flight suites were not rerun for this maintenance stage. Development `aviation` was inventoried read-only and has not received this migration.

## Email service extraction (12 November 2026)

Notification on 8084 owns SMTP and exposes service-key-protected POST `/internal/v1/emails` (200 after SMTP acceptance) and `/internal/v1/emails/async` (202, background send). The JSON body has `recipient`, `subject`, and `body`. Public business routes and message wording remain unchanged. Identity registration/reset calls wait for SMTP and return 503 with local rollback on failure. Aviation booking/airplane emails are sent after commit and failures are logged without undoing the action. Crew emails use the async endpoint after commit; only accepted background SMTP failures enter the existing in-memory three-hour retry list. See `notification-service/README.md` for configuration and test coverage.
