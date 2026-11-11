package com.ga.saudsFlightSystem.support;

import com.ga.identity.config.IdentitySeeder;
import com.ga.identity.controller.InternalIdentityController;
import com.ga.identity.service.EmployeeAccountService;
import com.ga.identity.security.MyUserDetails;
import com.ga.identity.security.MyUserDetailsService;
import com.ga.saudsFlightSystem.identity.*;
import com.ga.saudsFlightSystem.security.*;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@TestConfiguration
@PropertySource("classpath:identity-fixtures.properties")
@ComponentScan(basePackages = "com.ga.identity", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ga\\.identity\\.config\\..*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {com.ga.identity.IdentityApplication.class, com.ga.identity.security.SecurityConfiguration.class})
        })
@EntityScan({"com.ga.identity.model", "com.ga.saudsFlightSystem.model"})
@EnableJpaRepositories(basePackages = {"com.ga.identity.repository", "com.ga.saudsFlightSystem.repository"}, nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import(IdentitySeeder.class)
public class IdentityTestConfiguration {
    @Bean
    @org.springframework.core.annotation.Order(0)
    public org.springframework.security.web.SecurityFilterChain fixtureIdentityRoutes(org.springframework.security.config.annotation.web.builders.HttpSecurity http, com.ga.identity.security.JwtRequestFilter filter) throws Exception {
        http.securityMatcher("/auth/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/users/login", "/auth/users/register/email", "/auth/users/verification", "/auth/users/forgetPassword").permitAll()
                        .requestMatchers("/auth/users/setup").authenticated()
                        .anyRequest().hasAuthority("ACCOUNT_ACTIVE"));
        http.addFilterBefore(filter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public com.ga.identity.security.JwtRequestFilter fixtureJwtFilter() { return new com.ga.identity.security.JwtRequestFilter(); }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.ga.identity.security.JwtRequestFilter> fixtureFilterRegistration(com.ga.identity.security.JwtRequestFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public IdentityFixtures identityFixtures(EntityManager em) { return new IdentityFixtures(em); }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(MyUserDetailsService details, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    @Primary
    public com.ga.identity.service.EmailService identityTestMail() {
        return org.mockito.Mockito.mock(com.ga.identity.service.EmailService.class);
    }

    @Bean
    @Primary
    public CurrentActorProvider fixtureActorProvider(AccountLookup accounts) {
        return new CurrentActorProvider() {
            public CurrentActor getCurrentActor() {
                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                return principal instanceof MyUserDetails details ? IdentityFixtures.actor(details.getUser()) : (CurrentActor) principal;
            }
            public CurrentActor getFreshCurrentActor() {
                AccountSnapshot account = accounts.findAccountStateById(getCurrentActor().userId()).orElseThrow();
                return new CurrentActor(account.userId(), account.role(), account.status(), account.active());
            }
        };
    }

    @Bean
    @Primary
    public IdentityServiceClient fixtureIdentityClient(InternalIdentityController controller, EmployeeAccountService provisioner) {
        return new IdentityServiceClient() {
            public AccountSnapshot account(Long id) { return call(() -> snapshot(controller.account(id))); }
            public Map<Long, AccountSnapshot> accounts(List<Long> ids) {
                return controller.accounts(ids).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> snapshot(e.getValue())));
            }
            public AccountSnapshot accountByEmail(String email) { return call(() -> snapshot(controller.accountByEmail(email))); }
            public Map<Long, PersonalProfile> profiles(List<Long> ids) {
                return controller.profiles(ids).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var p = e.getValue();
                    return new PersonalProfile(p.getFName(), p.getLName(), p.getCpr(), p.getPhoneNumberOpeningCode(), p.getPhoneNumber());
                }));
            }
            public void checkDetails(ExistingDetails request) {
                call(() -> { provisioner.checkExistingDetails(request.emailAddress(), request.cpr()); return null; });
            }
            public ProvisionedAccount create(EmployeeAccountRequest r) {
                return call(() -> {
                    var result = provisioner.createAccount(new com.ga.identity.model.request.EmployeeAccountRequest(
                            com.ga.identity.model.request.EmployeeAccountRequest.AccountType.valueOf(r.accountType().name()), r.employeeId(),
                            r.emailAddress(), r.password(), r.securityQuestion(), r.normalizedSecurityAnswer(),
                            r.fName(), r.lName(), r.cpr(), r.phoneNumberOpeningCode(), r.phoneNumber()));
                    // These legacy fixtures share one rollback transaction; they do not exercise HTTP provisioning recovery.
                    provisioner.activate(r.accountType().name() + ":" + r.employeeId(), result.userId());
                    return new ProvisionedAccount(result.userId(), result.emailAddress());
                });
            }
            public void activate(String key, Long userId) { provisioner.activate(key, userId); }
            public void cancel(String key) { provisioner.cancel(key); }
            public CurrentActor authenticate(String authorization) {
                var actor = controller.authenticate(authorization);
                return new CurrentActor(actor.userId(), actor.role(), actor.status(), actor.active());
            }
        };
    }

    private static AccountSnapshot snapshot(com.ga.identity.model.request.response.AccountSnapshot account) {
        return new AccountSnapshot(account.userId(), account.role(), account.status(), account.active(), account.emailAddress());
    }

    private static <T> T call(Supplier<T> operation) {
        try { return operation.get(); }
        catch (com.ga.identity.exception.InformationExistException ex) { throw new com.ga.saudsFlightSystem.exception.InformationExistException(ex.getMessage()); }
        catch (com.ga.identity.exception.InformationNotFoundException ex) { throw new com.ga.saudsFlightSystem.exception.InformationNotFoundException(ex.getMessage()); }
        catch (com.ga.identity.exception.InvalidInformationException ex) { throw new com.ga.saudsFlightSystem.exception.InvalidInformationException(ex.getMessage()); }
    }
}
