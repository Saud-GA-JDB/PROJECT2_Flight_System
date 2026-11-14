package definitions;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import static definitions.TestData.assertStatus;

public class MaintenanceAPIStepDefinitions {
    @Autowired TestData data;
    private Response response;
    private long recordId;

    @Given("An airline has a contracted maintenance provider")
    public void getContractedProvider() { Assert.assertNotNull(data.maintenanceProvider.getId()); }

    @When("The airline admin requests airplane maintenance")
    public void postMaintenanceRequest() {
        response = data.request(data.airlineAdmin).body(Map.of("providerId", data.maintenanceProvider.getId(),
                "description", "Inspect the engine")).when()
                .post("/airlineAdmin/airplanes/" + data.airplane.getId() + "/maintenanceRequests");
        assertStatus(201, response);
        recordId = response.jsonPath().getLong("id");
        Assert.assertEquals("PENDING", response.jsonPath().getString("status"));
        Assert.assertEquals(data.airplane.getId().longValue(), response.jsonPath().getLong("airplaneId"));
    }

    @Then("The maintenance admin can see the pending request")
    public void verifyMaintenanceRequestListed() {
        response = data.request(data.maintenanceAdmin).when().get("/maintenanceAdmin/maintenanceRequests");
        assertStatus(200, response);
        Assert.assertEquals(1, response.jsonPath().getList("$").size());
        Assert.assertEquals(recordId, response.jsonPath().getLong("[0].id"));
        Assert.assertEquals("PENDING", response.jsonPath().getString("[0].status"));
        Assert.assertNull(response.jsonPath().get("[0].technicianId"));
    }

    @When("The maintenance admin assigns the technician")
    public void postAssignTechnician() {
        response = data.request(data.maintenanceAdmin).queryParam("technicianId", data.technicianEmployeeId)
                .when().post("/maintenanceAdmin/maintenanceRequests/" + recordId);
        assertStatus(200, response);
        Assert.assertEquals(data.technicianEmployeeId.longValue(), response.jsonPath().getLong("technicianId"));
    }

    @Then("The technician can see their assigned maintenance")
    public void verifyTechnicianAssignmentListed() {
        response = data.request(data.technician).when().get("/maintenanceTechnician/maintenanceRecords");
        assertStatus(200, response);
        Assert.assertEquals(1, response.jsonPath().getList("$").size());
        Assert.assertEquals(recordId, response.jsonPath().getLong("[0].id"));
        Assert.assertEquals(data.technicianEmployeeId.longValue(), response.jsonPath().getLong("[0].technicianId"));
    }

    @When("The technician completes the maintenance")
    public void putCompleteMaintenance() {
        response = data.request(data.technician).queryParam("findings", "Engine inspected successfully")
                .when().put("/maintenanceTechnician/maintenanceRecords/" + recordId + "/complete");
    }

    @Then("The maintenance is completed with the findings recorded")
    public void verifyMaintenanceCompleted() {
        assertStatus(200, response);
        Response records = data.request(data.technician).when().get("/maintenanceTechnician/maintenanceRecords");
        assertStatus(200, records);
        Assert.assertEquals(recordId, records.jsonPath().getLong("[0].id"));
        Assert.assertEquals("COMPLETED", records.jsonPath().getString("[0].status"));
        Assert.assertEquals("Engine inspected successfully", records.jsonPath().getString("[0].findings"));
        Assert.assertNotNull(records.jsonPath().getString("[0].completedAt"));
    }
}
