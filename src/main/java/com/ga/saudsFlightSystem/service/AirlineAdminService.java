package com.ga.saudsFlightSystem.service;

import com.ga.common.contract.identity.CustomerListResponse;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.common.web.Pagination;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.identity.IdentityServiceClient;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.Currency;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class AirlineAdminService {
    private CurrentActorProvider currentActorProvider;
    private AirlineEmployeeRepository airlineEmployeeRepository;
    private FlightRepository flightRepository;
    private PaymentRepository paymentRepository;
    private AirplaneRepository airplaneRepository;
    private AirplaneRequestRepository airplaneRequestRepository;
    private IdentityServiceClient identityServiceClient;

    @Transactional(readOnly = true)
    public AirlineDashboardResponse getDashboard(LocalDate fromDate, LocalDate toDate) {
        Long airlineId = getAirlineId();

        if (fromDate == null || toDate == null || fromDate.isAfter(toDate))
            throw new InvalidInformationException("Please enter a valid fromDate and toDate.");
        if (fromDate.getYear() < 1 || toDate.getYear() > 9999)
            throw new InvalidInformationException("Dates must be between years 1 and 9999.");
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= 366)
            throw new InvalidInformationException("The date range must not exceed 366 days.");

        LocalDateTime startDate = fromDate.atStartOfDay();
        LocalDateTime endDate = toDate.plusDays(1).atStartOfDay();

        List<AirlineDashboardDayResponse> days = new ArrayList<>();
        for (FlightRepository.AirlineDashboardDay day :
                flightRepository.findAirlineDashboardDays(airlineId, startDate, endDate)) {
            BigDecimal occupancyPercentage = null;
            if (day.getSeatCapacity() > 0) {
                occupancyPercentage = BigDecimal.valueOf(day.getOccupiedSeats())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(day.getSeatCapacity()), 2, RoundingMode.HALF_UP);
            }

            days.add(new AirlineDashboardDayResponse(
                    day.getDate(), day.getFlightCount(), day.getBookingCount(),
                    day.getBookedBookings(), day.getCancelledBookings(), day.getFinishedBookings(),
                    day.getOccupiedSeats(), day.getSeatCapacity(), occupancyPercentage));
        }

        List<AirlineRevenueResponse> revenue = new ArrayList<>();
        for (PaymentRepository.AirlineRevenue payment :
                paymentRepository.findAirlineRevenue(airlineId, startDate, endDate)) {
            revenue.add(new AirlineRevenueResponse(
                    payment.getDate(), Currency.valueOf(payment.getCurrency()), payment.getPaidTotal(), null));
        }

        return new AirlineDashboardResponse(
                fromDate, toDate, "Asia/Bahrain", "SCHEDULED_DEPARTURE", false, days, revenue);
    }

    public CustomerListResponse getCustomers(String query, int page, int size) {
        getAirlineId();
        validatePage(page, size);

        if (query == null || query.isBlank() || query.trim().length() > 100)
            throw new InvalidInformationException("query must contain between 1 and 100 characters.");

        return identityServiceClient.customers(query.trim(), page, size);
    }

    @Transactional(readOnly = true)
    public AirplaneRequestListResponse getAirplaneRequests(Long airplaneId, int page, int size) {
        Long airlineId = getAirlineId();
        validatePage(page, size);

        if (airplaneId == null || airplaneId <= 0)
            throw new InvalidInformationException("Please enter a valid airplane ID.");

        Airplane airplane = airplaneRepository.findByIdAndAirline_Id(airplaneId, airlineId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find an airplane with this ID."));

        Page<AirplaneRequest> requests = airplaneRequestRepository
                .findByAirplane_IdOrderByRequestedAtDescIdDesc(airplaneId, PageRequest.of(page, size));

        List<AirplaneRequestResponse> content = new ArrayList<>();
        for (AirplaneRequest request : requests.getContent()) {
            content.add(new AirplaneRequestResponse(
                    request.getId(), airplane.getId(), airplane.getRegistrationNumber(), airplane.getModel(),
                    request.getStatus(), request.getReviewReason(), request.getRequestedAt(), request.getReviewedAt()));
        }

        AirplaneRequest latestRequest = airplaneRequestRepository
                .findFirstByAirplane_IdOrderByRequestedAtDescIdDesc(airplaneId).orElse(null);
        boolean pendingRequest = airplaneRequestRepository
                .existsByAirplane_IdAndStatus(airplaneId, AirplaneRequest.ApprovalStatus.PENDING);

        LocalDateTime nextEligibleRequestAt = latestRequest == null
                ? null : latestRequest.getRequestedAt().plusDays(7);
        boolean blocked = airplane.getStatus() != Airplane.Status.GROUNDED || pendingRequest;
        boolean canRequestActivation = !blocked && (nextEligibleRequestAt == null ||
                !nextEligibleRequestAt.isAfter(LocalDateTime.now(ZoneId.of("Asia/Bahrain"))));

        if (blocked)
            nextEligibleRequestAt = null;

        return new AirplaneRequestListResponse(
                content, page, size, requests.getTotalElements(),
                Pagination.totalPages(requests.getTotalElements(), size),
                canRequestActivation, nextEligibleRequestAt);
    }

    private Long getAirlineId() {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()) || !"AIRLINE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("Only active airline admins can use this endpoint.");

        AirlineEmployee admin = airlineEmployeeRepository
                .findByUserIdAndRole(actor.userId(), AirlineEmployee.AirlineRole.ADMIN)
                .orElseThrow( () -> new IllegalEndpoint("Only airline admins can use this endpoint."));
        if (admin.getAirline() == null)
            throw new IllegalEndpoint("The admin must belong to an airline.");

        return admin.getAirline().getId();
    }

    private void validatePage(int page, int size) {
        Pagination.validateZeroBased(page, size);
        if ((long) page * size > Integer.MAX_VALUE)
            throw new InvalidInformationException("The requested page is outside the supported range.");
    }
}
