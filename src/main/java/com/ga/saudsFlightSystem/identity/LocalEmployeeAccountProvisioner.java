package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.AirportEmployee;
import com.ga.saudsFlightSystem.model.ATCEmployee;
import com.ga.saudsFlightSystem.model.MaintenanceEmployee;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.repository.UserRepository;
import com.ga.saudsFlightSystem.service.PendingRegistrationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocalEmployeeAccountProvisioner implements EmployeeAccountProvisioner {
    private final UserRepository userRepository;
    private final PendingRegistrationService pendingRegistrationService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Override
    public void checkExistingDetails(String emailAddress, String cpr) {
        pendingRegistrationService.checkExistingDetails(emailAddress, cpr);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProvisionedAccount createAccount(EmployeeAccountRequest request) {
        User user = new User();
        user.setEmailAddress(request.emailAddress());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setSecurityQuestion(request.securityQuestion());
        user.setSecurityQuestionAnswer(
                passwordEncoder.encode(request.normalizedSecurityAnswer()));
        user.setRole(User.Role.valueOf(request.accountType().name()));
        user.setStatus(User.Status.ACTIVE);
        user.setActive(true);

        // keeps the current profile links until the profile migration
        switch (request.accountType()) {
            case AIRLINE_EMPLOYEE -> user.setAirlineEmployee(
                    entityManager.getReference(AirlineEmployee.class, request.employeeId()));
            case AIRPORT_EMPLOYEE -> user.setAirportEmployee(
                    entityManager.getReference(AirportEmployee.class, request.employeeId()));
            case MAINTENANCE_EMPLOYEE -> user.setMaintenanceEmployee(
                    entityManager.getReference(MaintenanceEmployee.class, request.employeeId()));
            case ATC_EMPLOYEE -> user.setAtcEmployee(
                    entityManager.getReference(ATCEmployee.class, request.employeeId()));
        }

        User saved = userRepository.save(user);
        return new ProvisionedAccount(saved.getId(), saved.getEmailAddress());
    }
}
