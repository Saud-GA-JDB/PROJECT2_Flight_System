package com.ga.saudsFlightSystem.identity;

public record PersonalProfileUpdate(
        String fName,
        String lName,
        String cpr,
        String phoneNumberOpeningCode,
        String phoneNumber,
        String imageUrl
) {}
