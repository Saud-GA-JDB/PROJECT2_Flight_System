package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.UserProfile;
import com.ga.saudsFlightSystem.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocalPersonalProfileStore implements PersonalProfileStore {
    private final UserProfileRepository profileRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonalProfile> findByUserId(Long userId) {
        return profileRepository.findById(userId).map(this::snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, PersonalProfile> findByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return profileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, this::snapshot));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCpr(String cpr) {
        return profileRepository.existsByCpr(cpr);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Long userId, PersonalProfileUpdate update) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new InvalidInformationException(
                        "No profile information found for this user"));

        if (update.fName() != null)
            profile.setFName(update.fName());
        if (update.lName() != null)
            profile.setLName(update.lName());
        if (update.cpr() != null)
            profile.setCpr(update.cpr());
        if (update.phoneNumberOpeningCode() != null)
            profile.setPhoneNumberOpeningCode(update.phoneNumberOpeningCode());
        if (update.phoneNumber() != null)
            profile.setPhoneNumber(update.phoneNumber());
        if (update.imageUrl() != null)
            profile.setImageUrl(update.imageUrl());
    }

    private PersonalProfile snapshot(UserProfile profile) {
        return new PersonalProfile(
                profile.getFName(), profile.getLName(), profile.getCpr(),
                profile.getPhoneNumberOpeningCode(), profile.getPhoneNumber());
    }
}
