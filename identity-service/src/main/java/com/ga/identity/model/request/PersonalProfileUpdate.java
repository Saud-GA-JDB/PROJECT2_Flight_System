package com.ga.identity.model.request;

public record PersonalProfileUpdate(
        String fName,
        String lName,
        String cpr,
        String phoneNumberOpeningCode,
        String phoneNumber,
        String imageUrl
) {}
