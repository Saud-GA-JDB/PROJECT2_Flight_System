package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.request.response.FlightResponse;
import com.ga.saudsFlightSystem.model.request.response.FlightSearchResponse;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class FlightService {
    private FlightRepository flightRepository;
    private AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(FlightService.class.getName());

    public FlightSearchResponse searchFlights(String date, String airlineCode, String originAirport,
                                               String destinationAirport, String originCity, String destinationCity,
                                               String originCountry, String destinationCountry, String seatType,
                                               String page, String size, String sort) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        if (date != null) {
            LocalDate departureDate;
            try {
                departureDate = LocalDate.parse(date);
                startDate = departureDate.atStartOfDay();
                endDate = departureDate.plusDays(1).atStartOfDay();
            } catch (java.time.DateTimeException ex) {
                throw new InvalidInformationException("please enter a valid date in yyyy-MM-dd format");
            }
            if (departureDate.isBefore(now.toLocalDate()))
                throw new InvalidInformationException("please choose today or a future date");
        }
        if (seatType != null && !seatType.equals("firstClass") && !seatType.equals("standard"))
            throw new InvalidInformationException("please choose firstClass or standard");

        int pageNumber;
        int pageSize;
        try {
            pageNumber = Integer.parseInt(page);
            pageSize = Integer.parseInt(size);
        } catch (NumberFormatException ex) {
            throw new InvalidInformationException("page and size must be whole numbers");
        }
        if (pageNumber < 1)
            throw new InvalidInformationException("page must be at least 1");
        if (pageSize < 1 || pageSize > 100)
            throw new InvalidInformationException("size must be between 1 and 100");

        String[] sortValues = sort.split(",", -1);
        if (sortValues.length != 2)
            throw new InvalidInformationException("please use sort=field,asc or sort=field,desc");
        String sortField = sortValues[0];
        String sortDirection = sortValues[1];
        if (!sortField.equals("scheduledDeparture") && !sortField.equals("scheduledArrival") && !sortField.equals("flightNumber"))
            throw new InvalidInformationException("please sort by scheduledDeparture, scheduledArrival or flightNumber");
        if (!sortDirection.equals("asc") && !sortDirection.equals("desc"))
            throw new InvalidInformationException("sort direction must be asc or desc");

        long offset = (pageNumber - 1L) * pageSize;
        LocalDateTime bookingCutoff = now.plusMinutes(5);
        List<Flight> flights = flightRepository.searchFlights(bookingCutoff, startDate, endDate,
                airlineCode, originAirport, destinationAirport, originCity, destinationCity,
                originCountry, destinationCountry, seatType, sortField, sortDirection, pageSize, offset);
        long totalElements = flightRepository.countSearchFlights(bookingCutoff, startDate, endDate,
                airlineCode, originAirport, destinationAirport, originCity, destinationCity,
                originCountry, destinationCountry, seatType);

        List<FlightResponse> content = new ArrayList<>();
        for (Flight flight : flights) {
            content.add(new FlightResponse(flight.getId(), flight.getFlightNumber(), flight.getAirline().getAirlineCode(),
                    flight.getOriginAirport().getIataCode(), flight.getDestinationAirport().getIataCode(),
                    flight.getOriginAirport().getCity(), flight.getDestinationAirport().getCity(),
                    flight.getOriginAirport().getCountry(), flight.getDestinationAirport().getCountry(),
                    flight.getScheduledDeparture(), flight.getScheduledArrival(),
                    flight.getFirstClassSeatsCount(), flight.getStandardSeatsCount()));
        }
        long totalPages = totalElements / pageSize;
        if (totalElements % pageSize != 0)
            totalPages++;
        return new FlightSearchResponse(content, pageNumber, pageSize, totalElements, totalPages);
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void updateFlightStatus() {
        LocalDateTime now = LocalDateTime.now();

        for (Flight flight : flightRepository.findByStatus(Flight.FlightStatus.ACTIVE)) {
            if (flight.getActualDeparture() != null && !flight.getActualDeparture().isAfter(now)) {
                flight.setStatus(Flight.FlightStatus.IN_AIR);
                flightRepository.save(flight);
                String description = "System changed flight with id " + flight.getId() + " from ACTIVE to IN_AIR";
                auditLogService.addAuditLog(null, AuditLog.Action.FLIGHT_STATUS_CHANGED, AuditLog.EntityType.FLIGHT, flight.getId(), description);
                logger.info(description);
            }
        }

        for (Flight flight : flightRepository.findByStatus(Flight.FlightStatus.IN_AIR)) {
            if (flight.getActualArrival() != null && !flight.getActualArrival().isAfter(now)) {
                flight.setStatus(Flight.FlightStatus.CLOSED);
                flightRepository.save(flight);
                String description = "System changed flight with id " + flight.getId() + " from IN_AIR to CLOSED";
                auditLogService.addAuditLog(null, AuditLog.Action.FLIGHT_STATUS_CHANGED, AuditLog.EntityType.FLIGHT, flight.getId(), description);
                logger.info(description);
            }
        }
    }
}
