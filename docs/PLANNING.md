# Planning and progress

This document was created retrospectively on 8 October 2026 from local Git history and the author's explanation of the project. There was no separate planning board supplied. Dates below record observed development activity, not an original schedule or promised deadline.

## Purpose and scope

Build a flight-system backend inspired by earlier frontend work with the Amadeus API. The original ERD covered booking plus a broader aviation operation. To finish within the available time, delivery focused on customers, airline administrators, FAA administrators, booking, flight scheduling, and airplane activation approvals.

The current project uses its own database and does not call Amadeus. The README and implemented ERD now include the maintenance and inspection models; broader planned ATC entities remain outside the implemented scope.

## Deliverables, timeline, and progress

| Observed dates (2026) | Deliverable | Progress / evidence |
| --- | --- | --- |
| September 28 | Spring Boot project and initial README | Implemented; initial commit `b735e3a` |
| September 29–30 | Core entities and common authentication account | Implemented; model work and `User` restructuring in `f955040` |
| September 30–October 2 | Email, registration verification, validation, password services | Implemented; verification completed in `5a36198`, password recovery in `e07d92a` |
| October 3–4 | Airplane registration, flight scheduling, FAA review | Implemented; availability checks in `8502e24`, review workflow in `408a1f8` |
| October 5–6 | Booking, management, live notifications, scheduled updates | Implemented; booking in `45baabd`, SSE in `2e5f4a7`, scheduler in `4e543a9` |
| October 6–7 | Profile updates, flight search, audit logging, login lockout | Implemented; search in `798a51e`, audit work beginning at `d6b668b`, lockout in `b5c8813` |
| October 7–8 | Automated tests, seed data, Postman presentation package | Added; tests in `bffa3eb`, seed data in `c907689`, Postman package in `b7684d5` |
| October 8, current documentation work | Reworked README, user stories, planning record, configuration template, Swagger/OpenAPI | Added in this working-tree revision |
| October 27 | Maintenance workflow | Provider contracts, requests, assignment, and completion implemented; five PostgreSQL integration tests passed |
| October 28 | Airport accounts and flight inspections | Assignment, completion, reinspection, and scoped lists implemented; 16 PostgreSQL tests passed, including lock-contention and unique-index checks |
| No dates scheduled | Pilot departure enforcement, baggage services, ATC | Deferred or outside the current scope |

Review commit details with `git show <commit>` or view the [repository history](https://github.com/Saud-GA-JDB/PROJECT2_Flight_System/commits/).

## Delivery decisions

- Prioritize a usable booking and approval flow over implementing every entity in the original ERD.
- Use a shared account model with separate role profiles for authentication.
- Keep business rules in services and persistence in repositories.
- Implement SSE for the concrete FAA activation-request use case. Learning its concepts was difficult because it had not been taught, useful examples were limited, and there was little time for deeper study.
- Provide seed data and Postman examples to make the implemented workflows easier to demonstrate.

## Remaining work

1. Design the pilot take-off workflow and coordinate departure with the assigned pilot, latest inspection, crew coverage, and independent FAA airplane status under consistent locks.
2. Keep baggage handling deferred and ATC/navigation outside scope unless explicitly selected. Maintenance cancellation and airline maintenance history also remain deferred.
3. Improve notification reliability, time-zone handling, password recovery, response consistency, and test isolation.
4. Add a frontend and connect the wider workflows when time permits.

Future work has no committed timeline. See [User stories](USER-STORIES.md) for role-based requirements and the [README](../README.md#unsolved-problems-and-current-limitations) for current limitations.

Inspection results currently do not gate the existing actualDeparture-based scheduler. Departure enforcement and its race tests were explicitly postponed until the pilot workflow. The passing inspection tests cover services, not HTTP routing or all account setup operations. Database audit checks and the pending-inspection index were updated manually; a versioned migration is still future work.
