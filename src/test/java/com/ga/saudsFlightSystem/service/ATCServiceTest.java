package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.*;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stripe.secret-key=sk_test_unused",
        "stripe.success-url=http://localhost:8080/payments/success",
        "stripe.cancel-url=http://localhost:8080/payments/cancel"
})
@AutoConfigureMockMvc
@Transactional
public class ATCServiceTest {
    @Autowired ATCService service;
    @Autowired UserService userService;
    @Autowired PendingRegistrationService pendingRegistrationService;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;
    @Autowired JsonMapper jsonMapper;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired MockMvc mvc;
    @Autowired ExecutorService applicationExecutor;
    @Autowired PlatformTransactionManager transactionManager;

    User faa;
    Long faaProfileId;
    Airport airport;
    Airport secondAirport;
    Long providerId;
    Long otherProviderId;
    boolean committedFixtures;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        FAAAdmin profile = new FAAAdmin();
        profile.setCpr(cpr());
        faa = new User();
        faa.setEmailAddress(UUID.randomUUID() + "@example.com");
        faa.setPassword(passwords.encode("TestPassword1"));
        faa.setStatus(User.Status.ACTIVE);
        faa.setActive(true);
        faa.setRole(User.Role.FAA_ADMIN);
        faa.setFaaAdmin(profile);
        userRepository.saveAndFlush(faa);
        faaProfileId = profile.getId();
        airport = airport();
        secondAirport = airport();
        login(faa.getEmailAddress());
        providerId = service.addProvider(providerRequest(" Navigation ")).getBody().getId();
        otherProviderId = service.addProvider(providerRequest(" Navigation ")).getBody().getId();
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
        if (!committedFixtures) return;
        if (!TestTransaction.isActive()) TestTransaction.start();
        em.clear();
        em.createQuery("delete from AuditLog a where a.userId = :id").setParameter("id", faa.getId()).executeUpdate();
        em.createQuery("delete from ControlFacility f where f.provider.id in :ids")
                .setParameter("ids", List.of(providerId, otherProviderId)).executeUpdate();
        em.createQuery("delete from AirNavigationServiceProvider p where p.id in :ids")
                .setParameter("ids", List.of(providerId, otherProviderId)).executeUpdate();
        em.createQuery("delete from User u where u.id = :id").setParameter("id", faa.getId()).executeUpdate();
        em.createQuery("delete from FAAAdmin a where a.id = :id").setParameter("id", faaProfileId).executeUpdate();
        em.createQuery("delete from Airport a where a.id in :ids")
                .setParameter("ids", List.of(airport.getId(), secondAirport.getId())).executeUpdate();
        TestTransaction.flagForCommit();
    }

    @Test
    public final void whenProviderHasTwoTowersThenPagingAndAuditAreScoped() {
        FacilityResponse first = tower(providerId, airport);
        FacilityResponse second = tower(providerId, secondAirport);
        FacilityListResponse page = service.getFacilities(providerId, 0, 1).getBody();
        Assert.assertEquals(2, page.getTotalElements());
        Assert.assertEquals(2, page.getTotalPages());
        Assert.assertEquals(first.getId(), page.getContent().get(0).getId());
        Assert.assertEquals(second.getId(), service.getFacilities(providerId, 1, 1).getBody().getContent().get(0).getId());
        Assert.assertTrue(service.getFacilities(otherProviderId, 0, 20).getBody().getContent().isEmpty());
        Assert.assertEquals("Navigation", em.find(AirNavigationServiceProvider.class, providerId).getName());
        Assert.assertEquals(2L, auditCount(AuditLog.Action.CONTROL_FACILITY_CREATED));
        Assert.assertThrows(InformationExistException.class, () -> tower(otherProviderId, airport));
        Assert.assertEquals(2L, auditCount(AuditLog.Action.CONTROL_FACILITY_CREATED));
    }

    @Test
    public final void whenAdminCreatesControllersThenOnlyOwnTowerIsListedAndCredentialsAreEncoded() {
        Long facilityId = tower(providerId, airport).getId();
        Long secondFacilityId = tower(providerId, secondAirport).getId();
        ATCEmployeeResponse admin = service.addAdmin(facilityId, staffRequest()).getBody();
        service.addAdmin(facilityId, staffRequest());
        ATCEmployeeResponse otherAdmin = service.addAdmin(secondFacilityId, staffRequest()).getBody();
        login(otherAdmin.getEmailAddress());
        service.addController(staffRequest());
        login(admin.getEmailAddress());
        AddATCEmployeeRequest request = staffRequest();
        ATCEmployeeResponse controller = service.addController(request).getBody();
        User saved = userRepository.findByAtcEmployeeId(controller.getId());
        Assert.assertTrue(passwords.matches("TestPassword1", saved.getPassword()));
        Assert.assertTrue(passwords.matches("manama", saved.getSecurityQuestionAnswer()));
        Assert.assertTrue(saved.isActive());
        Assert.assertEquals(User.Status.ACTIVE, saved.getStatus());
        Assert.assertNull(saved.getAirportEmployee());
        Assert.assertNull(saved.getAirlineEmployee());
        Assert.assertNull(saved.getCustomer());
        Assert.assertEquals(ATCEmployee.ATCRole.CONTROLLER, controller.getAtcRole());
        Assert.assertEquals(request.getEmployeeNumber().trim().toUpperCase(), controller.getEmployeeNumber());
        ATCEmployeeListResponse page = service.getControllers(0, 20).getBody();
        Assert.assertEquals(1, page.getTotalElements());
        Assert.assertEquals(controller.getId(), page.getContent().get(0).getId());
        Assert.assertEquals(facilityId, service.getOwnFacility().getBody().getId());
        login(controller.getEmailAddress());
        Assert.assertEquals(facilityId, service.getOwnFacility().getBody().getId());
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getControllers(0, 20));
        Assert.assertThrows(IllegalEndpoint.class, () -> service.addController(staffRequest()));
        Assert.assertThrows(IllegalEndpoint.class, () -> service.addAdmin(facilityId, staffRequest()));
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getProviders(0, 20));
    }

    @Test
    public final void whenIdentityIsDuplicatedThenCreationAndOtherProfileChecksRejectIt() {
        Long facilityId = tower(providerId, airport).getId();
        AddATCEmployeeRequest request = staffRequest();
        ATCEmployeeResponse admin = service.addAdmin(facilityId, request).getBody();
        Assert.assertThrows(InformationExistException.class, () -> service.addAdmin(facilityId, request));
        Assert.assertThrows(InformationExistException.class, () -> pendingRegistrationService.checkExistingDetails(
                UUID.randomUUID() + "@example.com", request.getCpr()));
        Map<String, Object> duplicate = jsonMapper.convertValue(staffRequest(), Map.class);
        duplicate.put("employeeNumber", request.getEmployeeNumber());
        Assert.assertThrows(InformationExistException.class, () -> service.addAdmin(facilityId,
                jsonMapper.convertValue(duplicate, AddATCEmployeeRequest.class)));
        UpdateProfileRequest update = jsonMapper.readValue("{\"cpr\":\"" + request.getCpr() + "\"}", UpdateProfileRequest.class);
        Assert.assertThrows(InformationExistException.class, () -> userService.updateProfile(faa.getId(), update, null));
        Assert.assertEquals(1L, auditCount(AuditLog.Action.ATC_EMPLOYEE_CREATED));
        Assert.assertNotNull(admin.getUserId());
    }

    @Test
    public final void whenATCUpdatesOwnProfileThenExistingProfileFlowWorks() {
        Long facilityId = tower(providerId, airport).getId();
        ATCEmployeeResponse admin = service.addAdmin(facilityId, staffRequest()).getBody();
        login(admin.getEmailAddress());
        userService.updateProfile(null, jsonMapper.readValue("{\"phoneNumber\":\"33123457\"}", UpdateProfileRequest.class), null);
        Assert.assertEquals("33123457", userRepository.findById(admin.getUserId()).get().getAtcEmployee().getPhoneNumber());
        Assert.assertTrue(UserService.isAllowedEndpoint("atcEmployee", User.Role.ATC_EMPLOYEE));
        Assert.assertFalse(UserService.isAllowedEndpoint("faaadmin", User.Role.ATC_EMPLOYEE));
    }

    @Test
    public final void whenAccountOrProfileIsInvalidThenServiceRejectsIt() {
        faa.setStatus(User.Status.DEACTIVATED);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getProviders(0, 20));
        faa.setStatus(User.Status.ACTIVE);
        faa.setActive(false);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getProviders(0, 20));
        faa.setActive(true);
        faa.setRole(User.Role.CUSTOMER);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.addProvider(providerRequest("Name")));
        faa.setRole(User.Role.FAA_ADMIN);
        FAAAdmin profile = faa.getFaaAdmin();
        faa.setFaaAdmin(null);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getProviders(0, 20));
        faa.setFaaAdmin(profile);
    }

    @Test
    public final void whenFieldsOrPagingAreInvalidThenNoSuccessAuditIsWritten() {
        long before = auditCount(AuditLog.Action.AIR_NAVIGATION_PROVIDER_CREATED);
        for (String name : List.of(" ", "x".repeat(151)))
            Assert.assertThrows(InvalidInformationException.class, () -> service.addProvider(providerRequest(name)));
        Assert.assertThrows(InvalidInformationException.class, () -> service.getProviders(-1, 20));
        Assert.assertThrows(InvalidInformationException.class, () -> service.getProviders(0, 101));
        Assert.assertThrows(InvalidInformationException.class, () -> service.getProviders(0, 0));
        Assert.assertEquals(before, auditCount(AuditLog.Action.AIR_NAVIGATION_PROVIDER_CREATED));
        Long facilityId = tower(providerId, airport).getId();
        for (Map.Entry<String, String> invalid : Map.of("cpr", "123", "employeeNumber", "x".repeat(51),
                "emailAddress", "bad", "phoneNumber", "bad", "password", "weak", "securityQuestion", " ").entrySet()) {
            Map<String, Object> fields = jsonMapper.convertValue(staffRequest(), Map.class);
            fields.put(invalid.getKey(), invalid.getValue());
            Assert.assertThrows(InvalidInformationException.class, () -> service.addAdmin(facilityId,
                    jsonMapper.convertValue(fields, AddATCEmployeeRequest.class)));
        }
        Assert.assertEquals(0L, auditCount(AuditLog.Action.ATC_EMPLOYEE_CREATED));
    }

    @Test
    public final void whenUsingHTTPThenLoginScopeStatusAndResponseContractsAreEnforced() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/atc/facility")).andExpect(status().isUnauthorized());
        String faaToken = token(faa.getEmailAddress());
        mvc.perform(post("/faaadmin/airNavigationProviders").header("Authorization", faaToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"HTTP Navigation\",\"contactEmail\":\"ops@example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("HTTP Navigation"));
        mvc.perform(get("/faaadmin/airNavigationProviders").header("Authorization", faaToken).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.totalElements").isNumber());
        mvc.perform(get("/faaadmin/airNavigationProviders/0/controlFacilities").header("Authorization", faaToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/faaadmin/airNavigationProviders/9223372036854775807/controlFacilities").header("Authorization", faaToken))
                .andExpect(status().isNotFound());
        login(faa.getEmailAddress());
        Long facilityId = tower(providerId, airport).getId();
        ATCEmployeeResponse admin = service.addAdmin(facilityId, staffRequest()).getBody();
        String adminToken = token(admin.getEmailAddress());
        String body = mvc.perform(post("/atcAdmin/controllers").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(staffRequest())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.atcRole").value("CONTROLLER"))
                .andExpect(jsonPath("$.facilityId").value(facilityId)).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.cpr").doesNotExist()).andExpect(jsonPath("$.securityQuestionAnswer").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist()).andReturn().getResponse().getContentAsString();
        String controllerEmail = jsonMapper.readTree(body).get("emailAddress").asText();
        String controllerToken = token(controllerEmail);
        mvc.perform(get("/atc/facility").header("Authorization", controllerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(facilityId));
        mvc.perform(get("/atcAdmin/controllers").header("Authorization", controllerToken)).andExpect(status().isForbidden());
        mvc.perform(get("/faaadmin/airNavigationProviders").header("Authorization", adminToken)).andExpect(status().isForbidden());
        User controller = userRepository.findUserByEmailAddress(controllerEmail);
        controller.setActive(false);
        em.flush();
        mvc.perform(get("/atc/facility").header("Authorization", controllerToken)).andExpect(status().isUnauthorized());
    }

    @Test
    public final void whenHTTPContainsExtraFieldsThenTheyCannotOverrideServerOwnedValues() throws Exception {
        String auth = token(faa.getEmailAddress());
        mvc.perform(post("/faaadmin/airNavigationProviders").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Example\",\"contactEmail\":\"ops@example.com\",\"actorUserId\":1}"))
                .andExpect(status().isCreated());
        login(faa.getEmailAddress());
        Long facilityId = tower(providerId, airport).getId();
        Map<String, Object> fields = jsonMapper.convertValue(staffRequest(), Map.class);
        fields.put("atcRole", "CONTROLLER");
        fields.put("facilityId", Long.MAX_VALUE);
        fields.put("actorUserId", Long.MAX_VALUE);
        fields.put("status", "DEACTIVATED");
        mvc.perform(post("/faaadmin/controlFacilities/" + facilityId + "/admins").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(fields)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.atcRole").value("ADMIN"))
                .andExpect(jsonPath("$.facilityId").value(facilityId));
        User created = userRepository.findUserByEmailAddress((String) fields.get("emailAddress"));
        Assert.assertEquals(User.Status.ACTIVE, created.getStatus());
        Assert.assertEquals(1L, auditCount(AuditLog.Action.ATC_EMPLOYEE_CREATED));
    }

    @Test
    public final void whenSetupRollsBackThenRowsAndAuditsRollBackTogether() {
        Long facilityId = tower(providerId, airport).getId();
        Long staffUserId = service.addAdmin(facilityId, staffRequest()).getBody().getUserId();
        Long staffId = userRepository.findById(staffUserId).get().getAtcEmployee().getId();
        em.flush();
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
        Assert.assertNull(em.find(ControlFacility.class, facilityId));
        Assert.assertNull(em.find(User.class, staffUserId));
        Assert.assertNull(em.find(ATCEmployee.class, staffId));
        Assert.assertNull(em.find(AirNavigationServiceProvider.class, providerId));
        Assert.assertEquals(0L, auditCount(AuditLog.Action.ATC_EMPLOYEE_CREATED));
        Assert.assertEquals(0L, auditCount(AuditLog.Action.CONTROL_FACILITY_CREATED));
    }

    @Test
    public final void whenHTTPSetupConflictsOrReferencesAreMissingThenErrorsUseExistingContract() throws Exception {
        String auth = token(faa.getEmailAddress());
        String body = jsonMapper.writeValueAsString(facilityRequest(airport.getId()));
        mvc.perform(post("/faaadmin/airNavigationProviders/" + providerId + "/controlFacilities")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.facilityType").value("TOWER"));
        mvc.perform(post("/faaadmin/airNavigationProviders/" + otherProviderId + "/controlFacilities")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT")).andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/faaadmin/airNavigationProviders/" + providerId + "/controlFacilities")
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/faaadmin/controlFacilities/9223372036854775807/admins")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(staffRequest())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/faaadmin/airNavigationProviders").header("Authorization", auth).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public final void whenSwaggerIsReadThenSetupSchemasAndStatusesMatchTheAPI() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var api = jsonMapper.readTree(body);
        Assert.assertTrue(api.at("/paths/~1faaadmin~1airNavigationProviders/post/responses").has("201"));
        Assert.assertFalse(api.at("/paths/~1faaadmin~1airNavigationProviders/post/responses").has("200"));
        Assert.assertTrue(api.at("/paths/~1atcAdmin~1controllers/post/responses").has("201"));
        Assert.assertTrue(api.at("/paths/~1atc~1facility/get/responses").has("200"));
        Assert.assertTrue(api.at("/components/schemas/ATCEmployeeResponse/properties").has("employeeNumber"));
        Assert.assertFalse(api.at("/components/schemas/ATCEmployeeResponse/properties").has("password"));
        Assert.assertFalse(api.at("/components/schemas/ATCEmployeeResponse/properties").has("cpr"));
    }

    @Test
    public final void whenTwoProvidersCreateTowerAtSameAirportThenOneWins() throws Exception {
        Long airportId = airport.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        committedFixtures = true;
        CountDownLatch firstCreated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        Future<?> first = null;
        Future<?> second = null;
        try {
            first = applicationExecutor.submit(() -> runAsFAA(() -> {
                service.addFacility(providerId, facilityRequest(airportId));
                em.flush();
                firstCreated.countDown();
                await(release);
            }));
            Assert.assertTrue(firstCreated.await(15, TimeUnit.SECONDS));
            second = applicationExecutor.submit(() -> runAsFAA(() -> {
                secondPid.set(((Number) em.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue());
                secondStarted.countDown();
                service.addFacility(otherProviderId, facilityRequest(airportId));
            }));
            Assert.assertTrue(secondStarted.await(15, TimeUnit.SECONDS));
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !second.isDone()) {
                blocked = Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status ->
                        em.createNativeQuery("select cardinality(pg_blocking_pids(:pid)) > 0")
                                .setParameter("pid", secondPid.get()).getSingleResult()));
                if (blocked) break;
                Thread.sleep(50);
            }
            Assert.assertTrue("Second tower must wait on the airport lock", blocked);
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            Future<?> loser = second;
            ExecutionException failure = Assert.assertThrows(ExecutionException.class, () -> loser.get(15, TimeUnit.SECONDS));
            Assert.assertTrue(failure.getCause() instanceof InformationExistException);
        } finally {
            release.countDown();
            if (first != null) settle(first);
            if (second != null) settle(second);
            TestTransaction.start();
        }
        Assert.assertEquals(1L, em.createQuery("select count(f) from ControlFacility f where f.airport.id = :id", Long.class)
                .setParameter("id", airportId).getSingleResult().longValue());
        Assert.assertEquals(1L, auditCount(AuditLog.Action.CONTROL_FACILITY_CREATED));
    }

    private void runAsFAA(Runnable action) {
        try {
            login(faa.getEmailAddress());
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                em.createNativeQuery("set local lock_timeout = '10s'").executeUpdate();
                action.run();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void settle(Future<?> future) throws Exception {
        try { future.get(15, TimeUnit.SECONDS); }
        catch (ExecutionException expected) { /* The competing request may lose. */ }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for lock release");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Airport airport() {
        Airport value = new Airport();
        value.setIataCode(UUID.randomUUID().toString());
        value.setName("ATC test airport");
        value.setTimeZone("Asia/Bahrain");
        em.persist(value);
        return value;
    }

    private FacilityResponse tower(Long provider, Airport value) {
        return service.addFacility(provider, facilityRequest(value.getId())).getBody();
    }

    private AddControlFacilityRequest facilityRequest(Long airportId) {
        return jsonMapper.convertValue(Map.of("airportId", airportId, "name", " Test Tower "), AddControlFacilityRequest.class);
    }

    private AddAirNavigationProviderRequest providerRequest(String name) {
        return jsonMapper.convertValue(Map.of("name", name, "contactEmail", " ops@example.com "), AddAirNavigationProviderRequest.class);
    }

    private AddATCEmployeeRequest staffRequest() {
        return jsonMapper.convertValue(Map.of("fName", "Ahmed", "lName", "Ali", "cpr", cpr(),
                "emailAddress", UUID.randomUUID() + "@example.com", "phoneNumberOpeningCode", "+973",
                "phoneNumber", "33123456", "password", "TestPassword1", "securityQuestion", "City?",
                "securityQuestionAnswer", " Manama ", "employeeNumber", " atc-" + UUID.randomUUID() + " "), AddATCEmployeeRequest.class);
    }

    private String cpr() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100000000, 1000000000));
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "TestPassword1")));
    }

    private String token(String email) throws Exception {
        SecurityContextHolder.clearContext();
        String response = mvc.perform(post("/auth/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", email, "password", "TestPassword1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + jsonMapper.readTree(response).get("message").asText();
    }

    private long auditCount(AuditLog.Action action) {
        return em.createQuery("select count(a) from AuditLog a where a.action = :action and a.userId = :id", Long.class)
                .setParameter("action", action).setParameter("id", faa.getId()).getSingleResult();
    }
}
