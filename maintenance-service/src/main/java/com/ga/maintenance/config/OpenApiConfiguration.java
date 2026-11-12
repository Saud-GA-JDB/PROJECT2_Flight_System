package com.ga.maintenance.config;

import com.ga.maintenance.model.request.response.*;
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
    public OpenAPI maintenanceOpenApi() {
        return new OpenAPI().info(new Info().title("Maintenance API").version("1.0")
                .description("Maintenance providers, workforce, contracts and work records. Log in through Identity on port 8081 and send the bearer token to port 8083. Cancellation notification delivery to Aviation is best effort after commit."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer maintenanceDocumentation() {
        return openApi -> {
            document(openApi, "/faaadmin/maintenanceProviders", PathItem.HttpMethod.POST,
                    "Create maintenance provider",
                    "FAA admin only.",
                    "201", MaintenanceProviderResponse.class, false);
            document(openApi, "/faaadmin/maintenanceProviders/{providerId}/addMaintenanceAdmin", PathItem.HttpMethod.POST,
                    "Create maintenance admin",
                    "FAA admin only. Creates an admin account for the specified provider.",
                    "201", MaintenanceEmployeeResponse.class, false);
            document(openApi, "/airlineAdmin/maintenanceProviders/{providerId}/contract", PathItem.HttpMethod.POST,
                    "Contract maintenance provider",
                    "Airline admin only. Links the provider to the current airline.",
                    "200", MaintenanceProviderResponse.class, false);
            document(openApi, "/airlineAdmin/airplanes/{airplaneId}/maintenanceRequests", PathItem.HttpMethod.POST,
                    "Request airplane maintenance",
                    "Admin of the airplane's airline. Requires a contracted provider. Creates a pending request without a technician.",
                    "201", MaintenanceRecordResponse.class, false);
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
                    "Admin of the request's provider. Assigns a technician from the same provider to a pending request. Terminal records return 409.",
                    "200", MaintenanceRecordResponse.class, false);
            document(openApi, "/maintenanceTechnician/maintenanceRecords", PathItem.HttpMethod.GET,
                    "List assigned maintenance",
                    "Maintenance technician only. Returns records assigned to the current technician.",
                    "200", MaintenanceRecordResponse.class, true);
            document(openApi, "/maintenanceTechnician/maintenanceRecords/{recordId}/complete", PathItem.HttpMethod.PUT,
                    "Complete maintenance",
                    "Assigned technician only. Completes a pending record with optional findings. Cancelled/completed records return 409 without changing findings. Does not activate the airplane or change inspection results.",
                    "200", String.class, false);
            document(openApi, "/airlineAdmin/airplanes/{airplaneId}/maintenanceRecords", PathItem.HttpMethod.GET,
                    "Read airline airplane maintenance history",
                    "Active owning-airline ADMIN only. History across providers without requiring a current contract. "
                            + "Optional status PENDING/COMPLETED/CANCELLED; page defaults to 0, size to 20 (1-100). "
                            + "Scope/filter apply before count and pagination. Sorts createdAt descending, then id descending. "
                            + "Includes cancellationReason, UTC cancelledAt and cancelledByUserId. Empty history returns empty content. "
                            + "400 invalid filters; 401 missing JWT; 403 foreign airplane/role; 404 missing airplane. No business audits or notifications.",
                    "200", MaintenanceRecordListResponse.class, false);
            for (String path : List.of("/airlineAdmin/maintenanceRequests/{requestId}/cancel",
                    "/maintenanceAdmin/maintenanceRequests/{requestId}/cancel")) {
                document(openApi, path, PathItem.HttpMethod.POST, "Cancel pending maintenance",
                        "Active own-airline ADMIN or own-provider ADMIN required according to the route. "
                                + "Body: {\"reason\":\"Work is no longer required\"}; trimmed reason must be 1-2000 characters. "
                                + "Assigned and unassigned PENDING work can be cancelled. Record lock serializes cancellation, completion and assignment; first terminal action wins. "
                                + "Preserves technician, findings and history; records server UTC cancelledAt and authenticated cancelledByUserId. "
                                + "Cancellation and audit commit together. After commit, a best-effort HTTP handoff to Aviation sends Maintenance_Cancelled SSE to active owning airline admins, responsible provider admins and assigned technician. "
                                + "400 invalid input; 401 missing JWT; 403 role/scope; 404 missing record; 409 terminal state or lock conflict. No FAA or inspection changes.",
                        "200", MaintenanceRecordResponse.class, false);
            }
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
