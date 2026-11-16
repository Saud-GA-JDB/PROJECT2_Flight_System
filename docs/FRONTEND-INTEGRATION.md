# Frontend integration guide

Contract reviewed on **16 November 2026**, against repository revision `6cc3e94`, with current-user, current-employee, flight pricing/seat and booking-detail endpoints updated for stages 1–4. Covers the implemented browser-facing API across all services. Controller, security, service and response code take precedence over older README/Postman examples. This is a source-verified contract, not a claim that every flow was exercised against a running deployment.

## Contents

- [Connection and conventions](#connection-and-conventions)
- [Current integration gaps](#current-integration-gaps)
- [Authentication and profiles](#authentication-and-profiles)
- [Flight search, booking and payment](#flight-search-booking-and-payment)
- [Airline administration](#airline-administration)
- [FAA administration](#faa-administration)
- [Airport inspections](#airport-inspections)
- [Maintenance](#maintenance)
- [Pilot and air traffic control](#pilot-and-air-traffic-control)
- [Response schemas](#response-schemas)
- [Errors](#errors)
- [Live notifications](#live-notifications)
- [React integration examples](#react-integration-examples)

## Connection and conventions

**Local API base URL: `http://localhost:8085`.** Append the paths below directly; there is no `/api` prefix. Use this gateway for all business endpoints, including login and notifications. Paths and enum values are case-sensitive: `/faaadmin` and `/airlineAdmin` deliberately use different casing.

- Send `Authorization: Bearer <JWT>` on protected requests. No cookie session or refresh-token endpoint exists.
- All endpoints require an active account except the four public Identity routes listed below, public payment return pages, authenticated account setup, and the authenticated current-user endpoint.
- JSON bodies use `Content-Type: application/json`. Routes marked **query** take URL parameters, not JSON; **none** means no body. Profile updates use multipart.
- Success bodies are not wrapped in a common envelope. They may be an object, an array, plain text, or empty (`204`). Error bodies also vary; see [Errors](#errors).
- IDs are JSON integers. `userId`, employee `id`, `airplaneId`, `flightId`, `bookingId`, and request IDs are different identifiers. Never substitute an employee ID for a user ID.
- All request fields listed are required unless marked optional. Omit unused optional query parameters rather than sending `null`, `undefined` or an empty enum value.
- Dates use `YYYY-MM-DD`. Scheduled/actual flight times and ordinary record timestamps use ISO local date-time strings such as `2027-01-10T10:00:00`, **without a timezone suffix**. Docker runs in `Asia/Bahrain`; flight action timestamps explicitly use that zone. Search/booking time checks use the server's local clock. Confirm the same zone in other deployments; do not silently interpret these strings as the browser's local timezone or convert them with `toISOString()`.
- Clearance/event timestamps and maintenance cancellation times use UTC ISO instants, e.g. `2027-01-10T07:00:00Z`.
- Flight prices and payment `amount` are positive integer **minor currency units**, passed directly to checkout: USD cents (`15000` = USD 150), BHD fils (`15000` = BHD 15). Supported currency values are `USD` and `BHD`.

### Pagination

Every paged response has `{content: T[], page, size, totalElements, totalPages}`. Empty results have `content: []`.

| Endpoint family | First page | Defaults | Limits / sorting |
| --- | --- | --- | --- |
| `/flights/search` | **1** | `page=1&size=10` | `page >= 1`, size 1–100; configurable sort below |
| Pilot/ATC flight lists, control events, navigation providers, facilities, controllers, airline maintenance history | **0** | `page=0&size=20` | `page >= 0`, size 1–100; no configurable sort |
| Bookings, airlines, airplane requests, crew, inspections, provider maintenance requests, technician assignments | Unpaged | — | Returns a raw array |

### Browser access and interactive API docs

No CORS configuration is currently present in the gateway or application code. A React app served on another port/origin needs a development proxy or backend/deployment CORS configuration before direct browser requests will work. A same-origin `/api` proxy example appears below; that prefix belongs to the frontend proxy, not the backend.

Swagger is served directly by each service, not aggregated through the gateway:

| API | Local Swagger UI |
| --- | --- |
| Identity | `http://localhost:8081/swagger-ui/index.html` |
| Aviation (flights, bookings, payments, FAA/airport/ATC) | `http://localhost:8080/swagger-ui/index.html` |
| Maintenance | `http://localhost:8083/swagger-ui/index.html` |

Their generated JSON is at `/v3/api-docs` on the same port. Generic response declarations mean Swagger alone does not describe every body accurately. Production needs a deployment-provided base URL; `localhost` refers to the frontend developer's own computer. Never call `/internal/**` or put service credentials in frontend code.

## Current integration gaps

These affect screen design and must be resolved with the backend team or explicitly supplied development fixtures. The endpoints below do **not** imply missing read APIs exist.

| Needed by a frontend | Current limitation |
| --- | --- |
| Show the recovery question | No security-question-read endpoint. Password recovery accepts an answer but does not return the question. Basic profile reads are available through `/auth/users/me`. |
| Browse airports, aircraft, employees and business relationships | No airport list, airplane list/detail, airline-owned flight list, airline employee list, airport employee list, maintenance provider list, maintenance technician list, airport operator list, or contract list. ATC provider/facility/controller lists do exist. Several administrative screens therefore require IDs supplied separately. |
| Continue immediately after creating an aircraft or flight | `AddAirplaneResponse` and `AddFlightResponse` contain **no numeric ID**. New airline-admin creation also returns no user/employee ID. These responses need enrichment for a complete create-and-manage flow. |
| Profile images | Upload succeeds with a text response; no image URL is returned, and no public image-serving route is configured. Upload support alone does not provide an avatar display API. |
| Refunds and restarting expired checkout | No refund or replacement-checkout endpoint. A paid booking cannot be cancelled through the current API. Expired payments cannot be restarted for the same booking. |
| Other workflows | No implemented flight cancellation/rescheduling, baggage handling, check-in, notification inbox or notification read/unread API. Enum values alone do not establish an available workflow. |

## Authentication and profiles

| Method and path | Access | Input | Success |
| --- | --- | --- | --- |
| `POST /auth/users/register/email` | Public | JSON `{email, cpr}` | `200` `{message: "Verification email requested."}` |
| `POST /auth/users/verification` | Public | JSON `{email, code}` | `200` `{message: string}` instructing login/setup |
| `POST /auth/users/login` | Public | JSON `{email, password}` | `200` `{message: "<JWT>"}` |
| `GET /auth/users/me` | Authenticated, including `SETUP_REQUIRED` | None | `200` `CurrentUser` |
| `POST /auth/users/setup` | Authenticated, `SETUP_REQUIRED` | Setup JSON below | `200` `AccountSetup` |
| `POST /auth/users/forgetPassword` | **Public** | JSON `{email, securityQuestionAnswer}` | `200` `{message: "Your password has been reset. Please check your email."}` |
| `POST /auth/users/changePassword` | Active account | JSON `{newPassword}` | `200` text `Success! New Password Has Been Set` |
| `PUT /auth/users/updateProfile` | Active account; FAA for target `userId` | Multipart described below | `200` text `Profile updated successfully` |

### Current user

After login or page reload, call `GET /auth/users/me` with the JWT to retrieve the current account and profile. Authenticated setup-required accounts may call this endpoint; use `status === "SETUP_REQUIRED"` to show account setup. Active employees then use the corresponding current-employee endpoint below for employee roles and organization IDs.

The endpoint takes no user ID: it reads the authenticated account. It returns `userId`, `emailAddress`, account `role`, `status`, `active`, `fName`, `lName`, `phoneNumberOpeningCode` and `phoneNumber`. Unfilled or missing profile fields are `null`. Passwords, security answers and CPR are not returned. Missing or invalid authentication returns `401`.

### Current employee

Use the account role from `/auth/users/me` to select the next request. Both employee endpoints require an active account and the same Bearer token. They take no body or user ID and return only the authenticated employee's information.

| Account role | Method and path | Success |
| --- | --- | --- |
| `AIRLINE_EMPLOYEE`, `AIRPORT_EMPLOYEE`, `ATC_EMPLOYEE` | `GET /employees/me` | `200 CurrentAviationEmployee` |
| `MAINTENANCE_EMPLOYEE` | `GET /maintenanceEmployees/me` | `200 CurrentMaintenanceEmployee` |
| `CUSTOMER`, `FAA_ADMIN` | No employee request needed | Use the account role from `/auth/users/me` |

Use account `role` and `employeeRole` together to select a dashboard: `ADMIN` alone does not identify an organization type. `employeeId` is distinct from `userId`. Employee information does not replace backend authorization checks on subsequent operations.

| Account role | `employeeRole` values | Organization fields |
| --- | --- | --- |
| `AIRLINE_EMPLOYEE` | `ADMIN`, `PILOT`, `FLIGHT_ATTENDANT`, `GATE_RECEPTION` | `airlineId` |
| `AIRPORT_EMPLOYEE` | `ADMIN`, `INSPECTOR`, `BAGGAGE_HANDLER` | `airportId`, `operatorId` |
| `ATC_EMPLOYEE` | `ADMIN`, `CONTROLLER` | `airportId`, `facilityId`, `providerId` (air navigation provider) |
| `MAINTENANCE_EMPLOYEE` | `ADMIN`, `TECHNICIAN` | `providerId` (maintenance provider) |

The Aviation response includes all organization fields above; fields that do not apply to that employee are `null`. Maintenance returns only `employeeId`, `employeeRole` and `providerId`.

Errors: `401` missing or invalid authentication; `403` authenticated inactive/setup-required account, incorrect account role, or incomplete employee assignment; `404` no employee record linked to the authenticated account. An account rejected during authentication returns `401` instead of reaching the active-account check.

### Customer registration sequence

1. Request verification with a valid email and a **string of exactly nine digits** for `cpr` (preserve leading zeros).
2. Submit the six-digit email code as a string. It expires after 10 minutes and allows three incorrect attempts. No separate resend route exists: call registration/email again after expiry. An unexpired pending registration blocks a new code, even if attempts are exhausted.
3. Log in with the email and CPR as the initial password. Verification itself does not return a token. The account can authenticate but is not yet active.
4. Use that token to submit setup. Setup is tied to the logged-in account; do not send an email, code or pending-registration ID.

```json
{
  "password": "ExamplePassword1",
  "fName": "Sara",
  "lName": "Ahmed",
  "phoneNumber": "36001234",
  "phoneNumberOpeningCode": "+973",
  "securityQuestion": "In which city were you born?",
  "securityQuestionAnswer": "Manama"
}
```

Names and question/answer must be nonblank. The phone number and opening code must form a valid number together. Password rules: **8–30 characters, at least one uppercase letter, at least one digit, no whitespace**. Setup password must differ from CPR. Successful setup returns status `ACTIVE`, `active: true`; the existing token can then be used for protected operations. Verified setup is not limited by the email code's remaining lifetime.

Registration/verification failures: `400` invalid input, expired/incorrect code, attempts exhausted or unexpired registration; `404` no pending registration; `409` email/CPR already registered; `503` verification email delivery failed. Setup validation failures are `400`.

### Login and password behavior

- Wrong credentials or unavailable accounts return `401`. The third failed password attempt for a known account, and subsequent attempts that day, return `429`: `Too many failed login attempts. Try again after midnight Bahrain time.` Successful login resets the count. Do not automatically retry credentials.
- `DEACTIVATED` accounts cannot log in. Inactive accounts can log in while `SETUP_REQUIRED`; employee accounts awaiting activation cannot log in yet. Employee creation can succeed before account activation finishes; allow for a short delay before their first login.
- JWT lifetime is deployment-configured (example default: 24 hours). Use `exp` to anticipate expiry, but the server remains authoritative. There is no refresh or server logout endpoint; logout clears the frontend session and closes SSE.
- Password recovery is public in current code, despite older documentation. Unknown email, wrong/missing answer and the daily limit all return the same `401` message-only JSON: `Email or security question answer is incorrect, or the daily reset limit has been reached.` Three wrong answers block further resets until the next Bahrain calendar day.
- Recovery trims and lowercases the answer, resets the password to the profile's CPR and emails the user. It does not reactivate an account or clear a separate login lockout. Log in and change the password after recovery. Missing usable profile returns `400`; email failure returns `503`.
- Invalid change-password input returns **`422` plain text**, unlike setup's `400` JSON error.

### Profile updates

Send `multipart/form-data`, with a required part **`request` containing JSON with its own `application/json` content type**, and optional file part `image`. Do not manually set the outer multipart Content-Type; the browser must supply its boundary.

Self-update JSON fields (all optional): `phoneNumber`, `phoneNumberOpeningCode`, `securityQuestion`, `securityQuestionAnswer`. Supply question and answer together and nonblank. Either phone field may be updated alone; the resulting pair must be valid. Omitted/null fields retain current values.

FAA admins may supply query `?userId=123` to update a target. In this mode JSON may contain `fName`, `lName`, `cpr`, `emailAddress`, `phoneNumber`, `phoneNumberOpeningCode`, `active`; **security fields are forbidden**. Changing names/CPR/email/active requires the explicit `userId`, even for an FAA admin editing themselves. `active` accepts only `false` (deactivation); reactivation is not supported. Email and CPR must be valid and unique, and cannot be changed before setup completes.

Images must decode as an image; they are saved as PNG. Invalid images return `400`, oversized uploads `413`; upload size limits are deployment-dependent. An omitted or empty image keeps the existing image. The API does not return the resulting image URL.

## Flight search, booking and payment

### Flight search

`GET /flights/search` — **active account required**, no body, `200 Page<FlightSearchItem>`.

| Optional query | Accepted value |
| --- | --- |
| `date` | `YYYY-MM-DD`, today or later by server clock; scheduled-departure day |
| `airlineCode` | Airline code, e.g. `GF` |
| `originAirport`, `destinationAirport` | Airport IATA code, e.g. `BAH`, `DXB` |
| `originCity`, `destinationCity`, `originCountry`, `destinationCountry` | Exact text match ignoring case |
| `seatType` | Exactly `standard` or `firstClass`; omit to allow either class |
| `page`, `size` | One-based page; defaults 1 and 10; size max 100 |
| `sort` | `scheduledDeparture,asc` (default), `scheduledArrival,asc`, `flightNumber,asc`; each also supports `desc` |

All supplied filters must match. Codes/cities/countries match ignoring case, without substring search. Only `ACTIVE` flights with an `ACTIVE` airplane, no actual departure, available seats, and scheduled departure more than five minutes away qualify. A search result is not a guarantee that booking will succeed. Ties sort by flight ID ascending. Bad date, seat type, paging or sort returns `400`.

Each result includes `firstClassPrice`, `standardPrice` and `currency`. Prices are integer minor units as described above. Remaining seat counts are not capacities or the highest valid seat IDs; use flight details for seat selection.

Example: `/flights/search?originAirport=BAH&destinationAirport=DXB&date=2027-01-10&seatType=standard&page=1&size=10`.

### Flight details and seat selection

`GET /flights/{flightId}` — **active account required**, no body, `200 FlightDetail`.

Returns `flight` (the same fields as a search result, including prices), `firstClassSeatsCapacity`, `standardSeatsCapacity`, `availableFirstClassSeats` and `availableStandardSeats`. Available seats are numeric IDs starting at 1 within each class, sorted ascending. For example, `availableStandardSeats: [2, 4, 5]` allows selecting `seatType=standard` and `seatId=4` for the existing booking endpoint. These are seat IDs, not physical row/aisle positions.

Seats with a `BOOKED` booking are excluded; cancelled bookings do not block seats. A class with no remaining seats returns an empty array. A sold-out flight can still return details with empty availability arrays if the flight and airplane are active, actual departure is null, and scheduled departure is more than five minutes away.

Availability is a snapshot and does not reserve seats. Booking rechecks the flight, capacity and selected seat. Refresh flight details after a booking succeeds or a seat is rejected. No passenger details or booking references are included.

Errors: `400` invalid flight ID, inactive/closed/departed flight, inactive or missing airplane, or departure within five minutes; `404` flight not found; `401` missing/invalid authentication; `403` authenticated account without active-account access.

### Booking routes

| Method and path | Input | Access / result |
| --- | --- | --- |
| `POST /customer/{userId}/flights/{flightId}/{seatType}/{seatId}/book` | None; all four values are path parameters | Book for self; own-airline admin may book for another user. `201 Booking` |
| `GET /bookings` | None | Own bookings; airline admins get their airline's bookings. Non-admin airline employees are denied this list. `200 BookingSummary[]` |
| `GET /bookings/search` | Optional query `userId`, `flightId`, `status` | Airline admin only, scoped to own airline. `200 BookingSummary[]` |
| `GET /bookings/{bookingId}` | None | Booking owner or admin of the booking's airline. `200 BookingSummary` |
| `DELETE /bookings/{bookingId}` | None | Owner or admin of booking's airline. `200 Booking` |

Use seat type `standard` or `firstClass` and an integer seat ID starting at 1, within that class's aircraft capacity. Stored `seatNumber` looks like `standard-3`. Booking requires an active flight/aircraft and departure more than five minutes away. Taken/out-of-range seats, closed booking, and sold-out classes return `400`; missing user/flight `404`; unauthorized booking for another user `403`. Refresh availability after a rejected or successful booking. A booking immediately becomes `BOOKED`; that status does **not** mean paid.

Booking search accepts `BOOKED`, `CANCELLED`, `FINISHED`, combining all supplied filters. Admin lists/search sort by booking time descending; do not assume a particular ordering for a customer's own list.

Booking lists, search and detail return the same enriched `BookingSummary`: booking ID, timestamps, reference, seat and status, plus `userId`, `flightId`, `flightNumber`, `airlineCode`, origin/destination airport codes, scheduled/actual departure and arrival, and `flightStatus`. `status` is the booking status; `flightStatus` is the flight status. Actual times may be `null`.

Use `GET /bookings/{bookingId}` when opening or reloading a booking screen. The owner can read their own booking regardless of account role; reading someone else's booking requires an airline admin from the booking's airline. Existing list rules still deny non-admin airline employees. Cancelled/finished bookings and departed/closed flights remain readable. Flight fields reflect the linked flight's current stored information, not an immutable snapshot taken when booked.

Detail errors: `400` invalid booking ID; `404` booking not found; `403` unauthorized caller or authenticated account without active-account access; `401` missing/invalid authentication.

Payment information is retrieved separately through `GET /payments/bookings/{bookingId}`: the booking owner receives payment ID, amount, currency and status when a payment exists (`404` if none exists). An airline admin viewing someone else's booking cannot use that owner's payment endpoint. The booking response does not contain payment fields, and `BOOKED` does not imply paid.

Cancellation requires `BOOKED` (`400` otherwise). An owner must cancel at least 48 hours before scheduled departure; own-airline admins bypass that time limit. Too-late cancellation is **`409` plain text**, not the `417` described in older documentation. Existing `PENDING` payment blocks cancellation (`409`); `PAID` requires a refund and also blocks cancellation (`409`). Refresh payment status to record checkout expiry before retrying cancellation. Successful cancellation retains the record and restores the seat.

### Payments (hosted Stripe sandbox checkout)

The current implementation accepts **sandbox sessions only**; live-mode sessions are rejected. The frontend uses the returned hosted URL and never needs a Stripe secret key or to collect card details itself.

| Method and path | Access / input | Success |
| --- | --- | --- |
| `POST /payments/bookings/{bookingId}/checkout` | Active booking owner only; no body | `200 Checkout` |
| `GET /payments/bookings/{bookingId}` | Active booking owner only; no body | `200 Payment`; refreshes status from payment provider |
| `GET /payments/success` | Public; no body | `200` text: request payment status to verify completion |
| `GET /payments/cancel` | Public; no body | `200` text: payment has not been confirmed |

Flow: create booking → request checkout → preserve `bookingId` across navigation → redirect the browser to non-null `checkoutUrl` → on return, GET payment status → display paid only when `status === "PAID"`. Do not treat visiting a success URL as payment proof. The return URLs are configured on the backend and cannot be supplied in the checkout body; arrange frontend return URLs with the backend team. The built-in return routes are text endpoints, not React screens.

Checkout reuses an existing pending session. `checkoutUrl` can be `null` when the session is no longer open; fetch payment status rather than redirecting blindly. Payment must start for a `BOOKED` booking on an `ACTIVE`, not-yet-departed flight more than five minutes before departure. Neither an airline admin nor another user can pay for someone else.

Errors: `400` invalid booking/flight state, already paid, expired payment, invalid price, or checkout not yet saved; `403` not owner; `404` booking/payment missing; `409` competing checkout or a payment needing verification; `502` payment-provider failure; `503` provider temporarily unavailable. Expired payments cannot be restarted on the same booking. There is no refund API. Payment status changes are obtained by these calls; no frontend webhook subscription exists.

## Airline administration

All routes in this section require account role `AIRLINE_EMPLOYEE`, airline role `ADMIN`, and ownership of the relevant airline's resources.

| Method and path | Input | Success |
| --- | --- | --- |
| `POST /airlineAdmin/employees` | Query `role=PILOT` or `FLIGHT_ATTENDANT`; `AirlineEmployeeInput` JSON | `201 AirlineEmployee` |
| `POST /airlineAdmin/airplanes` | Aircraft JSON below | `201 AddAirplane` |
| `POST /airlineAdmin/airplanes/{airplaneId}/requestActivation` | None | `201 AirplaneRequest` |
| `POST /airlineAdmin/airplanes/{airplaneId}/addFlight` | Flight JSON below | `201 AddFlight` |
| `POST /airlineAdmin/flights/{flightId}/addCrewAssignment` | JSON `{employeeId, duty}` | `201 CrewAssignment` |
| `GET /airlineAdmin/flights/{flightId}/crewAssignments` | None | `200 CrewListItem[]` |
| `DELETE /airlineAdmin/flights/{flightId}/crewAssignments/{employeeId}` | None; employee ID, **not assignment ID** | `204`, no body |
| `PUT /airlineAdmin/flights/{flightId}/activate` | None | `200` text `Flight activated successfully.` |
| `POST /airlineAdmin/flights/{flightId}/reinspection` | None | `201` text containing inspection ID |
| `GET /airlineAdmin/flights/{flightId}/inspections` | None | `200 Inspection[]` |
| `GET /airlineAdmin/flights/{flightId}/operations` | None | `200 FlightOperationSummary` |
| `GET /airlineAdmin/flights/{flightId}/controlEvents` | Optional query `operation`, `page`, `size` | `200 Page<ControlEvent>` |

Maintenance routes under `/airlineAdmin` are listed in [Maintenance](#maintenance).

Aircraft request body:

```json
{"registrationNumber":"A9C-ABC","model":"Airbus A320","standardSeatCapacity":150,"firstClassSeatCapacity":12,"maxMileage":6000}
```

Registration/model must be nonblank; both seat capacities and max mileage must be positive. Invalid values return `422` text; duplicate registration returns `409` text. New aircraft are `GROUNDED`. Requesting activation requires a non-active aircraft, no pending activation request and at least seven days since the last request (`400` otherwise). FAA acceptance is needed before scheduling a flight.

Flight request body:

```json
{
  "originAirportIataCode":"BAH",
  "arrivalAirportIataCode":"DXB",
  "scheduledDeparture":"2027-01-10T10:00:00",
  "scheduledArrival":"2027-01-10T12:00:00",
  "firstClassPrice":30000,
  "standardPrice":15000,
  "currency":"USD"
}
```

Use existing, different airport IATA codes. Departure must be future and arrival later. Prices must be positive integer minor units; currency defaults to `BHD` when omitted (explicit null is invalid). Aircraft must be active and have at least a two-hour gap from other non-cancelled flights. Invalid scheduling/prices/availability return `400`. New flights start `PENDING_CREW`; flight number is generated.

Crew `duty` is `PILOT`, `SECONDARY_PILOT` or `FLIGHT_ATTENDANT`. Both pilot duties require a `PILOT` employee; attendant duty requires a `FLIGHT_ATTENDANT`. Employees must belong to the airline and cannot have overlapping flight assignments. The same employee cannot be added twice to a flight. Crew mutations are limited to `PENDING_CREW` or `ACTIVE` flights with both actual times null. Removing crew from an active flight must preserve all three required duties; add a replacement first. Rule failures generally return `400` (some ownership failures here also use `400`, not `403`).

Activate only a `PENDING_CREW` flight with no actual departure/arrival and all three duties assigned. Activation makes it bookable; it does not grant take-off clearance. A passed inspection is checked separately during departure clearance and take-off. Delayed flights can still be activated when these state conditions hold.

### Employee request bodies

`EmployeeInput` (airport, maintenance and ATC base fields):

```json
{
  "fName":"Ahmed",
  "lName":"Ali",
  "cpr":"123456789",
  "emailAddress":"employee@example.com",
  "phoneNumberOpeningCode":"+973",
  "phoneNumber":"36001234",
  "password":"ExamplePassword1",
  "securityQuestion":"In which city were you born?",
  "securityQuestionAnswer":"Manama"
}
```

| Request schema | Fields in addition to / differing from base |
| --- | --- |
| `AirlineEmployeeInput` | Add `hireDate` (`YYYY-MM-DD`) and positive integer `salary` |
| `AirlineAdminInput` | Same fields as AirlineEmployeeInput but **omit password**; initial password is CPR |
| `MaintenanceEmployeeInput` | Add `licenseNumber`: required for technicians, optional for provider admins |
| `ATCEmployeeInput` | Add required `employeeNumber` (max 50 characters; normalized to uppercase; unique) |

All base fields are required, except password is absent for AirlineAdminInput. Names/question/answer must be nonblank, CPR exactly nine digits, email valid and available, CPR available, phone valid, password follow the rules above. Ordinary employee text fields have a 255-character limit (ATC email max 254). Maintenance license max 255. IDs/organization/employee roles are derived from the route and signed-in admin; do not add them to JSON. Validation generally returns `400`, duplicates `409`, wrong scope `403`, missing parent resource `404`.

## FAA administration

All routes require active account role `FAA_ADMIN`.

| Method and path | Input | Success |
| --- | --- | --- |
| `POST /faaadmin/airlines` | **Query or form fields** `name`, `airlineCode`, `headquartersCountry`; no JSON | **`200 Airline`** |
| `GET /faaadmin/airlines` | None | `200 Airline[]` |
| `POST /faaadmin/airlines/{airlineId}/addAirlineAdmin` | `AirlineAdminInput` JSON | `201` submitted fields, with `securityQuestionAnswer: null`; no new ID |
| `GET /faaadmin/airplaneRequests` | None | `200 AirplaneRequest[]`, pending requests across airlines |
| `PUT /faaadmin/airplaneRequests/{requestId}` | JSON `{status, reviewReason?}` | `200 AirplaneRequest` |
| `POST /faaadmin/airportOperators` | JSON `{name, contactEmail}` | `201 Organization` |
| `PUT /faaadmin/airports/{airportId}/operator` | Query `operatorId`; no body | `200` text `Airport linked to operator successfully.` |
| `POST /faaadmin/airports/{airportId}/addAirportOperatorAdmin` | `EmployeeInput` JSON | `201 AirportEmployee` |
| `POST /faaadmin/airNavigationProviders` | JSON `{name, contactEmail}` | `201 Organization` |
| `GET /faaadmin/airNavigationProviders` | Optional query `page`, `size` | `200 Page<Organization>` |
| `POST /faaadmin/airNavigationProviders/{providerId}/controlFacilities` | JSON `{airportId, name}` | `201 Facility` |
| `GET /faaadmin/airNavigationProviders/{providerId}/controlFacilities` | Optional query `page`, `size` | `200 Page<Facility>` |
| `POST /faaadmin/controlFacilities/{facilityId}/admins` | `ATCEmployeeInput` JSON | `201 ATCEmployee` |

FAA maintenance provider/admin routes are in the next maintenance table.

- Airline fields must be nonblank (`400`); duplicate airline name/code returns `409`. Example: `POST /faaadmin/airlines?name=Example%20Air&airlineCode=EA&headquartersCountry=Bahrain`.
- Airplane review accepts only `ACCEPTED` or `DENIED`; denial requires a nonblank reason, max 500 characters. Acceptance with blank/omitted reason stores `approve`. Only pending requests can be reviewed (`400` otherwise). Acceptance activates the aircraft; denial grounds it.
- Airport operators require nonblank name and valid contact email, each max 255. An airport needs an operator before its admin can be created. Linking its existing operator again, or changing operator while employees exist, returns `400`. Despite the route name, `addAirportOperatorAdmin` creates an admin for **one airport**.
- Navigation provider name max 150, contact email max 254, both required. Facility name max 150; `airportId` must be positive and exist. Each airport can have only one tower (`409` duplicate). Facility type is always `TOWER`; do not send a type field. Providers/facilities are paginated from zero.

## Airport inspections

Airport admins are `AIRPORT_EMPLOYEE` / `ADMIN`; inspectors are `AIRPORT_EMPLOYEE` / `INSPECTOR`.

| Method and path | Caller / input | Success |
| --- | --- | --- |
| `POST /airportAdmin/employees` | Airport admin; query `role=INSPECTOR` or `BAGGAGE_HANDLER`, `EmployeeInput` JSON | `201 AirportEmployee` |
| `POST /airportAdmin/flights/{flightId}/inspection` | Origin-airport admin; query `inspectorId`, no body | `200` text `Inspector assigned successfully. Inspection ID: <id>` |
| `GET /airportAdmin/inspections` | Airport admin; no body | `200 Inspection[]` for own airport |
| `GET /airportInspector/inspections` | Inspector; no body | `200 Inspection[]` assigned to self |
| `PUT /airportInspector/flights/{flightId}/inspections/{inspectionId}/complete` | Assigned inspector; query `result=PASSED` or `FAILED`, optional `reason`; no body | `200` text `Inspection completed with result <result>.` |

Initial assignment creates a `PENDING` inspection. The inspector must belong to the same airport and operator. Assignment cannot replace an already assigned inspector. Only the latest pending attempt can be completed. `reason` is optional even for failure, max 2000 characters, stored as supplied.

After failure, the owning airline admin calls the reinspection route in the airline table. It creates a new **unassigned** pending attempt; the airport admin must assign it. Reinspection is rejected if the latest attempt is missing, pending or passed. Earlier results remain in history. Lists sort by ID descending.

Assignment, completion and reinspection require `PENDING_CREW` or `ACTIVE`, with both actual times null. A passed scheduled departure does not by itself prevent an inspection on a delayed flight. Invalid state/assignment/result returns `400`; wrong caller/airport/inspector `403`; missing record `404`; competing operations may return `409`. Inspection completion does not activate/ground an aircraft or create flight clearance.

## Maintenance

All routes use the same gateway. Airline caller means `AIRLINE_EMPLOYEE` / `ADMIN`; provider admin means `MAINTENANCE_EMPLOYEE` / `ADMIN`; technician means `MAINTENANCE_EMPLOYEE` / `TECHNICIAN`.

| Method and path | Caller / input | Success |
| --- | --- | --- |
| `POST /faaadmin/maintenanceProviders` | FAA; JSON `{name, contactEmail}` | `201 Organization` |
| `POST /faaadmin/maintenanceProviders/{providerId}/addMaintenanceAdmin` | FAA; `MaintenanceEmployeeInput` JSON | `201 MaintenanceEmployee` |
| `POST /maintenanceAdmin/technicians` | Provider admin; `MaintenanceEmployeeInput` JSON | `201 MaintenanceEmployee` |
| `POST /airlineAdmin/maintenanceProviders/{providerId}/contract` | Airline admin; no body | `200 Organization` |
| `POST /airlineAdmin/airplanes/{airplaneId}/maintenanceRequests` | Owning airline admin; JSON `{providerId, description?}` | `201 MaintenanceRecord` |
| `GET /maintenanceAdmin/maintenanceRequests` | Provider admin; no body | `200 MaintenanceRecord[]` for provider, **all statuses** |
| `POST /maintenanceAdmin/maintenanceRequests/{requestId}` | Provider admin; query `technicianId`, no body | `200 MaintenanceRecord` |
| `GET /maintenanceTechnician/maintenanceRecords` | Technician; no body | `200 MaintenanceRecord[]` assigned to self |
| `PUT /maintenanceTechnician/maintenanceRecords/{recordId}/complete` | Assigned technician; optional query `findings`, no body | `200` text `Maintenance completed successfully.` |
| `POST /airlineAdmin/maintenanceRequests/{requestId}/cancel` | Owning airline admin; JSON `{reason}` | `200 MaintenanceRecord` |
| `POST /maintenanceAdmin/maintenanceRequests/{requestId}/cancel` | Owning provider admin; JSON `{reason}` | `200 MaintenanceRecord` |
| `GET /airlineAdmin/airplanes/{airplaneId}/maintenanceRecords` | Owning airline admin; optional query `status`, `page`, `size` | `200 Page<MaintenanceRecord>` across providers |

Provider creation needs nonblank name (max 255) and valid contact email. An airline must contract the provider before requesting work; repeated contracting returns `409`, missing contract on request returns `400`. Description is optional, trimmed, max 2000 characters. Multiple pending requests are allowed.

Lifecycle: `PENDING` with null technician → assign a technician from the same provider, staying `PENDING` → assigned technician completes to `COMPLETED`, or owning airline/provider admin cancels to `CANCELLED`. No `IN_PROGRESS` status exists. Assignment cannot be overwritten. Only pending records can be assigned/completed/cancelled; cancellation also requires no completion timestamp. Completed/cancelled state conflicts return `409`. Findings are optional, trimmed, max 2000 characters; cancellation reason is required, trimmed, 1–2000 characters (`400` otherwise).

History accepts `PENDING`, `COMPLETED`, `CANCELLED`; pages start at zero and sort newest creation time then ID descending. Provider lists are newest creation time first. Missing resources return `404`, wrong organization/technician `403`, dependency outages `503`. Completion/cancellation does not change aircraft activation or inspection results.

## Pilot and air traffic control

Pilot caller: account role `AIRLINE_EMPLOYEE`, airline role `PILOT`, currently assigned as `PILOT` or `SECONDARY_PILOT` on that flight. Tower caller: `ATC_EMPLOYEE`, role `ADMIN` or `CONTROLLER`, assigned to the relevant tower. Both tower roles can decide clearances; controller creation/listing requires the tower admin.

### Read and setup routes

| Method and path | Caller / query | Success |
| --- | --- | --- |
| `POST /atcAdmin/controllers` | Own-tower admin; `ATCEmployeeInput` JSON | `201 ATCEmployee` |
| `GET /atcAdmin/controllers` | Own-tower admin; optional `page`, `size` | `200 Page<ATCEmployee>`, controllers only |
| `GET /atc/facility` | Tower staff | `200 Facility` |
| `GET /pilot/flights` | Pilot; optional `status`, `fromDate`, `toDate`, `page`, `size` | `200 Page<FlightOperationSummary>` |
| `GET /pilot/flights/{flightId}` | Assigned pilot | `200 FlightOperationSummary` |
| `GET /pilot/flights/{flightId}/readiness` | Assigned pilot | `200 Readiness`; operation inferred from flight phase |
| `GET /pilot/flights/{flightId}/controlEvents` | Assigned pilot; optional `operation`, `page`, `size` | `200 Page<ControlEvent>` |
| `GET /atc/flights` | Tower staff; optional `operation`, `status`, `fromDate`, `toDate`, `page`, `size` | `200 Page<FlightOperationSummary>` |
| `GET /atc/flights/{flightId}` | Tower staff; flight uses own airport | `200 FlightOperationSummary` |
| `GET /atc/flights/{flightId}/readiness` | Responsible tower; **required** query `operation` | `200 Readiness` |
| `GET /atc/requests` | Tower staff; optional `operation`, `page`, `size` | `200 Page<FlightOperationSummary>`, own-tower pending clearances |
| `GET /atc/flights/{flightId}/controlEvents` | Tower staff; optional `operation`, `page`, `size` | `200 Page<ControlEvent>`, own-facility events only |

`operation` is `DEPARTURE` or `ARRIVAL`. `status` is a flight status: `PENDING_CREW`, `ACTIVE`, `IN_AIR`, `CLOSED`, `CANCELLED`. Date bounds are inclusive calendar days of **scheduled departure**, including for arrival views; `fromDate` must not exceed `toDate`. Flight lists sort by scheduled departure then ID ascending. Pending requests sort by flight ID ascending. Control events sort by ID descending. All pages here start at zero.

### Clearance and flight commands

Every command needs a JSON body and the current **nonnegative integer `expectedRevision`**. Read it from `clearance.revision` in the latest flight summary or `clearanceRevision` in readiness. It is one revision counter for the flight, not a separate departure/arrival counter. Do not default it to zero for an existing flight.

| Method and path | Caller / JSON body | Success |
| --- | --- | --- |
| `POST /pilot/flights/{flightId}/requestTakeOff` | Assigned pilot; `{expectedRevision}` | `201 OperationResult` |
| `POST /pilot/flights/{flightId}/requestLanding` | Assigned pilot; `{expectedRevision}` | `201 OperationResult` |
| `POST /pilot/flights/{flightId}/clearance/withdraw` | Assigned pilot; `{operation, expectedRevision}` | `200 OperationResult` |
| `POST /atc/flights/{flightId}/clearance/decision` | Responsible tower; `{operation, expectedRevision, decision, reason?}` | `200 OperationResult` |
| `POST /atc/flights/{flightId}/clearance/direct` | Responsible tower; `{operation, expectedRevision, reason?}` | `200 OperationResult` |
| `POST /atc/flights/{flightId}/clearance/revoke` | Responsible tower; `{operation, expectedRevision, reason}` | `200 OperationResult` |
| `POST /pilot/flights/{flightId}/takeOff` | Assigned pilot; `{expectedRevision}` | `200 OperationResult` |
| `POST /pilot/flights/{flightId}/land` | Assigned pilot; `{expectedRevision}` | `200 OperationResult` |

`decision` is **`APPROVED` or `REJECTED`**, different from airplane review's `ACCEPTED`/`DENIED`. A reason is required for rejection and revocation, optional for approval/direct approval, trimmed, max 2000 characters. Withdrawal only applies to a pending request. Normal decision requires pending clearance; direct approval can approve without a preceding pilot request when readiness permits. Revocation requires current unexpired approved clearance.

Departure is handled by the origin tower; arrival by the destination tower. Departure needs an active, not-yet-departed flight. Arrival needs `IN_AIR`, a consistent actual departure, and no actual arrival. Approval expires after **20 minutes**. Read responses expose effective status `EXPIRED` even though no separate expiry event or revision increment occurs. Refetch readiness at expiry; do not rely exclusively on notifications.

Typical flow: assign crew → activate flight → pass origin inspection → pilot requests departure → tower approves → pilot takes off → pilot requests arrival → destination tower approves → pilot lands. Successful take-off sets `IN_AIR` and actual departure; landing sets `CLOSED` and actual arrival. Each consumes its clearance. Scheduled times alone do not depart or land a flight.

Use `readyForRequest`, `readyForApproval`, `readyForAction` to enable relevant actions, then allow the server to recheck on submission. Readiness is not an authorization grant and does not replace endpoint-specific state checks. Pilot readiness has null operation for phases with no applicable operation. A summary contains one current/latest clearance, not separate departure and arrival objects; use control events for history.

| Readiness blocker code | Meaning |
| --- | --- |
| `WRONG_FLIGHT_PHASE` | Operation does not match flight phase |
| `TOWER_NOT_CONFIGURED` | Responsible airport has no tower |
| `ACTUAL_TIME_CONFLICT` | Inconsistent actual/approval timestamps |
| `CREW_INCOMPLETE` | Required pilot, secondary pilot or attendant missing |
| `INSPECTION_NOT_PASSED` | Latest inspection is absent or not passed |
| `AIRPLANE_NOT_ACTIVE` | Aircraft is absent or not active |
| `AIRPLANE_IN_USE` / `CREW_IN_USE` | Aircraft/crew occupied by another flight |
| `REQUEST_ALREADY_PENDING` | Cannot submit another request now |
| `CLEARANCE_ALREADY_APPROVED` | Current approval blocks another request/approval |
| `CLEARANCE_OPERATION_MISMATCH` | Clearance belongs to another operation/tower or is consumed incompatibly |
| `CLEARANCE_EXPIRED` | Approval has expired |
| `CLEARANCE_REQUIRED` | Action needs approval |

Each blocker has `appliesTo: ("REQUEST" | "APPROVAL" | "ACTION")[]`. `400` means malformed command/reason; `403` wrong pilot/tower/scope; `404` missing flight; `409` stale revision, wrong phase/state, readiness blocker or concurrent operation. On `409`, refetch flight and readiness and let the user reassess; do not silently replay the action with a new revision. Successful commands return the updated revision and event ID.

## Response schemas

Compact TypeScript-style wire shapes below specify the returned fields; these names are local documentation labels, not JSON wrappers. `number` covers JSON integer IDs/counts/minor amounts; `LocalDateTime`, `DateOnly` and `Instant` are strings in the formats above. Treat timestamps, reasons and unassigned references as nullable where indicated. Empty lists are arrays, not null.

```ts
type DateOnly = string;
type LocalDateTime = string; // no offset; application-local time
type Instant = string;       // UTC ISO instant
type FlightStatus = "PENDING_CREW" | "ACTIVE" | "IN_AIR" | "CLOSED" | "CANCELLED";
type Duty = "PILOT" | "SECONDARY_PILOT" | "FLIGHT_ATTENDANT";
type Operation = "DEPARTURE" | "ARRIVAL";
type ClearanceStatus = "NONE" | "PENDING" | "APPROVED" | "REJECTED"
  | "WITHDRAWN" | "REVOKED" | "CONSUMED" | "EXPIRED";
type AccountRole = "CUSTOMER" | "AIRLINE_EMPLOYEE" | "FAA_ADMIN"
  | "AIRPORT_EMPLOYEE" | "MAINTENANCE_EMPLOYEE" | "ATC_EMPLOYEE";

type Page<T> = {
  content: T[]; page: number; size: number;
  totalElements: number; totalPages: number;
};
type AccountSetup = {
  userId: number; emailAddress: string; role: AccountRole; status: "ACTIVE";
  active: true; fName: string; lName: string;
  phoneNumberOpeningCode: string; phoneNumber: string;
};
type CurrentUser = {
  userId: number; emailAddress: string; role: AccountRole;
  status: "SETUP_REQUIRED" | "ACTIVE" | "DEACTIVATED" | "PENDING_EMPLOYEE";
  active: boolean; fName: string | null; lName: string | null;
  phoneNumberOpeningCode: string | null; phoneNumber: string | null;
};
type CurrentAviationEmployee = {
  employeeId: number;
  employeeRole: "ADMIN" | "PILOT" | "FLIGHT_ATTENDANT" | "GATE_RECEPTION"
    | "INSPECTOR" | "BAGGAGE_HANDLER" | "CONTROLLER";
  airlineId: number | null; airportId: number | null; operatorId: number | null;
  facilityId: number | null; providerId: number | null;
};
type CurrentMaintenanceEmployee = {
  employeeId: number; employeeRole: "ADMIN" | "TECHNICIAN"; providerId: number;
};
type FlightSearchItem = {
  id: number; flightNumber: string; airlineCode: string;
  originAirport: string; destinationAirport: string;
  originCity: string; destinationCity: string;
  originCountry: string; destinationCountry: string;
  scheduledDeparture: LocalDateTime; scheduledArrival: LocalDateTime;
  firstClassSeatsCount: number; standardSeatsCount: number;
  firstClassPrice: number; standardPrice: number; currency: "BHD" | "USD";
};
type FlightDetail = {
  flight: FlightSearchItem;
  firstClassSeatsCapacity: number; standardSeatsCapacity: number;
  availableFirstClassSeats: number[]; availableStandardSeats: number[];
};
type Booking = {
  id: number; bookingRef: string; userId: number; flightId: number;
  seatNumber: string; status: "BOOKED" | "CANCELLED" | "FINISHED";
  bookedAt: LocalDateTime;
};
type BookingSummary = {
  id: number; bookedAt: LocalDateTime; updatedAt: LocalDateTime | null;
  bookingRef: string; seatNumber: string; status: Booking["status"];
  userId: number; flightId: number; flightNumber: string; airlineCode: string;
  originAirport: string; destinationAirport: string;
  scheduledDeparture: LocalDateTime; scheduledArrival: LocalDateTime;
  actualDeparture: LocalDateTime | null; actualArrival: LocalDateTime | null;
  flightStatus: FlightStatus;
};
type Checkout = { paymentId: number; checkoutUrl: string | null };
type Payment = {
  id: number; amount: number; currency: "USD" | "BHD";
  status: "PENDING" | "PAID" | "EXPIRED";
};
type Airline = {
  id: number; name: string; airlineCode: string; headquartersCountry: string;
};
type Organization = { id: number; name: string; contactEmail: string };
// Organization shape is shared by airport operators, navigation and maintenance providers.
type AddAirplane = {
  registrationNumber: string; model: string; seatCapacity: number; // sum of both classes
  maxMileage: number; addedAt: LocalDateTime; addedBy: string;
  status: "ACTIVE" | "GROUNDED";
};
type AirplaneRequest = {
  requestId: number; airplaneId: number; registrationNumber: string; model: string;
  status: "PENDING" | "ACCEPTED" | "DENIED"; reviewReason: string | null;
  requestedAt: LocalDateTime; reviewedAt: LocalDateTime | null;
};
type AddFlight = {
  flightNumber: string; airplaneRegistrationNumber: string;
  originAirportIataCode: string; arrivalAirportIataCode: string;
  scheduledDeparture: LocalDateTime; scheduledArrival: LocalDateTime;
  status: FlightStatus;
};
type EmployeeIdentity = {
  id: number; userId: number; fName: string; lName: string; emailAddress: string;
};
type AirlineEmployee = EmployeeIdentity & {
  airlineId: number; airlineRole: "ADMIN" | "PILOT" | "FLIGHT_ATTENDANT" | "GATE_RECEPTION";
  hireDate: DateOnly; salary: number;
};
type AirportEmployee = EmployeeIdentity & {
  operatorId: number; airportId: number;
  airportRole: "ADMIN" | "INSPECTOR" | "BAGGAGE_HANDLER";
};
type MaintenanceEmployee = EmployeeIdentity & {
  providerId: number; maintenanceRole: "ADMIN" | "TECHNICIAN";
  licenseNumber: string | null;
};
type ATCEmployee = EmployeeIdentity & {
  facilityId: number; employeeNumber: string; atcRole: "ADMIN" | "CONTROLLER";
};
type Facility = {
  id: number; providerId: number; airportId: number; name: string; facilityType: "TOWER";
};
type CrewAssignment = {
  id: number; flightId: number; employeeId: number; duty: Duty; assignedAt: LocalDateTime;
};
type CrewListItem = { employeeId: number; duty: Duty; assignedAt: LocalDateTime };
type Inspection = {
  id: number; flightId: number; airportId: number; inspectorId: number | null;
  status: "PENDING" | "PASSED" | "FAILED"; reason: string | null;
  createdAt: LocalDateTime; completedAt: LocalDateTime | null;
};
type MaintenanceRecord = {
  id: number; airplaneId: number; providerId: number; technicianId: number | null;
  description: string | null; findings: string | null;
  status: "PENDING" | "COMPLETED" | "CANCELLED";
  createdAt: LocalDateTime; updatedAt: LocalDateTime | null;
  completedAt: LocalDateTime | null; cancellationReason: string | null;
  cancelledAt: Instant | null; cancelledByUserId: number | null;
};
type Clearance = {
  operation: Operation | null; status: ClearanceStatus; facilityId: number | null;
  cycle: number; revision: number; approvedAt: Instant | null; expiresAt: Instant | null;
};
type FlightOperationSummary = {
  flightId: number; flightNumber: string;
  airlineId: number | null; airplaneId: number | null;
  originAirportId: number | null; destinationAirportId: number | null;
  scheduledDeparture: LocalDateTime; scheduledArrival: LocalDateTime;
  actualDeparture: LocalDateTime | null; actualArrival: LocalDateTime | null;
  status: FlightStatus; clearance: Clearance;
  crew: {employeeId: number | null; duty: Duty}[];
  latestInspection: {id: number; status: Inspection["status"]} | null;
};
type Readiness = {
  flightId: number; operation: Operation | null; evaluatedAt: Instant;
  readyForRequest: boolean; readyForApproval: boolean; readyForAction: boolean;
  clearanceRevision: number;
  blockers: {code: string; appliesTo: ("REQUEST" | "APPROVAL" | "ACTION")[]}[];
};
type OperationResult = {
  flightId: number; flightNumber: string; status: FlightStatus;
  actualDeparture: LocalDateTime | null; actualArrival: LocalDateTime | null;
  clearance: Clearance; eventId: number;
};
type ControlEvent = {
  id: number; flightId: number; facilityId: number; actorUserId: number;
  operation: Operation; eventType: "REQUESTED" | "APPROVED" | "REJECTED"
    | "WITHDRAWN" | "REVOKED" | "TAKE_OFF" | "LANDED";
  cycle: number; revision: number; occurredAt: Instant; reason: string | null;
};
```

## Errors

Typical application error:

```json
{
  "timestamp":"2027-01-10T09:15:00",
  "status":409,
  "error":"CONFLICT",
  "message":"Clearance revision changed. Refresh and retry.",
  "path":"/pilot/flights/123/takeOff"
}
```

`error` is generally the HTTP status enum name; 404 uses `RESOURCE_NOT_FOUND`. There are no structured per-field errors or uniform domain error codes. Do not branch on exact English messages. Readiness blocker codes are the exception and can drive explanatory UI.

| HTTP status | Frontend interpretation |
| --- | --- |
| `400` | Invalid body, parameter, enum, date or business state. Show the message; correct input or refresh state. |
| `401` | Missing/invalid/expired token, failed login or rejected password recovery. Reauthenticate only when this is a protected-request auth failure. |
| `403` | Wrong role/ownership/scope, or account lacks active access. Do not retry unchanged. |
| `404` | Missing resource/route; also payment not yet created. |
| `405` | Wrong HTTP method. |
| `409` | Duplicate, state/revision conflict, occupied operation, payment blocking cancellation, or late cancellation. Refetch relevant data before retry. |
| `413` | Uploaded file too large. |
| `415` | Unsupported Content-Type (check JSON multipart part as well). |
| `422` | Invalid airplane creation fields or invalid changed password; plain-text response. |
| `429` | Daily login failure limit; wait until midnight Bahrain time. |
| `500` | Unexpected failure, normally `Something went wrong`. |
| `502` / `503` | Payment-provider/dependency/gateway failure; show temporary failure and allow an appropriate retry. |

Not every failure follows the sample. Security filters and gateway errors can have a different JSON shape or no useful body. Recovery rejection is `{message}` only. Late cancellation and duplicate/invalid aircraft creation are text; password validation also returns text. Normalize JSON, text, empty and non-JSON error responses.

Do not automatically retry mutations after a lost connection or timeout: the operation may have committed. Refresh the relevant list/status first. Disable duplicate submits. Checkout deliberately reuses its pending session, but other POSTs have no general client idempotency key. A successful business response does not guarantee delivery of its notification/email; absence of an email/SSE event is not a reason to recreate a booking or flight.

## Live notifications

`GET /notifications`, active account, `Authorization: Bearer <JWT>`, `Accept: text/event-stream`. Returns `200 text/event-stream` and remains open. There is no request body, user/role query, or JSON wrapper around each event's `data`.

Use a header-capable SSE client or streaming fetch; native browser `EventSource` cannot attach the required bearer header. Do not put JWTs in URL parameters. Keep one stream per active frontend session/tab and close it on logout or component cleanup.

| Event name (exact casing) | Data | Audience / use |
| --- | --- | --- |
| `connected` | Text `Notification stream connected` | Subscriber; refresh current screen data |
| `airplane-activation-requested` | `AirplaneRequest` JSON | FAA admins; refresh pending requests |
| `airplane-added` | `AddAirplane` JSON | Creating airline admin |
| `FLIGHT_CREATED` | Text | Creating airline admin |
| `Assignment_Created`, `Assignment_Removed` | Text | Acting airline admin |
| `Flight_Assignment` | Text | Affected employee; covers assignment and removal |
| `Clearance_Requested`, `Clearance_Approved`, `Clearance_Rejected`, `Clearance_Withdrawn`, `Clearance_Revoked` | Clearance event JSON below | Relevant airline admins/assigned pilots and responsible tower staff |
| `Flight_Departed`, `Flight_Landed` | Clearance event JSON below | Relevant airline admins/assigned pilots and both towers |
| `Maintenance_Cancelled` | `{recordId: number, status: "CANCELLED", cancelledAt: Instant}` | Relevant airline/provider admins and assigned technician |

Clearance event data:

```json
{"eventId":123,"flightId":456,"operation":"DEPARTURE","cycle":1,"revision":2,"status":"APPROVED","occurredAt":"2027-01-10T07:00:00Z","expiresAt":"2027-01-10T07:20:00Z"}
```

`expiresAt` is null except on approval events; action events have status `CONSUMED`. These event payloads are smaller than REST responses: use them to invalidate/refetch flight, readiness, requests and history. Do not replace an entire cached flight with an event payload.

The stream sends heartbeat **comments** every 25 seconds and closes after five minutes. Reconnect with the current token (server retry hint is 3000 ms; a fetch client must implement retries). Reconnection rechecks authentication/account state. Stop retrying on 401/403 until the session is resolved; use backoff for transport/503 failures. Handle named events, heartbeat comments and chunks split across event boundaries.

Delivery is best-effort: no stored inbox, no replay, no guaranteed delivery/order, and no SSE `id`/Last-Event-ID recovery contract. Refresh on every reconnect and when returning to a stale screen. FAA clients should refetch `/faaadmin/airplaneRequests`; operations clients should refetch their flight/request lists. Deduplicate activation requests by `requestId` and control events by their payload `eventId`; ignore older clearance revisions. No booking/payment/inspection completion or clearance-expiry SSE event is currently emitted.

## React integration examples

### Same-origin development proxy

For a Vite-based frontend, an example `server.proxy` entry is:

```ts
// Inside defineConfig({ server: { proxy: ... } }) in the frontend project:
{
  "/api": {
    target: "http://localhost:8085",
    changeOrigin: true,
    rewrite: (path: string) => path.replace(/^\/api/, ""),
  },
}
```

Then use `API_BASE = "/api"`, including `/api/notifications`. For deployment, configure an equivalent same-origin proxy or a permitted API origin. This does not add `/api` to backend route definitions.

### JSON, text and multipart requests through one helper

```ts
const API_BASE = "/api";

class ApiError extends Error {
  constructor(public status: number, message: string, public body: unknown) {
    super(message);
  }
}

async function api<T>(path: string, options: {
  method?: string;
  token?: string;
  json?: unknown;
  form?: FormData;
  signal?: AbortSignal;
} = {}): Promise<T> {
  const { method = "GET", token, json, form, signal } = options;
  if (form && json !== undefined) throw new Error("Use either form or json");
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (json !== undefined) headers.set("Content-Type", "application/json");

  const response = await fetch(`${API_BASE}${path}`, {
    method, headers, signal,
    body: form ?? (json === undefined ? undefined : JSON.stringify(json)),
  });
  const text = await response.text();
  let body: unknown = text || undefined;
  if (text) {
    try { body = JSON.parse(text); } catch { /* API also returns plain text */ }
  }
  if (!response.ok) {
    const message = typeof body === "object" && body !== null &&
      "message" in body && typeof body.message === "string"
      ? body.message
      : typeof body === "string" && !body.trimStart().startsWith("<")
        ? body : `Request failed (${response.status})`;
    throw new ApiError(response.status, message, body);
  }
  return body as T; // T documents expectations; add runtime validation if needed.
}
```

Use a separate streaming client for notifications; this helper waits for a completed response. Network/CORS failures reject fetch without an HTTP status and should be handled separately from `ApiError`.

```ts
// Login: do not attach an old token to public auth requests.
const login = await api<{message: string}>("/auth/users/login", {
  method: "POST", json: {email, password},
});
const token = login.message;
// No role/userId can be read from this response; see integration gaps.

const query = new URLSearchParams({
  originAirport: "BAH", destinationAirport: "DXB",
  seatType: "standard", page: "1", size: "10",
});
const flights = await api<Page<FlightSearchItem>>(`/flights/search?${query}`, {token});

const form = new FormData();
form.append("request", new Blob([JSON.stringify({
  phoneNumber: "36001234", phoneNumberOpeningCode: "+973",
})], {type: "application/json"}));
// if (selectedImage) form.append("image", selectedImage);
await api<string>("/auth/users/updateProfile", {method: "PUT", token, form});

// bookingId is obtained from a booking response/list and retained across redirect.
const checkout = await api<Checkout>(`/payments/bookings/${bookingId}/checkout`, {
  method: "POST", token,
});
if (checkout.checkoutUrl) window.location.assign(checkout.checkoutUrl);
else {
  const payment = await api<Payment>(`/payments/bookings/${bookingId}`, {token});
  // Render payment.status; only PAID confirms payment.
}
```

Use `URLSearchParams` for query values, especially `+973`, free-text reasons, names and dates. Pass abort signals when cancelling stale searches. Clear user-scoped caches on logout/account switch, and refetch affected resources after mutations rather than relying on notifications alone.
