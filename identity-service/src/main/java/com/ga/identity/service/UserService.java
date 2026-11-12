package com.ga.identity.service;

import com.ga.identity.client.NotificationServiceClient;
import com.ga.identity.model.request.EmailRequest;
import org.springframework.beans.factory.annotation.Value;

import com.ga.identity.security.CurrentActorProvider;
import com.ga.identity.security.CurrentActor;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.identity.exception.LoginLimitExceededException;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationExistException;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.identity.model.request.response.PersonalProfile;
import com.ga.identity.service.UserProfileService;
import com.ga.identity.model.request.PersonalProfileUpdate;
import com.ga.identity.model.request.response.AccountSetupResponse;
import com.ga.identity.model.AuditLog;
import com.ga.identity.model.PendingRegistration;
import com.ga.identity.model.User;
import com.ga.identity.model.request.LoginRequest;
import com.ga.identity.model.request.RegistrationRequest;
import com.ga.identity.model.request.UpdateProfileRequest;
import com.ga.identity.model.request.response.ForgetPasswordResponse;
import com.ga.identity.model.request.response.LoginResponse;
import com.ga.identity.repository.*;
import com.ga.identity.security.JWTUtils;
import com.ga.identity.security.MyUserDetails;
import com.ga.identity.validation.EmailValidation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class UserService {
    private static final Logger logger = Logger.getLogger(UserService.class.getName());
    private static final int MAX_DAILY_LOGIN_FAILURES = 3;
    private static final int MAX_DAILY_PASSWORD_RESET_FAILURES = 3;
    private static final ZoneId LOGIN_TIME_ZONE = ZoneId.of("Asia/Bahrain");

    private final UserRepository userRepository;
    private final CurrentActorProvider currentActorProvider;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final PendingRegistrationService pendingRegistrationService;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordService passwordService;
    private final PhoneValidationService phoneValidationService;
    private final NotificationServiceClient notificationServiceClient;
    @Value("${notification.service-key:${NOTIFICATION_SERVICE_KEY:}}")
    private String notificationServiceKey;
    private final UserProfileService userProfileService;
    private final AuditLogService auditLogService;
    private final String UPLOAD_DIR = "uploads/";

    @Autowired
    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder,
                       JWTUtils jwtUtils, @Lazy AuthenticationManager authenticationManager,
                       PendingRegistrationService pendingRegistrationService,
                       PendingRegistrationRepository pendingRegistrationRepository,
                       PasswordService passwordService, PhoneValidationService phoneValidationService, NotificationServiceClient notificationServiceClient,
                       UserProfileService userProfileService,
                       AuditLogService auditLogService,
                       CurrentActorProvider currentActorProvider) {
        this.userRepository = userRepository;
        this.currentActorProvider = currentActorProvider;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
        this.pendingRegistrationService = pendingRegistrationService;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordService = passwordService;
        this.phoneValidationService = phoneValidationService;
        this.notificationServiceClient = notificationServiceClient;
        this.userProfileService = userProfileService;
        this.auditLogService = auditLogService;
    }

    /**
     * Completes account setup after checking the profile details and new password.
     *
     * @param request details needed to finish setup
     * @return the activated user
     */
    @Transactional
    public AccountSetupResponse finishSetup(RegistrationRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"SETUP_REQUIRED".equals(actor.status()))
            throw new InvalidInformationException("This account does not require setup.");
        if (request.getPassword() == null || request.getPassword().isBlank())
            throw new InvalidInformationException("Password is required.");
        if (!passwordService.isValidPassword(request.getPassword()))
            throw new InvalidInformationException("Invalid Password. Password must be at least 8 chars and max 20, at least one uppercase, at least one digit, and must not contain white space,");
        if (request.getFName() == null || request.getFName().isBlank()
                || request.getLName() == null || request.getLName().isBlank()) {
            throw new InvalidInformationException("First name and last name are required.");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()
                || request.getPhoneNumberOpeningCode() == null
                || request.getPhoneNumberOpeningCode().isBlank()) {
            throw new InvalidInformationException(
                    "Phone number and opening code are required.");
        }
        if (!phoneValidationService.isValidPhoneNumber(request.getPhoneNumber(), request.getPhoneNumberOpeningCode()))
            throw new InvalidInformationException("Invalid phone number or country code.");
        if (request.getSecurityQuestion() == null
                || request.getSecurityQuestion().isBlank()
                || request.getSecurityQuestionAnswer() == null
                || request.getSecurityQuestionAnswer().isBlank()) {
            throw new InvalidInformationException("Security question and answer are required.");
        }

        String emailAddress = userRepository.findEmailAddressById(actor.userId())
                .orElseThrow(() -> new InformationNotFoundException("No user with that ID exists."));
        PendingRegistration pending = pendingRegistrationService.getVerifiedRegistration(emailAddress);

        User user = userRepository.findByIdForUpdate(actor.userId())
                .orElseThrow( () -> new InformationNotFoundException("No user with that ID exists."));

        if (user.getStatus() != User.Status.SETUP_REQUIRED)
            throw new InvalidInformationException("This account does not require setup.");

        if (request.getPassword().equals(pending.getCpr()))
            throw new InvalidInformationException("Choose a new password different from your CPR.");

        userProfileService.update(user.getId(), new PersonalProfileUpdate(
                request.getFName(),
                request.getLName(),
                null,
                request.getPhoneNumberOpeningCode(),
                request.getPhoneNumber(),
                null
        ));

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setSecurityQuestion(request.getSecurityQuestion());
        user.setSecurityQuestionAnswer(
                passwordEncoder.encode(
                        request.getSecurityQuestionAnswer()
                                .trim()
                                .toLowerCase(Locale.ROOT)));
        user.setActive(true);
        user.setStatus(User.Status.ACTIVE);

        User savedUser = userRepository.save(user);
        // Only delete pending after the user saves successfully.
        pendingRegistrationRepository.delete(pending);
        String description = "User completed their account setup";
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.USER_SETUP_COMPLETED, AuditLog.EntityType.USER, savedUser.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");
        return new AccountSetupResponse(
                savedUser.getId(), savedUser.getEmailAddress(),
                savedUser.getRole().name(), savedUser.getStatus().name(), savedUser.isActive(),
                request.getFName(), request.getLName(),
                request.getPhoneNumberOpeningCode(), request.getPhoneNumber());
    }

    public User findUserByEmailAddress(String email) {
        return userRepository.findUserByEmailAddress(email);
    }

    /**
     * Logs in the user and limits incorrect passwords to three attempts per day.
     *
     * @param loginRequest user email and password
     * @return the login token
     */
    // failed attempts must stay saved even when authentication fails
    @Transactional(noRollbackFor = AuthenticationException.class)
    public ResponseEntity<?> loginUser(LoginRequest loginRequest) {
        logger.info("Login attempted");

        User user = userRepository.findUserForLogin(loginRequest.getEmail());
        LocalDate today = LocalDate.now(LOGIN_TIME_ZONE);
        if (user != null) {
            if (!today.equals(user.getLoginAttemptsDate())) {
                user.setFailedLoginAttempts(0);
                user.setLoginAttemptsDate(today);
                userRepository.save(user);
            }
            if (user.getFailedLoginAttempts() >= MAX_DAILY_LOGIN_FAILURES) {
                throw new LoginLimitExceededException();
            }
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (BadCredentialsException e) {
            logger.warning("Login failed because the credentials were incorrect");
            if (user != null) {
                user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
                userRepository.save(user);
                if (user.getFailedLoginAttempts() >= MAX_DAILY_LOGIN_FAILURES) {
                    throw new LoginLimitExceededException();
                }
            }
            throw e;
        } catch (AuthenticationException e) {
            logger.warning("Login failed because the credentials were incorrect or the account was unavailable");
            throw e;
        }

        if (user != null) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        MyUserDetails myUserDetails = (MyUserDetails) authentication.getPrincipal();
        String jwt = jwtUtils.generateJwtToken(myUserDetails);

        logger.info("User with id " + myUserDetails.getUser().getId() + " logged in successfully");

        return ResponseEntity.ok(new LoginResponse(jwt));
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Bahrain")
    public void resetDailyLoginAttempts() {
        userRepository.resetDailyLoginAttempts(LocalDate.now(LOGIN_TIME_ZONE));
    }

    /**
     * Resets the password to the user's CPR after checking the security answer.
     *
     * @param email email of the account to reset
     * @param securityQuestionAnswer answer used to check the account owner
     * @return a success message or the reason the reset failed
     */
    @Transactional
    public ResponseEntity<?> forgetPassword(String email, String securityQuestionAnswer) {
        User user = userRepository.findByEmailAddressForUpdate(email);

        if (user == null) {
            logger.warning("Password reset failed because the account was not found");
            return passwordResetRejected();
        }

        LocalDate today = LocalDate.now(LOGIN_TIME_ZONE);
        if (!today.equals(user.getPasswordResetAttemptsDate())) {
            user.setFailedPasswordResetAttempts(0);
            user.setPasswordResetAttemptsDate(today);
        }
        if (user.getFailedPasswordResetAttempts() >= MAX_DAILY_PASSWORD_RESET_FAILURES) {
            return passwordResetRejected();
        }

        String hashedSecurityQuestionAnswer = user.getSecurityQuestionAnswer();
        if (securityQuestionAnswer == null || securityQuestionAnswer.isBlank()
                || hashedSecurityQuestionAnswer == null || hashedSecurityQuestionAnswer.isBlank()
                || !passwordEncoder.matches(securityQuestionAnswer.trim().toLowerCase(Locale.ROOT), hashedSecurityQuestionAnswer)) {
            user.setFailedPasswordResetAttempts(user.getFailedPasswordResetAttempts() + 1);
            userRepository.save(user);
            logger.warning("Password reset failed because the security answer was incorrect");
            return passwordResetRejected();
        }

        PersonalProfile person = userProfileService.findByUserId(user.getId())
                .orElse(null);
        if (person == null || person.getCpr() == null || person.getCpr().isBlank()) {
            throw new InvalidInformationException("Account profile is unavailable.");
        }

        user.setPassword(passwordEncoder.encode(person.getCpr()));
        user.setFailedPasswordResetAttempts(0);
        notificationServiceClient.sendEmail(notificationServiceKey, new EmailRequest(user.getEmailAddress(), "Reset Password", String.format(
                """
                        Dear %s. You have requested a password reset. The new password is your CPR, please use that to sign in and change your password immediately.
                        If you have not requested a password reset, Contact us immediately.
                        Best Regards,
                        Saud Flight System.""",
                        person.getFName()
                )));
        userRepository.save(user);

        String description = "User reset the password for user with id " + user.getId();
        auditLogService.addAuditLog(user, AuditLog.Action.PASSWORD_RESET, AuditLog.EntityType.USER, user.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ForgetPasswordResponse("Your password has been reset. Please check your email."));
    }

    private ResponseEntity<ForgetPasswordResponse> passwordResetRejected() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ForgetPasswordResponse("Email or security question answer is incorrect, or the daily reset limit has been reached."));
    }

    @Transactional
    public ResponseEntity<?> changePassword(String newPassword) {
        if (!passwordService.isValidPassword(newPassword)) {
            logger.warning("Password change rejected because the new password did not meet the requirements");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body("Invalid Password. Password must be at least 8 chars and max 20, at least one uppercase, at least one digit, and must not contain white space,");
        }

        CurrentActor actor = currentActorProvider.getCurrentActor();
        User user = userRepository.findByIdForUpdate(actor.userId())
                .orElseThrow( () -> new InformationNotFoundException("No user with that ID exists."));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        String description = "User changed their own password";
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.PASSWORD_CHANGED, AuditLog.EntityType.USER, user.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");
        return ResponseEntity.status(HttpStatus.OK).body("Success! New Password Has Been Set");
    }

    /**
     * Updates profile details based on what the logged in user is allowed to change.
     * Only FAA admins can update another user's profile.
     *
     * @param userId user to update, or null to update your own profile
     * @param request profile details to change
     * @param image optional profile image
     * @return a message confirming the update
     */
    @Transactional
    public ResponseEntity<?> updateProfile(Long userId, UpdateProfileRequest request, MultipartFile image) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (userId != null) {
            if (!"FAA_ADMIN".equals(actor.role())) {
                throw new IllegalEndpoint("Only FAA admins can update other users profiles");
            }

            if (request.getSecurityQuestion() != null
                    || request.getSecurityQuestionAnswer() != null) {
                throw new IllegalEndpoint("Security question and answer can only be changed through your own profile");
            }

        } else {
            if (request.getFName() != null || request.getLName() != null
                    || request.getCpr() != null
                    || request.getEmailAddress() != null
                    || request.getActive() != null) {
                throw new IllegalEndpoint("Only FAA admins can change these details");
            }
        }

        User profileOwner = userRepository.findByIdForUpdate(userId != null ? userId : actor.userId())
                .orElseThrow( () -> new InformationNotFoundException("No user with that ID exists."));

        PersonalProfile person = userProfileService.findByUserId(profileOwner.getId())
                .orElse(null);

        boolean hasImage = image != null && !image.isEmpty();

        if (person == null && (
                request.getFName() != null || request.getLName() != null
                        || request.getCpr() != null
                        || request.getPhoneNumber() != null
                        || request.getPhoneNumberOpeningCode() != null
                        || hasImage)) {
            throw new InvalidInformationException("No profile information found for this user");
        }

        // validate before updating
        if (request.getFName() != null && request.getFName().isBlank()) {
            throw new InvalidInformationException("First name cannot be blank");
        }
        if (request.getLName() != null && request.getLName().isBlank()) {
            throw new InvalidInformationException("Last name cannot be blank");
        }

        if (request.getActive() != null && request.getActive()) {
            throw new InvalidInformationException(
                    "This endpoint only allows account deactivation");
        }

        if (request.getEmailAddress() != null) {
            if (!EmailValidation.isValid(request.getEmailAddress())) {
                throw new InvalidInformationException("Invalid email format");
            }

            if (!request.getEmailAddress().equals(profileOwner.getEmailAddress())) {
                if (profileOwner.getStatus() == User.Status.SETUP_REQUIRED) {
                    throw new InvalidInformationException(
                            "Complete account setup before changing email");
                }

                if (userRepository.existsByEmailAddress(request.getEmailAddress())) {
                    throw new InformationExistException(
                            "A user with this email already exists.");
                }
            }
        }

        if (request.getCpr() != null) {
            if (!request.getCpr().matches("[0-9]{9}")) {
                throw new InvalidInformationException("CPR must contain exactly 9 digits.");
            }

            if (!request.getCpr().equals(person.getCpr())) {
                if (profileOwner.getStatus() == User.Status.SETUP_REQUIRED) {
                    throw new InvalidInformationException("Complete account setup before changing CPR");
                }

                checkCprAvailable(request.getCpr());
            }
        }

        String phoneNumber = null;
        String openingCode = null;

        if (request.getPhoneNumber() != null || request.getPhoneNumberOpeningCode() != null) {
            // keep the existing value if only one part of the phone number is changed
            phoneNumber = person.getPhoneNumber();
            openingCode = person.getPhoneNumberOpeningCode();

            if (request.getPhoneNumber() != null) {
                phoneNumber = request.getPhoneNumber();
            }
            if (request.getPhoneNumberOpeningCode() != null) {
                openingCode = request.getPhoneNumberOpeningCode();
            }

            if (phoneNumber == null || phoneNumber.isBlank()
                    || openingCode == null || openingCode.isBlank()
                    || !phoneValidationService.isValidPhoneNumber(phoneNumber, openingCode)) {
                throw new InvalidInformationException("Invalid phone number or country code.");
            }
        }

        if (request.getSecurityQuestion() != null || request.getSecurityQuestionAnswer() != null) {
            if (request.getSecurityQuestion() == null
                    || request.getSecurityQuestion().isBlank()
                    || request.getSecurityQuestionAnswer() == null
                    || request.getSecurityQuestionAnswer().isBlank()) {
                throw new InvalidInformationException("Security question and answer are required together.");
            }
        }


        boolean isRequestNonEmpty = request.getFName() != null || request.getLName() != null || request.getCpr() != null
                || request.getEmailAddress() != null || request.getPhoneNumber() != null || request.getPhoneNumberOpeningCode() != null
                || request.getSecurityQuestion() != null || hasImage;

        boolean isDeactivation = request.getActive() != null &&
                (profileOwner.isActive() || profileOwner.getStatus() != User.Status.DEACTIVATED);

        String imageUrl = hasImage ? saveProfileImage(image) : null;

        if (person != null) {
            userProfileService.update(profileOwner.getId(), new PersonalProfileUpdate(
                    request.getFName(),
                    request.getLName(),
                    request.getCpr(),
                    openingCode,
                    phoneNumber,
                    imageUrl
            ));
        }

        if (request.getEmailAddress() != null)
            profileOwner.setEmailAddress(request.getEmailAddress());

        if (request.getSecurityQuestion() != null) {
            profileOwner.setSecurityQuestion(request.getSecurityQuestion());
            profileOwner.setSecurityQuestionAnswer(passwordEncoder.encode(request.getSecurityQuestionAnswer().trim().toLowerCase()));
        }

        if (request.getActive() != null) {
            profileOwner.setActive(false);
            profileOwner.setStatus(User.Status.DEACTIVATED);
        }

        userRepository.save(profileOwner);

        if (isRequestNonEmpty) {
            String description;
            if (userId != null)
                description = "FAA admin updated the profile of user with id " + profileOwner.getId();
            else
                description = "User updated their own profile";


            auditLogService.addAuditLogForActor(actor, AuditLog.Action.PROFILE_UPDATED, AuditLog.EntityType.USER, profileOwner.getId(), description);

            logger.info(description + " (user id: " + actor.userId() + ")");
        }

        if (isDeactivation) {
            String description = "FAA admin deactivated user with id " + profileOwner.getId();
            auditLogService.addAuditLogForActor(actor, AuditLog.Action.USER_DEACTIVATED, AuditLog.EntityType.USER, profileOwner.getId(), description);
            logger.info(description + " (user id: " + actor.userId() + ")");
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body("Profile updated successfully");
    }

    /*
    Helper Methods
     */

    private void checkCprAvailable(String cpr) {
        if (userProfileService.existsByCpr(cpr)) {
            throw new InformationExistException("A person with this CPR already exists.");
        }
    }

    private String saveProfileImage(MultipartFile image) {
        BufferedImage profileImage;

        try (InputStream inputStream = image.getInputStream()) {
            profileImage = ImageIO.read(inputStream);
        } catch (IOException e) {
            throw new InvalidInformationException("Invalid image file");
        }

        if (profileImage == null)
            throw new InvalidInformationException("Please upload a valid image");

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath();

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String uniqueId = UUID.randomUUID().toString();
            String fileName = uniqueId + ".png";
            Path filePath = uploadPath.resolve(fileName);

            if (!ImageIO.write(profileImage, "png", filePath.toFile()))
                throw new IOException("Could not save image");

            return UPLOAD_DIR + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Could not save image", e);
        }
    }

    public static boolean isAllowedEndpoint(String endpoint, User.Role role) {
        switch (role) {
            case CUSTOMER:
                if (endpoint.trim().equalsIgnoreCase("customer")) return true;
                break;
            case FAA_ADMIN:
                if (endpoint.trim().equalsIgnoreCase("faaadmin")) return true;
                break;
            case AIRPORT_EMPLOYEE:
                if (endpoint.trim().equalsIgnoreCase("airportEmployee")) return true;
                break;
            case ATC_EMPLOYEE:
                if (endpoint.trim().equalsIgnoreCase("atcEmployee")) return true;
                break;
            case AIRLINE_EMPLOYEE:
                if (endpoint.trim().equalsIgnoreCase("airlineEmployee")) return true;
                break;
            case MAINTENANCE_EMPLOYEE:
                if (endpoint.trim().equalsIgnoreCase("maintenanceEmployee"))
                    return true;
                break;
            default:
                throw new InvalidInformationException("Role doesn't exits");
        }
        return false;
    }

    /** Legacy adapter for existing test fixtures; application callers use CurrentActorProvider. */
    @Deprecated
    public static User getCurrentLoggedInUser() {
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userDetails.getUser();
    }
}
