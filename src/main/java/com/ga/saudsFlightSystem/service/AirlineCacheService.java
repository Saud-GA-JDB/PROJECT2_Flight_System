package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.request.response.AirlineResponse;
import com.ga.saudsFlightSystem.repository.AirlineRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class AirlineCacheService {
    private AirlineRepository airlineRepository;

    @Cacheable(value = "airlines", key = "'all'")
    public List<AirlineResponse> getAllAirlines() {
        List<AirlineResponse> response = new ArrayList<>();

        for (Airline airline : airlineRepository.findAll()) {
            response.add(new AirlineResponse(
                    airline.getId(),
                    airline.getName(),
                    airline.getAirlineCode(),
                    airline.getHeadquartersCountry()
            ));
        }

        return response;
    }

    @EventListener(ApplicationReadyEvent.class)
    @CacheEvict(value = "airlines", key = "'all'")
    public void evictAirlines() {
        // Spring removes the cached list.
    }
}
