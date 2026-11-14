package definitions;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import static definitions.TestData.assertStatus;

public class BookingAPIStepDefinitions {
    @Autowired TestData data;
    private Response response;
    private long createdBookingId;

    @Given("A flight is available for booking")
    public void getFlightAvailable() { data.createFlight(false); }

    @When("I search for the available flight")
    public void getAvailableFlight() {
        response = data.request(data.customer).queryParam("airlineCode", data.airline.getAirlineCode())
                .queryParam("originAirport", data.origin.getIataCode())
                .queryParam("destinationAirport", data.destination.getIataCode())
                .when().get("/flights/search");
    }

    @Then("The flight is returned")
    public void verifyFlightReturned() {
        assertStatus(200, response);
        Assert.assertEquals(1, response.jsonPath().getList("content").size());
        Assert.assertEquals(data.flight.getId().longValue(), response.jsonPath().getLong("content[0].id"));
    }

    @When("I book a standard seat for myself")
    public void postNewBooking() {
        response = data.request(data.customer).when().post(bookingPath(data.customer.getId()));
    }

    @Then("The booking is added and one seat is reserved")
    public void verifyBookingAdded() {
        assertStatus(201, response);
        createdBookingId = response.jsonPath().getLong("id");
        Assert.assertTrue(createdBookingId > 0);
        Assert.assertEquals("BOOKED", response.jsonPath().getString("status"));
        Assert.assertEquals("standard-1", response.jsonPath().getString("seatNumber"));
        Assert.assertEquals(data.customer.getId().longValue(), response.jsonPath().getLong("userId"));
        Assert.assertEquals(data.flight.getId().longValue(), response.jsonPath().getLong("flightId"));
        Assert.assertFalse(response.jsonPath().getString("bookingRef").isBlank());
        Assert.assertEquals(9, data.savedFlight().getStandardSeatsCount());
    }

    @When("I view my bookings")
    public void getBookings() { response = data.request(data.customer).when().get("/bookings"); }

    @Then("My new booking is listed")
    public void verifyBookingListed() {
        assertStatus(200, response);
        Assert.assertEquals(1, response.jsonPath().getList("$").size());
        Assert.assertEquals(createdBookingId, response.jsonPath().getLong("[0].id"));
        Assert.assertEquals("BOOKED", response.jsonPath().getString("[0].status"));
    }

    @When("I cancel my booking")
    public void deleteBooking() { response = data.request(data.customer).when().delete("/bookings/" + createdBookingId); }

    @Then("The booking is cancelled and the seat is available again")
    public void verifyBookingCancelled() {
        assertStatus(200, response);
        Assert.assertEquals("CANCELLED", response.jsonPath().getString("status"));
        Assert.assertEquals(10, data.savedFlight().getStandardSeatsCount());
        Response bookings = data.request(data.customer).when().get("/bookings");
        assertStatus(200, bookings);
        Assert.assertEquals(createdBookingId, bookings.jsonPath().getLong("[0].id"));
        Assert.assertEquals("CANCELLED", bookings.jsonPath().getString("[0].status"));
    }

    @Given("Its first standard seat is already booked")
    public void bookFirstSeat() { postNewBooking(); verifyBookingAdded(); }

    @When("Another customer books the same seat")
    public void postDuplicateBooking() {
        response = data.request(data.otherCustomer).when().post(bookingPath(data.otherCustomer.getId()));
    }

    @Then("The duplicate booking is rejected without reserving another seat")
    public void verifyDuplicateRejected() {
        assertStatus(400, response);
        Assert.assertEquals("sorry this seat is already booked", response.jsonPath().getString("message"));
        Assert.assertEquals(9, data.savedFlight().getStandardSeatsCount());
        verifyOtherCustomerHasNoBookings();
    }

    @When("I book a seat for another customer")
    public void postOtherCustomerBooking() {
        response = data.request(data.customer).when().post(bookingPath(data.otherCustomer.getId()));
    }

    @Then("The booking is forbidden and no seat is reserved")
    public void verifyOtherCustomerBookingForbidden() {
        assertStatus(403, response);
        Assert.assertEquals(10, data.savedFlight().getStandardSeatsCount());
        verifyOtherCustomerHasNoBookings();
    }

    private void verifyOtherCustomerHasNoBookings() {
        Response bookings = data.request(data.otherCustomer).when().get("/bookings");
        assertStatus(200, bookings);
        Assert.assertTrue(bookings.jsonPath().getList("$").isEmpty());
    }

    private String bookingPath(Long userId) {
        return "/customer/" + userId + "/flights/" + data.flight.getId() + "/standard/1/book";
    }
}
