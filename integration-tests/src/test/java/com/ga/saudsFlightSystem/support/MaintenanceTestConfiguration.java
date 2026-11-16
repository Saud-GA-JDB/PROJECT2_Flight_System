package com.ga.saudsFlightSystem.support;

import com.ga.common.contract.maintenance.MaintenanceCancellationNotificationRequest;
import com.ga.common.contract.maintenance.MaintenanceScopeResponse;
import com.ga.common.contract.identity.ProvisionedAccount;
import com.ga.common.contract.identity.AccountSnapshot;

import com.ga.common.security.CurrentActor;
import com.ga.maintenance.client.AviationServiceClient;
import com.ga.maintenance.client.IdentityServiceClient;
import com.ga.maintenance.model.request.EmployeeAccountRequest;
import com.ga.saudsFlightSystem.controller.MaintenanceNotificationController;
import com.ga.saudsFlightSystem.service.MaintenanceScopeService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;

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
    public AviationServiceClient fixtureAviationClient(MaintenanceScopeService scope, MaintenanceNotificationController notifications,
                                                       PlatformTransactionManager transactionManager) {
        TransactionTemplate aviationRequest = new TransactionTemplate(transactionManager);
        aviationRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return new AviationServiceClient() {
            public MaintenanceScopeResponse getAirlineScope(String authorization) {
                return scope.getAirlineScope();
            }
            public MaintenanceScopeResponse getAirplaneScope(String authorization, Long airplaneId) {
                return scope.getAirplaneScope(airplaneId);
            }
            public void sendCancellation(String serviceKey, MaintenanceCancellationNotificationRequest request) {
                // An HTTP call to Aviation cannot inherit Maintenance's committing transaction.
                aviationRequest.executeWithoutResult(status -> notifications.cancellation(serviceKey, request));
            }
        };
    }

    @Bean
    public IdentityServiceClient fixtureMaintenanceIdentity(com.ga.saudsFlightSystem.identity.IdentityServiceClient identity) {
        return new IdentityServiceClient() {
            public CurrentActor authenticate(String authorization) { return identity.authenticate(authorization); }
            public AccountSnapshot account(Long id) { return identity.account(id); }
            public AccountSnapshot accountByEmail(String email) { return identity.accountByEmail(email); }
            public Map<Long, AccountSnapshot> accounts(List<Long> ids) {
                return identity.accounts(ids);
            }
            public void checkDetails(ExistingDetails request) {
                identity.checkDetails(new com.ga.saudsFlightSystem.identity.IdentityServiceClient.ExistingDetails(request.emailAddress(), request.cpr()));
            }
            public ProvisionedAccount create(EmployeeAccountRequest request) {
                var account = identity.create(new com.ga.saudsFlightSystem.identity.EmployeeAccountRequest(
                        com.ga.saudsFlightSystem.identity.EmployeeAccountRequest.AccountType.valueOf(request.accountType().name()),
                        request.employeeId(), request.emailAddress(), request.password(), request.securityQuestion(), request.normalizedSecurityAnswer(),
                        request.fName(), request.lName(), request.cpr(), request.phoneNumberOpeningCode(), request.phoneNumber()));
                return account;
            }
            public void activate(String key, Long userId) { identity.activate(key, userId); }
            public void cancel(String key) { identity.cancel(key); }
        };
    }

}
