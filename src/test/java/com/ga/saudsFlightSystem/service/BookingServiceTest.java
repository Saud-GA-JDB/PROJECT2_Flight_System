package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class BookingServiceTest {
    @Autowired
    BookingService bookingService;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    FlightRepository flightRepository;

    @Autowired
    AuthenticationManager authenticationManager;

    User user;
    Flight flight;
    Booking booking;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("flighttest.customer@mailsac.com", "TestPassword1"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        user = UserService.getCurrentLoggedInUser();
        Assert.assertEquals(User.Role.CUSTOMER, user.getRole());

        booking = bookingRepository.findById(900001L).orElse(null);
        Assert.assertNotNull("Add the test booking from TESTING-NOTES-TEMP.md", booking);
        Assert.assertEquals(user.getId(), booking.getUser().getId());
        Assert.assertEquals("flighttest.customer@mailsac.com", booking.getUser().getEmailAddress());
        Assert.assertEquals(Booking.BookingStatus.BOOKED, booking.getStatus());
        Assert.assertEquals("standard-1", booking.getSeatNumber());

        flight = booking.getFlight();
        Assert.assertEquals(Flight.FlightStatus.ACTIVE, flight.getStatus());
        Assert.assertEquals(Airplane.Status.ACTIVE, flight.getAirplane().getStatus());
        Assert.assertTrue(flight.getAirplane().getStandardSeatsCapacity() >= 2);

        // keep the flight in the future whenever the tests are run
        flight.setScheduledDeparture(LocalDateTime.now().plusDays(3));
        flight.setScheduledArrival(LocalDateTime.now().plusDays(3).plusHours(2));
        flightRepository.save(flight);
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test(expected = InvalidInformationException.class)
    @DisplayName("when no seats are available then booking is rejected")
    public final void whenNoSeatsAreAvailableThenBookingIsRejected() {
        flight.setStandardSeatsCount(0);
        flightRepository.save(flight);

        try {
            bookingService.bookFlight(user.getId(), flight.getId(), "standard", 2L);
        } catch (InvalidInformationException e) {
            Assert.assertEquals("sorry there are no seats available in this class", e.getMessage());
            throw e;
        }
    }

    @Test
    @DisplayName("when booking is cancelled then seat is available again")
    public final void whenBookingIsCancelledThenSeatIsAvailableAgain() {
        int availableSeats = flight.getStandardSeatsCount();

        ResponseEntity<?> response = bookingService.cancelBooking(booking.getId());

        Assert.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assert.assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
        Assert.assertEquals(availableSeats + 1, flight.getStandardSeatsCount());
        Assert.assertFalse(bookingRepository.existsByFlightIdAndSeatNumberAndStatus(
                flight.getId(), "standard-1", Booking.BookingStatus.BOOKED));
    }

    @Test
    @DisplayName("when departure is within 48 hours then cancellation is rejected")
    public final void whenDepartureIsWithin48HoursThenCancellationIsRejected() {
        flight.setScheduledDeparture(LocalDateTime.now().plusHours(24));
        flightRepository.save(flight);
        int availableSeats = flight.getStandardSeatsCount();

        ResponseEntity<?> response = bookingService.cancelBooking(booking.getId());

        Assert.assertEquals(HttpStatus.EXPECTATION_FAILED, response.getStatusCode());
        Assert.assertEquals(Booking.BookingStatus.BOOKED, booking.getStatus());
        Assert.assertEquals(availableSeats, flight.getStandardSeatsCount());
    }

    @Test(expected = IllegalEndpoint.class)
    @DisplayName("when customer views airline bookings then access is denied")
    public final void whenCustomerViewsAirlineBookingsThenAccessIsDenied() {
        bookingService.getUserBookings(null, null, null);
    }
}
