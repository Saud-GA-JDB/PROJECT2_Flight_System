package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class FlightServiceTest {
    @Autowired
    FlightService flightService;
    @Autowired
    FlightRepository flightRepository;
    @Autowired
    BookingRepository bookingRepository;

    @Test
    @DisplayName("when flight has departed then status is in air")
    public final void whenFlightHasDepartedThenStatusIsInAir() {
        Booking booking = bookingRepository.findById(900001L).orElse(null);
        Assert.assertNotNull("Add the test booking from TESTING-NOTES-TEMP.md", booking);
        Flight flight = booking.getFlight();
        Assert.assertEquals(Flight.FlightStatus.ACTIVE, flight.getStatus());
        flight.setActualDeparture(LocalDateTime.now().minusMinutes(10));
        flight.setActualArrival(null);
        flightRepository.save(flight);

        flightService.updateFlightStatus();

        Assert.assertEquals(Flight.FlightStatus.IN_AIR, flight.getStatus());
        Assert.assertEquals(Flight.FlightStatus.IN_AIR,
                flightRepository.findById(flight.getId()).orElseThrow().getStatus());
    }
}
