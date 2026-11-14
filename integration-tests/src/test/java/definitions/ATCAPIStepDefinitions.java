package definitions;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import static definitions.TestData.assertStatus;

public class ATCAPIStepDefinitions {
    @Autowired TestData data;
    private Response response;
    private Map<String, Object> details;
    private long createdControllerId;

    @Given("An ATC admin has details for a new controller")
    public void prepareController() { details = data.employeeDetails("newController"); }

    @When("The ATC admin adds the controller")
    public void postNewController() {
        response = data.request(data.towerAdmin).body(details).when().post("/atcAdmin/controllers");
    }

    @Then("The controller is added to the admin's tower")
    public void verifyControllerAdded() {
        assertStatus(201, response);
        createdControllerId = response.jsonPath().getLong("id");
        Assert.assertEquals("CONTROLLER", response.jsonPath().getString("atcRole"));
        Assert.assertEquals(data.originTower.getId().longValue(), response.jsonPath().getLong("facilityId"));
        Assert.assertEquals(details.get("emailAddress"), response.jsonPath().getString("emailAddress"));
    }

    @When("The ATC admin views the tower controllers")
    public void getControllers() { response = data.request(data.towerAdmin).when().get("/atcAdmin/controllers"); }

    @Then("The new controller is listed without controllers from another tower")
    public void verifyControllerListed() {
        assertStatus(200, response);
        Assert.assertEquals(2, response.jsonPath().getList("content").size());
        Assert.assertTrue(response.jsonPath().getList("content.id", Long.class).contains(createdControllerId));
        for (Long facilityId : response.jsonPath().getList("content.facilityId", Long.class))
            Assert.assertEquals(data.originTower.getId(), facilityId);
    }
}
