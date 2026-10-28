package com.ga.saudsFlightSystem.config;

import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.model.User;
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
                                + "maintenance, airport accounts, flight inspections, and SSE notifications. "
                                + "Log in, copy the JWT from the response message, then use Authorize. "
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
            document(openApi, "/auth/users/login", PathItem.HttpMethod.POST,
                    "Log in",
                    "Public. Returns the JWT in the response message; paste it into Authorize.",
                    "200", LoginResponse.class, false);
            document(openApi, "/auth/users/register/email", PathItem.HttpMethod.POST,
                    "Request verification email",
                    "Public. Requests a registration code for the supplied email and CPR.",
                    "200", RegistrationResponse.class, false);
            document(openApi, "/auth/users/verification", PathItem.HttpMethod.POST,
                    "Verify registration email",
                    "Public. Validates the code and creates the customer account for setup.",
                    "200", RegistrationResponse.class, false);
            document(openApi, "/auth/users/setup", PathItem.HttpMethod.POST,
                    "Complete account setup",
                    "Requires an authenticated customer account. Completes profile details and activates the account.",
                    "200", User.class, false);
            document(openApi, "/auth/users/forgetPassword", PathItem.HttpMethod.POST,
                    "Reset password",
                    "Requires an active authenticated account under the current security configuration. Checks the supplied email and security answer and emails a replacement password.",
                    "200", ForgetPasswordResponse.class, false);
            document(openApi, "/auth/users/changePassword", PathItem.HttpMethod.POST,
                    "Change password",
                    "Active account required. Changes the current user's password.",
                    "200", String.class, false);
            document(openApi, "/auth/users/updateProfile", PathItem.HttpMethod.PUT,
                    "Update profile",
                    "Active account required. Send a JSON request part and optional image as multipart form data. Only FAA admins may supply userId to edit another profile; security questions are self-service only.",
                    "200", String.class, false);
            document(openApi, "/flights/search", PathItem.HttpMethod.GET,
                    "Search flights",
                    "Active account required. Filters by route, date, airline, and seat class with pagination and sorting.",
                    "200", FlightSearchResponse.class, false);
            document(openApi, "/customer/{userId}/flights/{flightId}/{seatType}/{seatId}/book", PathItem.HttpMethod.POST,
                    "Book a flight seat",
                    "Active account required. Service checks the target user, flight, and seat availability before creating the booking.",
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
                    "200", Airline.class, false);
            document(openApi, "/faaadmin/airlines", PathItem.HttpMethod.GET,
                    "List airlines",
                    "FAA admin only.",
                    "200", Airline.class, true);
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
            document(openApi, "/faaadmin/maintenanceProviders", PathItem.HttpMethod.POST,
                    "Create maintenance provider",
                    "FAA admin only.",
                    "201", MaintenanceProviderResponse.class, false);
            document(openApi, "/faaadmin/maintenanceProviders/{providerId}/addMaintenanceAdmin", PathItem.HttpMethod.POST,
                    "Create maintenance admin",
                    "FAA admin only. Creates an admin account for the specified provider.",
                    "201", MaintenanceEmployeeResponse.class, false);
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
                    "Admin of the flight's airline. Rejects duplicate assignments, incompatible duties, and scheduling conflicts. Sends email and SSE notifications after commit.",
                    "201", CrewAssignmentResponse.class, false);
            document(openApi, "/airlineAdmin/flights/{flightId}/crewAssignments", PathItem.HttpMethod.GET,
                    "List flight crew",
                    "Admin of the flight's airline.",
                    "200", getCrewAssignmentsResponse.class, true);
            document(openApi, "/airlineAdmin/flights/{flightId}/crewAssignments/{employeeId}", PathItem.HttpMethod.DELETE,
                    "Remove crew assignment",
                    "Admin of the flight's airline. Requires a future flight; active flights must retain the required crew.",
                    "204", null, false);
            document(openApi, "/airlineAdmin/flights/{flightId}/activate", PathItem.HttpMethod.PUT,
                    "Activate flight",
                    "Admin of the flight's airline. Requires a future PENDING_CREW flight with a pilot, secondary pilot, and flight attendant. Inspection clearance is not enforced here.",
                    "200", String.class, false);
            document(openApi, "/airlineAdmin/maintenanceProviders/{providerId}/contract", PathItem.HttpMethod.POST,
                    "Contract maintenance provider",
                    "Airline admin only. Links the provider to the current airline.",
                    "200", MaintenanceProviderResponse.class, false);
            document(openApi, "/airlineAdmin/airplanes/{airplaneId}/maintenanceRequests", PathItem.HttpMethod.POST,
                    "Request airplane maintenance",
                    "Admin of the airplane's airline. Requires a contracted provider. Creates a pending request without a technician.",
                    "201", MaintenanceRecordResponse.class, false);
            document(openApi, "/airlineAdmin/flights/{flightId}/reinspection", PathItem.HttpMethod.POST,
                    "Request reinspection",
                    "Admin of the flight's airline. Requires a failed latest inspection and a future flight. Creates a pending attempt awaiting assignment.",
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
                    "Origin-airport admin only. Inspector must belong to the same airport and operator. Creates the first inspection or assigns the pending attempt.",
                    "200", String.class, false);
            document(openApi, "/airportAdmin/inspections", PathItem.HttpMethod.GET,
                    "List airport inspections",
                    "Airport admin only. Returns inspections at the current airport.",
                    "200", FlightInspectionResponse.class, true);
            document(openApi, "/airportInspector/flights/{flightId}/inspections/{inspectionId}/complete", PathItem.HttpMethod.PUT,
                    "Complete flight inspection",
                    "Assigned inspector only. Completes the latest pending attempt as PASSED or FAILED. Optional reason is limited to 2000 characters.",
                    "200", String.class, false);
            document(openApi, "/airportInspector/inspections", PathItem.HttpMethod.GET,
                    "List assigned inspections",
                    "Airport inspector only. Returns inspections assigned to the current inspector.",
                    "200", FlightInspectionResponse.class, true);
            document(openApi, "/maintenanceAdmin/technicians", PathItem.HttpMethod.POST,
                    "Create maintenance technician",
                    "Maintenance admin only. Creates a technician within the current provider; a license number is required.",
                    "201", MaintenanceEmployeeResponse.class, false);
            document(openApi, "/maintenanceAdmin/maintenanceRequests", PathItem.HttpMethod.GET,
                    "List maintenance requests",
                    "Maintenance admin only. Returns requests for the current provider.",
                    "200", MaintenanceRecordResponse.class, true);
            document(openApi, "/maintenanceAdmin/maintenanceRequests/{requestId}", PathItem.HttpMethod.POST,
                    "Assign maintenance technician",
                    "Admin of the request's provider. Assigns a technician from the same provider to a pending request.",
                    "200", MaintenanceRecordResponse.class, false);
            document(openApi, "/maintenanceTechnician/maintenanceRecords", PathItem.HttpMethod.GET,
                    "List assigned maintenance",
                    "Maintenance technician only. Returns records assigned to the current technician.",
                    "200", MaintenanceRecordResponse.class, true);
            document(openApi, "/maintenanceTechnician/maintenanceRecords/{recordId}/complete", PathItem.HttpMethod.PUT,
                    "Complete maintenance",
                    "Assigned technician only. Completes a pending record with optional findings. Does not activate the airplane or change inspection results.",
                    "200", String.class, false);
            document(openApi, "/payments/bookings/{bookingId}/checkout", PathItem.HttpMethod.POST,
                    "Start Stripe checkout",
                    "Booking owner only. Creates or reuses a sandbox checkout session. Requires an active booking and flight, positive pricing, and departure more than five minutes away.",
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
            document(openApi, "/notifications", PathItem.HttpMethod.GET,
                    "Subscribe to live notifications",
                    "Active account required. Receives events targeted to the user or role, including airplane, flight, and crew updates after transaction commit. Includes connection events and heartbeat comments. Reconnect after timeout; no persistent replay is provided. Use a streaming client supporting the Authorization header.",
                    "200", String.class, false);

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
            String mediaType = path.equals("/notifications") ? "text/event-stream"
                    : body == String.class ? "text/plain" : "application/json";
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
