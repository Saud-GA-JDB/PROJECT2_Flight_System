package com.ga.saudsFlightSystem.service;

import com.ga.common.contract.maintenance.MaintenanceAirlineResponse;
import com.ga.common.contract.maintenance.MaintenanceScopeResponse;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.maintenance.client.AviationServiceClient;
import com.ga.maintenance.model.MaintenanceContract;
import com.ga.maintenance.model.MaintenanceEmployee;
import com.ga.maintenance.model.MaintenanceProvider;
import com.ga.maintenance.repository.MaintenanceContractRepository;
import com.ga.maintenance.repository.MaintenanceEmployeeRepository;
import com.ga.maintenance.repository.MaintenanceProviderRepository;
import com.ga.maintenance.service.MaintenanceProviderService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Mocked cross-service calls; does not establish live Feign or database behavior.
class FrontendMaintenanceListsTest {
    private final MaintenanceProviderRepository providers = mock(MaintenanceProviderRepository.class);
    private final MaintenanceContractRepository contracts = mock(MaintenanceContractRepository.class);
    private final MaintenanceEmployeeRepository employees = mock(MaintenanceEmployeeRepository.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final AviationServiceClient aviation = mock(AviationServiceClient.class);
    private final MaintenanceProviderService service = new MaintenanceProviderService(providers, contracts, null, actors, aviation, employees);

    @Test
    void contractedProviderSelectorUsesVerifiedAirlineScope() {
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
        when(aviation.getAirlineScope("Bearer test")).thenReturn(new MaintenanceScopeResponse(17L, 6L, null));
        when(providers.findContractedByAirlineId(6L)).thenReturn(List.of());
        assertTrue(service.getAirlineProviders(true, "Bearer test").isEmpty());
        verify(providers).findContractedByAirlineId(6L);
        verify(providers, never()).findAllByOrderByNameAscIdAsc();
    }

    @Test
    void mismatchedAirlineScopeCannotReadContracts() {
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
        when(aviation.getAirlineScope("Bearer test")).thenReturn(new MaintenanceScopeResponse(18L, 6L, null));
        assertThrows(IllegalEndpoint.class, () -> service.getAirlineContracts("Bearer test"));
        verifyNoInteractions(contracts, providers);
    }

    @Test
    void providerContractsContainNamesAndOnlyUseOwnProviderQuery() {
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(20L, "MAINTENANCE_EMPLOYEE", "ACTIVE", true));
        MaintenanceProvider provider = new MaintenanceProvider();
        provider.setId(7L);
        provider.setName("Maintenance Co");
        MaintenanceEmployee admin = new MaintenanceEmployee();
        admin.setProvider(provider);
        admin.setMaintenanceRole(MaintenanceEmployee.MaintenanceRole.ADMIN);
        when(employees.findByUserId(20L)).thenReturn(Optional.of(admin));
        MaintenanceContract contract = new MaintenanceContract();
        contract.setId(2L);
        contract.setProviderId(7L);
        contract.setAirlineId(6L);
        when(contracts.findByProviderIdOrderByIdAsc(7L)).thenReturn(List.of(contract));
        when(providers.findAllById(List.of(7L))).thenReturn(List.of(provider));
        when(aviation.getAirlines(nullable(String.class), eq(List.of(6L)))).thenReturn(List.of(new MaintenanceAirlineResponse(6L, "Example Air", "EA")));
        var response = service.getProviderContracts();
        assertEquals("Example Air", response.get(0).getAirlineName());
        assertEquals("Maintenance Co", response.get(0).getProviderName());
        verify(contracts).findByProviderIdOrderByIdAsc(7L);
        verify(contracts, never()).findByAirlineIdOrderByIdAsc(anyLong());
    }

    @Test
    void technicianCannotBrowseProviderContracts() {
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(20L, "MAINTENANCE_EMPLOYEE", "ACTIVE", true));
        MaintenanceEmployee employee = new MaintenanceEmployee();
        employee.setMaintenanceRole(MaintenanceEmployee.MaintenanceRole.TECHNICIAN);
        when(employees.findByUserId(20L)).thenReturn(Optional.of(employee));
        assertThrows(IllegalEndpoint.class, () -> service.getProviderContracts());
        verifyNoInteractions(contracts, providers, aviation);
    }
}
