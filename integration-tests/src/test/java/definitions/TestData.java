package definitions;

import com.ga.identity.model.User;
import com.ga.identity.model.UserProfile;
import com.ga.maintenance.model.MaintenanceContract;
import com.ga.maintenance.model.MaintenanceEmployee;
import com.ga.maintenance.model.MaintenanceProvider;
import com.ga.saudsFlightSystem.model.*;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.persistence.EntityManager;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Cucumber creates a fresh instance for each scenario and shares it with the step definitions.
public class TestData {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PasswordEncoder passwords;
    @Autowired ApplicationContext context;
    @LocalServerPort int port;

    final String marker = "cucumber-" + UUID.randomUUID();
    final List<String> emails = new ArrayList<>();
    final List<String> airlineCodes = new ArrayList<>();
    final Map<Long, String> tokens = new HashMap<>();
    String encodedPassword;
    Airline airline;
    Airplane airplane;
    Airport origin;
    Airport destination;
    AirportOperator operator;
    AirNavigationServiceProvider navigationProvider;
    ControlFacility originTower;
    ControlFacility destinationTower;
    MaintenanceProvider maintenanceProvider;
    Flight flight;
    User customer;
    User otherCustomer;
    User faaAdmin;
    User airlineAdmin;
    User pilot;
    User secondaryPilot;
    User attendant;
    User towerAdmin;
    User originController;
    User destinationController;
    User airportAdmin;
    User inspector;
    User maintenanceAdmin;
    User technician;
    Long pilotEmployeeId;
    Long secondaryEmployeeId;
    Long attendantEmployeeId;
    Long inspectorEmployeeId;
    Long technicianEmployeeId;

    @Before
    public void createTestRecords(Scenario scenario) {
        Assert.assertFalse("Scheduled jobs must stay disabled when using the existing database",
                context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
        scenario.log("Test records: " + marker);
        encodedPassword = passwords.encode("TestPassword1");
        transaction().executeWithoutResult(status -> {
            airlineCodes.add(marker);
            airline = new Airline();
            airline.setName(marker);
            airline.setAirlineCode(marker);
            airline.setHeadquartersCountry("Bahrain");
            em.persist(airline);

            airplane = new Airplane();
            airplane.setAirline(airline);
            airplane.setRegistrationNumber(marker);
            airplane.setModel("Test airplane");
            airplane.setStatus(Airplane.Status.ACTIVE);
            airplane.setStandardSeatsCapacity(10);
            airplane.setFirstClassSeatsCapacity(2);
            airplane.setMaxMileage(100000L);
            em.persist(airplane);

            operator = new AirportOperator();
            operator.setName(marker);
            operator.setContactEmail("operator@example.com");
            em.persist(operator);
            origin = airport("O-");
            destination = airport("D-");
            navigationProvider = new AirNavigationServiceProvider();
            navigationProvider.setName(marker);
            navigationProvider.setContactEmail("tower@example.com");
            em.persist(navigationProvider);
            originTower = tower(origin);
            destinationTower = tower(destination);

            customer = user("customer", User.Role.CUSTOMER);
            otherCustomer = user("other", User.Role.CUSTOMER);
            faaAdmin = user("faa", User.Role.FAA_ADMIN);
            airlineAdmin = airlineUser("airline", AirlineEmployee.AirlineRole.ADMIN);
            pilot = airlineUser("pilot", AirlineEmployee.AirlineRole.PILOT);
            secondaryPilot = airlineUser("secondary", AirlineEmployee.AirlineRole.PILOT);
            attendant = airlineUser("attendant", AirlineEmployee.AirlineRole.FLIGHT_ATTENDANT);
            pilotEmployeeId = employeeId(pilot);
            secondaryEmployeeId = employeeId(secondaryPilot);
            attendantEmployeeId = employeeId(attendant);
            towerAdmin = atcUser("towerAdmin", originTower, ATCEmployee.ATCRole.ADMIN);
            originController = atcUser("originController", originTower, ATCEmployee.ATCRole.CONTROLLER);
            destinationController = atcUser("destinationController", destinationTower, ATCEmployee.ATCRole.CONTROLLER);
            airportAdmin = airportUser("airportAdmin", AirportEmployee.AirportRole.ADMIN);
            inspector = airportUser("inspector", AirportEmployee.AirportRole.INSPECTOR);
            inspectorEmployeeId = em.createQuery("select e.id from AirportEmployee e where e.userId = :id", Long.class)
                    .setParameter("id", inspector.getId()).getSingleResult();

            maintenanceProvider = new MaintenanceProvider();
            maintenanceProvider.setName(marker);
            maintenanceProvider.setContactEmail("maintenance@example.com");
            em.persist(maintenanceProvider);
            MaintenanceContract contract = new MaintenanceContract();
            contract.setAirlineId(airline.getId());
            contract.setProviderId(maintenanceProvider.getId());
            em.persist(contract);
            maintenanceAdmin = maintenanceUser("maintenanceAdmin", MaintenanceEmployee.MaintenanceRole.ADMIN);
            technician = maintenanceUser("technician", MaintenanceEmployee.MaintenanceRole.TECHNICIAN);
            technicianEmployeeId = em.createQuery("select e.id from MaintenanceEmployee e where e.userId = :id", Long.class)
                    .setParameter("id", technician.getId()).getSingleResult();
        });
    }

    public void createFlight(boolean readyForDeparture) {
        transaction().executeWithoutResult(status -> {
            flight = new Flight();
            flight.setAirline(airline);
            flight.setAirplane(airplane);
            flight.setFlightNumber(marker);
            flight.setOriginAirport(origin);
            flight.setDestinationAirport(destination);
            flight.setScheduledDeparture(LocalDateTime.now(ZoneId.of("Asia/Bahrain")).plusDays(4));
            flight.setScheduledArrival(flight.getScheduledDeparture().plusHours(2));
            flight.setStatus(Flight.FlightStatus.ACTIVE);
            flight.setStandardSeatsCount(10);
            flight.setFirstClassSeatsCount(2);
            flight.setStandardPrice(50L);
            flight.setFirstClassPrice(100L);
            em.persist(flight);
            if (readyForDeparture) {
                assign(pilotEmployeeId, Duty.PILOT);
                assign(secondaryEmployeeId, Duty.SECONDARY_PILOT);
                assign(attendantEmployeeId, Duty.FLIGHT_ATTENDANT);
                FlightInspection inspection = new FlightInspection();
                inspection.setFlight(flight);
                inspection.setAirport(origin);
                inspection.setStatus(FlightInspection.InspectionStatus.PASSED);
                em.persist(inspection);
            }
        });
    }

    public RequestSpecification request(User user) {
        if (!tokens.containsKey(user.getId())) {
            Response login = RestAssured.given().baseUri("http://localhost:" + port)
                    .contentType("application/json")
                    .body(Map.of("email", user.getEmailAddress(), "password", "TestPassword1"))
                    .when().post("/auth/users/login");
            assertStatus(200, login);
            String token = login.jsonPath().getString("message");
            Assert.assertNotNull("Login should return a JWT", token);
            tokens.put(user.getId(), token);
        }
        return RestAssured.given().baseUri("http://localhost:" + port)
                .contentType("application/json").header("Authorization", "Bearer " + tokens.get(user.getId()));
    }

    public static void assertStatus(int expected, Response response) {
        Assert.assertEquals("Unexpected API response: " + response.getBody().asString(), expected, response.getStatusCode());
    }

    public String email(String name) {
        String email = name + "." + marker + "@example.com";
        emails.add(email); // Register before making an HTTP request, even if its response fails.
        return email;
    }

    public String newCpr() {
        String cpr;
        do {
            cpr = String.valueOf(ThreadLocalRandom.current().nextLong(100000000, 1000000000));
        } while (em.createQuery("select count(p) from UserProfile p where p.cpr = :cpr", Long.class)
                .setParameter("cpr", cpr).getSingleResult() != 0);
        return cpr;
    }

    public Map<String, Object> employeeDetails(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("emailAddress", email(name));
        body.put("cpr", newCpr());
        body.put("fName", "Test");
        body.put("lName", "Employee");
        body.put("phoneNumberOpeningCode", "+973");
        body.put("phoneNumber", "33123456");
        body.put("securityQuestion", "Favourite colour?");
        body.put("securityQuestionAnswer", "blue");
        body.put("password", "TestPassword1");
        body.put("hireDate", "2026-01-01");
        body.put("salary", 1000);
        body.put("employeeNumber", "ATC-" + UUID.randomUUID());
        return body;
    }

    public Flight savedFlight() {
        return transaction().execute(status -> em.find(Flight.class, flight.getId()));
    }

    public TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private User user(String name, User.Role role) {
        User user = new User();
        user.setEmailAddress(email(name));
        user.setPassword(encodedPassword);
        user.setRole(role);
        user.setActive(true);
        user.setStatus(User.Status.ACTIVE);
        user.setPersonalProfile(new UserProfile("Test", name, newCpr(), "+973", "33123456"));
        em.persist(user);
        return user;
    }

    private User airlineUser(String name, AirlineEmployee.AirlineRole role) {
        User user = user(name, User.Role.AIRLINE_EMPLOYEE);
        AirlineEmployee employee = new AirlineEmployee();
        employee.setUserId(user.getId());
        employee.setAirline(airline);
        employee.setAirlineRole(role);
        em.persist(employee);
        return user;
    }

    private Long employeeId(User user) {
        return em.createQuery("select e.id from AirlineEmployee e where e.userId = :id", Long.class)
                .setParameter("id", user.getId()).getSingleResult();
    }

    private User atcUser(String name, ControlFacility facility, ATCEmployee.ATCRole role) {
        User user = user(name, User.Role.ATC_EMPLOYEE);
        ATCEmployee employee = new ATCEmployee();
        employee.setUserId(user.getId());
        employee.setAtcRole(role);
        employee.setFacility(facility);
        employee.setEmployeeNumber(UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
        em.persist(employee);
        return user;
    }

    private User airportUser(String name, AirportEmployee.AirportRole role) {
        User user = user(name, User.Role.AIRPORT_EMPLOYEE);
        AirportEmployee employee = new AirportEmployee();
        employee.setUserId(user.getId());
        employee.setAirport(origin);
        employee.setOperator(operator);
        employee.setAirportRole(role);
        em.persist(employee);
        return user;
    }

    private User maintenanceUser(String name, MaintenanceEmployee.MaintenanceRole role) {
        User user = user(name, User.Role.MAINTENANCE_EMPLOYEE);
        MaintenanceEmployee employee = new MaintenanceEmployee();
        employee.setUserId(user.getId());
        employee.setProvider(maintenanceProvider);
        employee.setMaintenanceRole(role);
        em.persist(employee);
        return user;
    }

    private Airport airport(String prefix) {
        Airport airport = new Airport();
        airport.setIataCode(prefix + marker);
        airport.setName(prefix + marker);
        airport.setCity(prefix + "Test city");
        airport.setCountry("Bahrain");
        airport.setTimeZone("Asia/Bahrain");
        airport.setOperator(operator);
        em.persist(airport);
        return airport;
    }

    private ControlFacility tower(Airport airport) {
        ControlFacility tower = new ControlFacility();
        tower.setName(marker);
        tower.setAirport(airport);
        tower.setProvider(navigationProvider);
        em.persist(tower);
        return tower;
    }

    private void assign(Long employeeId, Duty duty) {
        CrewAssignment assignment = new CrewAssignment();
        assignment.setFlight(flight);
        assignment.setAirlineEmployee(em.getReference(AirlineEmployee.class, employeeId));
        assignment.setDuty(duty);
        em.persist(assignment);
    }

    @After
    public void deleteTestRecords(Scenario scenario) {
        // HTTP requests commit their own transactions. Delete only this scenario's records.
        transaction().executeWithoutResult(status -> {
            List<Long> userIds = emails.isEmpty() ? List.of() : em.createQuery(
                    "select u.id from User u where u.emailAddress in :emails", Long.class)
                    .setParameter("emails", emails).getResultList();
            List<Long> airlineIds = airlineCodes.isEmpty() ? List.of() : em.createQuery(
                    "select a.id from Airline a where a.airlineCode in :codes", Long.class)
                    .setParameter("codes", airlineCodes).getResultList();
            if (!userIds.isEmpty()) {
                delete("delete from AuditLog a where a.userId in :ids", userIds);
                delete("delete from IdentityAuditLog a where a.userId in :ids", userIds);
                delete("delete from MaintenanceAuditLog a where a.userId in :ids", userIds);
            }
            if (!airlineIds.isEmpty()) {
                delete("delete from Booking b where b.flight.airline.id in :ids", airlineIds);
                delete("delete from FlightControlEvent e where e.flight.airline.id in :ids", airlineIds);
                delete("delete from CrewAssignment c where c.flight.airline.id in :ids", airlineIds);
                delete("delete from FlightInspection i where i.flight.airline.id in :ids", airlineIds);
                delete("delete from Flight f where f.airline.id in :ids", airlineIds);
                delete("delete from AirplaneRequest r where r.airplane.airline.id in :ids", airlineIds);
                delete("delete from MaintenanceRecord r where r.airplaneId in (select a.id from Airplane a where a.airline.id in :ids)", airlineIds);
                delete("delete from MaintenanceContract c where c.airlineId in :ids", airlineIds);
                delete("delete from Airplane a where a.airline.id in :ids", airlineIds);
                delete("delete from AirlineEmployee e where e.airline.id in :ids", airlineIds);
                delete("delete from Airline a where a.id in :ids", airlineIds);
            }
            if (!userIds.isEmpty()) {
                delete("delete from ATCEmployee e where e.userId in :ids", userIds);
                delete("delete from AirportEmployee e where e.userId in :ids", userIds);
                delete("delete from MaintenanceEmployee e where e.userId in :ids", userIds);
                delete("delete from UserProfile p where p.userId in :ids", userIds);
                delete("delete from User u where u.id in :ids", userIds);
            }
            // These root IDs come only from the setup transaction (which may have rolled back).
            if (navigationProvider != null && navigationProvider.getId() != null) {
                delete("delete from ControlFacility f where f.provider.id in :ids", List.of(navigationProvider.getId()));
                delete("delete from AirNavigationServiceProvider p where p.id in :ids", List.of(navigationProvider.getId()));
            }
            if (operator != null && operator.getId() != null) {
                delete("delete from Airport a where a.operator.id in :ids", List.of(operator.getId()));
                delete("delete from AirportOperator o where o.id in :ids", List.of(operator.getId()));
            }
            if (maintenanceProvider != null && maintenanceProvider.getId() != null)
                delete("delete from MaintenanceProvider p where p.id in :ids", List.of(maintenanceProvider.getId()));
            if (!emails.isEmpty()) Assert.assertEquals(0L, em.createQuery(
                    "select count(u) from User u where u.emailAddress in :emails", Long.class)
                    .setParameter("emails", emails).getSingleResult().longValue());
            if (!airlineCodes.isEmpty()) Assert.assertEquals(0L, em.createQuery(
                    "select count(a) from Airline a where a.airlineCode in :codes", Long.class)
                    .setParameter("codes", airlineCodes).getSingleResult().longValue());
        });
        scenario.log("Removed test records: " + marker);
    }

    private void delete(String query, List<Long> ids) {
        em.createQuery(query).setParameter("ids", ids).executeUpdate();
    }
}
