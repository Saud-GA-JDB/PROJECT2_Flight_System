package com.ga.saudsFlightSystem.service;


import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.repository.AirlineRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AirlineService {
    private AirlineRepository airlineRepository;

    // this function is used by FAAAdminService only
    // TODO: make sure the fields arent empty
    // TODO: for future check that the country is correct. maybe use file based csv
    public Airline addAirline(String name, String airlineCode, String headquartersCountry) {
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
        Airline temp1 = airlineRepository.findByAirlineCode(airlineCode).orElse(null);
        Airline temp2 = airlineRepository.findByName(name).orElse(null);

        if (temp1 != null || temp2 != null) throw new InformationExistException("Airline with the same Code or Name Already Exists!");

        Airline airline = new Airline();
        airline.setName(name);
        airline.setAirlineCode(airlineCode);
        airline.setHeadquartersCountry(headquartersCountry);

        return airlineRepository.save(airline);
    }
}
