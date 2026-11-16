package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.Airport;
import com.ga.saudsFlightSystem.model.request.response.AirportResponse;
import com.ga.saudsFlightSystem.repository.AirportRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class AirportService {
    private AirportRepository airportRepository;

    public List<AirportResponse> getAirports() {
        List<AirportResponse> response = new ArrayList<>();
        for (Airport airport : airportRepository.findAllByOrderByIataCodeAsc()) {
            response.add(new AirportResponse(
                    airport.getId(), airport.getIataCode(), airport.getName(),
                    airport.getCity(), airport.getCountry(), airport.getTimeZone()));
        }
        return response;
    }
}
