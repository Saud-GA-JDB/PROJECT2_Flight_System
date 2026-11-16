# Planning and progress

This document was created retrospectively on 8 October 2026 from local Git history and the author's explanation of the project, with the historical timeline recorded through 28 October 2026. Current status was reconciled with the code on 15 November 2026. Project 3 has a [Trello board](https://trello.com/b/b63eU4qX/project-3-concurrency). Dates below record observed development activity, not an original schedule or promised deadline.

## Purpose and scope

Build a flight-system backend inspired by earlier frontend work with the Amadeus API. The original ERD covered booking plus a broader aviation operation. To finish within the available time, delivery focused on customers, airline administrators, FAA administrators, booking, flight scheduling, and airplane activation approvals.

The project uses its own database and does not call Amadeus. Maintenance, inspections, ATC provider/tower/staff setup, clearance commands, pilot take-off/landing, and maintenance cancellation/history are implemented. Baggage handling remains deferred. See the [implemented ERD](ERD-IMPLEMENTED.md) and [API guide](API-GUIDE.md).

## Deliverables, timeline, and progress

| Observed dates (2026) | Deliverable | Progress / evidence |
| --- | --- | --- |
| September 28 | Spring Boot project and initial README | Added project setup and README |
| September 29–30 | Core entities and common authentication account | Added models and shared `User` authentication |
| September 30–October 2 | Email, registration verification, validation, password services | Added verification and password recovery |
| October 3–4 | Airplane registration, flight scheduling, FAA review | Added availability checks and review workflow |
| October 5–6 | Booking, management, live notifications, scheduled updates | Added booking, SSE, and flight scheduler |
| October 6–7 | Profile updates, flight search, audit logging, login lockout | Implemented profile, search, audit, and lockout workflows |
| October 7–8 | Automated tests, seed data, Postman presentation package | Added tests, demo data, and Postman examples |
| October 8 | README, user stories, planning record, configuration template, Swagger/OpenAPI | Added documentation and API setup |
| October 21 | Project 3 planning | Added concurrency planning and project documentation |
| October 22 | Stripe payments and crew models | Added checkout, payment race-condition tests, and crew models; removed unnecessary Stripe multithreading |
| October 23–24 | Crew assignment workflow | Added assignment and listing, employee locks, audit logging, and concurrency tests |
| October 25 | Crew coverage and asynchronous email | Added crew removal, activation checks, emails after transaction commit, and failed-email retries every 3 hours |
| October 26–27 | Maintenance workflow | Added providers, contracts, staff accounts, requests, technician assignment, completion, and tests |
| October 27–28 | Airport accounts and flight inspections | Added operators, staff, inspection assignment, completion, reinspection, scoped lists, and concurrency tests |
| October 28 | Notifications and database locking | Expanded SSE notifications and database locks; added concurrency tests and locking documentation |
| As of the October planning record | Pilot departure enforcement, baggage services, ATC | Originally deferred; pilot and ATC workflows have since been implemented. Baggage remains deferred. |

See the [repository history](https://github.com/Saud-GA-JDB/PROJECT2_Flight_System/commits/) for development details.

## Delivery decisions

- Prioritize a usable booking and approval flow over implementing every entity in the original ERD.
- Use a shared account model with separate role profiles for authentication.
- Keep business rules in services and persistence in repositories.
- Implement SSE for the concrete FAA activation-request use case. Learning its concepts was difficult because it had not been taught, useful examples were limited, and there was little time for deeper study.
- Provide seed data and Postman examples to make the implemented workflows easier to demonstrate.
- Use database locks to protect shared data during concurrent requests. Keep Stripe calls outside database transactions and send asynchronous crew emails after commit.

## Remaining work

1. Keep baggage handling deferred unless separately selected.
2. Improve notification reliability, time-zone handling, password recovery, response consistency, and test isolation.
3. Add a frontend and connect the wider workflows when time permits.
4. Optionally validate airline headquarters countries against a maintained country list. This was previously a TODO in `AirlineService`; no validation rule has been added.
5. Optionally email the airline and provider when a maintenance contract is created. This was previously a TODO in `MaintenanceProviderService`; no new email is sent.

Future work has no committed timeline. See [User stories](USER-STORIES.md) for role-based requirements and the [README](../README.md#unsolved-problems-and-current-limitations) for current limitations.

Pilot take-off now checks inspection/readiness, crew coverage, occupancy, FAA airplane status, and departure clearance. The scheduler reconciles status from actual departure/arrival times; it does not authorize departure. Service tests and the Cucumber API suite cover different boundaries; see the [integration-test guide](../integration-tests/README.md). Database audit checks and the pending-inspection index were updated manually; versioned migrations remain future work.
