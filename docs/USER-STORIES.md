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
