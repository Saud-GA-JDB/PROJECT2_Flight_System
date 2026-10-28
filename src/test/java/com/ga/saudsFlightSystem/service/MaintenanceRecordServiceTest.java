package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceRecordRequest;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceRecordResponse;
import jakarta.persistence.EntityManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stripe.secret-key=sk_test_unused",
        "stripe.success-url=http://localhost:8080/payments/success",
        "stripe.cancel-url=http://localhost:8080/payments/cancel"
})
@Transactional
public class MaintenanceRecordServiceTest {
    @Autowired MaintenanceRecordService service;
    @Autowired EntityManager entityManager;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper jsonMapper;

    Long recordId;
    Long airplaneId;
    Long airlineId;
    Long airlineEmployeeId;
    Long providerId;
    Long otherProviderId;
    Long technicianId;
    Long secondTechnicianId;
    String adminEmail;
    String secondAdminEmail;
    String otherAdminEmail;
    String technicianEmail;
    String secondTechnicianEmail;
    String encodedPassword;
    boolean committedFixtures;
    List<Long> userIds = new ArrayList<>();
    List<Long> employeeIds = new ArrayList<>();

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        encodedPassword = passwordEncoder.encode("TestPassword1");
        String suffix = UUID.randomUUID().toString();

        Airline airline = new Airline();
        airline.setName("Maintenance test " + suffix);
        airline.setAirlineCode(suffix);
        entityManager.persist(airline);
        airlineId = airline.getId();

        Airplane airplane = new Airplane();
        airplane.setAirline(airline);
        airplane.setRegistrationNumber("TEST-" + suffix);
        entityManager.persist(airplane);
        airplaneId = airplane.getId();

        MaintenanceProvider provider = provider("Provider " + suffix);
        provider.getAirlines().add(airline);
        providerId = provider.getId();
        MaintenanceProvider otherProvider = provider("Other " + suffix);
        otherProviderId = otherProvider.getId();

        User admin = maintenanceUser(provider, MaintenanceEmployee.MaintenanceRole.ADMIN);
        adminEmail = admin.getEmailAddress();
        secondAdminEmail = maintenanceUser(provider,
                MaintenanceEmployee.MaintenanceRole.ADMIN).getEmailAddress();
        otherAdminEmail = maintenanceUser(otherProvider,
                MaintenanceEmployee.MaintenanceRole.ADMIN).getEmailAddress();
        User technician = maintenanceUser(provider, MaintenanceEmployee.MaintenanceRole.TECHNICIAN);
        technicianEmail = technician.getEmailAddress();
        technicianId = technician.getMaintenanceEmployee().getId();
        User secondTechnician = maintenanceUser(provider, MaintenanceEmployee.MaintenanceRole.TECHNICIAN);
        secondTechnicianEmail = secondTechnician.getEmailAddress();
        secondTechnicianId = secondTechnician.getMaintenanceEmployee().getId();

        AirlineEmployee airlineAdmin = new AirlineEmployee();
        airlineAdmin.setCpr(cpr());
        airlineAdmin.setAirline(airline);
        airlineAdmin.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        User airlineUser = user(User.Role.AIRLINE_EMPLOYEE);
        airlineUser.setAirlineEmployee(airlineAdmin);
        entityManager.persist(airlineUser);
        userIds.add(airlineUser.getId());
        airlineEmployeeId = airlineAdmin.getId();
        entityManager.flush();

        login(airlineUser.getEmailAddress());
        AddMaintenanceRecordRequest request = jsonMapper.readValue(
                "{\"providerId\":" + providerId + ",\"description\":\"Inspect engine\"}",
                AddMaintenanceRecordRequest.class);
        var response = service.addMaintenanceRecord(airplaneId, request);
        MaintenanceRecordResponse record = (MaintenanceRecordResponse) response.getBody();
        Assert.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assert.assertNotNull(record);
        recordId = record.getId();
        Assert.assertNull(record.getTechnicianId());
        Assert.assertEquals(providerId, record.getProviderId());
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
        if (!committedFixtures) return;
        if (!TestTransaction.isActive()) TestTransaction.start();
        entityManager.clear();
        entityManager.createQuery("DELETE FROM AuditLog a WHERE a.entityType = :type AND a.entityId = :id")
                .setParameter("type", AuditLog.EntityType.MAINTENANCE_RECORD)
                .setParameter("id", recordId).executeUpdate();
        entityManager.createQuery("DELETE FROM MaintenanceRecord r WHERE r.id = :id")
                .setParameter("id", recordId).executeUpdate();
        entityManager.createQuery("DELETE FROM User u WHERE u.id IN :ids")
                .setParameter("ids", userIds).executeUpdate();
        entityManager.createQuery("DELETE FROM MaintenanceEmployee e WHERE e.id IN :ids")
                .setParameter("ids", employeeIds).executeUpdate();
        entityManager.createQuery("DELETE FROM AirlineEmployee e WHERE e.id = :id")
                .setParameter("id", airlineEmployeeId).executeUpdate();
        entityManager.remove(entityManager.find(MaintenanceProvider.class, providerId));
        entityManager.remove(entityManager.find(MaintenanceProvider.class, otherProviderId));
        entityManager.flush();
        entityManager.remove(entityManager.find(Airplane.class, airplaneId));
        entityManager.flush();
        entityManager.remove(entityManager.find(Airline.class, airlineId));
        TestTransaction.flagForCommit();
    }

    @Test
    public void whenMaintenanceIsRequestedThenProviderAssignsAndTechnicianCompletes() {
        login(adminEmail);
        List<?> requests = (List<?>) service.getMaintenanceRequests().getBody();
        Assert.assertNotNull(requests);
        Assert.assertEquals(1, requests.size());
        Assert.assertEquals(recordId, ((MaintenanceRecordResponse) requests.get(0)).getId());
        Assert.assertEquals(HttpStatus.OK, service.assignService(recordId, technicianId).getStatusCode());

        login(technicianEmail);
        List<?> assignments = (List<?>) service.getMyMaintenanceAssignments().getBody();
        Assert.assertNotNull(assignments);
        Assert.assertEquals(1, assignments.size());
        Assert.assertEquals(recordId, ((MaintenanceRecordResponse) assignments.get(0)).getId());
        Assert.assertEquals(HttpStatus.OK,
                service.completeMaintenance(recordId, "  Engine inspected  ").getStatusCode());
        MaintenanceRecord saved = savedRecord();
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.COMPLETED, saved.getStatus());
        Assert.assertEquals(technicianId, saved.getTechnician().getId());
        Assert.assertEquals("Engine inspected", saved.getFindings());
        Assert.assertNotNull(saved.getCompletedAt());
        assertAuditCount(AuditLog.Action.MAINTENANCE_RECORD_CREATED);
        assertAuditCount(AuditLog.Action.MAINTENANCE_TECHNICIAN_ASSIGNED);
        assertAuditCount(AuditLog.Action.MAINTENANCE_RECORD_COMPLETED);
    }

    @Test
    public void whenOtherProviderAssignsThenRequestIsUnchanged() {
        login(otherAdminEmail);
        Assert.assertThrows(IllegalEndpoint.class,
                () -> service.assignService(recordId, technicianId));
        Assert.assertNull(entityManager.find(MaintenanceRecord.class, recordId).getTechnician());
    }

    @Test
    public void whenUnassignedTechnicianCompletesThenRequestStaysPending() {
        login(adminEmail);
        service.assignService(recordId, technicianId);
        login(secondTechnicianEmail);
        Assert.assertThrows(IllegalEndpoint.class,
                () -> service.completeMaintenance(recordId, "Not my job"));
        MaintenanceRecord record = entityManager.find(MaintenanceRecord.class, recordId);
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.PENDING, record.getStatus());
        Assert.assertNull(record.getCompletedAt());
    }

    @Test
    public void whenAdminsAssignConcurrentlyThenOnlyFirstAssignmentWins() throws Exception {
        checkCompetingRequests(adminEmail, secondAdminEmail,
                () -> service.assignService(recordId, technicianId),
                () -> service.assignService(recordId, secondTechnicianId),
                InformationExistException.class);
        Assert.assertEquals(technicianId, savedRecord().getTechnician().getId());
        assertAuditCount(AuditLog.Action.MAINTENANCE_TECHNICIAN_ASSIGNED);
    }

    @Test
    public void whenTechnicianCompletesConcurrentlyThenOnlyFirstCompletionWins() throws Exception {
        login(adminEmail);
        service.assignService(recordId, technicianId);
        checkCompetingRequests(technicianEmail, technicianEmail,
                () -> service.completeMaintenance(recordId, "First findings"),
                () -> service.completeMaintenance(recordId, "Second findings"),
                InvalidInformationException.class);
        MaintenanceRecord saved = savedRecord();
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.COMPLETED, saved.getStatus());
        Assert.assertEquals("First findings", saved.getFindings());
        Assert.assertNotNull(saved.getCompletedAt());
        assertAuditCount(AuditLog.Action.MAINTENANCE_RECORD_COMPLETED);
    }

    private void checkCompetingRequests(String firstEmail, String secondEmail,
            Runnable firstAction, Runnable secondAction,
            Class<? extends Throwable> expectedFailure) throws Exception {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        committedFixtures = true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> runAs(firstEmail, () -> {
                firstAction.run();
                firstUpdated.countDown();
                await(release);
            }));
            Assert.assertTrue("First request did not update", firstUpdated.await(10, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> runAs(secondEmail, () -> {
                secondStarted.countDown();
                secondAction.run();
            }));
            Assert.assertTrue("Second request did not start", secondStarted.await(10, TimeUnit.SECONDS));
            Assert.assertThrows(TimeoutException.class,
                    () -> second.get(500, TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            ExecutionException failure = Assert.assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            Assert.assertEquals(expectedFailure, failure.getCause().getClass());
        } finally {
            release.countDown();
            executor.shutdownNow();
            boolean stopped = executor.awaitTermination(15, TimeUnit.SECONDS);
            TestTransaction.start();
            Assert.assertTrue("Workers did not stop", stopped);
        }
    }

    private void runAs(String email, Runnable action) {
        try {
            login(email);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                action.run();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void login(String email) {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "TestPassword1")));
    }

    private MaintenanceProvider provider(String name) {
        MaintenanceProvider provider = new MaintenanceProvider();
        provider.setName(name);
        provider.setContactEmail("test@example.com");
        entityManager.persist(provider);
        return provider;
    }

    private User maintenanceUser(MaintenanceProvider provider, MaintenanceEmployee.MaintenanceRole role) {
        MaintenanceEmployee employee = new MaintenanceEmployee();
        employee.setCpr(cpr());
        employee.setProvider(provider);
        employee.setMaintenanceRole(role);
        User user = user(User.Role.MAINTENANCE_EMPLOYEE);
        user.setMaintenanceEmployee(employee);
        entityManager.persist(user);
        userIds.add(user.getId());
        employeeIds.add(employee.getId());
        return user;
    }

    private User user(User.Role role) {
        User user = new User();
        user.setEmailAddress("maintenance-test-" + UUID.randomUUID() + "@example.com");
        user.setPassword(encodedPassword);
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        user.setActive(true);
        return user;
    }

    private String cpr() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100000000L, 1000000000L));
    }

    private MaintenanceRecord savedRecord() {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(MaintenanceRecord.class, recordId);
    }

    private void assertAuditCount(AuditLog.Action action) {
        Long count = entityManager.createQuery(
                "SELECT COUNT(a) FROM AuditLog a WHERE a.entityType = :type AND a.entityId = :id AND a.action = :action",
                Long.class).setParameter("type", AuditLog.EntityType.MAINTENANCE_RECORD)
                .setParameter("id", recordId).setParameter("action", action).getSingleResult();
        Assert.assertEquals(Long.valueOf(1), count);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out releasing lock");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
