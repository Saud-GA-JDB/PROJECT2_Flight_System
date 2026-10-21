# User stories

These stories were recorded retrospectively from the implemented API and the wider ERD. “Implemented” describes available functionality, not a claim of full production readiness.

## Implemented scope

| ID | User story | Acceptance criteria |
| --- | --- | --- |
| US-01 | As a customer, I want to verify my email and set up an account so I can book flights. | Request a code with a valid email and nine-digit CPR; verify it; log in with the initial CPR password; complete setup with a new password and required profile details. |
| US-02 | As a user, I want to log in securely so my account's actions are protected. | Valid credentials return a JWT; protected routes check account state; repeated failed logins trigger a temporary lockout. |
| US-03 | As a user, I want to maintain my contact details and password. | Authenticated users can update supported profile fields, optionally upload an image, and change their password with validation. |
| US-04 | As a customer, I want to search available flights so I can find a suitable journey. | Combine date, airline, route, and seat-class filters; receive paginated, sorted results for eligible flights. |
| US-05 | As a customer, I want to choose and book an available seat. | Validate flight eligibility and seat availability; create a booking reference; reduce availability; send confirmation email. |
| US-06 | As a customer, I want to view and cancel my bookings. | View my bookings; cancel a booked seat at least 48 hours before departure; restore availability and send cancellation email. |
| US-07 | As an airline administrator, I want to register airplanes and request activation. | Airplanes belong to my airline and start grounded; submit an eligible activation request; wait for FAA review. |
| US-08 | As an airline administrator, I want to schedule flights using approved airplanes. | Require an active airplane belonging to my airline, valid airports, future times, and at least two hours between non-cancelled flights. |
| US-09 | As an airline administrator, I want to manage bookings for my airline. | Filter bookings by user, flight, or status; book for another user on my airline; cancel my airline's bookings without the customer 48-hour restriction. |
| US-10 | As an FAA administrator, I want to create airlines and their administrators. | Create and list airlines, then create an administrator linked to an airline. |
| US-11 | As an FAA administrator, I want to review airplane activation requests. | List pending requests; accept or deny with review information; acceptance activates the airplane; notify the requester by email. |
| US-12 | As an FAA administrator, I want to receive live activation-request notifications. | An authenticated SSE connection receives new airplane-request events after commit; disconnected clients can fetch pending requests after reconnecting. |
| US-13 | As an FAA administrator, I want to maintain user records and deactivate accounts. | Supply a target user ID to update supported administrative fields or deactivate an account; enforce FAA authorization. |
| US-14 | As a developer, I want repeatable sample data and API documentation so I can explore the system. | Opt-in seeding creates demo workflows; Swagger exposes the API and supports JWT authorization; Postman examples and a detailed guide provide usage instructions. |

## Deferred stories from the broader design

- As an airline administrator, I want to assign crew to flights and record their duties.
- As an airport operator, I want to manage airport employees and ground-service assignments.
- As a maintenance provider, I want to manage technicians and record airplane maintenance and release status.
- As an air traffic control employee, I want to record flight-control events through my assigned control facility.
- As an aviation-system user, I want these operational stages connected into the full flight workflow.

These workflows remain planned; their appearance in the ERD does not mean they have working endpoints. See [Planning](PLANNING.md) and the [README](../README.md).

## Project 3 proposed user stories

Drafted on 21 October 2026 for scope selection. All stories below are proposed, not implemented or a commitment to implement the full ERD. Existing implemented stories describe Project 2 behavior; payment stories below propose changes to that booking flow.

### Concurrency and scheduled background work

| ID | User story | Acceptance criteria |
| --- | --- | --- |
| US-15 | As an airline administrator, I want flight-status updates, maintenance assessments, and passenger reminders to run independently so a slow background job does not delay the others. | Configure a shared scheduler pool; demonstrate different jobs executing concurrently; log job identity, start, completion, and failures; one failed job does not stop unrelated jobs. |
| US-16 | As an airline administrator, I want upcoming flights assessed concurrently so I can review operational readiness for a batch of flights without waiting for each flight to be assessed sequentially. | A scheduled trigger selects eligible flights; a managed executor processes multiple flights concurrently; each result lists missing crew, incomplete ground services, and aircraft restrictions where those features are implemented; collect individual successes and failures and publish batch completion. |
| US-17 | As an operations administrator, I want recurring jobs to avoid processing the same work twice so overlapping schedules do not create duplicate actions. | A new trigger skips an active batch or claims only unclaimed work; claims are safe under concurrent execution; failed or interrupted work has a defined recovery path; repeated processing cannot duplicate business effects. |
| US-18 | As a customer, I want confirmations and reminders delivered in the background so email delivery does not delay my booking response. | Submit notification work after the relevant transaction commits; process independent notifications concurrently; record delivery failures and support controlled retries without intentionally resending completed notifications. |
| US-19 | As a customer, I want my selected seat protected while other customers are booking so only one active reservation can claim it. | Simultaneous REST requests for the same seat produce one winner and conflict responses for the others; requests for different available seats can succeed; seat counts remain correct after booking, cancellation, and expiry. |
| US-20 | As an operations administrator, I want to inspect background-job progress so I can identify unfinished work and failures. | Show pending, running, completed, and failed outcomes with relevant flight or airplane identifiers; concurrent progress updates remain consistent; expose failure details without secrets or payment credentials. |
| US-21 | As a developer, I want repeatable concurrency demonstrations so I can prove that scheduled jobs and competing requests behave correctly. | Tests coordinate competing requests, assert final database state, and verify overlapping worker execution; cover duplicate work, partial failures, and executor shutdown; compare sequential and concurrent batch processing using the same workload without assuming parallel execution is always faster. |

Implementation direction: Spring `@Scheduled` with `ThreadPoolTaskScheduler` for independent recurring jobs, and an application-managed `ExecutorService` with `Callable`/`Future` for substantial per-flight batch work. `CompletableFuture` is optional for combining stages. Bound concurrency, observe task failures, and give database workers their own transaction boundaries. A readiness assessment is a snapshot; departure requires final protected validation. Scheduled execution processes application work, not simulated physical maintenance or refuelling.

### Stripe payments and booking lifecycle

Initial proposal: Stripe Checkout in test mode, with card payments only. Hold duration, fares, currency, and cancellation/refund policy must be selected before implementation.

| ID | User story | Acceptance criteria |
| --- | --- | --- |
| US-22 | As a customer, I want to temporarily reserve an available seat while paying so another customer cannot buy it during checkout. | Create a pending-payment booking with an expiry; active holds consume availability; competing requests cannot hold the same seat; returning from checkout alone does not confirm a booking. |
| US-23 | As a customer, I want to pay for my reservation through Stripe so I can complete my booking. | The server determines and records the amount and currency; create Checkout for the authenticated customer's eligible reservation; repeated requests reuse the logical payment attempt through idempotency; store Stripe references, not card details; demonstrate success and decline in test mode. |
| US-24 | As a customer, I want my booking confirmed when Stripe verifies successful payment so its status reflects the actual payment outcome. | Verify webhook signatures and the expected payment, booking, amount, and currency; require successful payment status; confirm the booking once; duplicate, concurrent, and out-of-order events cannot duplicate confirmation or regress a final state; send confirmation after commit. |
| US-25 | As a customer, I want abandoned checkout holds released automatically so unpaid reservations do not block seats indefinitely. | A scheduled job processes expired holds in bounded concurrent batches; coordinate checkout expiry with payment reconciliation before releasing inventory; release a seat at most once; test a payment webhook racing with expiry; a late successful payment must never reclaim a seat already allocated to someone else and must enter a recorded refund/reconciliation path. |
| US-26 | As a customer, I want eligible cancellations refunded so I can recover the amount allowed by the cancellation policy. | Validate ownership and policy; record a refund request and process it asynchronously; retries and concurrent cancellation requests cannot create duplicate refunds; track pending, successful, and failed refund outcomes; do not report a refund as successful until confirmed. |
| US-27 | As an airline administrator, I want to inspect payment and refund outcomes for my airline so I can resolve incomplete bookings. | View booking-linked amounts, currency, Stripe references, and payment/refund states; restrict access by airline; flag paid-but-unconfirmed and failed-refund cases for reconciliation. |

Payment design references: [Stripe post-payment handling](https://docs.stripe.com/payments/existing-customers?platform=web&ui=stripe-hosted) supports server-side webhook confirmation rather than relying on a redirect; [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests) explains safe retries of payment operations. These references informed the proposed acceptance criteria.

### Planned ERD models and operational workflows

These stories cover the wider ERD as a selection backlog. Choose a coherent subset rather than implementing every model merely to increase the entity count.

| ID | User story | Acceptance criteria | Planned models |
| --- | --- | --- | --- |
| US-28 | As an airport operator administrator, I want to manage my airports and employees so ground operations have accountable staff. | Associate airports with an operator and employees with an operator and workplace; authorize access through the existing user-account system; reject assignments outside the permitted scope. | `AirportOperator`, `AirportEmployee` |
| US-29 | As an airline administrator, I want to assign qualified crew to flights so each flight has the required personnel. | Record employee, flight, and duty; validate role and airline; reject duplicate or overlapping assignments, including simultaneous requests; define duty coverage and scheduling buffers before implementation. | `CrewAssignment` |
| US-30 | As an airline administrator, I want crew candidates evaluated for several flights concurrently so preparing a flight schedule requires less manual work. | Executor tasks evaluate eligible crew per flight and return proposals; one task failure does not discard other results; applying proposals rechecks availability so competing proposals cannot double-allocate an employee. | `CrewAssignment`, existing `AirlineEmployee` |
| US-31 | As an airport dispatcher, I want to assign and track ground services so I can see which flights still need preparation. | Record service type, assigned employee, and lifecycle status; validate workplace and availability; prevent conflicting employee allocations; staff explicitly start and complete work; repeated completion requests have one effect. | `GroundServiceAssignment`, `AirportEmployee` |
| US-32 | As a maintenance provider administrator, I want to manage technicians and their credentials so maintenance records identify responsible personnel. | Associate technicians with a provider; record license and role; authorize technician actions through the existing account system; prevent unauthorized changes to another provider's records. | `MaintenanceProvider`, `MaintenanceEmployee` |
| US-33 | As a maintenance technician, I want to record maintenance and aircraft release decisions so restricted aircraft are not used for flight operations. | Record airplane, technician, maintenance type, findings, time, and release decision; concurrent scheduling or departure cannot bypass an active restriction; keep an audit trail and notify affected airline staff. | `MaintenanceRecord` |
| US-34 | As an airline administrator, I want scheduled maintenance assessments to process airplanes concurrently so I can identify aircraft needing attention. | Apply explicitly defined maintenance rules to independent airplanes using executor tasks; return per-airplane findings; prevent duplicate alerts across overlapping runs; do not automatically certify an airplane as safe based only on a background calculation. | `MaintenanceRecord`, existing `Airplane` |
| US-35 | As an air-navigation administrator, I want to manage control facilities and their controllers so flight events have an authorized operational source. | Associate facilities with a provider and, where applicable, an airport; associate controllers with a facility and an authenticated account; restrict actions to authorized scope. | `AirNavigationServiceProvider`, `ControlFacility`, `ATCEmployee` |
| US-36 | As an air traffic controller, I want to record permitted flight-control events so the flight's operational history remains consistent. | Record controller, flight, event type, time, and notes; validate permitted state transitions; concurrent conflicting transitions cannot both succeed; save flight state and event together and publish updates after commit. | `FlightControlEvent` |

### Proposed model mapping

| Model group | Models | Scope notes |
| --- | --- | --- |
| Airport operations | `AirportOperator`, `AirportEmployee`, `GroundServiceAssignment` | Already planned in the full ERD; ground-service lifecycle status and allocation intervals need definition. |
| Crew | `CrewAssignment` | Already planned; scheduling checks use flight intervals plus the chosen duty buffers. |
| Maintenance | `MaintenanceProvider`, `MaintenanceEmployee`, `MaintenanceRecord` | Already planned; maintenance rules and permissions need definition. |
| Air traffic control | `AirNavigationServiceProvider`, `ControlFacility`, `ATCEmployee`, `FlightControlEvent` | Already planned; optional broader scope for this iteration. |
| Payment attempts | `Payment` | Proposed addition to the ERD: booking link, amount, currency, status, Stripe identifiers, idempotency reference, timestamps. A booking can have multiple attempts but must not be fulfilled twice. |
| Refunds | `Refund` | Proposed addition: payment link, amount, reason, status, Stripe refund identifier, idempotency reference, timestamps. |
| Webhook processing | `PaymentWebhookEvent` | Proposed addition: unique Stripe event identifier, event type, processing state, timestamps, and failure information for durable deduplication and recovery. |

Extend existing `Booking` with pending-payment/expiry behavior and an agreed price snapshot. A separate seat-hold entity is optional, not required merely to add a model. Persistent job-run tracking can be added if required by US-20; its storage design remains undecided. This mapping proposes future schema work and does not change the implemented ERD.
