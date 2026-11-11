package com.ga.saudsFlightSystem.service;

import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceScopeResponse;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.AirplaneRepository;
import com.ga.common.security.CurrentActor;
import com.ga.saudsFlightSystem.security.CurrentActorProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceScopeService {
    private final CurrentActorProvider currentActorProvider;
    private final AirlineEmployeeRepository airlineEmployeeRepository;
    private final AirplaneRepository airplaneRepository;

    public MaintenanceScopeResponse getAirlineScope() {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRLINE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("Only airline admins can access this endpoint.");

        AirlineEmployee admin = airlineEmployeeRepository
                .findByUserIdAndRole(actor.userId(), AirlineEmployee.AirlineRole.ADMIN)
                .orElseThrow(() -> new IllegalEndpoint("Only airline admins can access this endpoint."));

        if (admin.getAirline() == null)
            throw new IllegalEndpoint("The admin must belong to an airline.");

        return new MaintenanceScopeResponse(actor.userId(), admin.getAirline().getId(), null);
    }

    public MaintenanceScopeResponse getAirplaneScope(Long airplaneId) {
        MaintenanceScopeResponse scope = getAirlineScope();

        if (airplaneId == null || airplaneId <= 0)
            throw new InvalidInformationException("Airplane ID must be positive.");

        Airplane airplane = airplaneRepository.findById(airplaneId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find airplane with this id."));

        if (!Objects.equals(airplane.getAirline().getId(), scope.airlineId()))
            throw new IllegalEndpoint("You can only access maintenance for your airline's airplanes.");

        return new MaintenanceScopeResponse(scope.userId(), scope.airlineId(), airplane.getId());
    }
}