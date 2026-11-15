package dockerbdd.definitions;

import com.ga.identity.model.User;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static dockerbdd.config.DockerCucumberConfiguration.setting;

public class DockerTestData {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;

    final String identityUrl = setting("DOCKER_TEST_IDENTITY_URL", "http://localhost:8081");
    final String aviationUrl = setting("DOCKER_TEST_AVIATION_URL", "http://localhost:8080");
    final String notificationUrl = setting("DOCKER_TEST_NOTIFICATION_URL", "http://localhost:8084");
    final String marker = "docker-cucumber-" + UUID.randomUUID();
    final String airlineEmail = marker + "-airline@example.invalid";
    final String faaEmail = marker + "-faa@example.invalid";
    private final String password = "Docker1!" + UUID.randomUUID().toString().substring(0, 8);
    private Long airlineId;
    private Long airlineUserId;
    private Long faaUserId;
    Long airplaneId;
    private boolean fixturesCommitted;

    @Before
    public void createTestRecords(Scenario scenario) {
        for (String url : new String[]{identityUrl, aviationUrl, notificationUrl}) {
            Assert.assertEquals("Start the Docker services before running this scenario: " + url,
                    200, request(url).get("/v3/api-docs").statusCode());
        }
        scenario.log("Test records: " + marker);
        String encodedPassword = new BCryptPasswordEncoder().encode(password);
        transaction.executeWithoutResult(status -> {
            airlineUserId = createUser(airlineEmail, User.Role.AIRLINE_EMPLOYEE, encodedPassword);
            faaUserId = createUser(faaEmail, User.Role.FAA_ADMIN, encodedPassword);
            airlineId = jdbc.queryForObject("""
                    INSERT INTO airline (name, airline_code, headquarters_country)
                    VALUES (?, ?, 'Bahrain') RETURNING id
                    """, Long.class, marker, marker);
            jdbc.update("""
                    INSERT INTO airline_employees (user_id, airline_id, airline_role, created_at, updated_at)
                    VALUES (?, ?, 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, airlineUserId, airlineId);
            airplaneId = jdbc.queryForObject("""
                    INSERT INTO airplanes (registration_number, model, status, airline_id, added_at,
                                           standard_seats_capacity, first_class_seats_capacity, max_mileage)
                    VALUES (?, 'Docker test airplane', 'GROUNDED', ?, CURRENT_TIMESTAMP, 10, 2, 100000)
                    RETURNING id
                    """, Long.class, marker, airlineId);
        });
        fixturesCommitted = true;
    }

    private Long createUser(String email, User.Role role, String encodedPassword) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (email_address, password, role, status, is_active,
                                   failed_login_attempts, failed_password_reset_attempts)
                VALUES (?, ?, ?, ?, true, 0, 0) RETURNING id
                """, Long.class, email, encodedPassword, role.name(), User.Status.ACTIVE.ordinal());
        String cpr;
        do {
            cpr = String.valueOf(ThreadLocalRandom.current().nextLong(100000000, 1000000000));
        } while (jdbc.queryForObject("SELECT count(*) FROM user_profiles WHERE cpr = ?", Long.class, cpr) != 0);
        jdbc.update("""
                INSERT INTO user_profiles (user_id, f_name, l_name, cpr, phone_number_opening_code,
                                           phone_number, created_at, updated_at)
                VALUES (?, 'Docker', 'Test', ?, '+973', '33123456', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, cpr);
        return id;
    }

    public RequestSpecification request(String url) {
        return RestAssured.given().baseUri(url).contentType("application/json")
                .config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 5000).setParam("http.socket.timeout", 30000)));
    }

    public String login(String email) {
        Response response = request(identityUrl).body(Map.of("email", email, "password", password))
                .when().post("/auth/users/login");
        Assert.assertEquals("Test account login failed", 200, response.statusCode());
        String token = response.jsonPath().getString("message");
        Assert.assertNotNull("Login must return a JWT", token);
        return token;
    }

    @After(order = 0)
    public void deleteTestRecords(Scenario scenario) {
        if (!fixturesCommitted) return; // A setup failure rolls back the entire fixture transaction.
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM audit_logs WHERE user_id IN (?, ?)", airlineUserId, faaUserId);
            jdbc.update("DELETE FROM identity_audit_logs WHERE user_id IN (?, ?)", airlineUserId, faaUserId);
            jdbc.update("DELETE FROM airplane_requests WHERE airplane_id = ?", airplaneId);
            jdbc.update("DELETE FROM airplanes WHERE id = ?", airplaneId);
            jdbc.update("DELETE FROM airline_employees WHERE user_id = ?", airlineUserId);
            jdbc.update("DELETE FROM airline WHERE id = ?", airlineId);
            jdbc.update("DELETE FROM user_profiles WHERE user_id IN (?, ?)", airlineUserId, faaUserId);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", airlineUserId, faaUserId);
            Assert.assertEquals(0L, jdbc.queryForObject(
                    "SELECT count(*) FROM users WHERE id IN (?, ?)", Long.class, airlineUserId, faaUserId).longValue());
            Assert.assertEquals(0L, jdbc.queryForObject(
                    "SELECT count(*) FROM airplanes WHERE id = ?", Long.class, airplaneId).longValue());
        });
        scenario.log("Removed test records: " + marker);
    }
}
