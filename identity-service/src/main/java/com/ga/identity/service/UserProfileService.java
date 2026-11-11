package com.ga.identity.service;

import com.ga.identity.model.request.PersonalProfileUpdate;
import com.ga.identity.model.request.response.PersonalProfile;

import com.ga.common.web.exception.InvalidInformationException;
import com.ga.identity.model.UserProfile;
import com.ga.identity.repository.UserProfileRepository;
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
public class UserProfileService {
    private final UserProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public Optional<PersonalProfile> findByUserId(Long userId) {
        return profileRepository.findById(userId).map(this::snapshot);
    }

    @Transactional(readOnly = true)
    public Map<Long, PersonalProfile> findByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return profileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, this::snapshot));
    }

    @Transactional(readOnly = true)
    public boolean existsByCpr(String cpr) {
        return profileRepository.existsByCpr(cpr);
    }

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
