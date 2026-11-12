package com.ga.saudsFlightSystem.service;

import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.common.web.exception.InformationExistException;
import com.ga.saudsFlightSystem.controller.AirlineAdminController;
import com.ga.saudsFlightSystem.exception.GlobalExceptionHandler;
import com.ga.saudsFlightSystem.identity.EmployeeAccountProvisioner;
import com.ga.saudsFlightSystem.identity.EmployeeAccountRequest;
import com.ga.saudsFlightSystem.identity.ProvisionedAccount;
import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exercises HTTP binding and the real service/validators with mocked persistence and Identity.
// It does not start applications, modify PostgreSQL, or send email.
class AirlineEmployeeCreationTest {
    private final AirlineEmployeeRepository employees = mock(AirlineEmployeeRepository.class);
    private final EmployeeAccountProvisioner provisioner = mock(EmployeeAccountProvisioner.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final AuditLogService audits = mock(AuditLogService.class);
    private final AirlineEmployee admin = new AirlineEmployee();
    private final CurrentActor actor = new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", true);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        Airline airline = new Airline();
        airline.setId(6L);
        admin.setAirline(airline);
        admin.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        when(actors.getCurrentActor()).thenReturn(actor);
        when(employees.findByUserIdAndRole(17L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.of(admin));
        when(employees.save(any())).thenAnswer(call -> {
            AirlineEmployee employee = call.getArgument(0);
            employee.setId(30L);
            return employee;
        });
        when(provisioner.createAccount(any())).thenReturn(new ProvisionedAccount(400L, "crew@example.com"));
        AirlineEmployeeService service = new AirlineEmployeeService(
                null, provisioner, employees, null, null, null, null,
                new PhoneValidationService(), new PasswordService(), null, audits, actors);
        mvc = MockMvcBuilders.standaloneSetup(new AirlineAdminController(service, null, null, null, null, null))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fName", " Ahmed ");
        body.put("lName", " Ali ");
        body.put("emailAddress", " crew@example.com ");
        body.put("cpr", "926111220");
        body.put("phoneNumberOpeningCode", "+973");
        body.put("phoneNumber", "33123458");
        body.put("hireDate", "2026-11-12");
        body.put("salary", 1500);
        body.put("password", "TestPassword1");
        body.put("securityQuestion", " Favourite city? ");
        body.put("securityQuestionAnswer", " MANAMA ");
        return body;
    }

    @ParameterizedTest
    @EnumSource(value = AirlineEmployee.AirlineRole.class, names = {"PILOT", "FLIGHT_ATTENDANT"})
    void createsAllowedRoleForAdminsOwnAirline(AirlineEmployee.AirlineRole role) throws Exception {
        mvc.perform(post("/airlineAdmin/employees").param("role", role.name())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.userId").value(400))
                .andExpect(jsonPath("$.airlineId").value(6))
                .andExpect(jsonPath("$.airlineRole").value(role.name()))
                .andExpect(jsonPath("$.fName").value("Ahmed"))
                .andExpect(jsonPath("$.salary").value(1500))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.securityQuestionAnswer").doesNotExist())
                .andExpect(jsonPath("$.cpr").doesNotExist());
        ArgumentCaptor<AirlineEmployee> saved = ArgumentCaptor.forClass(AirlineEmployee.class);
        verify(employees).save(saved.capture());
        assertSame(admin.getAirline(), saved.getValue().getAirline());
        assertEquals(400L, saved.getValue().getUserId());
        assertEquals(LocalDate.of(2026, 11, 12), saved.getValue().getHireDate());
        verify(provisioner).checkExistingDetails("crew@example.com", "926111220");
        ArgumentCaptor<EmployeeAccountRequest> account = ArgumentCaptor.forClass(EmployeeAccountRequest.class);
        verify(provisioner).createAccount(account.capture());
        assertEquals(EmployeeAccountRequest.AccountType.AIRLINE_EMPLOYEE, account.getValue().accountType());
        assertEquals(30L, account.getValue().employeeId());
        assertEquals("TestPassword1", account.getValue().password());
        assertEquals("manama", account.getValue().normalizedSecurityAnswer());
        verify(audits).addAuditLogForActor(eq(actor), eq(AuditLog.Action.AIRLINE_EMPLOYEE_CREATED), eq(AuditLog.EntityType.USER), eq(400L), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GATE_RECEPTION", "UNKNOWN"})
    void rejectsUnsupportedRole(String role) throws Exception {
        mvc.perform(post("/airlineAdmin/employees").param("role", role)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(provisioner, audits);
        verify(employees, never()).save(any());
    }

    @Test
    void requiresRoleParameter() throws Exception {
        mvc.perform(post("/airlineAdmin/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(provisioner, audits);
    }

    static Stream<CurrentActor> unauthorizedActors() {
        return Stream.of(new CurrentActor(17L, "CUSTOMER", "ACTIVE", true),
                new CurrentActor(17L, "FAA_ADMIN", "ACTIVE", true),
                new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", false),
                new CurrentActor(17L, "AIRLINE_EMPLOYEE", "PENDING_EMPLOYEE", true));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedActors")
    void rejectsWrongAccountRoleOrInactiveAccount(CurrentActor caller) throws Exception {
        when(actors.getCurrentActor()).thenReturn(caller);
        expectRejected(body(), 403);
    }

    @Test
    void rejectsEmployeeWithoutAdminMembership() throws Exception {
        when(employees.findByUserIdAndRole(17L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.empty());
        expectRejected(body(), 403);
    }

    @Test
    void rejectsAdminWithoutAirline() throws Exception {
        admin.setAirline(null);
        expectRejected(body(), 403);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(Arguments.of("fName", " "), Arguments.of("lName", "x".repeat(256)),
                Arguments.of("emailAddress", "invalid"), Arguments.of("cpr", "123"),
                Arguments.of("phoneNumber", "123"), Arguments.of("phoneNumberOpeningCode", ""),
                Arguments.of("password", "short"), Arguments.of("password", null),
                Arguments.of("salary", 0), Arguments.of("salary", -1), Arguments.of("salary", null),
                Arguments.of("hireDate", "2026-02-30"), Arguments.of("hireDate", ""),
                Arguments.of("securityQuestion", ""), Arguments.of("securityQuestionAnswer", null));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void validatesBeforeProvisioning(String field, Object value) throws Exception {
        Map<String, Object> body = body();
        body.put(field, value);
        expectRejected(body, 400);
    }

    @Test
    void duplicateAccountDetailsDoNotCreateAnEmployee() throws Exception {
        doThrow(new InformationExistException("Already registered.")).when(provisioner).checkExistingDetails(anyString(), anyString());
        mvc.perform(post("/airlineAdmin/employees").param("role", "PILOT")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body())))
                .andExpect(status().isConflict());
        verify(employees, never()).save(any());
        verify(provisioner, never()).createAccount(any());
        verifyNoInteractions(audits);
    }

    private void expectRejected(Map<String, Object> body, int status) throws Exception {
        mvc.perform(post("/airlineAdmin/employees").param("role", "PILOT")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(status));
        verifyNoInteractions(provisioner, audits);
        verify(employees, never()).save(any());
    }
}
