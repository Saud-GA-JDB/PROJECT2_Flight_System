package com.ga.saudsFlightSystem.support;

import com.ga.common.security.CurrentActor;
import com.ga.maintenance.client.AviationServiceClient;
import com.ga.maintenance.client.IdentityServiceClient;
import com.ga.maintenance.model.request.EmployeeAccountRequest;
import com.ga.maintenance.model.request.response.AccountSnapshot;
import com.ga.maintenance.model.request.response.ProvisionedAccount;
import com.ga.maintenance.model.request.MaintenanceCancellationNotificationRequest;
import com.ga.maintenance.model.request.response.MaintenanceScopeResponse;
import com.ga.saudsFlightSystem.controller.MaintenanceNotificationController;
import com.ga.saudsFlightSystem.service.MaintenanceScopeService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Adapters for the existing in-process fixtures, not HTTP integration coverage.
@TestConfiguration
@Import(IdentityTestConfiguration.class)
@ComponentScan(basePackages = "com.ga.maintenance", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ga\\.maintenance\\.config\\..*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        com.ga.maintenance.MaintenanceApplication.class,
                        com.ga.maintenance.security.SecurityConfiguration.class,
                        com.ga.maintenance.service.PendingEmployeeActivation.class})
        })
public class MaintenanceTestConfiguration {
    @Bean
    public AviationServiceClient fixtureAviationClient(MaintenanceScopeService scope, MaintenanceNotificationController notifications) {
        return new AviationServiceClient() {
            public MaintenanceScopeResponse getAirlineScope(String authorization) {
                var result = scope.getAirlineScope();
                return new MaintenanceScopeResponse(result.userId(), result.airlineId(), result.airplaneId());
            }
            public MaintenanceScopeResponse getAirplaneScope(String authorization, Long airplaneId) {
                var result = scope.getAirplaneScope(airplaneId);
                return new MaintenanceScopeResponse(result.userId(), result.airlineId(), result.airplaneId());
            }
            public void sendCancellation(String serviceKey, MaintenanceCancellationNotificationRequest request) {
                notifications.cancellation(serviceKey, new com.ga.saudsFlightSystem.model.request.MaintenanceCancellationNotificationRequest(
                        request.recordId(), request.airplaneId(), request.status(), request.cancelledAt(), request.maintenanceUserIds()));
            }
        };
    }

    @Bean
    public IdentityServiceClient fixtureMaintenanceIdentity(com.ga.saudsFlightSystem.identity.IdentityServiceClient identity) {
        return new IdentityServiceClient() {
            public CurrentActor authenticate(String authorization) { return identity.authenticate(authorization); }
            public AccountSnapshot account(Long id) { return snapshot(identity.account(id)); }
            public AccountSnapshot accountByEmail(String email) { return snapshot(identity.accountByEmail(email)); }
            public Map<Long, AccountSnapshot> accounts(List<Long> ids) {
                return identity.accounts(ids).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> snapshot(entry.getValue())));
            }
            public void checkDetails(ExistingDetails request) {
                identity.checkDetails(new com.ga.saudsFlightSystem.identity.IdentityServiceClient.ExistingDetails(request.emailAddress(), request.cpr()));
            }
            public ProvisionedAccount create(EmployeeAccountRequest request) {
                var account = identity.create(new com.ga.saudsFlightSystem.identity.EmployeeAccountRequest(
                        com.ga.saudsFlightSystem.identity.EmployeeAccountRequest.AccountType.valueOf(request.accountType().name()),
                        request.employeeId(), request.emailAddress(), request.password(), request.securityQuestion(), request.normalizedSecurityAnswer(),
                        request.fName(), request.lName(), request.cpr(), request.phoneNumberOpeningCode(), request.phoneNumber()));
                return new ProvisionedAccount(account.userId(), account.emailAddress());
            }
            public void activate(String key, Long userId) { identity.activate(key, userId); }
            public void cancel(String key) { identity.cancel(key); }
        };
    }

    private static AccountSnapshot snapshot(com.ga.saudsFlightSystem.identity.AccountSnapshot account) {
        return new AccountSnapshot(account.userId(), account.role(), account.status(), account.active(), account.emailAddress());
    }
}
