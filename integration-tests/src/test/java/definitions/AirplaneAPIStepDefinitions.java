package definitions;

import com.ga.saudsFlightSystem.model.Airplane;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import static definitions.TestData.assertStatus;

public class AirplaneAPIStepDefinitions {
    @Autowired TestData data;
    private Response response;
    private String registrationNumber;
    private Long airplaneId;
    private long requestId;

    @Given("An airline admin has a new airplane to register")
    public void prepareNewAirplane() { registrationNumber = "NEW-" + data.marker; }

    @When("The airline admin adds the airplane")
    public void postNewAirplane() {
        response = data.request(data.airlineAdmin).body(Map.of("registrationNumber", registrationNumber,
                "model", "Test airplane", "standardSeatCapacity", 10, "firstClassSeatCapacity", 2,
                "maxMileage", 100000)).when().post("/airlineAdmin/airplanes");
    }

    @Then("The airplane is added as grounded")
    public void verifyAirplaneAdded() {
        assertStatus(201, response);
        Assert.assertEquals(registrationNumber, response.jsonPath().getString("registrationNumber"));
        Assert.assertEquals("GROUNDED", response.jsonPath().getString("status"));
        airplaneId = data.em.createQuery("select a.id from Airplane a where a.registrationNumber = :number", Long.class)
                .setParameter("number", registrationNumber).getSingleResult();
    }

    @When("The airline admin requests airplane activation")
    public void postActivationRequest() {
        response = data.request(data.airlineAdmin).when().post("/airlineAdmin/airplanes/" + airplaneId + "/requestActivation");
        assertStatus(201, response);
        requestId = response.jsonPath().getLong("requestId");
        Assert.assertEquals("PENDING", response.jsonPath().getString("status"));
    }

    @Then("The FAA admin can see the pending activation request")
    public void verifyPendingRequest() {
        response = data.request(data.faaAdmin).when().get("/faaadmin/airplaneRequests");
        assertStatus(200, response);
        Assert.assertTrue(response.jsonPath().getList("requestId", Long.class).contains(requestId));
    }

    @When("The FAA admin accepts the activation request")
    public void putAcceptRequest() { review("ACCEPTED", "Airplane checks passed"); }

    @Then("The activation request is accepted and the airplane is active")
    public void verifyRequestAccepted() { verifyReview("ACCEPTED", Airplane.Status.ACTIVE); }

    @Given("A new airplane has a pending activation request")
    public void preparePendingRequest() {
        prepareNewAirplane(); postNewAirplane(); verifyAirplaneAdded(); postActivationRequest();
    }

    @When("The FAA admin denies the activation request")
    public void putDenyRequest() { review("DENIED", "Further maintenance is required"); }

    @Then("The activation request is denied and the airplane remains grounded")
    public void verifyRequestDenied() {
        verifyReview("DENIED", Airplane.Status.GROUNDED);
        Assert.assertEquals("Further maintenance is required", response.jsonPath().getString("reviewReason"));
    }

    private void review(String status, String reason) {
        response = data.request(data.faaAdmin).body(Map.of("status", status, "reviewReason", reason))
                .when().put("/faaadmin/airplaneRequests/" + requestId);
    }

    private void verifyReview(String status, Airplane.Status airplaneStatus) {
        assertStatus(200, response);
        Assert.assertEquals(requestId, response.jsonPath().getLong("requestId"));
        Assert.assertEquals(status, response.jsonPath().getString("status"));
        Assert.assertNotNull(response.jsonPath().getString("reviewedAt"));
        Assert.assertEquals(airplaneStatus, data.transaction().execute(tx -> data.em.find(Airplane.class, airplaneId).getStatus()));
    }
}
