package definitions;

import com.ga.saudsFlightSystem.model.Flight;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import static definitions.TestData.assertStatus;

public class FlightOperationAPIStepDefinitions {
    @Autowired TestData data;
    private Response response;
    private long revision;

    @Given("A ready flight is assigned to the pilot")
    public void getReadyFlight() { data.createFlight(true); }

    @Then("The pilot can see the assigned flight")
    public void verifyPilotFlightListed() {
        response = data.request(data.pilot).when().get("/pilot/flights");
        assertStatus(200, response);
        Assert.assertEquals(1, response.jsonPath().getList("content").size());
        Assert.assertEquals(data.flight.getId().longValue(), response.jsonPath().getLong("content[0].flightId"));
    }

    @When("The pilot requests take-off clearance")
    public void postTakeOffRequest() {
        response = data.request(data.pilot).body(Map.of("expectedRevision", revision)).when()
                .post("/pilot/flights/" + data.flight.getId() + "/requestTakeOff");
        assertStatus(201, response);
        verifyClearance("DEPARTURE", "PENDING");
        revision = response.jsonPath().getLong("clearance.revision");
    }

    @Then("The origin controller can see the pending request")
    public void verifyPendingRequestListed() {
        Response pending = data.request(data.originController).queryParam("operation", "DEPARTURE").when().get("/atc/requests");
        assertStatus(200, pending);
        Assert.assertEquals(1, pending.jsonPath().getList("content").size());
        Assert.assertEquals(data.flight.getId().longValue(), pending.jsonPath().getLong("content[0].flightId"));
    }

    @When("The origin controller approves take-off clearance")
    public void postApproveDeparture() {
        response = data.request(data.originController).body(Map.of("operation", "DEPARTURE",
                "expectedRevision", revision, "decision", "APPROVED")).when()
                .post("/atc/flights/" + data.flight.getId() + "/clearance/decision");
    }

    @Then("Departure clearance is approved")
    public void verifyDepartureApproved() {
        assertStatus(200, response);
        verifyClearance("DEPARTURE", "APPROVED");
        Assert.assertNotNull(response.jsonPath().getString("clearance.expiresAt"));
        revision = response.jsonPath().getLong("clearance.revision");
    }

    @When("The pilot takes off")
    @When("The pilot tries to take off")
    public void postTakeOff() {
        response = data.request(data.pilot).body(Map.of("expectedRevision", revision)).when()
                .post("/pilot/flights/" + data.flight.getId() + "/takeOff");
    }

    @Then("The flight is in the air and departure clearance is consumed")
    public void verifyFlightDeparted() {
        assertStatus(200, response);
        Assert.assertEquals("IN_AIR", response.jsonPath().getString("status"));
        Assert.assertNotNull(response.jsonPath().getString("actualDeparture"));
        verifyClearance("DEPARTURE", "CONSUMED");
        Assert.assertEquals(Flight.FlightStatus.IN_AIR, data.savedFlight().getStatus());
        revision = response.jsonPath().getLong("clearance.revision");
    }

    @When("The pilot requests landing clearance")
    public void postLandingRequest() {
        response = data.request(data.pilot).body(Map.of("expectedRevision", revision)).when()
                .post("/pilot/flights/" + data.flight.getId() + "/requestLanding");
        assertStatus(201, response);
        verifyClearance("ARRIVAL", "PENDING");
        revision = response.jsonPath().getLong("clearance.revision");
    }

    @When("The destination controller approves landing clearance")
    public void postApproveArrival() {
        response = data.request(data.destinationController).body(Map.of("operation", "ARRIVAL",
                "expectedRevision", revision, "decision", "APPROVED")).when()
                .post("/atc/flights/" + data.flight.getId() + "/clearance/decision");
    }

    @Then("Arrival clearance is approved")
    public void verifyArrivalApproved() {
        assertStatus(200, response);
        verifyClearance("ARRIVAL", "APPROVED");
        Assert.assertEquals(data.destinationTower.getId().longValue(), response.jsonPath().getLong("clearance.facilityId"));
        revision = response.jsonPath().getLong("clearance.revision");
    }

    @When("The pilot lands")
    public void postLanding() {
        response = data.request(data.pilot).body(Map.of("expectedRevision", revision)).when()
                .post("/pilot/flights/" + data.flight.getId() + "/land");
    }

    @Then("The flight is closed and arrival clearance is consumed")
    public void verifyFlightLanded() {
        assertStatus(200, response);
        Assert.assertEquals("CLOSED", response.jsonPath().getString("status"));
        Assert.assertNotNull(response.jsonPath().getString("actualArrival"));
        verifyClearance("ARRIVAL", "CONSUMED");
        Flight saved = data.savedFlight();
        Assert.assertEquals(Flight.FlightStatus.CLOSED, saved.getStatus());
        Assert.assertNotNull(saved.getActualArrival());
        Assert.assertFalse(saved.getActualArrival().isBefore(saved.getActualDeparture()));
    }

    @When("The origin controller rejects take-off clearance")
    public void postRejectDeparture() {
        response = data.request(data.originController).body(Map.of("operation", "DEPARTURE",
                "expectedRevision", revision, "decision", "REJECTED", "reason", "Runway unavailable"))
                .when().post("/atc/flights/" + data.flight.getId() + "/clearance/decision");
    }

    @Then("Departure clearance is rejected")
    public void verifyDepartureRejected() {
        assertStatus(200, response);
        verifyClearance("DEPARTURE", "REJECTED");
        revision = response.jsonPath().getLong("clearance.revision");
    }

    @Then("Take-off is rejected and the flight has not departed")
    public void verifyTakeOffRejected() {
        assertStatus(409, response);
        Flight saved = data.savedFlight();
        Assert.assertEquals(Flight.FlightStatus.ACTIVE, saved.getStatus());
        Assert.assertNull(saved.getActualDeparture());
        Assert.assertEquals(revision, saved.getClearanceRevision());
    }

    private void verifyClearance(String operation, String status) {
        Assert.assertEquals(data.flight.getId().longValue(), response.jsonPath().getLong("flightId"));
        Assert.assertEquals(operation, response.jsonPath().getString("clearance.operation"));
        Assert.assertEquals(status, response.jsonPath().getString("clearance.status"));
    }
}
