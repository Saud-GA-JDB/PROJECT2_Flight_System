# Service boundaries

The system has four independently runnable services. Aviation, Identity, and Maintenance share PostgreSQL but own different tables. Notification has no database. `common` is a shared library, not a service.

Aviation, Identity, and Maintenance use the `public` schema in the shared `aviation` PostgreSQL database. Each service manages its own tables and obtains other services' information through APIs rather than directly accessing their tables. Ownership is enforced by application design, not separate schemas or database permissions. Notification has no database or schema.

| Service | Responsibility and reason for the boundary | Data owned | Main APIs |
| --- | --- | --- | --- |
| **Identity — 8081** | Centralizes registration, login, JWT authentication, passwords, profiles, and employee accounts so all services use consistent account management. | Users, profiles, pending registrations, identity audit logs. | `/auth/users/*`; `/internal/identity/*` for authentication, account/profile lookups, and employee provisioning. |
| **Aviation — 8080** | Owns flight booking and operations: airlines, aircraft, scheduling, payments, crew, inspections, and ATC. These remain together because seat allocation and flight readiness require coordinated checks and local transactions. | Flights, bookings, payments, fleet, airports, operational employees, crew assignments, inspections, clearances/control events, and operational audit logs. | `/flights/search`, `/customer/*`, `/bookings/*`, `/payments/*`, operational admin/pilot/ATC routes, and internal maintenance scope/notification routes. |
| **Maintenance — 8083** | Owns provider contracts and maintenance work: requests, technician assignment, completion, cancellation, and history. This is a separate workflow whose completion updates maintenance records rather than flight readiness or aircraft activation. | Providers, contracts, maintenance employees, work records, and maintenance audit logs; references other services through IDs. | Maintenance routes under `/faaadmin`, `/airlineAdmin`, `/maintenanceAdmin`, and `/maintenanceTechnician`. |
| **Notification — 8084** | Centralizes email delivery and live notifications. Business services decide recipients and message content; Notification handles delivery. | In-memory SSE connections, recent event IDs, and failed background emails; no persistent notification history. | `GET /notifications`; `POST /internal/v1/emails`; `POST /internal/v1/emails/async`. |

## Communication

- **Identity:** Receives synchronous Feign/HTTP account and authentication calls. Calls Notification synchronously for verification and password-reset emails.
- **Aviation:** Calls Identity synchronously. Sends live notifications asynchronously through Kafka after commit. Requests emails through HTTP after commit; crew emails use background delivery. Calls Stripe for checkout and payment status.
- **Maintenance:** Calls Identity and Aviation synchronously for accounts and airline/aircraft authorization. After cancellation commits, calls Aviation over HTTP to trigger a Kafka notification.
- **Notification:** Consumes Kafka events asynchronously and pushes them through SSE. Calls Identity synchronously to authenticate subscribers. Sends email through SMTP, either during the request or in the background.
