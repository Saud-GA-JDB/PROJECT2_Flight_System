package dockerbdd.definitions;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NotificationAPIStepDefinitions {
    @Autowired DockerTestData data;
    private Response response;
    private long requestId;
    private String airlineToken;
    private InputStream stream;
    private ExecutorService reader;
    private final CompletableFuture<Void> connected = new CompletableFuture<>();
    private final CompletableFuture<JsonPath> notification = new CompletableFuture<>();

    @Given("an airline admin has a grounded airplane")
    public void prepareAirlineAdmin() {
        airlineToken = data.login(data.airlineEmail);
        Assert.assertEquals("GROUNDED", data.jdbc.queryForObject(
                "SELECT status FROM airplanes WHERE id = ?", String.class, data.airplaneId));
    }

    @Given("an FAA admin is connected to notifications")
    public void connectFaaAdmin() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(data.notificationUrl + "/notifications"))
                .header("Authorization", "Bearer " + data.login(data.faaEmail))
                .header("Accept", "text/event-stream").timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<InputStream> subscription = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        stream = subscription.body();
        Assert.assertEquals("SSE subscription failed", 200, subscription.statusCode());
        Assert.assertTrue(subscription.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "docker-cucumber-sse");
            thread.setDaemon(true);
            return thread;
        });
        reader.submit(this::readNotifications);
        connected.get(10, TimeUnit.SECONDS); // Subscribe before the business action, so its event cannot be missed.
    }

    private void readNotifications() {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String event = "";
            StringBuilder payload = new StringBuilder();
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.isEmpty()) {
                    if ("connected".equals(event)) connected.complete(null);
                    if ("airplane-activation-requested".equals(event)) {
                        JsonPath message = JsonPath.from(payload.toString());
                        if (data.airplaneId.equals(message.getLong("airplaneId"))) notification.complete(message);
                    }
                    event = "";
                    payload.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).stripLeading();
                } else if (line.startsWith("data:")) {
                    payload.append(line.substring(5).stripLeading()).append('\n');
                }
            }
            throw new IllegalStateException("SSE stream closed before the expected notification");
        } catch (Exception error) {
            connected.completeExceptionally(error);
            notification.completeExceptionally(error);
        }
    }

    @When("the airline admin requests airplane activation")
    public void requestActivation() {
        response = data.request(data.aviationUrl).header("Authorization", "Bearer " + airlineToken)
                .when().post("/airlineAdmin/airplanes/" + data.airplaneId + "/requestActivation");
    }

    @Then("the activation request is created successfully")
    public void verifyRequestCreated() {
        Assert.assertEquals(201, response.statusCode());
        Assert.assertEquals(data.airplaneId.longValue(), response.jsonPath().getLong("airplaneId"));
        Assert.assertEquals("PENDING", response.jsonPath().getString("status"));
        requestId = response.jsonPath().getLong("requestId");
        Assert.assertTrue(requestId > 0);
    }

    @Then("the FAA admin receives its activation-requested notification")
    public void verifyNotification() throws Exception {
        JsonPath message = notification.get(15, TimeUnit.SECONDS);
        Assert.assertEquals(requestId, message.getLong("requestId"));
        Assert.assertEquals(data.airplaneId.longValue(), message.getLong("airplaneId"));
        Assert.assertEquals(data.marker, message.getString("registrationNumber"));
        Assert.assertEquals("PENDING", message.getString("status"));
    }

    @After(order = 10)
    public void closeSubscription() throws Exception {
        try {
            if (stream != null) stream.close();
        } finally {
            if (reader != null) {
                reader.shutdownNow();
                Assert.assertTrue("SSE reader must stop", reader.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }
}
