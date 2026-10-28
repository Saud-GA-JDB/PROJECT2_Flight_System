# Planning and progress

This document was created retrospectively on 8 October 2026 from local Git history and the author's explanation of the project, and updated through 28 October 2026. Project 3 has a [Trello board](https://trello.com/b/b63eU4qX/project-3-concurrency). Dates below record observed development activity, not an original schedule or promised deadline.

## Purpose and scope

Build a flight-system backend inspired by earlier frontend work with the Amadeus API. The original ERD covered booking plus a broader aviation operation. To finish within the available time, delivery focused on customers, airline administrators, FAA administrators, booking, flight scheduling, and airplane activation approvals.

The current project uses its own database and does not call Amadeus. The README and implemented ERD now include the maintenance and inspection models; broader planned ATC entities remain outside the implemented scope.

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
| No dates scheduled | Pilot departure enforcement, baggage services, ATC | Deferred or outside the current scope |

See the [repository history](https://github.com/Saud-GA-JDB/PROJECT2_Flight_System/commits/) for development details.

## Delivery decisions

- Prioritize a usable booking and approval flow over implementing every entity in the original ERD.
- Use a shared account model with separate role profiles for authentication.
- Keep business rules in services and persistence in repositories.
- Implement SSE for the concrete FAA activation-request use case. Learning its concepts was difficult because it had not been taught, useful examples were limited, and there was little time for deeper study.
- Provide seed data and Postman examples to make the implemented workflows easier to demonstrate.
- Use database locks to protect shared data during concurrent requests. Keep Stripe calls outside database transactions and send asynchronous crew emails after commit.

## Remaining work

1. Design the pilot take-off workflow and coordinate departure with the assigned pilot, latest inspection, crew coverage, and independent FAA airplane status under consistent locks.
2. Keep baggage handling deferred and ATC/navigation outside scope unless explicitly selected. Maintenance cancellation and airline maintenance history also remain deferred.
3. Improve notification reliability, time-zone handling, password recovery, response consistency, and test isolation.
4. Add a frontend and connect the wider workflows when time permits.

Future work has no committed timeline. See [User stories](USER-STORIES.md) for role-based requirements and the [README](../README.md#unsolved-problems-and-current-limitations) for current limitations.

Inspection results currently do not gate the existing actualDeparture-based scheduler. Departure enforcement and its race tests were explicitly postponed until the pilot workflow. The passing inspection tests cover services, not HTTP routing or all account setup operations. Database audit checks and the pending-inspection index were updated manually; a versioned migration is still future work.
