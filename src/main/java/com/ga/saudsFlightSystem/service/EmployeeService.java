package com.ga.saudsFlightSystem.service;

import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.AirportEmployee;
import com.ga.saudsFlightSystem.model.ATCEmployee;
import com.ga.saudsFlightSystem.model.request.response.CurrentEmployeeResponse;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.AirportEmployeeRepository;
import com.ga.saudsFlightSystem.repository.ATCEmployeeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class EmployeeService {
    private CurrentActorProvider currentActorProvider;
    private AirlineEmployeeRepository airlineEmployeeRepository;
    private AirportEmployeeRepository airportEmployeeRepository;
    private ATCEmployeeRepository atcEmployeeRepository;

    @Transactional(readOnly = true)
    public CurrentEmployeeResponse getCurrentEmployee() {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if ("AIRLINE_EMPLOYEE".equals(actor.role())) {
            AirlineEmployee employee = airlineEmployeeRepository.findByUserId(actor.userId())
                    .orElseThrow( () -> new InformationNotFoundException("Couldn't find the airline employee."));

            if (employee.getAirlineRole() == null || employee.getAirline() == null)
                throw new IllegalEndpoint("The employee must have an airline role and belong to an airline.");

            return new CurrentEmployeeResponse(
                    employee.getId(), employee.getAirlineRole().name(),
                    employee.getAirline().getId(), null, null, null, null);
        }

        if ("AIRPORT_EMPLOYEE".equals(actor.role())) {
            AirportEmployee employee = airportEmployeeRepository.findByUserId(actor.userId())
                    .orElseThrow( () -> new InformationNotFoundException("Couldn't find the airport employee."));

            if (employee.getAirportRole() == null || employee.getAirport() == null || employee.getOperator() == null)
                throw new IllegalEndpoint("The employee must have an airport role, airport and operator.");

            return new CurrentEmployeeResponse(
                    employee.getId(), employee.getAirportRole().name(),
                    null, employee.getAirport().getId(), employee.getOperator().getId(), null, null);
        }

        if ("ATC_EMPLOYEE".equals(actor.role())) {
            ATCEmployee employee = atcEmployeeRepository.findByUserId(actor.userId())
                    .orElseThrow( () -> new InformationNotFoundException("Couldn't find the ATC employee."));

            if (employee.getAtcRole() == null || employee.getFacility() == null
                    || employee.getFacility().getAirport() == null || employee.getFacility().getProvider() == null)
                throw new IllegalEndpoint("The employee must have an ATC role and a facility with an airport and provider.");

            return new CurrentEmployeeResponse(
                    employee.getId(), employee.getAtcRole().name(),
                    null, employee.getFacility().getAirport().getId(), null,
                    employee.getFacility().getId(), employee.getFacility().getProvider().getId());
        }

        throw new IllegalEndpoint("You are not allowed this endpoint.");
    }
}
