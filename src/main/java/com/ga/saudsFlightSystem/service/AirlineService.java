package com.ga.saudsFlightSystem.service;


import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.repository.AirlineRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AirlineService {
    private AirlineRepository airlineRepository;

    // this function is used by FAAAdminService only
    public Airline addAirline(Airline airline) {
        if(!UserService.isAllowedEndpoint("faaadmin", UserService.getCurrentLoggedInUser().getRole()) ) {
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");
        }
        // TODO: Delete Later
//        airlineRepository.findByAirlineCode(airline.getAirlineCode()).orElseThrow( () ->
//                new InformationExistException("Airline with Code: " + airline.getAirlineCode() + " already exists.")
//                );
//        airlineRepository.findByName(airline.getName()).orElseThrow( () ->
//                new InformationExistException("Airline with Code: " + airline.getName() + " already exists.")
//                );
        Airline temp1 = airlineRepository.findByAirlineCode(airline.getAirlineCode()).orElse(null);
        Airline temp2 = airlineRepository.findByName(airline.getName()).orElse(null);

        if (temp1 != null || temp2 != null) throw new InformationExistException("Airline with the same Code or Name Already Exists!");

        return airlineRepository.save(airline);
    }
}
