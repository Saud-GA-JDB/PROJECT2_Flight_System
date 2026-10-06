package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class FlightService {
    private FlightRepository flightRepository;

    @Scheduled(cron = "0 */5 * * * *")
    public void updateFlightStatus() {
        LocalDateTime now = LocalDateTime.now();

        for (Flight flight : flightRepository.findByStatus(Flight.FlightStatus.ACTIVE)) {
            if (flight.getActualDeparture() != null && !flight.getActualDeparture().isAfter(now)) {
                flight.setStatus(Flight.FlightStatus.IN_AIR);
                flightRepository.save(flight);
            }
        }

        for (Flight flight : flightRepository.findByStatus(Flight.FlightStatus.IN_AIR)) {
            if (flight.getActualArrival() != null && !flight.getActualArrival().isAfter(now)) {
                flight.setStatus(Flight.FlightStatus.CLOSED);
                flightRepository.save(flight);
            }
        }
    }
}
