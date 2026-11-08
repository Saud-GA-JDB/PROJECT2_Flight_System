package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class FlightStatusService {
    private FlightRepository flightRepository;
    private AuditLogService auditLogService;
    private static final Logger logger = Logger.getLogger(FlightStatusService.class.getName());

    // Called through a separate service so each scheduler candidate has its own transaction.
    @Transactional
    public void updateFlightStatus(Long flightId) {
        Flight flight = flightRepository.findByIdForUpdate(flightId).orElse(null);
        if (flight == null || (flight.getStatus() != Flight.FlightStatus.ACTIVE && flight.getStatus() != Flight.FlightStatus.IN_AIR))
            return;

        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("Asia/Bahrain"));
        LocalDateTime departure = flight.getActualDeparture();
        LocalDateTime arrival = flight.getActualArrival();
        if ((departure == null && (arrival != null || flight.getStatus() == Flight.FlightStatus.IN_AIR))
                || (departure != null && departure.isAfter(now))
                || (arrival != null && (arrival.isBefore(departure) || arrival.isAfter(now)))) {
            logger.warning("Skipped flight " + flightId + " because its actual times are inconsistent");
            return;
        }

        if (flight.getStatus() == Flight.FlightStatus.ACTIVE && departure != null) {
            flight.setStatus(Flight.FlightStatus.IN_AIR);
            flightRepository.save(flight);
            String description = "System changed flight with id " + flightId + " from ACTIVE to IN_AIR";
            auditLogService.addAuditLog(null, AuditLog.Action.FLIGHT_STATUS_CHANGED, AuditLog.EntityType.FLIGHT, flightId, description);
            logger.info(description);
        }

        if (flight.getStatus() == Flight.FlightStatus.IN_AIR && departure != null && arrival != null) {
            flight.setStatus(Flight.FlightStatus.CLOSED);
            flightRepository.save(flight);
            String description = "System changed flight with id " + flightId + " from IN_AIR to CLOSED";
            auditLogService.addAuditLog(null, AuditLog.Action.FLIGHT_STATUS_CHANGED, AuditLog.EntityType.FLIGHT, flightId, description);
            logger.info(description);
        }
    }
}
