package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.BookingResponse;
import com.ga.saudsFlightSystem.repository.AirlineRepository;
import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
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
    private AirlineRepository airlineRepository;
    private EmailService emailService;
    private AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(BookingService.class.getName());

    public List<Booking> getBookings() {
        User user = UserService.getCurrentLoggedInUser();
        if (user.getRole() != User.Role.AIRLINE_EMPLOYEE)
            return user.getBookingsList();
        if (user.getAirlineEmployee() != null && user.getAirlineEmployee().getAirlineRole() == AirlineEmployee.AirlineRole.ADMIN)
            return bookingRepository.findByFlight_Airline_IdOrderByBookedAtDesc(user.getAirlineEmployee().getAirline().getId());
        throw new IllegalEndpoint("You are not allowed to view these bookings");
    }

    public List<Booking> getUserBookings(Long userId, Long flightId, Booking.BookingStatus status) {
        User user = UserService.getCurrentLoggedInUser();
        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint");


        if (user.getAirlineEmployee().getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint.");
        Long airlineId = user.getAirlineEmployee().getAirline().getId();

        return bookingRepository.searchBookings(airlineId, userId, flightId, status);
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
        User user = UserService.getCurrentLoggedInUser();
        Booking booking = bookingRepository.findById(bookingId).orElseThrow( () -> {
            throw new InvalidInformationException("No Booking with that id found");
        });
        if (user.getRole() == User.Role.AIRLINE_EMPLOYEE &&
                (user.getAirlineEmployee() == null || user.getAirlineEmployee().getAirline() == null))
            throw new IllegalEndpoint("No airline employee information found");
        boolean isAirlineAdmin = user.getRole() == User.Role.AIRLINE_EMPLOYEE &&
                user.getAirlineEmployee().getAirlineRole() == AirlineEmployee.AirlineRole.ADMIN &&
                Objects.equals(user.getAirlineEmployee().getAirline().getId(), booking.getFlight().getAirline().getId());
        //check that only airline admin can change other people bookings
        if (!Objects.equals(user.getId(), booking.getUser().getId()) && !isAirlineAdmin)
            throw new IllegalEndpoint("Only airline admins can cancel other people bookings for their airline");

        if (booking.getStatus() != Booking.BookingStatus.BOOKED)
            throw new InvalidInformationException("booking is already done. you can only cancel active bookings");


        // the 48 hour cancellation limit only applies when the user is not an airline admin
        if (isAirlineAdmin) {
            booking.setStatus(Booking.BookingStatus.CANCELLED);
        } else {
            if (!LocalDateTime.now().isAfter(booking.getFlight().getScheduledDeparture().minusHours(48))) {
                booking.setStatus(Booking.BookingStatus.CANCELLED);
            } else {
                logger.warning("User with id " + user.getId() + " could not cancel booking with id " + bookingId + " because departure is less than 48 hours away");
                return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                        .body("Sorry, Bookings must be cancelled 48 hours before flight departure. You can contact the airline customer service if you have special circumstances.");
            }
        }
        Flight flight = booking.getFlight();
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


        auditLogService.addAuditLog(user, AuditLog.Action.BOOKING_CANCELLED, AuditLog.EntityType.BOOKING, savedBooking.getId(), description);

        emailService.sendEmail(savedBooking.getUser().getEmailAddress(), "Booking Cancelled", String.format(
                """
                        Your booking %s for flight %s has been cancelled.
                        Best Regards,
                        Saud Flight System.""",
                savedBooking.getBookingRef(),
                flight.getFlightNumber()
        ));
        logger.info(description + " (user id: " + user.getId() + ")");
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
        User bookingOwner = userRepository.findById(userId)
                .orElseThrow(() -> new InformationNotFoundException("No user with that ID exists."));
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow( () -> {
                    throw new InformationNotFoundException("No flight with that Id exist.");
                });
        if (flight.getStatus() != Flight.FlightStatus.ACTIVE)
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
        User user = UserService.getCurrentLoggedInUser();
        User.Role role = user.getRole();
        Airline flightAirline = flight.getAirline();
        // not booking for himself
        if (!Objects.equals(user.getId(), userId)) {
            if (role != User.Role.AIRLINE_EMPLOYEE || user.getAirlineEmployee() == null) {
                throw new IllegalEndpoint("only airline admin can book a flight for another user");
            }
            if (role == User.Role.AIRLINE_EMPLOYEE && !Objects.equals(user.getAirlineEmployee().getAirline().getId(), flightAirline.getId())) {
                throw new IllegalEndpoint("You are not allowed to book for another airline");
            }
            if (role == User.Role.AIRLINE_EMPLOYEE && user.getAirlineEmployee().getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN) {
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
        if (Objects.equals(user.getId(), bookingOwner.getId()))
            description = "User created booking with id " + savedBooking.getId() + " for their own account";
        else
            description = "Airline admin created booking with id " + savedBooking.getId() + " for user with id " + bookingOwner.getId();


        auditLogService.addAuditLog(user, AuditLog.Action.BOOKING_CREATED, AuditLog.EntityType.BOOKING, savedBooking.getId(), description);

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
        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(201).body(new BookingResponse(
                savedBooking.getId(), savedBooking.getBookingRef(), bookingOwner.getId(),
                flight.getId(), savedBooking.getSeatNumber(), savedBooking.getStatus(),
                savedBooking.getBookedAt()));
    }
}
