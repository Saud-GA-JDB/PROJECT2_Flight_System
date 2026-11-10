package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Person;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LegacyPersonalProfileStore implements PersonalProfileStore {
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AirlineEmployeeRepository airlineEmployeeRepository;
    private final FAAAdminRepository faaAdminRepository;
    private final MaintenanceEmployeeRepository maintenanceEmployeeRepository;
    private final AirportEmployeeRepository airportEmployeeRepository;
    private final ATCEmployeeRepository atcEmployeeRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonalProfile> findByUserId(Long userId) {
        return userRepository.findById(userId)
                .map(this::profileOf)
                .map(person -> new PersonalProfile(
                        person.getFName(),
                        person.getLName(),
                        person.getCpr(),
                        person.getPhoneNumberOpeningCode(),
                        person.getPhoneNumber()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCpr(String cpr) {
        return customerRepository.existsByCpr(cpr)
                || airlineEmployeeRepository.existsByCpr(cpr)
                || faaAdminRepository.existsByCpr(cpr)
                || maintenanceEmployeeRepository.existsByCpr(cpr)
                || airportEmployeeRepository.existsByCpr(cpr)
                || atcEmployeeRepository.existsByCpr(cpr);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Long userId, PersonalProfileUpdate update) {
        Person person = userRepository.findById(userId)
                .map(this::profileOf)
                .orElseThrow(() -> new InvalidInformationException(
                        "No profile information found for this user"));

        if (update.fName() != null)
            person.setFName(update.fName());
        if (update.lName() != null)
            person.setLName(update.lName());
        if (update.cpr() != null)
            person.setCpr(update.cpr());
        if (update.phoneNumberOpeningCode() != null)
            person.setPhoneNumberOpeningCode(update.phoneNumberOpeningCode());
        if (update.phoneNumber() != null)
            person.setPhoneNumber(update.phoneNumber());
        if (update.imageUrl() != null)
            person.setImageUrl(update.imageUrl());
    }

    private Person profileOf(User user) {
        if (user.getRole() == null)
            return null;

        return switch (user.getRole()) {
            case CUSTOMER -> user.getCustomer();
            case AIRLINE_EMPLOYEE -> user.getAirlineEmployee();
            case AIRPORT_EMPLOYEE -> user.getAirportEmployee();
            case MAINTENANCE_EMPLOYEE -> user.getMaintenanceEmployee();
            case ATC_EMPLOYEE -> user.getAtcEmployee();
            case FAA_ADMIN -> user.getFaaAdmin();
        };
    }
}
