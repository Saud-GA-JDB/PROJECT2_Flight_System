package com.ga.saudsFlightSystem.config;

import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.model.request.AddAirlineAdminRequest;
import com.ga.saudsFlightSystem.model.request.response.*;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI flightSystemOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Saud's Flight System API").version("1.0")
                        .description("Flight booking, Stripe payments, airline and crew administration, FAA airplane approvals, "
                                + "airport accounts and flight inspections. SSE notifications are served separately on port 8084. "
                                + "Log in through Identity on port 8081, copy the JWT, then use Authorize. "
                                + "Unless marked public, endpoints require an active account, except account setup which requires authentication only. "
                                + "Role and ownership rules apply as described on each operation."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer flightSystemDocumentation() {
        return openApi -> {
            document(openApi, "/flights/search", PathItem.HttpMethod.GET,
                    "Search flights",
                    "Active account required. Filters by route, date, airline, and seat class with pagination and sorting.",
                    "200", FlightSearchResponse.class, false);
            document(openApi, "/customer/{userId}/flights/{flightId}/{seatType}/{seatId}/book", PathItem.HttpMethod.POST,
                    "Book a flight seat",
                    "Active account required. Service checks the target user, flight, and seat availability before creating the booking. Actual departure blocks booking even if status is ACTIVE; the five-minute scheduled cutoff remains.",
                    "201", BookingResponse.class, false);
            document(openApi, "/bookings", PathItem.HttpMethod.GET,
                    "List bookings",
                    "Active account required. Airline admins see their airline's bookings; non-airline accounts see their own bookings. Other airline employees are rejected.",
                    "200", Booking.class, true);
            document(openApi, "/bookings/search", PathItem.HttpMethod.GET,
                    "Search airline bookings",
                    "Airline admin only. Searches bookings within the current airline.",
                    "200", Booking.class, true);
            document(openApi, "/bookings/{bookingId}", PathItem.HttpMethod.DELETE,
                    "Cancel booking",
                    "Booking owner or admin of the booking's airline. Pending or paid payments block cancellation. Customer cancellation requires at least 48 hours before departure.",
                    "200", BookingResponse.class, false);
            document(openApi, "/faaadmin/airlines", PathItem.HttpMethod.POST,
                    "Create airline",
                    "FAA admin only. Supply name, airlineCode, and headquartersCountry as query parameters.",
                    "200", AirlineResponse.class, false);
            document(openApi, "/faaadmin/airlines", PathItem.HttpMethod.GET,
                    "List airlines",
                    "FAA admin only.",
                    "200", AirlineResponse.class, true);
            document(openApi, "/faaadmin/airlines/{airlineId}/addAirlineAdmin", PathItem.HttpMethod.POST,
                    "Create airline admin",
                    "FAA admin only. Creates an admin for the specified airline. The current response echoes the request object.",
                    "201", AddAirlineAdminRequest.class, false);
            document(openApi, "/faaadmin/airplaneRequests", PathItem.HttpMethod.GET,
                    "List pending airplane requests",
                    "FAA admin only. Lists pending activation requests across airlines.",
                    "200", AirplaneRequestResponse.class, true);
            document(openApi, "/faaadmin/airplaneRequests/{requestId}", PathItem.HttpMethod.PUT,
                    "Review airplane activation",
                    "FAA admin only. Accepts or denies a pending activation request.",
                    "200", AirplaneRequestResponse.class, false);


            document(openApi, "/faaadmin/airportOperators", PathItem.HttpMethod.POST,
                    "Create airport operator",
                    "FAA admin only.",
                    "201", AirportOperatorResponse.class, false);
            document(openApi, "/faaadmin/airports/{airportId}/operator", PathItem.HttpMethod.PUT,
                    "Link airport to operator",
                    "FAA admin only. Rejects linking the current operator again or changing operators when the airport has employees.",
                    "200", String.class, false);
            document(openApi, "/faaadmin/airports/{airportId}/addAirportOperatorAdmin", PathItem.HttpMethod.POST,
                    "Create airport admin",
                    "FAA admin only. Creates an admin for this airport, which must have an operator.",
                    "201", AirportEmployeeResponse.class, false);
            document(openApi, "/airlineAdmin/airplanes", PathItem.HttpMethod.POST,
                    "Register airplane",
                    "Airline admin only. Creates a grounded airplane for the current airline and sends a notification after commit.",
                    "201", AddAirplaneResponse.class, false);
            document(openApi, "/airlineAdmin/airplanes/{airplaneId}/requestActivation", PathItem.HttpMethod.POST,
                    "Request airplane activation",
                    "Admin of the airplane's airline. Creates a pending FAA review request and notifies FAA admins after commit.",
                    "201", AirplaneRequestResponse.class, false);
            document(openApi, "/airlineAdmin/airplanes/{airplaneId}/addFlight", PathItem.HttpMethod.POST,
                    "Schedule flight",
                    "Admin of the airplane's airline. Requires an active, available airplane and positive prices in the smallest currency unit. Creates a PENDING_CREW flight.",
                    "201", AddFlightResponse.class, false);
            document(openApi, "/airlineAdmin/flights/{flightId}/addCrewAssignment", PathItem.HttpMethod.POST,
                    "Assign crew to a flight",
                    "Admin of the flight's airline. Requires PENDING_CREW or ACTIVE with both actual times null. Rejects duplicate assignments, incompatible duties, and scheduling conflicts. Sends email and SSE notifications after commit.",
                    "201", CrewAssignmentResponse.class, false);
            document(openApi, "/airlineAdmin/flights/{flightId}/crewAssignments", PathItem.HttpMethod.GET,
                    "List flight crew",
                    "Admin of the flight's airline.",
                    "200", getCrewAssignmentsResponse.class, true);
            document(openApi, "/airlineAdmin/flights/{flightId}/crewAssignments/{employeeId}", PathItem.HttpMethod.DELETE,
                    "Remove crew assignment",
                    "Admin of the flight's airline. Requires PENDING_CREW or ACTIVE with both actual times null; delayed flights are allowed and active flights must retain required crew.",
                    "204", null, false);
            document(openApi, "/airlineAdmin/flights/{flightId}/activate", PathItem.HttpMethod.PUT,
                    "Activate flight",
                    "Admin of the flight's airline. Requires a PENDING_CREW flight with both actual times null, a pilot, secondary pilot, and flight attendant; delayed flights are allowed. Inspection clearance is not enforced here.",
                    "200", String.class, false);


            document(openApi, "/airlineAdmin/flights/{flightId}/reinspection", PathItem.HttpMethod.POST,
                    "Request reinspection",
                    "Admin of the flight's airline. Requires a failed latest inspection and a PENDING_CREW or ACTIVE flight with both actual times null. Creates a pending attempt awaiting assignment.",
                    "201", String.class, false);
            document(openApi, "/airlineAdmin/flights/{flightId}/inspections", PathItem.HttpMethod.GET,
                    "List flight inspection history",
                    "Admin of the flight's airline. Includes previous inspection attempts.",
                    "200", FlightInspectionResponse.class, true);
            document(openApi, "/airportAdmin/employees", PathItem.HttpMethod.POST,
                    "Create airport employee",
                    "Airport admin only. Creates an INSPECTOR or BAGGAGE_HANDLER at the current airport. The operator is derived from the airport.",
                    "201", AirportEmployeeResponse.class, false);
            document(openApi, "/airportAdmin/flights/{flightId}/inspection", PathItem.HttpMethod.POST,
                    "Assign flight inspector",
                    "Origin-airport admin only. Both actual times must be null and phase PENDING_CREW or ACTIVE. Inspector must belong to the same airport and operator. Creates the first inspection or assigns the pending attempt.",
                    "200", String.class, false);
            document(openApi, "/airportAdmin/inspections", PathItem.HttpMethod.GET,
                    "List airport inspections",
                    "Airport admin only. Returns inspections at the current airport.",
                    "200", FlightInspectionResponse.class, true);
            document(openApi, "/airportInspector/flights/{flightId}/inspections/{inspectionId}/complete", PathItem.HttpMethod.PUT,
                    "Complete flight inspection",
                    "Assigned inspector only. Both actual times must be null and phase PENDING_CREW or ACTIVE. Completes the latest pending attempt as PASSED or FAILED. Optional reason is limited to 2000 characters.",
                    "200", String.class, false);
            document(openApi, "/airportInspector/inspections", PathItem.HttpMethod.GET,
                    "List assigned inspections",
                    "Airport inspector only. Returns inspections assigned to the current inspector.",
                    "200", FlightInspectionResponse.class, true);








            document(openApi, "/payments/bookings/{bookingId}/checkout", PathItem.HttpMethod.POST,
                    "Start Stripe checkout",
                    "Booking owner only. Creates or reuses a sandbox checkout session. Requires an active booking and flight with no actual departure, positive pricing, and scheduled departure more than five minutes away. Existing payment reconciliation remains allowed after departure.",
                    "200", CheckoutResponse.class, false);
            document(openApi, "/payments/bookings/{bookingId}", PathItem.HttpMethod.GET,
                    "Refresh payment status",
                    "Booking owner only. Queries Stripe and updates the stored payment. Amounts use the smallest currency unit. No webhook reconciliation is implemented.",
                    "200", PaymentResponse.class, false);
            document(openApi, "/payments/success", PathItem.HttpMethod.GET,
                    "Checkout success landing page",
                    "Public informational response. Does not verify payment; call the booking payment-status endpoint.",
                    "200", String.class, false);
            document(openApi, "/payments/cancel", PathItem.HttpMethod.GET,
                    "Checkout cancellation landing page",
                    "Public informational response. Does not cancel the booking or expire the checkout session.",
                    "200", String.class, false);

            document(openApi, "/faaadmin/airNavigationProviders", PathItem.HttpMethod.POST,
                    "Create air navigation provider",
                    "Active FAA admin only. Requires a nonblank name (maximum 150 characters) and valid contactEmail (maximum 254). Names and emails may repeat. Creation and audit commit together.",
                    "201", ProviderResponse.class, false);
            document(openApi, "/faaadmin/airNavigationProviders/{providerId}/controlFacilities", PathItem.HttpMethod.POST,
                    "Create airport control tower",
                    "Active FAA admin only. Requires positive providerId and airportId and a nonblank name (maximum 150 characters). One tower per airport across all providers; duplicates return 409 and missing references return 404.",
                    "201", FacilityResponse.class, false);
            document(openApi, "/faaadmin/controlFacilities/{facilityId}/admins", PathItem.HttpMethod.POST,
                    "Create tower administrator",
                    "Active FAA admin only. Creates an active ATC account with ADMIN role at the route's tower. All staff fields are required; CPR is nine digits, employeeNumber is trimmed uppercase (1–50 characters). Uses existing email/phone/password validation. Duplicate identities return 409. Multiple admins per tower are allowed.",
                    "201", ATCEmployeeResponse.class, false);
            document(openApi, "/atcAdmin/controllers", PathItem.HttpMethod.POST,
                    "Create own-tower controller",
                    "Active ATC ADMIN only. Derives the tower from the account and fixes the new role to CONTROLLER. All staff fields are required; employeeNumber is normalized uppercase (1–50 characters). Credentials are encoded. Extra JSON fields cannot override role, tower, actor or account state.",
                    "201", ATCEmployeeResponse.class, false);
            document(openApi, "/atc/facility", PathItem.HttpMethod.GET,
                    "View own control tower",
                    "Active ATC ADMIN or CONTROLLER only. Returns the account's own tower. Shared provider membership grants no access to another tower.",
                    "200", FacilityResponse.class, false);
            for (String path : List.of("/faaadmin/airNavigationProviders",
                    "/faaadmin/airNavigationProviders/{providerId}/controlFacilities", "/atcAdmin/controllers")) {
                Operation list = operation(openApi, path, PathItem.HttpMethod.GET);
                if (list != null) {
                    list.setDescription("Active FAA admin for provider/tower lists; active own-tower ATC ADMIN for controller lists. "
                            + "Page defaults to 0 (minimum 0); size defaults to 20 (1–100). Sorted by entity ID ascending. "
                            + "Counts and content are scoped before pagination. Controllers exclude admins and include inactive accounts. No business audit is written.");
                }
            }

            for (String path : List.of("/pilot/flights/{flightId}", "/atc/flights/{flightId}",
                    "/airlineAdmin/flights/{flightId}/operations")) {
                document(openApi, path, PathItem.HttpMethod.GET, "View scoped flight operations",
                        "Active account required. Pilot: currently assigned PILOT or SECONDARY_PILOT. ATC: flight touches own tower's airport. Airline: own-airline ADMIN. "
                                + "Includes current crew, latest inspection and effective clearance status; expiry never writes state. No passenger or payment details. Missing flight 404; forbidden scope 403.",
                        "200", FlightOperationSummary.class, false);
            }
            document(openApi, "/pilot/flights/{flightId}/readiness", PathItem.HttpMethod.GET,
                    "Check assigned flight readiness",
                    "Current assigned pilot only. Operation derives from phase; terminal flights return no operation and all readiness flags false. "
                            + "Blocked readiness is 200 with blocker codes and appliesTo stages. Flags describe lifecycle eligibility, not permission to perform another role's action. "
                            + "Read-only snapshot: no approval, resource reservation, audit or SSE. evaluatedAt is UTC; actual flight times use Asia/Bahrain.",
                    "200", ReadinessResponse.class, false);
            document(openApi, "/atc/flights/{flightId}/readiness", PathItem.HttpMethod.GET,
                    "Check responsible tower's flight readiness",
                    "Active tower ADMIN or CONTROLLER. operation is required: DEPARTURE for the origin tower, ARRIVAL for the destination. "
                            + "Shared provider grants no wider access. Blocked readiness returns 200; wrong scope 403; invalid/missing operation 400. "
                            + "Departure checks crew, latest inspection, FAA status and actual occupancy. Arrival requires airborne phase and consistent actual times; no fresh inspection/FAA/resource checks.",
                    "200", ReadinessResponse.class, false);
            for (String path : List.of("/pilot/flights", "/atc/flights", "/atc/requests",
                    "/pilot/flights/{flightId}/controlEvents", "/atc/flights/{flightId}/controlEvents",
                    "/airlineAdmin/flights/{flightId}/controlEvents")) {
                Operation list = operation(openApi, path, PathItem.HttpMethod.GET);
                if (list != null) {
                    list.setDescription("Active scoped account required. Pilot: current pilot assignment; ATC: own airport/tower; airline: owning ADMIN. "
                            + "ATC history includes only own-tower events; pilot and owning airline history includes all flight events. "
                            + "page defaults to 0, size to 20 (1–100); scope applies before counts/paging. Flight lists sort by scheduledDeparture then id; requests by flight id; history by event id descending. "
                            + "Flight date filters are inclusive Asia/Bahrain scheduled-departure dates. Requests include only PENDING clearances for the current tower. "
                            + "No writes/audits/notifications. Invalid filters return 400; forbidden scope 403; missing flight 404.");
                }
            }

            for (String path : List.of("/pilot/flights/{flightId}/requestTakeOff", "/pilot/flights/{flightId}/requestLanding",
                    "/pilot/flights/{flightId}/clearance/withdraw", "/atc/flights/{flightId}/clearance/decision",
                    "/atc/flights/{flightId}/clearance/direct", "/atc/flights/{flightId}/clearance/revoke")) {
                document(openApi, path, PathItem.HttpMethod.POST, "Change flight clearance",
                        "Active account required: current assigned pilot for pilot routes; responsible own-tower ADMIN/CONTROLLER for ATC routes. "
                                + "expectedRevision is required and nonnegative. DEPARTURE uses origin; ARRIVAL uses destination. "
                                + "Decision accepts APPROVED or REJECTED only. Rejection/revocation require a trimmed reason (1-2000 characters). "
                                + "Approval lasts exactly 20 minutes; expiry is effective at equality. Direct approval uses the pending cycle or starts a new eligible cycle; valid approval cannot be extended. "
                                + "Each success atomically writes one event and audit, increments revision and sends scoped SSE after commit. "
                                + "400 invalid input; 401 missing/invalid JWT; 403 scope; 404 missing flight; 409 stale revision, phase/readiness/state/expiry or lock conflict. Refresh before retrying.",
                        path.endsWith("requestTakeOff") || path.endsWith("requestLanding") ? "201" : "200", OperationResponse.class, false);
            }

            for (String path : List.of("/pilot/flights/{flightId}/takeOff", "/pilot/flights/{flightId}/land")) {
                document(openApi, path, PathItem.HttpMethod.POST, path.endsWith("takeOff") ? "Take off" : "Land",
                        "Active current PILOT or SECONDARY_PILOT required. Body: {\"expectedRevision\":2}. "
                                + "Requires matching unexpired APPROVED clearance; equality at expiry fails. Rechecks phase, revision and readiness after Airplane, Flight and sorted crew locks. "
                                + "takeOff requires ACTIVE, no actual times, required crew, latest passed inspection, FAA ACTIVE and free resources; sets IN_AIR and server actualDeparture. "
                                + "land requires IN_AIR and destination approval with consistent actual times; sets CLOSED and server actualArrival, without fresh FAA/inspection/occupancy checks. "
                                + "Consumes approval, increments revision once and atomically writes one event/audit. Actual times use Asia/Bahrain; event time is UTC. "
                                + "After-commit SSE reaches active origin/destination tower teams, current pilots and owning airline admins. "
                                + "400 invalid input; 401 missing/invalid JWT; 403 assignment/scope; 404 missing flight; 409 stale revision, phase/readiness/expiry or lock conflict. Duplicate actions never overwrite times.",
                        "200", OperationResponse.class, false);
            }

            List.of("/auth/users/login", "/auth/users/register/email", "/auth/users/verification")
                    .forEach(path -> makePublic(openApi, path, PathItem.HttpMethod.POST));
            List.of("/payments/success", "/payments/cancel")
                    .forEach(path -> makePublic(openApi, path, PathItem.HttpMethod.GET));

            allowedValues(openApi, "/airportAdmin/employees", PathItem.HttpMethod.POST,
                    "role", List.of("INSPECTOR", "BAGGAGE_HANDLER"));
            allowedValues(openApi, "/airportInspector/flights/{flightId}/inspections/{inspectionId}/complete",
                    PathItem.HttpMethod.PUT, "result", List.of("PASSED", "FAILED"));
        };
    }

    // Documentation overrides only: request mappings, authorization, and service behavior are unchanged.
    private void document(OpenAPI api, String path, PathItem.HttpMethod method,
                          String summary, String description, String status, Class<?> body, boolean list) {
        Operation operation = operation(api, path, method);
        if (operation == null) {
            return;
        }
        operation.setSummary(summary);
        operation.setDescription(description);
        ApiResponse response = new ApiResponse().description(
                status.equals("201") ? "Created" : status.equals("204") ? "No Content" : "OK");
        if (body != null) {
            Schema<?> schema;
            if (body == String.class) {
                schema = new StringSchema();
            } else {
                var resolved = ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(body).resolveAsRef(true));
                resolved.referencedSchemas.forEach(api.getComponents()::addSchemas);
                schema = resolved.schema;
            }
            if (list) {
                schema = new ArraySchema().items(schema);
            }
            String mediaType = body == String.class ? "text/plain" : "application/json";
            response.setContent(new Content().addMediaType(mediaType, new MediaType().schema(schema)));
        }
        // Replace inferred success codes while retaining any generated error responses.
        operation.getResponses().keySet().removeIf(code -> code.matches("2\\d\\d"));
        operation.getResponses().addApiResponse(status, response);
    }

    private Operation operation(OpenAPI api, String path, PathItem.HttpMethod method) {
        PathItem item = api.getPaths() == null ? null : api.getPaths().get(path);
        return item == null ? null : item.readOperationsMap().get(method);
    }

    private void makePublic(OpenAPI api, String path, PathItem.HttpMethod method) {
        Operation operation = operation(api, path, method);
        if (operation != null) {
            operation.setSecurity(List.of());
        }
    }

    private void allowedValues(OpenAPI api, String path, PathItem.HttpMethod method,
                               String name, List<String> values) {
        Operation operation = operation(api, path, method);
        if (operation != null && operation.getParameters() != null) {
            operation.getParameters().stream()
                    .filter(parameter -> name.equals(parameter.getName()) && "query".equals(parameter.getIn()))
                    .forEach(parameter -> parameter.setSchema(new StringSchema()._enum(values)));
        }
    }
}
