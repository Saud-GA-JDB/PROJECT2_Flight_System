package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.PendingRegistration;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.Customer;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.response.RegistrationResponse;
import com.ga.saudsFlightSystem.repository.PendingRegistrationRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import com.ga.saudsFlightSystem.repository.CustomerRepository;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.FAAAdminRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class PendingRegistrationService {
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AirlineEmployeeRepository airlineEmployeeRepository;
    private final FAAAdminRepository faaAdminRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(PendingRegistrationService.class.getName());

    private static final int maxFailedAttempts = 3;
    private static final int expireTimeInMin = 10;

    public void checkExistingDetails(String email, String cpr) {
        if (email == null || email.isBlank() || cpr == null || cpr.isBlank()) {
            throw new InvalidInformationException("Email and CPR are required.");
        }
        if (customerRepository.existsByCpr(cpr)
                || airlineEmployeeRepository.existsByCpr(cpr)
                || faaAdminRepository.existsByCpr(cpr)) {
            throw new InformationExistException("A person with this CPR already exists.");
        }
        if (userRepository.existsByEmailAddress(email)) {
            throw new InformationExistException("A user with this email already exists.");
        }
    }

    public ResponseEntity<?> sendCode(String email, String cpr) {
        if(!EmailService.validateEmailFormat(email)) throw new InvalidInformationException("invalid Email Format");
        if (cpr == null || !cpr.matches("[0-9]{9}")) {
            throw new InvalidInformationException("CPR must contain exactly 9 digits.");
        }

        checkExistingDetails(email, cpr);
        checkPending(pendingRegistrationRepository.findByEmailAddress(email));
        checkPending(pendingRegistrationRepository.findByCpr(cpr));

        // Generates a number from 100000 to 999999.
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);

        PendingRegistration pending = new PendingRegistration();
        pending.setEmailAddress(email);
        pending.setCpr(cpr);
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(expireTimeInMin));
        pending.setFailedAttempts(0);
        pending.setVerified(false);
        pendingRegistrationRepository.save(pending);

        emailService.sendEmail(email, "Verify your email",
                "Your verification code is " + code + ". It expires in 10 minutes.");
        logger.info("Verification email sent for pending registration with id " + pending.getId());

        return ResponseEntity.ok(new RegistrationResponse("Verification email requested."));
    }

    private void checkPending(PendingRegistration pending) {
        if (pending == null) {
            return;
        }
        if (pending.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidInformationException(
                    "A registration is already pending. Complete it or wait until it expires.");
        }
        pendingRegistrationRepository.delete(pending);
    }

    // Incorrect verification attempts must still be saved when validation fails.
    @Transactional(noRollbackFor = InvalidInformationException.class)
    public ResponseEntity<?> verify(String email, String code) {
        PendingRegistration pending = checkCode(email, code);

        User user = userRepository.findUserByEmailAddress(pending.getEmailAddress());
        if (user == null) {
            checkExistingDetails(pending.getEmailAddress(), pending.getCpr());

            Customer customer = new Customer();
            customer.setCpr(pending.getCpr());

            user = new User();
            user.setEmailAddress(pending.getEmailAddress());
            user.setPassword(passwordEncoder.encode(pending.getCpr()));
            user.setRole(User.Role.CUSTOMER);
            user.setActive(false);
            user.setStatus(User.Status.SETUP_REQUIRED);
            user.setCustomer(customer);
            userRepository.save(user);
            String description = "User registered an account after verifying their email";
            auditLogService.addAuditLog(user, AuditLog.Action.USER_REGISTERED, AuditLog.EntityType.USER, user.getId(), description);
            logger.info(description + " (user id: " + user.getId() + ")");
        } else {
            // Repeating verification must not reset an existing password.
            if (user.getStatus() != User.Status.SETUP_REQUIRED
                    || user.getCustomer() == null
                    || !pending.getCpr().equals(user.getCustomer().getCpr())) {
                throw new InformationExistException("A user with this email already exists.");
            }
        }

        pending.setVerified(true);
        pendingRegistrationRepository.save(pending);
        logger.info("Email verification completed for user with id " + user.getId());
        return ResponseEntity.ok(new RegistrationResponse(
                "Verification successful. Your password is your CPR. Please log in and finish setup."));
    }

    public PendingRegistration getVerifiedRegistration(String email) {
        PendingRegistration pending =
                pendingRegistrationRepository.findByEmailAddress(email);
        if (pending == null) {
            throw new InformationNotFoundException("No pending registration found.");
        }
        if (!pending.isVerified()) {
            throw new InvalidInformationException("Verify your email first.");
        }
        return pending;
    }

    private PendingRegistration checkCode(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new InvalidInformationException("Email and code are required.");
        }
        PendingRegistration pending = pendingRegistrationRepository.findByEmailAddress(email);
        if (pending == null) {
            throw new InformationNotFoundException("No pending registration found.");
        }
        if (!pending.getExpiresAt().isAfter(LocalDateTime.now())) {
            logger.warning("Email verification failed because the registration expired");
            throw new InvalidInformationException("Registration expired. Request a new code.");
        }
        if (pending.getFailedAttempts() >= maxFailedAttempts) {
            logger.warning("Email verification rejected because the maximum number of incorrect attempts was reached");
            throw new InvalidInformationException(
                    "Too many incorrect attempts. Request a new code after expiry.");
        }
        if (!passwordEncoder.matches(code, pending.getCodeHash())) {
            pending.setFailedAttempts(pending.getFailedAttempts() + 1);
            pendingRegistrationRepository.save(pending);
            logger.warning("Email verification failed because the code was incorrect");
            throw new InvalidInformationException("Incorrect verification code.");
        }
        return pending;
    }
}
