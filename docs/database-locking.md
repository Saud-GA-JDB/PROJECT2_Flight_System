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
| `UserService` — login, password changes/reset, profile edits | User write lock | Protects login-attempt counters and serializes account updates. |
| Registration verification / account setup | PendingRegistration and User write locks | Protects verification attempts, account-state checks, and setup consumption. **PendingRegistration → User** when the User exists. |
| `PendingRegistrationService` — send code | Matching pending-registration write locks, **ascending ID** | Coordinates expiry checks and replacement of existing registrations matching email or CPR. |
| `DatabaseSeeder` | Transaction advisory lock | Prevents multiple application instances from seeding simultaneously. |

Unique constraints also reject duplicate values when no existing row can be locked. `GlobalExceptionHandler` translates unique-constraint violations into **409 Conflict**; the handler itself does not acquire a lock.

Concurrency tests are grouped in [ConcurrencyTest.java](../src/test/java/com/ga/saudsFlightSystem/service/ConcurrencyTest.java).
