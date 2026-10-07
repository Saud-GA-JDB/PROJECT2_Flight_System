# Temporary testing notes

## Approved test setup

Tests follow CalculatorTest.java: JUnit 4 Test, Assert, expected exceptions, public final void methods, descriptive when...Then... names, and DisplayName annotations. The user approved JUnit 4 support, @Before instead of @BeforeEach, SpringRunner, @SpringBootTest, and @Transactional. No Mockito mocks are used. JUnit 4 reports may show method names instead of DisplayName labels.

Show future proposed code changes and wait for approval before implementing them.

## Accounts for future database seeding

| Email | Test password | Role | Status | Active |
| --- | --- | --- | --- | --- |
| flighttest.customer@mailsac.com | TestPassword1 | CUSTOMER | ACTIVE | true |
| flighttest.faa@mailsac.com | TestPassword1 | FAA_ADMIN | ACTIVE | true |
| flighttest.airline@mailsac.com | TestPassword1 | AIRLINE_EMPLOYEE | ACTIVE | true |

Store the password using the application's PasswordEncoder (BCrypt), not as plaintext. Give the account a normal customer profile and zero failed login attempts. Its generated user ID can be any value; tests find it by email.

Give the FAA account a linked FAAAdmin profile. Give the airline account a linked AirlineEmployee profile with airlineRole ADMIN and a valid airline. All three accounts need zero failed login attempts and encoded passwords. User and profile IDs can be generated normally.

WrongPassword1 is deliberately incorrect and must not be the seeded password. These are test credentials only.

## Flight and booking for future database seeding

- Create an ACTIVE test airplane with at least two standard seats and a valid airline.
- Create an ACTIVE flight for that airplane and airline with valid origin and destination airports and a flight number, for example TEST101. Its generated flight ID can be any value.
- Set one standard seat as booked and the remaining standard seats as available. For a two-seat airplane, standardSeatsCount is 1.
- Keep actualDeparture and actualArrival null. Set scheduled departure three days in the future and arrival two hours later.
- Create booking ID **900001**, bookingRef **TEST-BOOKING-900001**, owned by **flighttest.customer@mailsac.com**, for this flight, with seatNumber **standard-1**, status **BOOKED**, and a valid bookedAt timestamp.
- Reserve that booking ID for these tests. If inserting an explicit identity ID, keep the database booking ID sequence above existing IDs.
- Tests look up this booking by ID and obtain its flight from the booking. They adjust scheduled times inside their rolled-back transaction so the fixture does not expire.

## Additional admin seed records

- Use the airline admin's airline for the additional airplanes below. Give the airline a valid unique name and code, for example Test Seed Airline / TST.
- Create a separate GROUNDED airplane and a PENDING activation request with ID **900002** for it. Set requestedBy to flighttest.airline@mailsac.com, requestedAt to a valid timestamp, and reviewedBy/reviewedAt to null. The request's airplane ID can be generated normally. This airplane must not be the ACTIVE booking airplane.
- Create a separate ACTIVE airplane with ID **900003**, registrationNumber **TEST-FREE-PLANE**, positive standard and first-class seat capacities, and positive maxMileage, owned by the airline admin's airline. It must have no flights. The flight creation test uses this airplane.
- Create airports with IATA codes **BAH** and **DXB**, with valid city/country details.
- Do not seed airline name **Test Created Airline**, airline code **TCA**, or airplane registration **TEST-NEW-PLANE**. Tests create them and roll back afterward.
- Keep identity sequences above explicit seed IDs for airplane requests and airplanes too.
- Admin tests use the application's JSON mapper to populate getter-only request objects; production request classes are unchanged.

## Fifteen tests

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

- Run the two password tests without a database: mvn "-Dtest=PasswordServiceTest" test
- Run all fifteen after seeding and database/mail configuration are ready: mvn "-Dtest=PasswordServiceTest,AuthenticationTest,BookingServiceTest,FAAAdminServiceTest,AirlineEmployeeServiceTest,FlightServiceTest" test
- Missing database fixtures cause failures rather than silently skipped tests.
- Database test transactions roll back after each test. Booking tests clear the logged-in security context afterward.
- The successful cancellation test sends a real email to flighttest.customer@mailsac.com using the configured mail server. Database rollback does not undo sent emails.
- Airplane addition and FAA approval send real emails to flighttest.airline@mailsac.com. These are not undone by rollback either.
- Database tests start the full application, including scheduled jobs; use a dedicated test database.
