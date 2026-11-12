package com.ga.saudsFlightSystem.service;

import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.request.response.FlightResponse;
import com.ga.saudsFlightSystem.model.request.response.FlightSearchResponse;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class FlightService {
    private FlightRepository flightRepository;
    private AuditLogService auditLogService;
    private CrewAssignmentService crewAssignmentService;
    private FlightStatusService flightStatusService;
    private CurrentActorProvider currentActorProvider;
    private AirlineEmployeeRepository airlineEmployeeRepository;

    private static final Logger logger = Logger.getLogger(FlightService.class.getName());

    /**
     * Searches flights open for booking using the given filters and page settings.
     *
     * @param date optional departure date in yyyy-MM-dd format
     * @param airlineCode optional airline code
     * @param originAirport optional origin airport code
     * @param destinationAirport optional destination airport code
     * @param originCity optional origin city
     * @param destinationCity optional destination city
     * @param originCountry optional origin country
     * @param destinationCountry optional destination country
     * @param seatType optional firstClass or standard seat filter
     * @param page page number starting from 1
     * @param size number of flights per page
     * @param sort field and direction, such as scheduledDeparture,asc
     * @return matching flights with page details
     */
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
        // booking closes five minutes before departure, so hide flights past that limit
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

    @Transactional
    public ResponseEntity<?> activateFlight(Long flightId) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRLINE_EMPLOYEE".equals(actor.role() ))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = airlineEmployeeRepository.findByUserIdAndRole(actor.userId(), AirlineEmployee.AirlineRole.ADMIN)
                .orElseThrow( () -> new IllegalEndpoint("You are not allowed this endpoint!"));

        Flight flight = flightRepository.findByIdForCrewWrite(flightId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find a flight with this ID."));

        if (!Objects.equals(flight.getAirline().getId(), airlineAdmin.getAirline().getId()))
            throw new IllegalEndpoint("You are not allowed to edit flights that don't belong to your airline.");

        if (flight.getStatus() != Flight.FlightStatus.PENDING_CREW || flight.getActualDeparture() != null || flight.getActualArrival() != null)
            throw new InvalidInformationException("Only pending crew flights with no actual departure or arrival can be activated.");

        if (!crewAssignmentService.hasRequiredCrew(flightId))
            throw new InvalidInformationException("Make sure the flight has all the required crew assignments.");

        flight.setStatus(Flight.FlightStatus.ACTIVE);
        flightRepository.save(flight);

        String description = "Airline admin activated flight with id " + flightId;
        auditLogService.addAuditLogForActor(actor,
                AuditLog.Action.FLIGHT_STATUS_CHANGED,
                AuditLog.EntityType.FLIGHT,
                flightId, description);
        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.ok("Flight activated successfully.");
    }

    /**
     * Updates flight status every five minutes using actual departure and arrival times.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void updateFlightStatus() {
        List<Long> flightIds = flightRepository.findIdsByStatuses(Flight.FlightStatus.ACTIVE, Flight.FlightStatus.IN_AIR);

        for (Long flightId : flightIds) {
            try {
                flightStatusService.updateFlightStatus(flightId);
            } catch (RuntimeException ex) {
                logger.warning("Could not update status for flight " + flightId + ": " + ex.getClass().getSimpleName());
            }
        }
    }
}
