package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.BookingResponse;
import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@AllArgsConstructor
public class BookingService {
    private FlightRepository flightRepository;
    private BookingRepository bookingRepository;
    private UserRepository userRepository;

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

        return ResponseEntity.status(201).body(new BookingResponse(
                savedBooking.getId(), savedBooking.getBookingRef(), bookingOwner.getId(),
                flight.getId(), savedBooking.getSeatNumber(), savedBooking.getStatus(),
                savedBooking.getBookedAt()));
    }
}
