# Database locking

Pessimistic locks stay held until the transaction commits or rolls back. Write locks serialize competing changes; read locks allow concurrent readers while coordinating with writers.

| Where | Lock | Why / acquisition order |
| --- | --- | --- |
| `BookingService` — booking and cancellation | Flight write lock; cancellation also locks Booking | Prevents duplicate seat bookings and lost seat-count updates. **Flight → Booking**. |
| `PaymentService` — payment preparation | Flight and Booking write locks | Protects flight eligibility checks and payment creation. **Flight → Booking → payment changes**. |
| `PaymentService` — checkout saving and Stripe updates | Booking write lock | Serializes payment changes for the same booking. `Payment` also uses **`@Version`** for optimistic conflict detection. |
| `CrewAssignmentService` — add/remove crew | Flight and AirlineEmployee write locks | Protects crew requirements and prevents overlapping employee assignments. **Flight → AirlineEmployee → assignment changes**. |
| `CrewAssignmentService` — view assignments | Flight read lock | Allows concurrent readers while coordinating with assignment writers. |
| `FlightService` — activation | Flight write lock | Coordinates the crew check and activation with crew changes. |
| `FlightService` — scheduled status updates | Flight write locks, **ascending flight ID** | Rechecks status under lock and prevents competing transitions. Locks remain held until the whole scheduler transaction ends. |
| `AirlineEmployeeService` — create flight | Airplane write lock | Serializes schedule checks and flight creation for the same airplane. **Airplane → new Flight**. |
| `AirplaneService` / `FAAAdminService` — activation request and review | Airplane write lock | Prevents duplicate requests and competing review decisions. Review loads the request **after locking Airplane**, then updates both. |
| `FlightInspectionService` — assign, complete, request reinspection | Flight write lock | Serializes inspection changes and latest-inspection checks. **Flight → inspection changes**. |
| `MaintenanceRecordService` — assign technician | MaintenanceRecord and MaintenanceEmployee write locks | Prevents competing assignments and protects technician validation. **MaintenanceRecord → MaintenanceEmployee**. |
| `MaintenanceRecordService` — complete maintenance | MaintenanceRecord write lock | Prevents competing completion updates. |
| `MaintenanceProviderService`, `MaintenanceRecordService`, `MaintenanceEmployeeService` — contracts, requests, employee creation | MaintenanceProvider write lock | Coordinates contract creation, maintenance requests, and employee creation using that provider. |
| `AirportOperatorService` / `AirportEmployeeService` | Airport write lock | Coordinates operator assignment with employee creation and their validation checks. |
| `ATCService` — create tower | Airport write lock | Check the provider and existing tower after locking the airport; the unique airport foreign key protects cross-provider duplicates. Tower and audit commit together. |
| `ATCService` — create admin/controller | ControlFacility write lock | Recheck caller role and tower scope, then create employee, User and audit in one transaction. No flight/resource locks. |
| `UserService` — login, password changes/reset, profile edits | User write lock | Protects login-attempt counters and serializes account updates. |
| Registration verification / account setup | PendingRegistration and User write locks | Protects verification attempts, account-state checks, and setup consumption. **PendingRegistration → User** when the User exists. |
| `PendingRegistrationService` — send code | Matching pending-registration write locks, **ascending ID** | Coordinates expiry checks and replacement of existing registrations matching email or CPR. |
| `DatabaseSeeder` | Transaction advisory lock | Prevents multiple application instances from seeding simultaneously. |

Unique constraints also reject duplicate values when no existing row can be locked. `GlobalExceptionHandler` translates unique-constraint violations into **409 Conflict**; the handler itself does not acquire a lock.

Concurrency tests are grouped in [ConcurrencyTest.java](../src/test/java/com/ga/saudsFlightSystem/service/ConcurrencyTest.java).

ATC list/facility reads take no write locks and create no business audits. Setup lock timeout/deadlock failures return 409 with a refresh/retry message; failed writes roll back. `ATCServiceTest` covers the two-provider tower race using independent PostgreSQL transactions and the existing application executor.


Flight-operation GETs use read-only transactions with no explicit database locks. Pilot assignment, tower and airline scope are checked before returning data; list queries apply scope before counts/paging. Readiness uses nonlocking occupancy queries and is only a snapshot, not a reservation. Effective clearance expiry performs no write. Clearance commands acquire the required resource locks and recheck relevant conditions before changing state.

## Clearance commands

Departure request, approval and direct approval use **Airplane -> Flight -> assigned AirlineEmployee rows ascending ID -> event/audit**. A scalar airplane-ID query avoids caching the Flight before waiting. The service rechecks the airplane association after locking Flight, reads sorted crew IDs under that lock (including extras), acquires the existing employee assignment locks, then validates current caller, revision and readiness using time captured after locks. Occupancy queries run after resource locks at PostgreSQL READ_COMMITTED; approval/request do not reserve resources for future take-off.

Arrival request/approval and reject/withdraw/revoke lock Flight only and never acquire later airplane/employee locks. Permission-removing commands require correct phase/actual times, scope and matching clearance, but do not require operational readiness. Crew writes retain their existing Flight -> Employee order; inspection/activation remain Flight-first with no later airplane lock.

Every successful command updates Flight and inserts one unique (flight_id,revision) event plus its audit in the same transaction. NotificationService defers scoped User-ID delivery until commit. Failure rolls everything back; lock timeout/deadlock returns 409 with refresh/retry guidance and no automatic replay. GETs take no write locks and never expire state by writing.

FlightClearanceTransactionTest uses committed isolated fixtures, the existing executor, independent transactions and bounded waits. It verifies PostgreSQL blocking for competing requests/approvals/direct issuance, fresh airplane/extra-crew occupancy after waiting, expiry across a lock wait, event/audit rollback and after-commit notifications. Physical takeOff/land locking and scheduler isolation remain the next stage.
