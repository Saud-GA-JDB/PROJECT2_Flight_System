package com.ga.saudsFlightSystem.service;


import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationExistException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.request.response.AirlineResponse;
import com.ga.saudsFlightSystem.repository.AirlineRepository;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class AirlineService {
    private AirlineRepository airlineRepository;
    private AuditLogService auditLogService;
    private CurrentActorProvider currentActorProvider;

    private static final Logger logger = Logger.getLogger(AirlineService.class.getName());

    // this function is used by FAAAdminService only
    // TODO: for future check that the country is correct. maybe use file based csv
    /**
     * Lets an FAA admin add an airline if its name and code are not already used.
     *
     * @param name airline name
     * @param airlineCode unique airline code
     * @param headquartersCountry country where the airline is based
     * @return the new airline
     */
    @Transactional
    public AirlineResponse addAirline(String name, String airlineCode, String headquartersCountry) {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!"FAA_ADMIN".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");

        if (name == null || name.isBlank() || airlineCode == null || airlineCode.isBlank() || headquartersCountry == null || headquartersCountry.isBlank())
            throw new InvalidInformationException("Please fill out all the required fields");

        Airline temp1 = airlineRepository.findByAirlineCode(airlineCode).orElse(null);
        Airline temp2 = airlineRepository.findByName(name).orElse(null);

        if (temp1 != null || temp2 != null) throw new InformationExistException("Airline with the same Code or Name Already Exists!");

        Airline airline = new Airline();
        airline.setName(name);
        airline.setAirlineCode(airlineCode);
        airline.setHeadquartersCountry(headquartersCountry);

        Airline savedAirline = airlineRepository.save(airline);
        String description = "FAA admin created airline with id " + savedAirline.getId();
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.AIRLINE_CREATED, AuditLog.EntityType.AIRLINE, savedAirline.getId(), description);
        logger.info(description + " (user id: " + actor.userId() + ")");
        return new AirlineResponse(savedAirline.getId(), savedAirline.getName(), savedAirline.getAirlineCode(), savedAirline.getHeadquartersCountry());
    }
    public List<AirlineResponse> getAllAirlines() {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!"FAA_ADMIN".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint");

        List<AirlineResponse> response = new ArrayList<>();

        for (Airline airline : airlineRepository.findAll()) {
            response.add(new AirlineResponse(airline.getId(), airline.getName(), airline.getAirlineCode(), airline.getHeadquartersCountry()));
        }

        return response;
    }
}
