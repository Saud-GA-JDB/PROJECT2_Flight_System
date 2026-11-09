package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.repository.PaymentRepository;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.BookingResponse;
import com.ga.saudsFlightSystem.model.request.response.BookingSummaryResponse;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import com.ga.saudsFlightSystem.security.CurrentActor;
import com.ga.saudsFlightSystem.security.CurrentActorProvider;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class BookingService {
    private FlightRepository flightRepository;
    private BookingRepository bookingRepository;
    private UserRepository userRepository;
    private EmailService emailService;
    private AuditLogService auditLogService;
    private PaymentRepository paymentRepository;
    private CurrentActorProvider currentActorProvider;
    private AirlineEmployeeRepository airlineEmployeeRepository;

    private static final Logger logger = Logger.getLogger(BookingService.class.getName());

    public List<BookingSummaryResponse> getBookings() {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRLINE_EMPLOYEE".equals(actor.role())) {
            return bookingRepository.findByUser_Id(actor.userId())
                    .stream()
                    .map(this::toBookingSummary)
                    .toList();
        }

        AirlineEmployee employee = requireAirlineEmployee(actor.userId());

        if (employee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed to view these bookings");

        return bookingRepository.findByFlight_Airline_IdOrderByBookedAtDesc(employee.getAirline().getId())
                .stream()
                .map(this::toBookingSummary)
                .toList();
    }

    public List<BookingSummaryResponse> getUserBookings(Long userId, Long flightId, Booking.BookingStatus status) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRLINE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint");

        AirlineEmployee employee = requireAirlineEmployee(actor.userId());

        if (employee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint.");

        Long airlineId = employee.getAirline().getId();

        return bookingRepository.searchBookings(airlineId, userId, flightId, status)
                .stream()
                .map(this::toBookingSummary)
                .toList();
    }

    /**
     * Cancels an active booking and makes the seat available again.
     * Airline admins can cancel bookings for their airline within 48 hours of departure.
     *
     * @param bookingId booking to cancel
     * @return the cancelled booking details or a message if cancellation is too late
     */
    @Transactional
    public ResponseEntity<?> cancelBooking(Long bookingId) {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        Long flightId = bookingRepository.findFlightIdByBookingId(bookingId)
                .orElseThrow( () -> new InformationNotFoundException("No Booking with that id found"));
        Flight flight = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow( () -> new InformationNotFoundException("No flight with that Id exist."));
        Booking booking = bookingRepository.findByIdForPayment(bookingId).orElseThrow( () -> {
            throw new InformationNotFoundException("No Booking with that id found");
        });
        AirlineEmployee employee = "AIRLINE_EMPLOYEE".equals(actor.role())
                ? requireAirlineEmployee(actor.userId()) : null;
        boolean isAirlineAdmin = employee != null &&
                employee.getAirlineRole() == AirlineEmployee.AirlineRole.ADMIN &&
                Objects.equals(employee.getAirline().getId(), booking.getFlight().getAirline().getId());
        //check that only airline admin can change other people bookings
        if (!Objects.equals(actor.userId(), booking.getUser().getId()) && !isAirlineAdmin)
            throw new IllegalEndpoint("Only airline admins can cancel other people bookings for their airline");

        if (booking.getStatus() != Booking.BookingStatus.BOOKED)
            throw new InvalidInformationException("booking is already done. you can only cancel active bookings");


        Payment payment = paymentRepository.findByBooking_Id(bookingId).orElse(null);

        if (payment != null && payment.getStatus() == Payment.PaymentStatus.PENDING)
            throw new InformationExistException(
                    "Payment is pending. Checkout must expire before cancelling");

        if (payment != null && payment.getStatus() == Payment.PaymentStatus.PAID)
            throw new InformationExistException(
                    "This booking is paid and requires a refund before cancellation");

        // the 48 hour cancellation limit only applies when the user is not an airline admin
        if (isAirlineAdmin) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);
        } else {
            if (!LocalDateTime.now().isAfter(booking.getFlight().getScheduledDeparture().minusHours(48))) {
                booking.setStatus(Booking.BookingStatus.CANCELLED);
            } else {
                logger.warning("User with id " + actor.userId() + " could not cancel booking with id " + bookingId + " because departure is less than 48 hours away");
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Sorry, Bookings must be cancelled 48 hours before flight departure. You can contact the airline customer service if you have special circumstances.");
            }
        }
        if (booking.getSeatNumber().startsWith("firstClass-")) {
            flight.setFirstClassSeatsCount(flight.getFirstClassSeatsCount() + 1);
        } else {
            flight.setStandardSeatsCount(flight.getStandardSeatsCount() + 1);
        }
        flightRepository.save(flight);
        Booking savedBooking = bookingRepository.save(booking);
        String description;
        if (isAirlineAdmin)
            description = "Airline admin cancelled booking with id " + savedBooking.getId() + " for user with id " + savedBooking.getUser().getId();
        else
            description = "User cancelled booking with id " + savedBooking.getId() + " for their own account";


        auditLogService.addAuditLogForActor(actor, AuditLog.Action.BOOKING_CANCELLED, AuditLog.EntityType.BOOKING, savedBooking.getId(), description);

        emailService.sendEmail(savedBooking.getUser().getEmailAddress(), "Booking Cancelled", String.format(
                """
                        Your booking %s for flight %s has been cancelled.
                        Best Regards,
                        Saud Flight System.""",
                savedBooking.getBookingRef(),
                flight.getFlightNumber()
        ));
        logger.info(description + " (user id: " + actor.userId() + ")");
        return ResponseEntity.status(HttpStatus.OK)
                .body(new BookingResponse(
                        savedBooking.getId(), savedBooking.getBookingRef(), savedBooking.getUser().getId(),
                        savedBooking.getFlight().getId(), savedBooking.getSeatNumber(), savedBooking.getStatus(),
                        savedBooking.getBookedAt()));
    }

    /**
     * Books a flight after checking user permission and seat availability.
     *
     * @param userId user who will own the booking
     * @param flightId flight to book
     * @param seatType firstClass or standard
     * @param seatId seat number within the selected class
     * @return the new booking details
     */
    @Transactional
    public ResponseEntity<BookingResponse> bookFlight(Long userId, Long flightId, String seatType, Long seatId) {
        //validate input
        Flight flight = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow( () -> new InformationNotFoundException("No flight with that Id exist."));
        User bookingOwner = userRepository.findById(userId)
                .orElseThrow( () -> new InformationNotFoundException("No user with that ID exists."));
        if (flight.getStatus() != Flight.FlightStatus.ACTIVE || flight.getActualDeparture() != null)
            throw new InvalidInformationException("This flight is not Open for booking");
        if (!flight.getScheduledDeparture().isAfter(LocalDateTime.now().plusMinutes(5)))
            throw new InvalidInformationException("sorry this flight doors are already closed");
        if (seatType == null || !seatType.equals("firstClass") && !seatType.equals("standard"))
            throw new InvalidInformationException("please choose firstClass or standard");
        if (seatId == null || seatId <= 0 ||
                seatType.equals("firstClass") && seatId > flight.getAirplane().getFirstClassSeatsCapacity() ||
                seatType.equals("standard") && seatId > flight.getAirplane().getStandardSeatsCapacity()
        ) throw new InvalidInformationException("please put valid seat number");
        if (seatType.equals("firstClass") && flight.getFirstClassSeatsCount() <= 0 ||
            seatType.equals("standard") && flight.getStandardSeatsCount() <= 0
        ) throw new InvalidInformationException("sorry there are no seats available in this class");
        if (bookingRepository.existsByFlightIdAndSeatNumberAndStatus(flightId, seatType + "-" + seatId, Booking.BookingStatus.BOOKED))
            throw new InvalidInformationException("sorry this seat is already booked");
        if (flight.getAirplane().getStatus() != Airplane.Status.ACTIVE)
            throw new InvalidInformationException("the plane assigned for this flight is InActive, please choose another flight");
        CurrentActor actor = currentActorProvider.getCurrentActor();
        Airline flightAirline = flight.getAirline();
        // not booking for himself
        if (!Objects.equals(actor.userId(), userId)) {
            if (!"AIRLINE_EMPLOYEE".equals(actor.role())) {
                throw new IllegalEndpoint("only airline admin can book a flight for another user");
            }
            AirlineEmployee employee = requireAirlineEmployee(actor.userId());
            if (!Objects.equals(employee.getAirline().getId(), flightAirline.getId())) {
                throw new IllegalEndpoint("You are not allowed to book for another airline");
            }
            if (employee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN) {
                throw new IllegalEndpoint("only airline admin can book a flight for customers");
            }
        }

        // now that we validated, we can book.
        Booking booking = new Booking();
        booking.setFlight(flight);
        booking.setStatus(Booking.BookingStatus.BOOKED);
        booking.setUser(bookingOwner);
        booking.setBookingRef(UUID.randomUUID().toString());
        booking.setSeatNumber(seatType + "-" + seatId);
        if (seatType.equals("firstClass")) {
            flight.setFirstClassSeatsCount(flight.getFirstClassSeatsCount() - 1);
        } else {
            flight.setStandardSeatsCount(flight.getStandardSeatsCount() - 1);
        }
        flightRepository.save(flight);
        Booking savedBooking = bookingRepository.save(booking);

        String description;
        if (Objects.equals(actor.userId(), bookingOwner.getId()))
            description = "User created booking with id " + savedBooking.getId() + " for their own account";
        else
            description = "Airline admin created booking with id " + savedBooking.getId() + " for user with id " + bookingOwner.getId();


        auditLogService.addAuditLogForActor(actor, AuditLog.Action.BOOKING_CREATED, AuditLog.EntityType.BOOKING, savedBooking.getId(), description);

        emailService.sendEmail(bookingOwner.getEmailAddress(), "Booking Confirmation", String.format(
                """
                        Your flight has been booked successfully.
                        Booking reference: %s
                        Flight number: %s
                        Seat: %s
                        From: %s
                        To: %s
                        Scheduled departure: %s
                        Best Regards,
                        Saud Flight System.""",
                savedBooking.getBookingRef(),
                flight.getFlightNumber(),
                savedBooking.getSeatNumber(),
                flight.getOriginAirport().getIataCode(),
                flight.getDestinationAirport().getIataCode(),
                flight.getScheduledDeparture()
        ));
        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.status(201).body(new BookingResponse(
                savedBooking.getId(), savedBooking.getBookingRef(), bookingOwner.getId(),
                flight.getId(), savedBooking.getSeatNumber(), savedBooking.getStatus(),
                savedBooking.getBookedAt()));
    }

    private BookingSummaryResponse toBookingSummary(Booking booking) {
        return new BookingSummaryResponse(
                booking.getId(),
                booking.getBookedAt(),
                booking.getUpdatedAt(),
                booking.getBookingRef(),
                booking.getSeatNumber(),
                booking.getStatus() == null ? null : booking.getStatus().name()
        );
    }

    private AirlineEmployee requireAirlineEmployee(Long userId) {
        return airlineEmployeeRepository.findByUserId(userId)
                .filter(employee -> employee.getAirline() != null)
                .orElseThrow(() -> new IllegalEndpoint("No airline employee information found"));
    }
}
