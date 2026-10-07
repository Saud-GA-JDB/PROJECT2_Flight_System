# Sample database: accounts, flights, and bookings

Use this reference to try the application without opening PostgreSQL. Values below
were checked against the local `aviation` database on **8 October 2026**, after seeding.
It covers the seeded sample records; older personal records are not listed.

**IDs shown are for this database.** On another database, generated IDs can differ.
Only booking `900001`, activation request `900002`, and airplane `900003` are fixed.
Use login responses, flight search, bookings, and FAA airline/request lists to find
the corresponding IDs on a fresh installation. Use user IDs, not profile IDs, in URLs.

## Setup

Configure PostgreSQL and mail in `src/main/resources/application.properties` or
environment variables. To populate a new database, run from PowerShell:

```powershell
$env:APP_SEED_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

The startup log reports `Database seed completed; existing records were preserved.`
After stopping the application, set `$env:APP_SEED_ENABLED = "false"` and start it
normally. The current local database is already seeded.

Rerunning adds missing records and preserves existing passwords, bookings, approvals,
and flight dates. It does not reset manual testing changes. Initial departures are
three, four, and five days after seeding; flights eventually disappear from available
flight search as their scheduled departures pass. Use future flights when testing.

## Login accounts

**Password for every account below: `TestPassword1`** (case-sensitive).

| User ID | Name | Email | Role | Airline | CPR | Phone (+973) |
| --- | --- | --- | --- | --- | --- | --- |
| 15 | Ahmed Yousif | flighttest.customer@mailsac.com | CUSTOMER | — | 990000001 | 36001001 |
| 16 | Fatima Salman | flighttest.faa@mailsac.com | FAA_ADMIN | — | 990000002 | 36001002 |
| 17 | Hassan Mahmood | flighttest.airline@mailsac.com | AIRLINE_EMPLOYEE / ADMIN | Gulf Air | 990000003 | 36001003 |
| 18 | Sara Ahmed | sara.customer@mailsac.com | CUSTOMER | — | 990000004 | 36001004 |
| 19 | Omar Ali | omar.customer@mailsac.com | CUSTOMER | — | 990000005 | 36001005 |
| 20 | Layla Hassan | layla.customer@mailsac.com | CUSTOMER | — | 990000006 | 36001006 |
| 21 | Noor Khalid | noor.airline@mailsac.com | AIRLINE_EMPLOYEE / ADMIN | Emirates | 990000007 | 36001007 |

All accounts start with `status=ACTIVE`, `active=true`, and zero failed login attempts.
Security question: **In which city were you born?** Answer: **`manama`**.
Passwords and security answers are stored using BCrypt. Names and personal details
are fictional sample data. All sample inboxes use Mailsac so you can inspect emails.

Linked profile IDs in this database:

| User | Profile type | Profile ID |
| --- | --- | --- |
| Ahmed | Customer | 8 |
| Fatima | FAA admin | 4 |
| Hassan | Airline employee | 8 |
| Sara | Customer | 9 |
| Omar | Customer | 10 |
| Layla | Customer | 11 |
| Noor | Airline employee | 9 |

Both airline employees have `airlineRole=ADMIN`, hire date `2025-01-01`, and salary
`1500`. Sample profiles have no uploaded image.

### Login request

`POST http://localhost:8080/auth/users/login`, with `Content-Type: application/json`:

```json
{
  "email": "sara.customer@mailsac.com",
  "password": "TestPassword1"
}
```

Use the returned JWT on protected requests: `Authorization: Bearer YOUR_TOKEN`.
The login field is **`email`**, not `emailAddress`. Switch accounts by logging in
again and using that account's token.

## Airlines and airports

| Airline ID | Name | Code | Headquarters | Airline admin |
| --- | --- | --- | --- | --- |
| 1 | Gulf Air | GF | Bahrain | Hassan |
| 6 | Emirates | EK | United Arab Emirates | Noor |

Gulf Air and the airports below were already present locally and were reused.

| Airport ID | IATA code | Name | City | Country | Time zone |
| --- | --- | --- | --- | --- | --- |
| 1 | BAH | Bahrain International Airport | Muharraq | Bahrain | Asia/Bahrain |
| 2 | DXB | Dubai International Airport | Dubai | United Arab Emirates | Asia/Dubai |
| 3 | DOH | Hamad International Airport | Doha | Qatar | Asia/Qatar |

## Airplanes

| Airplane ID | Registration | Model | Airline | Standard capacity | First-class capacity | Status | Use |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 900003 | TEST-FREE-PLANE | Boeing 787-9 | Gulf Air | 250 | 26 | ACTIVE | No flights; create a flight |
| 900008 | A9C-SF01 | Airbus A320-200 | Gulf Air | 138 | 12 | ACTIVE | GF501 / booking fixture |
| 900009 | A9C-SF02 | Airbus A321neo | Gulf Air | 154 | 16 | GROUNDED | Pending FAA approval |
| 900010 | A9C-SF03 | Airbus A320-200 | Gulf Air | 138 | 12 | ACTIVE | GF524 and GF525 |
| 900011 | A6-SF01 | Boeing 777-300ER | Emirates | 310 | 8 | ACTIVE | EK837 and EK838 |

Every seeded airplane has `maxMileage=6000`. Model names are real; registrations,
seat configurations, mileage values, flight numbers, and schedules are illustrative
application data, not specifications of actual airline aircraft.

## Flights and remaining seats

These are the initial values. All five flights are `ACTIVE`; actual departure and
arrival are null. Timestamps are stored without a time-zone offset.

| Flight ID | Number | Route | Airplane ID | Scheduled departure | Scheduled arrival | Standard available | First-class available |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 7 | GF501 | BAH → DXB | 900008 | 2026-10-11 01:08:41 | 2026-10-11 03:08:41 | 137 | 12 |
| 8 | GF524 | BAH → DOH | 900010 | 2026-10-12 01:08:41 | 2026-10-12 03:08:41 | 137 | 12 |
| 9 | GF525 | DOH → BAH | 900010 | 2026-10-13 01:08:41 | 2026-10-13 03:08:41 | 138 | 12 |
| 10 | EK837 | DXB → BAH | 900011 | 2026-10-12 01:08:41 | 2026-10-12 03:08:41 | 310 | 7 |
| 11 | EK838 | BAH → DXB | 900011 | 2026-10-13 01:08:41 | 2026-10-13 03:08:41 | 310 | 8 |

Seat types in URLs are exactly `standard` and `firstClass`. Seat IDs start at 1.
Flight seat counts are remaining availability, while airplane capacities are totals.

## Bookings

| Booking ID | Reference | Owner (user ID) | Flight (ID) | Seat | Status |
| --- | --- | --- | --- | --- | --- |
| 900001 | TEST-BOOKING-900001 | Ahmed (15) | GF501 (7) | standard-1 | BOOKED |
| 900005 | SFA7K2 | Sara (18) | GF524 (8) | standard-1 | BOOKED |
| 900006 | SFB8L3 | Omar (19) | EK837 (10) | firstClass-1 | BOOKED |
| 900007 | SFC9M4 | Sara (18) | EK837 (10) | standard-2 | CANCELLED |

Layla starts with no bookings. The cancelled booking does not consume a seat.
Booking timestamps are generated when seeded. Rebooking a cancelled seat creates
a new booking; it does not reactivate the old record.

## Airplane activation request

| Request ID | Airplane | Requested by | Status | Reviewer / review time |
| --- | --- | --- | --- | --- |
| 900002 | A9C-SF02 (900009) | Hassan (user 17) | PENDING | null / null |

The request timestamp is generated during seeding. Approving it activates the
airplane. The requester belongs to Gulf Air; the reviewer must be an FAA admin.

## Things to try

Use `http://localhost:8080` and the appropriate account's bearer token for each
request. Examples assume the initial data above and future flight departures.

1. **Search flights — any sample account.**
   `GET /flights/search?originAirport=BAH&destinationAirport=DOH&seatType=standard`
   should include GF524. Use `airlineCode=EK` to search Emirates flights.

2. **Customer with no bookings — Layla.**
   `GET /bookings` initially returns an empty list.
   `POST /customer/20/flights/9/standard/2/book` books GF525 seat `standard-2`.
   Its standard availability changes from 138 to 137; Layla receives an email.
   Use the returned booking ID for later cancellation.

3. **Occupied seat — Sara.**
   `POST /customer/18/flights/8/standard/1/book` is rejected because her seeded
   booking already occupies that seat. `standard/2` is initially available.

4. **Cancel a booking — Sara.**
   `DELETE /bookings/900005` succeeds when at least 48 hours remain before departure.
   Status becomes `CANCELLED`, GF524 standard availability becomes 138, and Sara
   receives an email. Repeating cancellation is rejected. Airline admins can cancel
   their own airline's bookings without the customer 48-hour restriction.

5. **Airline booking search — Hassan and Noor.**
   Hassan: `GET /bookings/search?status=BOOKED` includes Ahmed's and Sara's Gulf Air
   bookings. Noor sees Emirates bookings, including Omar's first-class booking.
   A customer cannot use the airline booking search endpoint.

6. **FAA review — Fatima.**
   `GET /faaadmin/airplaneRequests` includes request `900002`.
   `PUT /faaadmin/airplaneRequests/900002` with JSON
   `{"status":"ACCEPTED","reviewReason":"Safety checks passed"}` activates airplane
   `900009` and sends Hassan an email. Airline admins cannot approve requests.
   This changes a fixture used by the automated tests.

7. **Create a flight — Hassan.**
   `POST /airlineAdmin/airplanes/900003/addFlight` with the JSON below. Choose future
   times, and leave at least two hours between flights on the same airplane.
   Successful creation returns the new flight ID with 250 standard and 26 first-class
   seats available. This changes the automated-test fixture that expects no flights.

   ```json
   {
     "originAirportIataCode": "BAH",
     "arrivalAirportIataCode": "DXB",
     "scheduledDeparture": "2026-10-20T10:00:00",
     "scheduledArrival": "2026-10-20T12:00:00"
   }
   ```

Mail configuration must work for operations that send email. Database changes may
roll back if email sending fails. Seeder startup itself sends no emails.

## Automated-test fixtures

Keep the three `flighttest.*@mailsac.com` accounts with password `TestPassword1`,
booking `900001` in `BOOKED` status, request `900002` in `PENDING` status with its
airplane grounded, and airplane `900003` active with no flights when running the
existing service tests. Their flight dates are adjusted inside rolled-back tests.
Manual changes to these fixtures are not repaired by rerunning the seeder.

The tests create `Test Created Airline`, airline code `TCA`, and registration
`TEST-NEW-PLANE`; leave these values unused. Use a separate freshly seeded database
for automated tests if manual testing has changed the fixtures.

For more endpoints and accepted request fields, see [README.md](README.md).
