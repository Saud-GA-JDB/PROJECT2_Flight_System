# Sample database for Project 3

`DatabaseSeeder` is an optional PostgreSQL startup seeder. It creates connected
sample data for bookings, crew readiness, airport inspections, maintenance, and
payments. This document describes initial fixtures, not a snapshot of a particular
machine's database.

## Run

Configure the application using `src/main/resources/application-example.properties`
as a reference, then run from PowerShell:

```powershell
$env:APP_SEED_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

After stopping the application, disable seeding for normal starts:

```powershell
$env:APP_SEED_ENABLED = "false"
```

Seeding is transactional and uses a PostgreSQL advisory lock to serialize seed runs.
It does not send email or call Stripe. Existing records are reused. Missing prices
are filled, and legacy ACTIVE sample flights with both prices missing and no crew
receive their initial crew. Existing passwords, statuses, dates, prices, bookings,
payment state, assignments, and maintenance progress are not reset. Conflicting
reserved IDs or overlapping seed crew schedules abort the transaction.

The seeder adds the Project 3 fixtures to an older seeded database. It does not
reschedule old flights; their dates may already have passed. New Project 3 flights
start six and seven days after their first seed run. Reruns preserve those dates too.
Use a fresh development database or create future flights when fixtures expire.

## Accounts

Initial password: **`TestPassword1`**. Security question: **In which city were you
born?** Answer: **`manama`**. Both secrets are encoded. All accounts start ACTIVE.
The addresses and personal details are sample data.

| Email | Role / scope |
| --- | --- |
| flighttest.customer@mailsac.com | Customer Ahmed |
| flighttest.faa@mailsac.com | FAA admin Fatima |
| flighttest.airline@mailsac.com | Gulf Air admin Hassan |
| sara.customer@mailsac.com | Customer Sara |
| omar.customer@mailsac.com | Customer Omar |
| layla.customer@mailsac.com | Customer Layla, initially no bookings |
| noor.airline@mailsac.com | Emirates admin Noor |
| gf.crew1@mailsac.com | Gulf Air pilot |
| gf.crew2@mailsac.com | Gulf Air secondary pilot |
| gf.crew3@mailsac.com | Gulf Air flight attendant |
| ek.crew1@mailsac.com | Emirates pilot |
| ek.crew2@mailsac.com | Emirates secondary pilot |
| ek.crew3@mailsac.com | Emirates flight attendant |
| airport.admin@mailsac.com | BAH airport admin |
| airport.inspector@mailsac.com | BAH inspector |
| maintenance.admin@mailsac.com | Sample Gulf Maintenance admin |
| maintenance.technician@mailsac.com | Sample Gulf Maintenance technician |

Login with `POST /auth/users/login` and JSON:

```json
{"email":"flighttest.airline@mailsac.com","password":"TestPassword1"}
```

Use the returned JWT as `Authorization: Bearer YOUR_TOKEN`. Discover generated IDs
through login and list responses. Employee assignment endpoints use **employee
profile IDs**, which differ from user IDs. See [API guide](docs/API-GUIDE.md).

## Airlines, airports, and airplanes

Airlines: Gulf Air (`GF`) and Emirates (`EK`). Airports: BAH, DXB, and DOH, with
Asia/Bahrain, Asia/Dubai, and Asia/Qatar time zones respectively. BAH receives a
sample operator if it has none; an existing operator is retained and used by the
sample airport staff. Sample Gulf Maintenance starts contracted to Gulf Air.

| Registration | Airline | Standard / first-class capacity | Initial state |
| --- | --- | --- | --- |
| TEST-FREE-PLANE | GF | 250 / 26 | ACTIVE; no flights; fixed ID 900003 |
| A9C-SF01 | GF | 138 / 12 | ACTIVE; GF501 and Project 3 flights |
| A9C-SF02 | GF | 154 / 16 | GROUNDED; pending activation request 900002 |
| A9C-SF03 | GF | 138 / 12 | ACTIVE; GF524 and GF525 |
| A6-SF01 | EK | 310 / 8 | ACTIVE; EK837 and EK838 |

Capacities, registrations, schedules, and mileage are illustrative. Every airplane
starts with `maxMileage=6000`. Only airplane 900003, request 900002, and booking
900001 have reserved IDs; all other IDs are generated.

## Flights, crew, and prices

| Flight | Route | Departure after initial seed | Initial status | Standard / first-class available |
| --- | --- | --- | --- | --- |
| GF501 | BAH to DXB | 3 days | ACTIVE | 137 / 12 |
| GF524 | BAH to DOH | 4 days | ACTIVE | 137 / 12 |
| GF525 | DOH to BAH | 5 days | ACTIVE | 138 / 12 |
| EK837 | DXB to BAH | 4 days | ACTIVE | 310 / 7 |
| EK838 | BAH to DXB | 5 days | ACTIVE | 310 / 8 |
| GF-P3-READY | BAH to DXB | 6 days | PENDING_CREW | 138 / 12 |
| GF-P3-PAY | BAH to DXB | 7 days | ACTIVE | 137 / 12 |

Each flight lasts two hours. ACTIVE flights start with a pilot, a secondary pilot,
and an attendant from their own airline. The same airline's sample crew are reused
on non-overlapping days. GF-P3-READY starts without crew for the assignment workflow.

Prices are `45000` standard and `120000` first class, in BHD minor units (45.000 and
120.000 BHD). Existing non-null prices are preserved. Seat names are `standard-1`,
`firstClass-1`, etc. Flight counts represent availability, not airplane capacity.

## Bookings and payments

| Reference | Owner | Flight | Seat | Initial status |
| --- | --- | --- | --- | --- |
| TEST-BOOKING-900001 | Ahmed | GF501 | standard-1 | BOOKED; ID 900001 |
| SFA7K2 | Sara | GF524 | standard-1 | BOOKED |
| SFB8L3 | Omar | EK837 | firstClass-1 | BOOKED |
| SFC9M4 | Sara | EK837 | standard-2 | CANCELLED |
| P3PAY01 | Omar | GF-P3-PAY | standard-3 | BOOKED |

Only P3PAY01 has a seeded payment: PENDING, 45000 BHD minor units, with no Stripe
session or intent. Start checkout as Omar using the payment endpoints; real checkout
requires Stripe test configuration. A pending payment without a session becomes
ineligible for retry after 23 hours under the current service rules. For later demos,
create a new booking and prepare its payment through the API. Rerunning the seeder
does not reset payment age or state.

Other bookings intentionally start without payments, preserving the cancellation
test fixtures. Cancelled bookings consume no seats. Pending or paid payments block
cancellation under the current service rules.

## Project 3 workflows to try

1. **Crew readiness:** as Hassan, assign gf.crew1, gf.crew2, and gf.crew3 to
   GF-P3-READY with PILOT, SECONDARY_PILOT, and FLIGHT_ATTENDANT duties, then activate
   it. Attempting activation before the required crew is present should fail.
2. **Inspection assignment:** GF-P3-READY has one PENDING inspection with no
   inspector. As the BAH airport admin, assign the BAH inspector. Log in as the
   inspector to complete it. A FAILED result permits an airline re-inspection request.
   Other flights can receive inspections through the normal assignment endpoint.
3. **Maintenance:** the record described as `Project 3 sample routine maintenance`
   belongs to Sample Gulf Maintenance and airplane A9C-SF01. It starts PENDING with
   no technician. Assign the sample technician as the maintenance admin, then complete
   it as the technician. Completion does not automatically change airplane status.
4. **Concurrency:** submit competing assignments to the same pending inspection or
   maintenance request, or competing bookings for the same available seat. Use the
   existing concurrency tests for repeatable assertions.
5. **Checkout:** as Omar, locate P3PAY01 and use the payment workflow. No fake PAID
   state or external Stripe identifier is inserted by the seeder.
6. **FAA approval:** as Fatima, review request 900002 for A9C-SF02. This changes an
   existing test fixture; use a separate database for manual demos.

Audit logs are produced by these real service actions. The seeder does not invent
user activity or temporary email-verification codes. New staff accounts are already
set up so you can exercise the workflows immediately.

## Verification and existing tests

`DatabaseSeederTest` exercises seeding twice, relationship consistency, unchanged
row counts and seat counts, and preservation of edited credentials, prices, and
workflow state. It uses the configured PostgreSQL test database and rolls back row
changes; PostgreSQL sequence advances are not rolled back.

```powershell
.\mvnw.cmd '-Dtest=DatabaseSeederTest' test
```

For existing service tests, retain the three `flighttest.*` accounts with their
initial password, booking 900001 BOOKED, request 900002 PENDING with its airplane
GROUNDED, and airplane 900003 ACTIVE without flights. Rerunning does not repair
manual changes to those fixtures. Leave `TCA`, `Test Created Airline`, and
`TEST-NEW-PLANE` unused for the tests. See [API guide](docs/API-GUIDE.md) for request
paths and DTOs, and [locking notes](docs/database-locking.md) for concurrency details.
