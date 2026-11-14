package definitions;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import static definitions.TestData.assertStatus;

public class InspectionAPIStepDefinitions {
    @Autowired TestData data;
    private Response response;
    private long inspectionId;

    @Given("A flight is waiting for inspection")
    public void getFlightAwaitingInspection() { data.createFlight(false); }

    @When("The airport admin assigns the inspector")
    public void postAssignInspector() {
        response = data.request(data.airportAdmin).queryParam("inspectorId", data.inspectorEmployeeId)
                .when().post("/airportAdmin/flights/" + data.flight.getId() + "/inspection");
        assertStatus(200, response);
    }

    @Then("The airport admin can see the assigned inspection")
    public void verifyAdminInspectionListed() {
        response = data.request(data.airportAdmin).when().get("/airportAdmin/inspections");
        assertStatus(200, response);
        Assert.assertEquals(1, response.jsonPath().getList("$").size());
        inspectionId = response.jsonPath().getLong("[0].id");
        verifyAssignment();
    }

    @Then("The inspector can see their assigned inspection")
    public void verifyInspectorAssignmentListed() {
        response = data.request(data.inspector).when().get("/airportInspector/inspections");
        assertStatus(200, response);
        Assert.assertEquals(1, response.jsonPath().getList("$").size());
        Assert.assertEquals(inspectionId, response.jsonPath().getLong("[0].id"));
        verifyAssignment();
    }

    @When("The inspector passes the inspection")
    public void putPassInspection() { completeInspection("PASSED", "All checks passed"); }

    @Then("The inspection is passed with its reason recorded")
    public void verifyInspectionPassed() { verifyResult("PASSED", "All checks passed"); }

    @Given("A flight has an assigned inspection")
    public void prepareAssignedInspection() {
        getFlightAwaitingInspection(); postAssignInspector(); verifyAdminInspectionListed();
    }

    @When("The inspector fails the inspection")
    public void putFailInspection() { completeInspection("FAILED", "Landing gear needs repair"); }

    @Then("The inspection is failed with its reason recorded")
    public void verifyInspectionFailed() { verifyResult("FAILED", "Landing gear needs repair"); }

    private void verifyAssignment() {
        Assert.assertEquals(data.flight.getId().longValue(), response.jsonPath().getLong("[0].flightId"));
        Assert.assertEquals(data.inspectorEmployeeId.longValue(), response.jsonPath().getLong("[0].inspectorId"));
        Assert.assertEquals("PENDING", response.jsonPath().getString("[0].status"));
    }

    private void completeInspection(String result, String reason) {
        response = data.request(data.inspector).queryParam("result", result).queryParam("reason", reason)
                .when().put("/airportInspector/flights/" + data.flight.getId() + "/inspections/" + inspectionId + "/complete");
    }

    private void verifyResult(String result, String reason) {
        assertStatus(200, response);
        Response inspections = data.request(data.inspector).when().get("/airportInspector/inspections");
        assertStatus(200, inspections);
        Assert.assertEquals(inspectionId, inspections.jsonPath().getLong("[0].id"));
        Assert.assertEquals(result, inspections.jsonPath().getString("[0].status"));
        Assert.assertEquals(reason, inspections.jsonPath().getString("[0].reason"));
        Assert.assertNotNull(inspections.jsonPath().getString("[0].completedAt"));
    }
}
