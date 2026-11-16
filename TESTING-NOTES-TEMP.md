# Test setup and legacy fixtures

## Approved test setup

The original service tests use JUnit 4 `Test`, `Assert`, `@Before`, `SpringRunner`, `@SpringBootTest`, and `@Transactional`. Newer focused checks use JUnit Jupiter. Mockito mocks and spies substitute external clients and inject failures; database fixtures exercise real persistence. JUnit 4 reports may show method names instead of `DisplayName` labels. See the [integration-test guide](integration-tests/README.md) for the current suite layout and coverage.

Show future proposed code changes and wait for approval before implementing them.

## Accounts required by legacy database fixtures

| Email | Test password | Role | Status | Active |
| --- | --- | --- | --- | --- |
| flighttest.customer@mailsac.com | TestPassword1 | CUSTOMER | ACTIVE | true |
| flighttest.faa@mailsac.com | TestPassword1 | FAA_ADMIN | ACTIVE | true |
| flighttest.airline@mailsac.com | TestPassword1 | AIRLINE_EMPLOYEE | ACTIVE | true |

Store the password using the application's PasswordEncoder (BCrypt), not as plaintext. Give the account a normal customer profile and zero failed login attempts. Its generated user ID can be any value; tests find it by email.

The FAA account uses the `FAA_ADMIN` role; there is no separate FAAAdmin entity. Give the airline account a linked AirlineEmployee record with airlineRole ADMIN and a valid airline. All three accounts need personal profiles, zero failed login attempts, and encoded passwords. User and profile IDs can be generated normally.

WrongPassword1 is deliberately incorrect and must not be the seeded password. These are test credentials only.

## Flight and booking required by legacy fixtures

- Create an ACTIVE test airplane with at least two standard seats and a valid airline.
- Create an ACTIVE flight for that airplane and airline with valid origin and destination airports and a flight number, for example TEST101. Its generated flight ID can be any value.
- Set one standard seat as booked and the remaining standard seats as available. For a two-seat airplane, standardSeatsCount is 1.
- Keep actualDeparture and actualArrival null. Set scheduled departure three days in the future and arrival two hours later.
- Create booking ID **900001**, bookingRef **TEST-BOOKING-900001**, owned by **flighttest.customer@mailsac.com**, for this flight, with seatNumber **standard-1**, status **BOOKED**, and a valid bookedAt timestamp.
- Reserve that booking ID for these tests. If inserting an explicit identity ID, keep the database booking ID sequence above existing IDs.
- Tests look up this booking by ID and obtain its flight from the booking. They adjust scheduled times inside their rolled-back transaction so the fixture does not expire.

## Additional admin seed records

- Use the airline admin's airline for the additional airplanes below. Give the airline a valid unique name and code, for example Test Seed Airline / TST.
- The FAA approval test creates its own GROUNDED airplane and PENDING activation request inside its rollback transaction. It no longer requires seed request **900002** to remain pending.
- Create a separate ACTIVE airplane with ID **900003**, registrationNumber **TEST-FREE-PLANE**, positive standard and first-class seat capacities, and positive maxMileage, owned by the airline admin's airline. It must have no flights. The flight creation test uses this airplane.
- Create airports with IATA codes **BAH** and **DXB**, with valid city/country details.
- Do not seed airline name **Test Created Airline**, airline code **TCA**, or airplane registration **TEST-NEW-PLANE**. Tests create them and roll back afterward.
- Keep identity sequences above explicit seed IDs for airplane requests and airplanes too.
- Admin tests use the application's JSON mapper to populate getter-only request objects; production request classes are unchanged.

## Original fifteen-test coverage (historical)

1. Valid password accepted.
2. Weak passwords rejected.
3. Correct credentials authenticate the seeded account.
4. Incorrect password rejected.
5. No available standard seats prevents booking.
6. Cancellation changes booking status and returns the standard seat to availability.
7. Customer cancellation within 48 hours is rejected without returning a seat.
8. Customer cannot use the airline booking search service.
9. FAA admin creates an airline through AirlineService.
10. FAA admin approval activates an airplane through FAAAdminService.
11. Airline admin cannot approve an airplane activation request.
12. Airline admin adds an airplane through AirplaneService.
13. Airline admin creates a flight through AirlineEmployeeService.
14. Customer cannot create a flight.
15. FlightService changes a departed flight to IN_AIR.

Cancellation and seat restoration also cover important service-layer logic. AuthenticationTest exercises the real AuthenticationManager, not UserService login-attempt limits or JWT generation. These are minimal tests, not full HTTP security coverage.

## Running

Run commands from the repository root. Configure a dedicated PostgreSQL test database and prepare legacy fixtures using [SAMPLE-DATABASE.md](SAMPLE-DATABASE.md). Missing fixtures fail tests rather than being silently skipped. Install the application JARs using the commands in the [integration-test guide](integration-tests/README.md) before running that module.

```powershell
# Password checks need no database.
.\mvnw.cmd -f identity-service/pom.xml test '-Dtest=PasswordServiceTest'
# Authentication checks need the seeded Identity test account.
.\mvnw.cmd -f identity-service/pom.xml test '-Dtest=AuthenticationTest' '-Dapp.seed.enabled=false'
# Legacy service fixtures now live in the integration-test module.
.\mvnw.cmd -f integration-tests/pom.xml test '-Dtest=BookingServiceTest,FAAAdminServiceTest,AirlineEmployeeServiceTest,FlightServiceTest' '-Dapp.seed.enabled=false'
```

Use the module's example properties as a starting point for local configuration. Aviation contexts require `stripe.secret-key`, `stripe.success-url`, and `stripe.cancel-url`; tests that do not call Stripe can use a nonfunctional `sk_test_` placeholder and loopback callback URLs. Never use live payment credentials for these fixtures.

Most legacy service tests roll back their transactions. Concurrency and Cucumber tests that commit fixtures have explicit cleanup; do not interrupt them during cleanup. Application test contexts explicitly set `app.scheduling.enabled=false` so background jobs do not mutate fixtures. Continue using a dedicated test database for the service operations themselves.

The combined in-process fixtures replace email clients and Kafka publication with test substitutes. They do not send real SMTP email or prove HTTP/Kafka transport. The separate Docker notification runner uses running services, real Kafka, and the normal email path; SMTP delivery is not asserted. Follow its separate setup in the [integration-test guide](integration-tests/README.md).
