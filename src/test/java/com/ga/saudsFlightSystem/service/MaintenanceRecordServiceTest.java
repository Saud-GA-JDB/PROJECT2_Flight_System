package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.BusinessConflictException;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.repository.UserRepository;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
@AutoConfigureMockMvc
public class MaintenanceRecordServiceTest {
    @Autowired MaintenanceRecordService service;
    @Autowired EntityManager entityManager;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper jsonMapper;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ExecutorService applicationExecutor;
    @MockitoSpyBean NotificationService notifications;
    @MockitoSpyBean AuditLogService audits;

    Long recordId;
    Long airplaneId;
    Long airlineId;
    Long airlineEmployeeId;
    Long providerId;
    Long otherProviderId;
    Long technicianId;
    Long secondTechnicianId;
    String adminEmail;
    String airlineAdminEmail;
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
        airlineAdmin.setAirline(airline);
        airlineAdmin.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        User airlineUser = user(User.Role.AIRLINE_EMPLOYEE);
        airlineUser.setAirlineEmployee(airlineAdmin);
        airlineAdminEmail = airlineUser.getEmailAddress();
        entityManager.persist(airlineUser);
        airlineAdmin.setUserId(airlineUser.getId());
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
        notifications.closeConnections();
        reset(audits);
        if (!committedFixtures) return;
        if (!TestTransaction.isActive()) TestTransaction.start();
        entityManager.clear();
        entityManager.createQuery("DELETE FROM AuditLog a WHERE a.entityType = :type AND a.entityId = :id")
                .setParameter("type", AuditLog.EntityType.MAINTENANCE_RECORD)
                .setParameter("id", recordId).executeUpdate();
        entityManager.createQuery("DELETE FROM MaintenanceRecord r WHERE r.id = :id")
                .setParameter("id", recordId).executeUpdate();
        entityManager.createQuery("DELETE FROM UserProfile p WHERE p.userId IN :ids")
                .setParameter("ids", userIds).executeUpdate();
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
                BusinessConflictException.class);
        MaintenanceRecord saved = savedRecord();
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.COMPLETED, saved.getStatus());
        Assert.assertEquals("First findings", saved.getFindings());
        Assert.assertNotNull(saved.getCompletedAt());
        assertAuditCount(AuditLog.Action.MAINTENANCE_RECORD_COMPLETED);
    }

    @Test
    public void airlineCancelsAssignedWorkAndHistoryPreservesMetadata() throws Exception {
        login(adminEmail);
        service.assignService(recordId, technicianId);
        String auth = token(airlineAdminEmail);
        mvc.perform(post("/airlineAdmin/maintenanceRequests/" + recordId + "/cancel")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  Work no longer required  \",\"cancelledByUserId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Work no longer required"))
                .andExpect(jsonPath("$.cancelledAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.technicianId").value(technicianId))
                .andExpect(jsonPath("$.cancelledByUserId").value(users.findUserByEmailAddress(airlineAdminEmail).getId()));
        mvc.perform(get("/airlineAdmin/airplanes/" + airplaneId + "/maintenanceRecords")
                .header("Authorization", auth).param("status", "CANCELLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Inspect engine"))
                .andExpect(jsonPath("$.content[0].completedAt").doesNotExist());
        assertAuditCount(AuditLog.Action.MAINTENANCE_CANCELLED);
        login(technicianEmail);
        Assert.assertThrows(BusinessConflictException.class, () -> service.completeMaintenance(recordId, "Must not be saved"));
        Assert.assertNull(entityManager.find(MaintenanceRecord.class, recordId).getFindings());
    }

    @Test
    public void providerCancelsUnassignedWorkAndRepeatedCancellationIsConflict() throws Exception {
        String auth = token(adminEmail);
        String path = "/maintenanceAdmin/maintenanceRequests/" + recordId + "/cancel";
        mvc.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate request\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.technicianId").doesNotExist());
        mvc.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Again\"}"))
                .andExpect(status().isConflict());
        assertAuditCount(AuditLog.Action.MAINTENANCE_CANCELLED);
    }

    @Test
    public void cancellationHttpRejectsInvalidReasonWrongProviderTechnicianAndMissingJwt() throws Exception {
        String path = "/maintenanceAdmin/maintenanceRequests/" + recordId + "/cancel";
        mvc.perform(post(path).header("Authorization", token(otherAdminEmail)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Other provider\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", token(technicianEmail)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Technician\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", token(adminEmail)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No JWT\"}"))
                .andExpect(status().isUnauthorized());
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.PENDING, entityManager.find(MaintenanceRecord.class, recordId).getStatus());
    }

    @Test
    public void historyCountsAcrossProvidersWithoutCurrentContractAndEnforcesScope() {
        MaintenanceRecord completed = new MaintenanceRecord();
        completed.setAirplane(entityManager.find(Airplane.class, airplaneId));
        completed.setProvider(entityManager.find(MaintenanceProvider.class, otherProviderId));
        completed.setStatus(MaintenanceRecord.MaintenanceStatus.COMPLETED);
        completed.setCompletedAt(java.time.LocalDateTime.now());
        entityManager.persist(completed);
        entityManager.find(MaintenanceProvider.class, providerId).getAirlines().clear();
        entityManager.flush();
        login(airlineAdminEmail);
        var all = service.getAirlineMaintenanceHistory(airplaneId, null, 0, 1).getBody();
        Assert.assertEquals(2, all.getTotalElements());
        Assert.assertEquals(2, all.getTotalPages());
        Assert.assertEquals(1, all.getContent().size());
        var filtered = service.getAirlineMaintenanceHistory(airplaneId, MaintenanceRecord.MaintenanceStatus.COMPLETED, 0, 20).getBody();
        Assert.assertEquals(1, filtered.getTotalElements());
        Assert.assertEquals(otherProviderId, filtered.getContent().get(0).getProviderId());
        Assert.assertTrue(service.getAirlineMaintenanceHistory(airplaneId, null, 4, 20).getBody().getContent().isEmpty());
        Assert.assertThrows(InformationNotFoundException.class, () -> service.getAirlineMaintenanceHistory(Long.MAX_VALUE, null, 0, 20));
        Airline foreign = new Airline();
        foreign.setAirlineCode(UUID.randomUUID().toString());
        entityManager.persist(foreign);
        entityManager.find(Airplane.class, airplaneId).setAirline(foreign);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getAirlineMaintenanceHistory(airplaneId, null, 0, 20));
        Assert.assertThrows(IllegalEndpoint.class, () -> service.cancelAirlineMaintenance(recordId, "Foreign airline"));
    }

    @Test
    public void inactiveAdminCannotCancelAndReasonLengthIsValidated() {
        login(adminEmail);
        Assert.assertThrows(InvalidInformationException.class, () -> service.cancelProviderMaintenance(recordId, "x".repeat(2001)));
        users.findUserByEmailAddress(adminEmail).setActive(false);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.cancelProviderMaintenance(recordId, "Inactive"));
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.PENDING, entityManager.find(MaintenanceRecord.class, recordId).getStatus());
    }

    @Test
    public void cancellationWinsAgainstCompletionAndPreservesFindings() throws Exception {
        login(adminEmail);
        service.assignService(recordId, technicianId);
        checkCompetingRequests(airlineAdminEmail, technicianEmail,
                () -> service.cancelAirlineMaintenance(recordId, "Cancelled first"),
                () -> service.completeMaintenance(recordId, "Too late"), BusinessConflictException.class);
        MaintenanceRecord saved = savedRecord();
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.CANCELLED, saved.getStatus());
        Assert.assertNull(saved.getFindings());
        Assert.assertNull(saved.getCompletedAt());
        assertAuditCount(AuditLog.Action.MAINTENANCE_CANCELLED);
    }

    @Test
    public void completionWinsAgainstCancellation() throws Exception {
        login(adminEmail);
        service.assignService(recordId, technicianId);
        checkCompetingRequests(technicianEmail, adminEmail,
                () -> service.completeMaintenance(recordId, "Completed first"),
                () -> service.cancelProviderMaintenance(recordId, "Too late"), BusinessConflictException.class);
        MaintenanceRecord saved = savedRecord();
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.COMPLETED, saved.getStatus());
        Assert.assertNull(saved.getCancelledAt());
        assertAuditCount(AuditLog.Action.MAINTENANCE_RECORD_COMPLETED);
    }

    @Test
    public void cancellationWinsAgainstTechnicianAssignment() throws Exception {
        checkCompetingRequests(airlineAdminEmail, adminEmail,
                () -> service.cancelAirlineMaintenance(recordId, "Cancelled first"),
                () -> service.assignService(recordId, technicianId), BusinessConflictException.class);
        Assert.assertNull(savedRecord().getTechnician());
        assertAuditCount(AuditLog.Action.MAINTENANCE_CANCELLED);
    }

    @Test
    public void auditFailureRollsBackCancellationAndNotifications() throws Exception {
        commitFixtures();
        AtomicInteger sent = subscribe(airlineAdminEmail);
        doThrow(new IllegalStateException("Injected failure")).when(audits).addAuditLog(any(), eq(AuditLog.Action.MAINTENANCE_CANCELLED), any(), any(), any());
        try {
            Assert.assertThrows(IllegalStateException.class, () -> runAs(adminEmail, () -> service.cancelProviderMaintenance(recordId, "Rollback")));
        } finally {
            reset(audits);
        }
        Assert.assertEquals(0, sent.get());
        TestTransaction.start();
        Assert.assertEquals(MaintenanceRecord.MaintenanceStatus.PENDING, savedRecord().getStatus());
        Assert.assertNull(savedRecord().getCancelledAt());
    }

    @Test
    public void cancellationSseIsScopedAndDeliveredOnlyAfterCommit() throws Exception {
        login(adminEmail);
        service.assignService(recordId, technicianId);
        users.findUserByEmailAddress(secondAdminEmail).setActive(false);
        commitFixtures();
        AtomicInteger airline = subscribe(airlineAdminEmail);
        AtomicInteger provider = subscribe(adminEmail);
        AtomicInteger technician = subscribe(technicianEmail);
        AtomicInteger unrelated = subscribe(otherAdminEmail);
        AtomicInteger unassigned = subscribe(secondTechnicianEmail);
        AtomicInteger inactive = subscribe(secondAdminEmail);
        runAs(adminEmail, () -> {
            service.cancelProviderMaintenance(recordId, "No longer required");
            Assert.assertEquals(0, airline.get());
            Assert.assertEquals(0, technician.get());
        });
        Assert.assertEquals(1, airline.get());
        Assert.assertEquals(1, provider.get());
        Assert.assertEquals(1, technician.get());
        Assert.assertEquals(0, unrelated.get());
        Assert.assertEquals(0, unassigned.get());
        Assert.assertEquals(0, inactive.get());
        TestTransaction.start();
        assertAuditCount(AuditLog.Action.MAINTENANCE_CANCELLED);
    }

    private void commitFixtures() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        committedFixtures = true;
    }

    private AtomicInteger subscribe(String email) throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicInteger sent = new AtomicInteger();
        doAnswer(invocation -> {
            SseEmitter.SseEventBuilder event = invocation.getArgument(0);
            if (event.build().stream().anyMatch(data -> data.getData() instanceof String text && text.contains("event:Maintenance_Cancelled")))
                sent.incrementAndGet();
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doReturn(emitter).when(notifications).createEmitter();
        notifications.subscribe(users.findUserByEmailAddress(email));
        return sent;
    }

    private String token(String email) throws Exception {
        SecurityContextHolder.clearContext();
        String response = mvc.perform(post("/auth/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("email", email, "password", "TestPassword1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + jsonMapper.readTree(response).get("message").asText();
    }

    private void settle(Future<?> future) throws Exception {
        try { future.get(15, TimeUnit.SECONDS); }
        catch (ExecutionException expected) { /* Competing action may lose. */ }
    }

    private void checkCompetingRequests(String firstEmail, String secondEmail,
            Runnable firstAction, Runnable secondAction,
            Class<? extends Throwable> expectedFailure) throws Exception {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        committedFixtures = true;
        ExecutorService executor = applicationExecutor;
        AtomicInteger secondPid = new AtomicInteger();
        Future<?> first = null;
        Future<?> second = null;
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            first = executor.submit(() -> runAs(firstEmail, () -> {
                firstAction.run();
                firstUpdated.countDown();
                await(release);
            }));
            Assert.assertTrue("First request did not update", firstUpdated.await(10, TimeUnit.SECONDS));
            second = executor.submit(() -> runAs(secondEmail, () -> {
                secondPid.set(((Number) entityManager.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue());
                secondStarted.countDown();
                secondAction.run();
            }));
            Assert.assertTrue("Second request did not start", secondStarted.await(10, TimeUnit.SECONDS));
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !second.isDone()) {
                blocked = Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status ->
                        entityManager.createNativeQuery("select cardinality(pg_blocking_pids(:pid)) > 0")
                                .setParameter("pid", secondPid.get()).getSingleResult()));
                if (blocked) break;
                Thread.sleep(50);
            }
            Assert.assertTrue("Competing action must wait for the record lock", blocked);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            Future<?> loser = second;
            ExecutionException failure = Assert.assertThrows(ExecutionException.class,
                    () -> loser.get(10, TimeUnit.SECONDS));
            Assert.assertEquals(expectedFailure, failure.getCause().getClass());
        } finally {
            release.countDown();
            if (first != null) settle(first);
            if (second != null) settle(second);
            TestTransaction.start();
        }
    }

    private void runAs(String email, Runnable action) {
        try {
            login(email);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                entityManager.createNativeQuery("set local lock_timeout = '10s'").executeUpdate();
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
        employee.setProvider(provider);
        employee.setMaintenanceRole(role);
        User user = user(User.Role.MAINTENANCE_EMPLOYEE);
        user.setMaintenanceEmployee(employee);
        entityManager.persist(user);
        employee.setUserId(user.getId());
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
        user.setPersonalProfile(new UserProfile(
                "Test", "Maintenance", cpr(), null, null));
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
