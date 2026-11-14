package definitions;

import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import static definitions.TestData.assertStatus;

public class AirlineAPIStepDefinitions {
    @Autowired TestData data;
    private Response response;
    private String airlineCode;
    private long createdAirlineId;
    private String adminEmail;

    @Given("The FAA admin has details for a new airline")
    public void prepareNewAirline() {
        airlineCode = "NEW-" + data.marker;
        data.airlineCodes.add(airlineCode);
    }

    @When("The FAA admin creates the airline")
    public void postNewAirline() {
        response = data.request(data.faaAdmin).queryParam("name", airlineCode).queryParam("airlineCode", airlineCode)
                .queryParam("headquartersCountry", "Bahrain").when().post("/faaadmin/airlines");
        assertStatus(200, response);
        createdAirlineId = response.jsonPath().getLong("id");
        Assert.assertEquals(airlineCode, response.jsonPath().getString("airlineCode"));
    }

    @Then("The new airline is listed")
    public void verifyAirlineListed() {
        response = data.request(data.faaAdmin).when().get("/faaadmin/airlines");
        assertStatus(200, response);
        Assert.assertTrue(response.jsonPath().getList("id", Long.class).contains(createdAirlineId));
    }

    @When("The FAA admin adds an administrator to the new airline")
    public void postNewAirlineAdmin() {
        Map<String, Object> details = data.employeeDetails("newAirlineAdmin");
        adminEmail = (String) details.get("emailAddress");
        response = data.request(data.faaAdmin).body(details).when()
                .post("/faaadmin/airlines/" + createdAirlineId + "/addAirlineAdmin");
    }

    @Then("The new administrator belongs to that airline")
    public void verifyAirlineAdminAdded() {
        assertStatus(201, response);
        Long userId = data.em.createQuery("select u.id from User u where u.emailAddress = :email", Long.class)
                .setParameter("email", adminEmail).getSingleResult();
        Long owner = data.em.createQuery("select e.airline.id from AirlineEmployee e where e.userId = :id and e.airlineRole = :role", Long.class)
                .setParameter("id", userId).setParameter("role", AirlineEmployee.AirlineRole.ADMIN).getSingleResult();
        Assert.assertEquals(createdAirlineId, owner.longValue());
    }

    @Given("An airline admin has an active airplane available")
    public void getActiveAirplane() { Assert.assertNotNull(data.airplane.getId()); }

    @When("The airline admin schedules a flight")
    public void postNewFlight() {
        LocalDateTime departure = LocalDateTime.now(ZoneId.of("Asia/Bahrain")).plusDays(4);
        response = data.request(data.airlineAdmin).body(Map.of("scheduledDeparture", departure.toString(),
                "scheduledArrival", departure.plusHours(2).toString(),
                "originAirportIataCode", data.origin.getIataCode(), "arrivalAirportIataCode", data.destination.getIataCode(),
                "firstClassPrice", 100, "standardPrice", 50, "currency", "BHD"))
                .when().post("/airlineAdmin/airplanes/" + data.airplane.getId() + "/addFlight");
    }

    @Then("The flight is waiting for crew")
    public void verifyFlightAdded() {
        assertStatus(201, response);
        Assert.assertEquals("PENDING_CREW", response.jsonPath().getString("status"));
        data.flight = data.em.createQuery("select f from Flight f where f.airline.id = :id", Flight.class)
                .setParameter("id", data.airline.getId()).getSingleResult();
        Assert.assertEquals(data.flight.getFlightNumber(), response.jsonPath().getString("flightNumber"));
    }

    @When("The airline admin assigns both pilots and a flight attendant")
    public void postCrewAssignments() {
        assign(data.pilotEmployeeId, "PILOT");
        assign(data.secondaryEmployeeId, "SECONDARY_PILOT");
        assign(data.attendantEmployeeId, "FLIGHT_ATTENDANT");
    }

    @Then("The three crew assignments are listed")
    public void verifyCrewListed() {
        response = data.request(data.airlineAdmin).when().get("/airlineAdmin/flights/" + data.flight.getId() + "/crewAssignments");
        assertStatus(200, response);
        Assert.assertEquals(3, response.jsonPath().getList("$").size());
        Assert.assertTrue(response.jsonPath().getList("duty", String.class)
                .containsAll(List.of("PILOT", "SECONDARY_PILOT", "FLIGHT_ATTENDANT")));
    }

    @When("The airline admin activates the flight")
    public void putActivateFlight() {
        response = data.request(data.airlineAdmin).when().put("/airlineAdmin/flights/" + data.flight.getId() + "/activate");
    }

    @Then("The flight is active and available for booking")
    public void verifyFlightActivated() {
        assertStatus(200, response);
        Assert.assertEquals(Flight.FlightStatus.ACTIVE, data.savedFlight().getStatus());
        Response search = data.request(data.customer).queryParam("airlineCode", data.airline.getAirlineCode()).when().get("/flights/search");
        assertStatus(200, search);
        Assert.assertEquals(data.flight.getId().longValue(), search.jsonPath().getLong("content[0].id"));
    }

    private void assign(Long employeeId, String duty) {
        Response assignment = data.request(data.airlineAdmin).body(Map.of("employeeId", employeeId, "duty", duty))
                .when().post("/airlineAdmin/flights/" + data.flight.getId() + "/addCrewAssignment");
        assertStatus(201, assignment);
    }
}
