package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.model.Customer;
import com.ga.saudsFlightSystem.model.Person;
import com.ga.saudsFlightSystem.model.PendingRegistration;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.LoginRequest;
import com.ga.saudsFlightSystem.model.request.RegistrationRequest;
import com.ga.saudsFlightSystem.model.request.UpdateProfileRequest;
import com.ga.saudsFlightSystem.model.request.response.ForgetPasswordResponse;
import com.ga.saudsFlightSystem.model.request.response.LoginResponse;
import com.ga.saudsFlightSystem.repository.PendingRegistrationRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import com.ga.saudsFlightSystem.repository.CustomerRepository;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.FAAAdminRepository;
import com.ga.saudsFlightSystem.security.JWTUtils;
import com.ga.saudsFlightSystem.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final PendingRegistrationService pendingRegistrationService;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordService passwordService;
    private final PhoneValidationService phoneValidationService;
    private final EmailService emailService;
    private final CustomerRepository customerRepository;
    private final AirlineEmployeeRepository airlineEmployeeRepository;
    private final FAAAdminRepository faaAdminRepository;
    private final String UPLOAD_DIR = "uploads/";

    @Autowired
    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder,
                       JWTUtils jwtUtils, @Lazy AuthenticationManager authenticationManager,
                       PendingRegistrationService pendingRegistrationService,
                       PendingRegistrationRepository pendingRegistrationRepository,
                       PasswordService passwordService, PhoneValidationService phoneValidationService, EmailService emailService,
                       CustomerRepository customerRepository, AirlineEmployeeRepository airlineEmployeeRepository,
                       FAAAdminRepository faaAdminRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
        this.pendingRegistrationService = pendingRegistrationService;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordService = passwordService;
        this.phoneValidationService = phoneValidationService;
        this.emailService = emailService;
        this.customerRepository = customerRepository;
        this.airlineEmployeeRepository = airlineEmployeeRepository;
        this.faaAdminRepository = faaAdminRepository;
    }

    public User finishSetup(RegistrationRequest request) {
        User user = getCurrentLoggedInUser();

        if (user == null || user.getStatus() != User.Status.SETUP_REQUIRED) {
            throw new InvalidInformationException("This account does not require setup.");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidInformationException("Password is required.");
        }
        if (!passwordService.isValidPassword(request.getPassword())) {
            throw new InvalidInformationException("Invalid Password. Password must be at least 8 chars and max 20, at least one uppercase, at least one digit, and must not contain white space,");
        }
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
        if (phoneValidationService.isValidPhoneNumber(request.getPhoneNumber(), request.getPhoneNumberOpeningCode()))
            throw new InvalidInformationException(
                    "Invalid phone number or country code.");
        if (request.getSecurityQuestion() == null
                || request.getSecurityQuestion().isBlank()
                || request.getSecurityQuestionAnswer() == null
                || request.getSecurityQuestionAnswer().isBlank()) {
            throw new InvalidInformationException(
                    "Security question and answer are required.");
        }

        PendingRegistration pending = pendingRegistrationService.getVerifiedRegistration(
                user.getEmailAddress());
        if (request.getPassword().equals(pending.getCpr())) {
            throw new InvalidInformationException("Choose a new password different from your CPR.");
        }

        Customer customer = user.getCustomer();
        customer.setFName(request.getFName());
        customer.setLName(request.getLName());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setPhoneNumberOpeningCode(request.getPhoneNumberOpeningCode());

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
        return savedUser;
    }

    public User findUserByEmailAddress(String email) {
        return userRepository.findUserByEmailAddress(email);
    }

    public ResponseEntity<?> loginUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MyUserDetails myUserDetails = (MyUserDetails) authentication.getPrincipal();
        String jwt = jwtUtils.generateJwtToken(myUserDetails);
        return ResponseEntity.ok(new LoginResponse(jwt));
    }
    /*
     * boolean

     * */
    public ResponseEntity<?> forgetPassword(String email, String securityQuestionAnswer) {
        /*TODO: Continue here----------------------*/
        if (!userRepository.existsByEmailAddress(email))
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ForgetPasswordResponse("Error: Email does not exist."));

        User user = findUserByEmailAddress(email);
        String hashedSecurityQuestionAnswer = user.getSecurityQuestionAnswer();
        if (securityQuestionAnswer == null || !passwordEncoder.matches(securityQuestionAnswer.trim().toLowerCase(), hashedSecurityQuestionAnswer))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ForgetPasswordResponse("Error: Email or security question answer is incorrect"));

        user.setPassword(passwordEncoder.encode(user.getCustomer().getCpr())); // TODO: check what would happen if a user that havent setup tries to forget password bc there wouldnt be a customer connected right?
        // TODO: Should i handle an exception here with try catch? bc sending an email may fail
        emailService.sendEmail(user.getEmailAddress(), "Reset Password", String.format(
                """
                        Dear %s. You have requested a password reset. The new password is your CPR, please use that to sign in and change your password immediately.
                        If you have not requested a password reset, Call us immediately.
                        Best Regards,
                        Saud Flight System.""",
                        user.getCustomer().getFName()
                ));
        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ForgetPasswordResponse("You're Password has been reset. Please check you're mail"));
    }

    public ResponseEntity<?> changePassword(String newPassword) {
        if (!passwordService.isValidPassword(newPassword))
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body("Invalid Password. Password must be at least 8 chars and max 20, at least one uppercase, at least one digit, and must not contain white space,");

        User user = getCurrentLoggedInUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.OK).body("Success! New Password Has Been Set");
    }

    public ResponseEntity<?> updateProfile(Long userId, UpdateProfileRequest request, MultipartFile image) {
        User user = getCurrentLoggedInUser();
        User profileOwner = user;

        if (userId != null) {
            if (!UserService.isAllowedEndpoint("faaadmin", user.getRole())
                    || user.getFaaAdmin() == null) {
                throw new IllegalEndpoint("Only FAA admins can update other users profiles");
            }

            if (request.getSecurityQuestion() != null
                    || request.getSecurityQuestionAnswer() != null) {
                throw new IllegalEndpoint("Security question and answer can only be changed through your own profile");
            }

            profileOwner = userRepository.findById(userId)
                    .orElseThrow(() -> new InformationNotFoundException("No user with that ID exists."));
        } else {
            if (request.getFName() != null || request.getLName() != null
                    || request.getCpr() != null
                    || request.getEmailAddress() != null
                    || request.getActive() != null) {
                throw new IllegalEndpoint("Only FAA admins can change these details");
            }
        }

        Person person = null;

        if (profileOwner.getRole() == User.Role.CUSTOMER) {
            person = profileOwner.getCustomer();
        } else if (profileOwner.getRole() == User.Role.AIRLINE_EMPLOYEE) {
            person = profileOwner.getAirlineEmployee();
        } else if (profileOwner.getRole() == User.Role.FAA_ADMIN) {
            person = profileOwner.getFaaAdmin();
        }

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
            if (!EmailService.validateEmailFormat(request.getEmailAddress())) {
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


        if (hasImage) {
            saveProfileImage(person, image);
        }

        if (request.getFName() != null) {
            person.setFName(request.getFName());
        }
        if (request.getLName() != null) {
            person.setLName(request.getLName());
        }
        if (request.getCpr() != null) {
            person.setCpr(request.getCpr());
        }
        if (request.getEmailAddress() != null) {
            profileOwner.setEmailAddress(request.getEmailAddress());
        }
        if (phoneNumber != null) {
            person.setPhoneNumber(phoneNumber);
            person.setPhoneNumberOpeningCode(openingCode);
        }

        if (request.getSecurityQuestion() != null) {
            profileOwner.setSecurityQuestion(request.getSecurityQuestion());
            profileOwner.setSecurityQuestionAnswer(passwordEncoder.encode(request.getSecurityQuestionAnswer().trim().toLowerCase()));
        }

        if (request.getActive() != null) {
            profileOwner.setActive(false);
            profileOwner.setStatus(User.Status.DEACTIVATED);
        }

        userRepository.save(profileOwner);

        return ResponseEntity.status(HttpStatus.OK)
                .body("Profile updated successfully");
    }

    /*
    Helper Methods
     */

    private void checkCprAvailable(String cpr) {
        if (customerRepository.existsByCpr(cpr)
                || airlineEmployeeRepository.existsByCpr(cpr)
                || faaAdminRepository.existsByCpr(cpr)) {
            throw new InformationExistException("A person with this CPR already exists.");
        }
    }

    private void saveProfileImage(Person person, MultipartFile image) {
        String originalFileName = image.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()
                || originalFileName.contains("/")
                || originalFileName.contains("\\")) {
            throw new InvalidInformationException("Invalid image filename");
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath();

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String uniqueId = UUID.randomUUID().toString();
            String fileName = uniqueId + "_" + originalFileName;
            Path filePath = uploadPath.resolve(fileName);

            image.transferTo(filePath);
            person.setImageUrl(UPLOAD_DIR + fileName);
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
            case AIRLINE_EMPLOYEE:
                if (endpoint.trim().equalsIgnoreCase("airlineEmployee")) return true;
                break;
            default:
                throw new InvalidInformationException("Role doesn't exits");
        }
        return false;
    }

    public static User getCurrentLoggedInUser() {
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userDetails.getUser();
    }
}
