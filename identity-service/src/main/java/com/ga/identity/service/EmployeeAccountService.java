package com.ga.identity.service;

import com.ga.identity.model.request.EmployeeAccountRequest;
import com.ga.identity.model.request.response.ProvisionedAccount;

import com.ga.identity.exception.InformationExistException;
import com.ga.identity.exception.InformationNotFoundException;
import com.ga.identity.exception.InvalidInformationException;
import com.ga.identity.model.User;
import com.ga.identity.model.UserProfile;
import com.ga.identity.repository.UserRepository;
import com.ga.identity.validation.EmailValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeAccountService {
    private final UserRepository userRepository;
    private final PendingRegistrationService pendingRegistrationService;
    private final PasswordEncoder passwordEncoder;

    public void checkExistingDetails(String emailAddress, String cpr) {
        pendingRegistrationService.checkExistingDetails(emailAddress, cpr);
    }

    @Transactional
    public ProvisionedAccount createAccount(EmployeeAccountRequest request) {
        if (request.accountType() == null || request.employeeId() == null || request.employeeId() <= 0
                || !EmailValidation.isValid(request.emailAddress()) || request.cpr() == null || !request.cpr().matches("[0-9]{9}")
                || request.password() == null || request.password().isBlank()
                || request.securityQuestion() == null || request.securityQuestion().isBlank()
                || request.normalizedSecurityAnswer() == null || request.normalizedSecurityAnswer().isBlank()) {
            throw new InvalidInformationException("Valid employee and account details are required.");
        }
        String key = request.accountType().name() + ":" + request.employeeId();
        User existing = userRepository.findByProvisioningKeyForUpdate(key).orElse(null);
        if (existing != null) {
            if (!existing.getEmailAddress().equals(request.emailAddress()) || !existing.getPersonalProfile().getCpr().equals(request.cpr())) {
                throw new InformationExistException("This employee already has a different account.");
            }
            return new ProvisionedAccount(existing.getId(), existing.getEmailAddress());
        }
        checkExistingDetails(request.emailAddress(), request.cpr());
        User user = new User();
        user.setEmailAddress(request.emailAddress());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setSecurityQuestion(request.securityQuestion());
        user.setSecurityQuestionAnswer(passwordEncoder.encode(request.normalizedSecurityAnswer()));
        user.setRole(User.Role.valueOf(request.accountType().name()));
        user.setStatus(User.Status.PENDING_EMPLOYEE);
        user.setActive(false);
        user.setProvisioningKey(key);
        user.setPersonalProfile(new UserProfile(request.fName(), request.lName(), request.cpr(), request.phoneNumberOpeningCode(), request.phoneNumber()));
        userRepository.saveAndFlush(user);
        return new ProvisionedAccount(user.getId(), user.getEmailAddress());
    }

    @Transactional
    public void activate(String key, Long userId) {
        User user = userRepository.findByProvisioningKeyForUpdate(key)
                .orElseThrow(() -> new InformationNotFoundException("Pending account not found."));
        if (!user.getId().equals(userId)) throw new InvalidInformationException("Employee account does not match.");
        if (user.getStatus() == User.Status.PENDING_EMPLOYEE) {
            user.setStatus(User.Status.ACTIVE);
            user.setActive(true);
        }
    }

    @Transactional
    public void cancel(String key) {
        userRepository.findByProvisioningKeyForUpdate(key)
                .filter(user -> user.getStatus() == User.Status.PENDING_EMPLOYEE)
                .ifPresent(userRepository::delete);
    }
}
