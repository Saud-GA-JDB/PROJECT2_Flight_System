# Planning and progress

This document was created retrospectively on 8 October 2026 from local Git history and the author's explanation of the project. There was no separate planning board supplied. Dates below record observed development activity, not an original schedule or promised deadline.

## Purpose and scope

Build a flight-system backend inspired by earlier frontend work with the Amadeus API. The original ERD covered booking plus a broader aviation operation. To finish within the available time, delivery focused on customers, airline administrators, FAA administrators, booking, flight scheduling, and airplane activation approvals.

The current project uses its own database and does not call Amadeus. The ERD is retained unchanged and needs a later accuracy update.

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
| No dates scheduled | Crew, ground services, maintenance, airport operations, air traffic control | Deferred to complete the core project on time |

Review commit details with `git show <commit>` or view the [repository history](https://github.com/Saud-GA-JDB/PROJECT2_Flight_System/commits/).

## Delivery decisions

- Prioritize a usable booking and approval flow over implementing every entity in the original ERD.
- Use a shared account model with separate role profiles for authentication.
- Keep business rules in services and persistence in repositories.
- Implement SSE for the concrete FAA activation-request use case. Learning its concepts was difficult because it had not been taught, useful examples were limited, and there was little time for deeper study.
- Provide seed data and Postman examples to make the implemented workflows easier to demonstrate.

## Remaining work

1. Manually update the ERD to reflect the current models and separate future scope clearly.
2. Complete crew assignments, airport staff and ground operations, maintenance records, and air traffic control workflows.
3. Improve notification reliability, time-zone handling, password recovery, response consistency, and test isolation.
4. Add a frontend and connect the wider workflows when time permits.

Future work has no committed timeline. See [User stories](USER-STORIES.md) for role-based requirements and the [README](../README.md#unsolved-problems-and-current-limitations) for current limitations.
