package com.ga.identity.config;

import com.ga.identity.model.User;
import com.ga.identity.model.UserProfile;
import com.ga.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.core.annotation.Order(0)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class IdentitySeeder implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Override
    @Transactional
    public void run(String... args) {
        account("flighttest.customer@mailsac.com", "Ahmed", "Yousif", "990000001", User.Role.CUSTOMER);
        account("flighttest.faa@mailsac.com", "Fatima", "Salman", "990000002", User.Role.FAA_ADMIN);
        account("flighttest.airline@mailsac.com", "Hassan", "Mahmood", "990000003", User.Role.AIRLINE_EMPLOYEE);
        account("sara.customer@mailsac.com", "Sara", "Ahmed", "990000004", User.Role.CUSTOMER);
        account("omar.customer@mailsac.com", "Omar", "Ali", "990000005", User.Role.CUSTOMER);
        account("layla.customer@mailsac.com", "Layla", "Hassan", "990000006", User.Role.CUSTOMER);
        account("noor.airline@mailsac.com", "Noor", "Khalid", "990000007", User.Role.AIRLINE_EMPLOYEE);
        for (String code : new String[]{"gf", "ek"}) {
            for (int i = 0; i < 3; i++) staff(code + ".crew" + (i + 1) + "@mailsac.com", 20 + (code.equals("gf") ? 0 : 10) + i, User.Role.AIRLINE_EMPLOYEE);
        }
        staff("airport.admin@mailsac.com", 40, User.Role.AIRPORT_EMPLOYEE);
        staff("airport.inspector@mailsac.com", 41, User.Role.AIRPORT_EMPLOYEE);
        staff("maintenance.admin@mailsac.com", 50, User.Role.MAINTENANCE_EMPLOYEE);
        staff("maintenance.technician@mailsac.com", 51, User.Role.MAINTENANCE_EMPLOYEE);
        String[] airports = {"bah", "dxb", "doh"};
        for (int i = 0; i < airports.length; i++) {
            staff("atc." + airports[i] + ".admin@example.com", 60 + i * 2, User.Role.ATC_EMPLOYEE);
            staff("atc." + airports[i] + ".controller@example.com", 61 + i * 2, User.Role.ATC_EMPLOYEE);
        }
    }

    private void staff(String email, int suffix, User.Role role) {
        account(email, "Sample", email.substring(0, email.indexOf('@')), "9900000" + suffix, role);
    }

    private void account(String email, String first, String last, String cpr, User.Role role) {
        if (users.existsByEmailAddress(email)) return;
        User user = new User();
        user.setEmailAddress(email);
        user.setPassword(passwords.encode("TestPassword1"));
        user.setSecurityQuestion("In which city were you born?");
        user.setSecurityQuestionAnswer(passwords.encode("manama"));
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        user.setActive(true);
        user.setPersonalProfile(new UserProfile(first, last, cpr, "+973", "3600" + cpr.substring(5)));
        users.save(user);
    }
}
