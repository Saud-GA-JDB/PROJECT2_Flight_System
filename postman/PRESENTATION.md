# Postman presentation

For the shorter flow, import **00-main-presentation.postman_collection.json** and the shared environment. Its 37 requests include the selected business flow, role logins, ID lookups, registration, search and booking/cancellation. Send them in order, pausing for the emailed verification code. This collection uses the same demo identities as the full collections: use either flow after cleanup, not both creation flows consecutively. The nine full collections remain available for additional examples.

Import all nine `*.postman_collection.json` files and `Presentation.postman_environment.json` from this folder into Postman. Select **Sauds Flight System - Presentation** as the environment. These are importable files; they have not been installed into a Postman workspace.

Start the application using your configured PostgreSQL and mail service at `http://localhost:8080`. Restart it if it was running an older build. Run requests individually in their numbered order. The status in each request name is the expected result, including intentional rejections. Open **Tests / Test Results** after sending; a successful rejection demonstration should have passing assertions.

## Presentation order

| Collection | Story and validation examples |
| --- | --- |
| 01 | FAA logs in, rejects incomplete airline details, creates Presentation Airways, rejects duplicate airline, validates administrator details, assigns admin, rejects duplicate identity. |
| 02 | New airline admin logs in, fails FAA permission check, validates airplane fields, adds two grounded airplanes, rejects duplicate registration, looks up their IDs using FAA credentials, rejects grounded flight and foreign ownership, requests activation, rejects duplicate pending request. |
| 03 | FAA lists requests, rejects invalid decision/missing denial reason/oversized reason, approves one airplane and denies the other. Repeat review, already-active request and seven-day cooldown are rejected. |
| 04 | Validate required fields, future departure, airports, time order, distinct airports and ownership. Create a flight seven days ahead, reject overlap, create another flight tomorrow, capture both flight IDs. |
| 05 | Customer registers, verifies email, logs in with CPR, cannot search before setup, demonstrates invalid setup, finishes setup, logs in with new password. |
| 06 | Customer searches flights and demonstrates date, class, pagination, sorting and authentication checks. |
| 07 | Validate seats and booking permissions, book a seat, reject duplicate seat, list/search bookings, cancel and reject repeated cancellation. Customer cannot cancel tomorrow's flight; owning airline admin can. |
| 08 | Validate profile permissions and phone/security fields, update profile, change/reset password, then deactivate demo customer and reject login. Deactivation must be last for customer operations. |
| 09 | Open manually alongside collection 02 to show FAA live notifications. It is a persistent stream, not a normal runner step. |

For each request, explain the input, predict the status, send it, then show the response and tests. A body is included only when the controller accepts one. Airline creation uses URL-encoded form fields; profile updates use a multipart `request` part with `application/json`; booking inputs are path parameters.

## Two manual presentation steps

1. In collection 05, after requesting verification email, open the inbox for `sauds.presentation.customer@mailsac.com`. Enter the received code into the selected environment's `verificationCode`. Finish verification within ten minutes. Run the wrong-code example only once: three failures exhaust the attempts. These public demo addresses contain fictional information; to use different inboxes, edit the literal emails in the requests (or edit build_collections.py and rebuild), the matching environment values used by scripts, and DEMO-MANIFEST.json before starting.
2. To show notifications, login in collection 01, open collection 09's FAA subscription in a separate tab, then send the activation request in collection 02. The stream emits `connected`, then `airplane-activation-requested` after a successful transaction. It has 25-second keep-alives and a five-minute timeout. Reopen if needed. A fresh subscription does not replay past events.

## Shared state and repeating the demonstration

Login scripts save the JWT from response `message` into `faaToken`, `adminToken` or `customerToken`. Requests inherit the appropriate bearer authentication; a few explicit overrides switch roles. No real JWT is bundled. The seeded FAA login is `flighttest.faa@mailsac.com` / `TestPassword1`; its active database row was checked, but the password was not tested by logging in.

Fixed demonstration values are written directly in the requests. Only generated IDs, tokens, dates and the verification code remain dynamic. All generated IDs live in the selected environment. Airplane creation omits its ID, so collection 02 finds it by registration in the FAA airline listing. Flight creation omits its ID, so collection 04 resolves it by flight number through search. Setup returns the customer **user ID**. Never substitute a customer profile ID for it.

The first flight request initializes future dates once. The JSON datetimes have no timezone because the server accepts LocalDateTime; interpret them in the server's local timezone. Preserve them through the rehearsal. After database cleanup, reimport the original environment to clear IDs, tokens, verification code and generated dates. New dates will be generated for the next presentation. IDs and booking references can change after cleanup; automatic capture handles that.

Creation examples are intentionally not idempotent: rerunning successful creations without cleanup produces conflicts. Stop when a success request fails, resolve the cause, then continue. In particular, email failures can roll back airplane, activation/review and booking changes. Registration email failure can leave a pending registration. Do not blindly run every collection: verification pauses and SSE require manual operation.

Ask to **reset the presentation data using postman/DEMO-MANIFEST.json** when ready. The manifest records exact identities and the baseline; it survives chat context changes. No cleanup has been run and no demo records were created while building these files. Database inspection was read-only. Do not delete seeded accounts, existing airlines/airplanes, or airports. Cleanup should inspect actual relationships first and remove only confirmed demo records and their related rows. Already delivered emails cannot be removed by database cleanup.

## How the validation works

| Layer / source | What to explain |
| --- | --- |
| SecurityConfiguration, JwtRequestFilter, MyUserDetails | Registration/login/verification are public; setup requires authentication; other routes require ACCOUNT_ACTIVE. JWT is checked and the user is loaded again for each request. Role checks occur in services. Missing/invalid bearer authentication is expected to return 403 with this configuration. |
| AirlineService | FAA-only; nonblank name/code/country; unique name or code queried before insertion. Country is not checked against a country catalog. |
| AirlineEmployeeService.addAirlineAdmin | FAA-only; exactly nine CPR digits, email format, nonblank names, required/valid phone and opening code, positive salary, parseable ISO hire date, required security question/answer, unique CPR across person tables and unique user email, existing airline. Hire date is parsed but future dates are not explicitly prohibited. |
| AirplaneService | Airline ADMIN-only; registration/model nonblank, both seat capacities and mileage positive, unique registration. Activation checks ownership, airplane state, pending request, and seven days since previous request. |
| FAAAdminService | FAA profile/role required; only ACCEPTED or DENIED; denial needs a reason; reason <=500 characters; request must exist and still be pending. |
| AirlineEmployeeService.addFlight / AirplaneService.checkAvailability | Required times/airports, future departure, known airport codes, arrival strictly later, distinct airports, airplane ownership, grounded restriction, two-hour scheduling gap around non-cancelled flights. |
| PendingRegistrationService | Valid email and nine-digit CPR, unique identity, no unexpired pending registration. BCrypt verification code comparison, ten-minute expiry, maximum three failures; correct code creates SETUP_REQUIRED account with CPR password. |
| UserService.finishSetup / PasswordService / PhoneValidationService | Setup state, required fields, Passay password rules, libphonenumber phone validation, verified pending record. Activation and pending-record deletion complete setup. Password and security answer are BCrypt encoded. |
| FlightService | Parse date and prohibit past dates, exact seat class, integer page/size, page >=1, size 1–100, allowlisted sorting fields/directions. Repository filters bookable flights and available seats. Unknown airline/route filters can simply produce an empty result rather than an input error. |
| BookingService | User/flight existence, ACTIVE flight, departure >5 minutes away, seat class/range/remaining capacity/occupancy, active airplane, self-booking or owning airline admin. Cancellation checks ownership/admin, BOOKED state, and customer 48-hour cutoff; restores capacity. Airline booking searches are scoped to the administrator airline. |
| UserService.updateProfile | Only FAA can target another user or change protected identity fields; cannot change someone else's security answer. Validate identity uniqueness/format, nonblank submitted names, phone, paired question/answer. Only deactivation is supported. Optional file handling validates filename path separators, not image contents. |
| UserService.loginUser | Authentication and account state; third bad password throws 429 and further attempts are blocked while the stored count is at least three. A successful login resets the count; Bahrain date rollover also resets it. Main presentation avoids locking accounts. |
| UserService.forgetPassword | Requires active bearer auth under current security configuration; checks target email and normalized security answer, resets customer password to CPR and sends email. It is not a public forgotten-password workflow. |
| GlobalExceptionHandler | Maps validation to 400, missing records to 404, conflicts to 409, service permission denial to 403, authentication to 401, login limit to 429, mail/unhandled errors to 500. Airplane fields/password change use 422; late customer cancellation uses 417. |

## Limits and honest discussion points

The 114 prepared examples cover all 21 HTTP endpoints and major success/rejection paths; they are not exhaustive branch or concurrency tests. Code and database prerequisites were inspected, JSON parsed, scripts syntax-checked and endpoint coverage checked; HTTP requests were left for your rehearsal. Expected statuses are source-derived, not a claim that every request has passed against the running application.

- Password validation actually permits **8–30 characters**, uppercase and digit, no whitespace. Its error text incorrectly says maximum 20. No lowercase/symbol requirement exists.
- Generic exception handling can turn malformed JSON, wrong enum values, bad path types or missing multipart parts into **500**. These are bugs to discuss if encountered, not correct 400 validation demonstrations. The main negative examples use well-formed inputs that reach explicit validation.
- Timer-dependent rules (verification expiry, full verification/login lockout, five-minute booking cutoff), completely sold-out flights, image upload and concurrent double booking require additional controlled setup. They are explained above but not forced during the main presentation.
- The booking route allows any authenticated active user to book for themselves; its `/customer` URL does not itself enforce the CUSTOMER role. Do not claim that it does.
- Password reset assumes a customer profile and does not require the target email to match the caller. Demonstrate only the demo customer's own reset.
- No backend code was changed as part of this package. Existing local modifications were preserved.

To rebuild the JSON files after editing the examples, run `python postman/build_collections.py`. This overwrites the generated local JSON artifacts only; it does not contact the application or update an imported Postman workspace.
